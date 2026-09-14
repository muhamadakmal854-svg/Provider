package com.sad25kag.drakorkita

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
import java.util.Base64
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
    override val mainUrl = "https://drakor.kita.mobi"
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
 * Extractor for AbyssCDN/Hydrax player.
 * URL format: https://abysscdn.com/?v=HASH
 */
class AbyssCdn : ExtractorApi() {
    override val name = "AbyssCDN"
    override val mainUrl = "https://abysscdn.com"
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

            // 1. Direct m3u8 links if any
            val unpacked = runCatching { getAndUnpack(text) }.getOrDefault("")
            val m3u8Regex = Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
            m3u8Regex.findAll(unpacked.ifBlank { text }).forEach { match ->
                generateM3u8(name, match.value, mainUrl).forEach(callback)
            }

            // 2. Decrypt datas block using AES-CTR
            val datasMatch = Regex("""const\s+datas\s*=\s*"([^"]+)"""").find(text)
            if (datasMatch != null) {
                val rawB64 = datasMatch.groupValues[1]
                val decodedJson = String(Base64.getDecoder().decode(rawB64), Charsets.ISO_8859_1)
                val json = JSONObject(decodedJson)
                val slug = json.optString("slug")
                val md5Id = json.opt("md5_id")?.toString().orEmpty()
                val userId = json.opt("user_id")?.toString().orEmpty()
                val media = json.optString("media")

                if (slug.isNotBlank() && media.isNotBlank()) {
                    val keyStr = "$userId:$slug:$md5Id"
                    val md5Bytes = MessageDigest.getInstance("MD5").digest(keyStr.toByteArray(Charsets.UTF_8))
                    val md5Hex = md5Bytes.joinToString("") { "%02x".format(it) }
                    val keyBytes = md5Hex.toByteArray(Charsets.UTF_8)
                    val counterBytes = keyBytes.copyOfRange(0, 16)

                    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                    val secretKey = SecretKeySpec(keyBytes, "AES")
                    val ivSpec = IvParameterSpec(counterBytes)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

                    val mediaBytes = media.toByteArray(Charsets.ISO_8859_1)
                    val decrypted = String(cipher.doFinal(mediaBytes), Charsets.UTF_8)
                    val mediaJson = JSONObject(decrypted)
                    val mp4Obj = mediaJson.optJSONObject("mp4")
                    val sources = mp4Obj?.optJSONArray("sources")

                    if (sources != null) {
                        for (i in 0 until sources.length()) {
                            val src = sources.optJSONObject(i) ?: continue
                            val label = src.optString("label", "HD")
                            val fileUrl = src.optString("file")
                            if (fileUrl.isNotBlank() && fileUrl.startsWith("http")) {
                                val type = if (fileUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                callback.invoke(
                                    newExtractorLink(name, "$name $label", fileUrl, type) {
                                        this.referer = url
                                        this.quality = parseQuality(label)
                                    }
                                )
                            }
                        }
                    }
                }
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

class StbP2P : P2pStreamExtractor("STBP2P", "https://stb.strp2p.com")
class Playerupnone : P2pStreamExtractor("UPNP2P", "https://player.upn.one")
class FastdlP2P : P2pStreamExtractor("FastDLP2P", "https://fastdl.p2pstream.online")
class P2PStreamOnline : P2pStreamExtractor("P2PStream", "https://p2pstream.online")
class Strp2pCom : P2pStreamExtractor("STRP2P", "https://strp2p.com")
class UpnOneCom : P2pStreamExtractor("UPNOne", "https://upn.one")
