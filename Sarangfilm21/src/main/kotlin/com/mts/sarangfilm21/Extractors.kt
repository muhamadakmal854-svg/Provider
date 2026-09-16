package com.mts.sarangfilm21

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

class AbyssExtractor : ExtractorApi() {
    override var name = "Abyss"
    override var mainUrl = "https://abyssplayer.com"
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
        try {
            // Tukar abysscdn.com ke abyssplayer.com untuk melepasi 403 Forbidden
            val cleanUrl = url.replace("abysscdn.com", "abyssplayer.com").replace("\\", "")
            val domain = try {
                val u = java.net.URL(cleanUrl)
                "${u.protocol}://${u.host}/"
            } catch (_: Exception) { mainUrl }

            val pageHtml = app.get(cleanUrl, headers = mapOf("Referer" to domain, "User-Agent" to USER_AGENT)).text
            val base64Str = Regex("""const datas\s*=\s*"([^"]+)"""").find(pageHtml)?.groupValues?.get(1) ?: return
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            val latin1Str = String(decodedBytes, Charsets.ISO_8859_1)
            val json = JSONObject(latin1Str)
            val slug = json.getString("slug")
            val userId = json.getString("user_id")
            val md5Id = json.getString("md5_id")
            val media = json.getString("media")
            val keyStr = "$userId:$slug:$md5Id"
            val keyBytesStr = md5(keyStr.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            val key = keyBytesStr.toByteArray(Charsets.UTF_8)
            val iv = key.sliceArray(0 until 16)

            // CRITICAL FIX: 'media' adalah rentetan perduaan ISO-8859-1 (Bukan Base64)
            val mediaCiphertext = media.toByteArray(Charsets.ISO_8859_1)
            val decryptedMediaBytes = decryptAesCtr(mediaCiphertext, key, iv)
            val decryptedMediaStr = String(decryptedMediaBytes, Charsets.UTF_8)
            val mediaJson = JSONObject(decryptedMediaStr)
            val mp4 = mediaJson.optJSONObject("mp4") ?: return
            val sources = mp4.optJSONArray("sources") ?: return
            val domainsArr = mp4.optJSONArray("domains") ?: mediaJson.optJSONArray("domains")
            val domainsObj = mp4.optJSONObject("domains") ?: mediaJson.optJSONObject("domains")

            for (i in 0 until sources.length()) {
                val src = sources.getJSONObject(i)
                val size = src.optLong("size", 0L)
                val resId = src.optInt("res_id", 0)
                val label = src.optString("label", "Unknown")
                val sub = src.optString("sub", "")
                if (size == 0L || resId == 0) continue

                var hostDomain = ""
                if (domainsObj != null && sub.isNotBlank()) {
                    hostDomain = domainsObj.optString(sub, "")
                }
                if (hostDomain.isBlank() && domainsArr != null && sub.isNotBlank()) {
                    for (j in 0 until domainsArr.length()) {
                        val dStr = domainsArr.getString(j)
                        if (dStr.startsWith(sub)) {
                            hostDomain = dStr
                            break
                        }
                    }
                }
                if (hostDomain.isBlank()) {
                    hostDomain = if (sub.isNotBlank()) "$sub.sssrr.org" else "sssrr.org"
                }

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

                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name - $label (Direct MP4)",
                        url = finalStreamUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = domain
                        this.quality = when (label.lowercase()) {
                            "360p" -> Qualities.P360.value
                            "480p" -> Qualities.P480.value
                            "720p" -> Qualities.P720.value
                            "1080p" -> Qualities.P1080.value
                            else -> Qualities.Unknown.value
                        }
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class SarangStreamWishExtractor : ExtractorApi() {
    override var name = "StreamWish"
    override var mainUrl = "https://streamwish.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val domain = try {
                val u = java.net.URL(url)
                "${u.protocol}://${u.host}/"
            } catch (_: Exception) { mainUrl }

            val pageHtml = app.get(url, headers = mapOf("Referer" to domain, "User-Agent" to USER_AGENT)).text
            val unpacked = getAndUnpack(pageHtml)
            val m3u8Matches = Regex("""https?://[^\s"'\]+\.m3u8[^\s"'\]*""").findAll(unpacked).map { it.value }.toList()

            for (m3u8 in m3u8Matches) {
                // Dual emission: generateM3u8 + direct master link
                runCatching {
                    generateM3u8(name, m3u8, domain).forEach { callback(it) }
                }
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name HLS",
                        url = m3u8,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = domain
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

open class VidHideExtractor : ExtractorApi() {
    override var name = "VidHide"
    override var mainUrl = "https://vidhidepro.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val targetUrls = mutableListOf<String>()
        targetUrls.add(url)
        if (url.contains("vidhidehub.com") || url.contains("mevidhides.xyz") || url.contains("dintezuvio.com")) {
            val id = url.substringAfterLast("/").substringBefore("?")
            if (id.isNotBlank()) {
                targetUrls.add("https://vidhidepro.com/v/$id")
                targetUrls.add("https://vidhidepro.com/embed/$id")
            }
        }

        for (targetUrl in targetUrls) {
            try {
                val domain = try {
                    val u = java.net.URL(targetUrl)
                    "${u.protocol}://${u.host}/"
                } catch (_: Exception) { mainUrl }

                val res = app.get(targetUrl, headers = mapOf("Referer" to domain, "User-Agent" to USER_AGENT), timeout = 10).text
                val unpacked = getAndUnpack(res)
                val targetText = if (unpacked.length > res.length) unpacked else res

                val m3u8Regex = Regex("""(?:file|source)\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']|["'](https?://[^"']+\.m3u8[^"']*)["']""")
                val links = m3u8Regex.findAll(targetText).mapNotNull {
                    it.groupValues[1].ifBlank { it.groupValues[2] }
                }.distinct().toList()

                if (links.isNotEmpty()) {
                    for (m3u8 in links) {
                        runCatching {
                            generateM3u8(name, m3u8, domain).forEach { callback(it) }
                        }
                        callback(
                            newExtractorLink(
                                source = name,
                                name = "$name HLS",
                                url = m3u8,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.referer = domain
                                this.quality = Qualities.Unknown.value
                            }
                        )
                    }
                    return
                }
            } catch (_: Exception) {}
        }
    }
}

class VidhidehubExtractor : VidHideExtractor() {
    override var name = "Vidhidehub"
    override var mainUrl = "https://vidhidehub.com"
}
