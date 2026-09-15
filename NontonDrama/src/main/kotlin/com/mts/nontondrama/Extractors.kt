package com.mts.nontondrama

import android.util.Base64
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.LuluStream
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
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
                    "User-Agent" to USER_AGENT,
                    "Referer" to (referer ?: domain)
                )
            )
            val pageHtml = response.text

            // 1. Direct m3u8 in page if present
            val unpacked = runCatching { getAndUnpack(pageHtml) }.getOrDefault("")
            val m3u8Regex = Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
            m3u8Regex.findAll(unpacked.ifBlank { pageHtml }).forEach { match ->
                generateM3u8(name, match.value, domain).forEach(callback)
            }

            // 2. Decrypt datas block using AES-CTR algorithm
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

            val mediaCiphertext = media.toByteArray(Charsets.ISO_8859_1)
            val decryptedMediaBytes = decryptAesCtr(mediaCiphertext, key, iv)
            val decryptedMediaStr = String(decryptedMediaBytes, Charsets.UTF_8)
            val mediaJson = JSONObject(decryptedMediaStr)

            val mp4 = mediaJson.optJSONObject("mp4") ?: return@runCatching
            val sources = mp4.optJSONArray("sources") ?: return@runCatching

            for (i in 0 until sources.length()) {
                val src = sources.optJSONObject(i) ?: continue
                val label = src.optString("label", "HD")
                val srcUrl = src.optString("url", "")
                val srcPath = src.optString("path", "")
                if (srcUrl.isBlank() || srcPath.isBlank()) continue

                val finalStreamUrl = if (srcUrl.endsWith("/")) "$srcUrl$srcPath" else "$srcUrl/$srcPath"

                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name $label",
                        url = finalStreamUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = domain
                        this.headers = mapOf(
                            "Referer" to domain,
                            "User-Agent" to USER_AGENT
                        )
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
 * Extractor untuk Turbovid / Emturbovid (turbovidhls.com, emturbovid.com)
 * Mengekstrak fail master playlist m3u8.
 */
open class TurbovidExtractor(
    override val name: String = "Turbovid",
    override val mainUrl: String = "https://turbovidhls.com"
) : ExtractorApi() {
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        runCatching {
            val cleanUrl = url.replace("\\", "").trim()
            val id = cleanUrl.removeSuffix("/").substringAfterLast("/").substringBefore("?").substringBefore("#")
            val domain = try {
                val u = java.net.URL(cleanUrl)
                "${u.protocol}://${u.host}/"
            } catch (_: Exception) { "$mainUrl/" }

            // 1. Direct master M3U8 from CDN (cdn4.turboviplay.com)
            if (id.isNotBlank()) {
                val directM3u8 = "https://cdn4.turboviplay.com/data3/$id/$id.m3u8"
                runCatching {
                    generateM3u8(name, directM3u8, domain).forEach(callback)
                }
            }

            // 2. Scan player page for any embedded m3u8
            val pageHtml = runCatching {
                app.get(
                    cleanUrl,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to (referer ?: domain)
                    )
                ).text
            }.getOrDefault("")

            if (pageHtml.isNotBlank()) {
                val m3u8Regex = Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
                val foundM3u8 = m3u8Regex.findAll(pageHtml).map { it.value }.toSet()
                foundM3u8.forEach { m3u8Url ->
                    if (!m3u8Url.contains("/data3/$id/$id.m3u8")) {
                        generateM3u8(name, m3u8Url, domain).forEach(callback)
                    }
                }
            }
        }
    }
}

class Emturbovid : TurbovidExtractor("Turbovid", "https://emturbovid.com")

/**
 * Extractor untuk StreamWish / Gn1r5n
 */
class Gn1r5nOrg : StreamWishExtractor() {
    override var name = "Gn1r5n"
    override var mainUrl = "https://gn1r5n.org"
    override val requiresReferer = true
}

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
            val doc = app.get(embedUrl, headers = mapOf("User-Agent" to USER_AGENT)).document
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

class PlaycinematicCom : ExtractorApi() {
    override var name = "Playcinematic"
    override var mainUrl = "https://playcinematic.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = url.replace("\\", "")
        val id = cleanUrl.substringAfter("/video/").substringBefore("/").substringBefore("?")
        val streamUrl = if (id.isNotBlank()) "$mainUrl/stream/$id#.mp4" else null

        if (streamUrl != null) {
            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = streamUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = cleanUrl
                    this.headers = mapOf("User-Agent" to USER_AGENT, "Referer" to cleanUrl)
                    this.quality = Qualities.P720.value
                }
            )
        }
    }
}

class EmbedpyroxXyz : ExtractorApi() {
    override var name = "Embedpyrox"
    override var mainUrl = "https://embedpyrox.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = url.replace("\\", "")
        val id = cleanUrl.substringAfter("/video/").substringBefore("/").substringBefore("?")
        if (id.isEmpty()) return

        val ajaxUrl = "$mainUrl/player/index.php?data=$id&do=getVideo"
        val response = app.post(
            url = ajaxUrl,
            data = mapOf("hash" to id, "r" to (referer ?: "")),
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to cleanUrl,
                "Origin" to mainUrl,
                "User-Agent" to USER_AGENT,
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
            )
        )
        if (!response.isSuccessful) return
        val text = response.text
        val json = runCatching { JSONObject(text) }.getOrNull()
        if (json != null) {
            val refHeader = mapOf("Referer" to mainUrl, "User-Agent" to USER_AGENT)
            if (json.has("securedLink")) {
                val s1 = json.getString("securedLink").replace("\\/", "/")
                if (s1.isNotBlank()) {
                    callback(newExtractorLink(name, "$name - Server 1", s1, ExtractorLinkType.M3U8) {
                        this.referer = mainUrl
                        this.headers = refHeader
                    })
                }
            }
        }
    }
}
