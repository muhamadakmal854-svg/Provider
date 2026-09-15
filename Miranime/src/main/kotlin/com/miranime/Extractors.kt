package com.miranime

import android.util.Base64
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.LuluStream
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Extractor untuk AbyssCDN / Hydrax / Sora player.
 * Melakukan dekripsi blok AES-CTR dan menghasilkan link MP4 Sora yang boleh dimainkan terus.
 * Menyokong abyssplayer.com dan abyss.to.
 */
open class AbyssCdn(
    override val name: String = "AbyssCDN",
    override val mainUrl: String = "https://abysscdn.com"
) : ExtractorApi() {
    override val requiresReferer = false

    private fun decryptAesCtr(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val spec = SecretKeySpec(key, "AES")
        val parameterSpec = IvParameterSpec(iv)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, spec, parameterSpec)
        return cipher.doFinal(ciphertext)
    }

    private fun md5(input: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input)
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        runCatching {
            val cleanUrl = url.replace("\\", "").trim()
            val domain = try {
                val u = java.net.URL(cleanUrl)
                "${u.protocol}://${u.host}/"
            } catch (_: Exception) { "$mainUrl/" }

            val response = app.get(
                cleanUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to domain
                )
            )
            val pageHtml = response.text

            // 1. Direct m3u8 in page if present
            val unpacked = runCatching { getAndUnpack(pageHtml) }.getOrDefault("")
            val m3u8Regex = Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
            m3u8Regex.findAll(unpacked.ifBlank { pageHtml }).forEach { match ->
                generateM3u8(name, match.value, domain).forEach(callback)
            }

            // 2. Decrypt datas block using Sora stream algorithm
            val base64Str = Regex("const\\s+datas\\s*=\\s*\"([^\"]+)\"").find(pageHtml)?.groupValues?.get(1) ?: return@runCatching
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            val latin1Str = String(decodedBytes, Charsets.ISO_8859_1)
            val json = JSONObject(latin1Str)
            val slug = json.optString("slug")
            val userId = json.opt("user_id")?.toString().orEmpty()
            val md5Id = json.opt("md5_id")?.toString().orEmpty()
            val media = json.optString("media")

            if (slug.isBlank() || media.isBlank() || userId.isBlank() || md5Id.isBlank()) return@runCatching

            val keyStr = "$userId:$slug:$md5Id"
            val keyBytesStr = md5(keyStr.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            val key = keyBytesStr.toByteArray(Charsets.UTF_8)
            val iv = key.sliceArray(0 until 16)

            val mediaCiphertext = try {
                Base64.decode(media, Base64.DEFAULT)
            } catch (_: Exception) {
                media.toByteArray(Charsets.ISO_8859_1)
            }
            val decryptedMediaBytes = decryptAesCtr(mediaCiphertext, key, iv)
            val decryptedMediaStr = String(decryptedMediaBytes, Charsets.UTF_8)
            val mediaJson = JSONObject(decryptedMediaStr)

            val mp4 = mediaJson.optJSONObject("mp4") ?: return@runCatching
            val sources = mp4.optJSONArray("sources") ?: return@runCatching
            val domainsObj = if (mp4.has("domains")) mp4.optJSONObject("domains") else if (mediaJson.has("domains")) mediaJson.optJSONObject("domains") else null
            val domainsArr = if (mp4.has("domains")) mp4.optJSONArray("domains") else if (mediaJson.has("domains")) mediaJson.optJSONArray("domains") else null

            for (i in 0 until sources.length()) {
                val src = sources.optJSONObject(i) ?: continue
                val size = src.optLong("size", 0L)
                val resId = src.optInt("res_id", 0)
                val label = src.optString("label", "HD")
                val sub = src.optString("sub")

                var hostDomain = ""
                if (domainsObj != null && sub.isNotBlank()) {
                    hostDomain = domainsObj.optString(sub, "")
                } else if (domainsArr != null && sub.isNotBlank()) {
                    for (j in 0 until domainsArr.length()) {
                        val dStr = domainsArr.optString(j)
                        if (dStr.startsWith(sub)) {
                            hostDomain = dStr
                            break
                        }
                    }
                }
                if (hostDomain.isBlank() && sub.isNotBlank()) {
                    hostDomain = "$sub.sssrr.org"
                }
                if (hostDomain.isBlank() || size <= 0L) continue

                val pathStr = "/mp4/$md5Id/$resId/$size?v=$slug"
                val sizeStr = size.toString()
                val digitBytes = sizeStr.map { it.toString().toInt().toByte() }.toByteArray()
                val sizeHashHex = md5(digitBytes).joinToString("") { "%02x".format(it) }
                val pathKey = sizeHashHex.toByteArray(Charsets.UTF_8)
                val pathIv = pathKey.sliceArray(0 until 16)
                val pathBytes = pathStr.toByteArray(Charsets.UTF_8)
                val encryptedPathBytes = decryptAesCtr(pathBytes, pathKey, pathIv)
                val b64Once = Base64.encodeToString(encryptedPathBytes, Base64.NO_WRAP)
                val b64Twice = Base64.encodeToString(b64Once.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val cleanPath = b64Twice.replace("=", "").replace("\n", "").replace("\r", "")
                val finalStreamUrl = "https://$hostDomain/sora/$size/$cleanPath"

                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name $label",
                        url = finalStreamUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = cleanUrl
                        this.quality = parseQuality(label)
                    }
                )
            }
        }
    }

    private fun parseQuality(label: String): Int {
        return when {
            label.contains("1080") -> Qualities.P1080.value
            label.contains("720") -> Qualities.P720.value
            label.contains("480") -> Qualities.P480.value
            label.contains("360") -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }
}

class AbyssPlayer : AbyssCdn("AbyssPlayer", "https://abyssplayer.com")
class AbyssTo : AbyssCdn("Abyss", "https://abyss.to")

/**
 * Extractor untuk Luluvid embed (luluvid.com)
 */
class Luluvid : LuluStream() {
    override var name = "Luluvid"
    override var mainUrl = "https://luluvid.com"
}

/**
 * Extractor untuk pautan krakenfiles.com embed video
 */
class KrakenfilesExtractor : ExtractorApi() {
    override val name = "Krakenfiles"
    override val mainUrl = "https://krakenfiles.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        runCatching {
            val id = Regex("""/(?:view|embed-video)/([a-zA-Z0-9]+)""").find(url)?.groupValues?.get(1) ?: return@runCatching
            val embedUrl = "$mainUrl/embed-video/$id"
            val doc = app.get(embedUrl, headers = mapOf("User-Agent" to "Mozilla/5.0")).document
            val link = doc.selectFirst("source")?.attr("src")
            if (!link.isNullOrBlank()) {
                val directUrl = httpsify(link)
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = directUrl,
                        type = if (directUrl.contains(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "$mainUrl/"
                    }
                )
            }
        }
    }
}
