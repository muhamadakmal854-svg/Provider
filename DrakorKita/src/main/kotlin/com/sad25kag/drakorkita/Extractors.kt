package com.sad25kag.drakorkita

import android.util.Base64
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
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

/**
 * Base extractor for P2P/WebTorrent-based streaming sites.
 * These sites use HLS streams served from a P2P CDN.
 */
open class P2pStreamExtractor(
    override val name: String,
    override val mainUrl: String
) : ExtractorApi() {
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        runCatching {
            val response = app.get(url, referer = referer ?: "$mainUrl/")
            val text = response.text
            val unpacked = runCatching { getAndUnpack(text) }.getOrDefault("")
            val m3u8Regex = Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
            val matches = m3u8Regex.findAll(unpacked.ifBlank { text }).toList()

            matches.forEach { match ->
                generateM3u8(name, match.value, mainUrl).forEach(callback)
            }
        }
    }
}

/**
 * Extractor for drakorkita.stream P2P player.
 * URL format: https://drakorkita.stream/#HASH
 */
class DrakorKitaStream : ExtractorApi() {
    override val name = "DrakorKitaP2P"
    override val mainUrl = "https://drakorkita.stream"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val hash = url.substringAfter("#", "").substringBefore("&").trim()
        if (hash.length < 4) return

        runCatching {
            val apiUrl = "$mainUrl/api/v1/folder?id=$hash"
            val resp = app.get(
                apiUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Mobile Safari/537.36",
                    "Accept" to "application/json"
                ),
                referer = referer ?: "$mainUrl/"
            )
            if (!resp.isSuccessful) return@runCatching

            val text = resp.text
            val m3u8Regex = Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
            m3u8Regex.findAll(text).forEach { match ->
                generateM3u8(name, match.value, mainUrl).forEach(callback)
            }

            val mp4Regex = Regex("""https?://[^'"\s<>]+\.mp4[^'"\s<>]*""")
            mp4Regex.findAll(text).forEach { match ->
                callback.invoke(
                    newExtractorLink(name, name, match.value, ExtractorLinkType.VIDEO) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }
    }
}

/**
 * Extractor for AbyssCDN / Hydrax / Sora player.
 * Decrypts the media block and constructs the playable Sora MP4 stream.
 * URL format: https://abysscdn.com/?v=HASH or https://abyssplayer.com/?v=HASH
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
            val cleanUrl = url.replace("\\", "")
            val domain = try {
                val u = java.net.URL(cleanUrl)
                "${u.protocol}://${u.host}/"
            } catch (_: Exception) { "$mainUrl/" }

            val response = app.get(
                cleanUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
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

            // 2. Decrypt datas block using proven Sora stream algorithm
            val base64Str = Regex("""const\s+datas\s*=\s*"([^"]+)"""").find(pageHtml)?.groupValues?.get(1) ?: return@runCatching
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

/**
 * Extractor for dqt.my.id / StreamSB embeds.
 * Unpacks packed JavaScript and extracts working premilkyway / master.m3u8 streams.
 */
class DqtMyId : ExtractorApi() {
    override val name = "StreamSB"
    override val mainUrl = "https://dqt.my.id"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = if (url.contains("/f/")) {
            val id = url.substringAfter("/f/").substringBefore("_").substringBefore(".")
            "https://dqt.my.id/e/$id.html"
        } else url

        runCatching {
            val response = app.get(
                cleanUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Referer" to (referer ?: "https://dqt.my.id/")
                )
            )
            val text = response.text
            if (text.contains("File is no longer available") || text.contains("expired or has been deleted")) {
                return@runCatching
            }

            val unpacked = runCatching { getAndUnpack(text) }.getOrDefault("")
            val content = unpacked.ifBlank { text }

            // 1. Check for master.m3u8 or hls2/hls3 links
            val m3u8Regex = Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
            m3u8Regex.findAll(content).forEach { match ->
                val m3u8Url = match.value.replace("\\/", "/")
                generateM3u8(name, m3u8Url, cleanUrl).forEach(callback)
            }

            // 2. Direct MP4 if present
            val mp4Regex = Regex("""https?://[^'"\s<>]+\.mp4[^'"\s<>]*""")
            mp4Regex.findAll(content).forEach { match ->
                val mp4Url = match.value.replace("\\/", "/")
                callback.invoke(
                    newExtractorLink(name, name, mp4Url, ExtractorLinkType.VIDEO) {
                        this.referer = cleanUrl
                    }
                )
            }
        }
    }
}

class StbP2P : P2pStreamExtractor("STBP2P", "https://stb.strp2p.com")
class Playerupnone : P2pStreamExtractor("UPNP2P", "https://player.upn.one")
class FastdlP2P : P2pStreamExtractor("FastDLP2P", "https://fastdl.p2pstream.online")
class P2PStreamOnline : P2pStreamExtractor("P2PStream", "https://p2pstream.online")
class Strp2pCom : P2pStreamExtractor("STRP2P", "https://strp2p.com")
class UpnOneCom : P2pStreamExtractor("UPNOne", "https://upn.one")
