package com.mts.lk21

import android.util.Base64
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// ─── DEAN EDWARDS / PACKER UNPACKER HELPER ────────────────────────────────────
object DeanEdwardsHelper {
    fun unpack(packed: String): String {
        return try {
            val pattern = Regex("""eval\(function\(p,a,c,k,e,[rd]\).+?\}\('(.*)',(\d+),(\d+),'([^']*)'\.split\('\|'\)""", RegexOption.DOT_MATCHES_ALL)
            val match = pattern.find(packed) ?: return packed
            val payload = match.groupValues[1]
            val radix = match.groupValues[2].toIntOrNull() ?: 36
            val count = match.groupValues[3].toIntOrNull() ?: 0
            val symtab = match.groupValues[4].split('|')

            fun unbase(str: String, base: Int): Int {
                val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                var res = 0
                for (ch in str) {
                    val idx = chars.indexOf(ch)
                    if (idx < 0 || idx >= base) return -1
                    res = res * base + idx
                }
                return res
            }

            val wordRegex = Regex("""\b\w+\b""")
            val result = wordRegex.replace(payload) { m ->
                val word = m.value
                val idx = unbase(word, radix)
                if (idx in symtab.indices && symtab[idx].isNotBlank()) symtab[idx] else word
            }
            result
        } catch (_: Exception) {
            packed
        }
    }
}

// ─── VIDHIDE / MORENCIUS / CALLISTANISE EXTRACTOR ────────────────────────────
open class VidHideExtractor : ExtractorApi() {
    override var name = "VidHide"
    override var mainUrl = "https://vidhidepro.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val targetUrl = url.replace("/d/", "/v/").replace("/download/", "/v/")
        val ref = referer ?: "https://tv12.lk21official.cc/"

        val html = try {
            app.get(
                targetUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Referer" to ref
                ),
                timeout = 15
            ).text
        } catch (_: Exception) {
            return
        }

        var unpacked = html
        if (html.contains("eval(function(p,a,c,k,e,")) {
            unpacked = DeanEdwardsHelper.unpack(html)
        }

        val m3u8Regex = Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""")
        val foundLinks = mutableSetOf<String>()

        m3u8Regex.findAll(unpacked).forEach { match ->
            val m3u8 = match.groupValues[1]
            if (foundLinks.add(m3u8)) {
                // Direct emission as fallback
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name (Auto)",
                        url = m3u8,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = targetUrl
                        this.quality = Qualities.P1080.value
                    }
                )
                runCatching {
                    generateM3u8(
                        source = name,
                        streamUrl = m3u8,
                        referer = targetUrl,
                        headers = mapOf("Referer" to targetUrl)
                    ).forEach(callback)
                }
            }
        }
    }
}

open class MorenciusExtractor : VidHideExtractor() {
    override var name = "Morencius"
    override var mainUrl = "https://morencius.com"
}

open class CallistaniseExtractor : VidHideExtractor() {
    override var name = "Callistanise"
    override var mainUrl = "https://callistanise.com"
}

// ─── ABYSS CDN / ABYSSPLAYER EXTRACTOR ──────────────────────────────────────
open class AbyssPlayer : ExtractorApi() {
    override var name = "AbyssPlayer"
    override var mainUrl = "https://player.abyssplayer.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val html = try {
            app.get(
                url,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Referer" to (referer ?: "https://iwcsantodomingo.org/")
                ),
                timeout = 15
            ).text
        } catch (_: Exception) {
            return
        }

        var unpacked = html
        if (html.contains("eval(function(p,a,c,k,e,")) {
            unpacked = DeanEdwardsHelper.unpack(html)
        }

        val m3u8Regex = Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""")
        m3u8Regex.findAll(unpacked).forEach { match ->
            generateM3u8(
                source = name,
                streamUrl = match.groupValues[1],
                referer = url
            ).forEach(callback)
        }
    }
}

// ─── EFEKSTREAM (VIP SERVER) EXTRACTOR ───────────────────────────────────────
open class EfekStream : ExtractorApi() {
    override var name = "EfekStream"
    override var mainUrl = "https://efek.stream"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val ref = referer ?: "https://iwcsantodomingo.org/"
        val html = try {
            app.get(
                url,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Referer" to ref
                ),
                timeout = 15
            ).text
        } catch (_: Exception) {
            return
        }

        var unpacked = html
        if (html.contains("eval(function(p,a,c,k,e,")) {
            unpacked = DeanEdwardsHelper.unpack(html)
        }

        val m3u8Regex = Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""")
        m3u8Regex.findAll(unpacked).forEach { match ->
            generateM3u8(
                source = name,
                streamUrl = match.groupValues[1],
                referer = url,
                headers = mapOf("Referer" to url)
            ).forEach(callback)
        }
    }
}

// ─── STREAMWISH / HGCLOUD EXTRACTOR ─────────────────────────────────────────
open class StreamWishExtractor : ExtractorApi() {
    override var name = "StreamWish"
    override var mainUrl = "https://streamwish.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val ref = referer ?: "https://iwcsantodomingo.org/"
        val html = try {
            app.get(
                url,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Referer" to ref
                ),
                timeout = 15
            ).text
        } catch (_: Exception) {
            return
        }

        var unpacked = html
        if (html.contains("eval(function(p,a,c,k,e,")) {
            unpacked = DeanEdwardsHelper.unpack(html)
        }

        val m3u8Regex = Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""")
        m3u8Regex.findAll(unpacked).forEach { match ->
            generateM3u8(
                source = name,
                streamUrl = match.groupValues[1],
                referer = url,
                headers = mapOf("Referer" to url)
            ).forEach(callback)
        }
    }
}

// ─── BYSEQ / BYSE / FILEMOON EXTRACTOR (AES-256-GCM BYSE API & LEGACY) ─────
open class ByseqExtractor : ExtractorApi() {
    override var name = "Byse"
    override var mainUrl = "https://bysesayeveum.com"
    override val requiresReferer = false

    private fun base64UrlDecode(str: String): ByteArray {
        var s = str.replace('-', '+').replace('_', '/')
        val mod = s.length % 4
        if (mod != 0) {
            s += "=".repeat(4 - mod)
        }
        return Base64.decode(s, Base64.DEFAULT)
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val host = runCatching { URI(url).host }.getOrNull() ?: "bysesayeveum.com"
        val code = Regex("""/e/([a-zA-Z0-9]+)""").find(url)?.groupValues?.get(1)

        // 1. Byse Modern AES-256-GCM Encrypted API
        if (!code.isNullOrBlank()) {
            val apiUrl = "https://$host/api/videos/$code/"
            try {
                val jsonStr = app.get(
                    apiUrl,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        "Referer" to url
                    ),
                    timeout = 10
                ).text

                val rootJson = JSONObject(jsonStr)
                val playback = rootJson.optJSONObject("playback")
                if (playback != null) {
                    val vNum = playback.optInt("version", 1)
                    val keyParts = playback.optJSONArray("key_parts")
                    if (keyParts != null && keyParts.length() > 0) {
                        val idx1 = vNum
                        val idx2 = 31 - vNum
                        if (idx1 in 1..keyParts.length() && idx2 in 1..keyParts.length()) {
                            val part1 = base64UrlDecode(keyParts.getString(idx1 - 1))
                            val part2 = base64UrlDecode(keyParts.getString(idx2 - 1))
                            val keyBytes = part1 + part2
                            val ivBytes = base64UrlDecode(playback.getString("iv"))
                            val payloadBytes = base64UrlDecode(playback.getString("payload"))

                            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                            val keySpec = SecretKeySpec(keyBytes, "AES")
                            val gcmSpec = GCMParameterSpec(128, ivBytes)
                            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
                            val decryptedBytes = cipher.doFinal(payloadBytes)
                            val decryptedJson = JSONObject(String(decryptedBytes, Charsets.UTF_8))
                            val sources = decryptedJson.optJSONArray("sources")
                            if (sources != null && sources.length() > 0) {
                                for (i in 0 until sources.length()) {
                                    val src = sources.optJSONObject(i) ?: continue
                                    val m3u8Url = src.optString("url")
                                    val label = src.optString("label", "1080p")
                                    val quality = when {
                                        label.contains("1080") -> Qualities.P1080.value
                                        label.contains("720") -> Qualities.P720.value
                                        label.contains("480") -> Qualities.P480.value
                                        else -> Qualities.Unknown.value
                                    }
                                    if (m3u8Url.isNotBlank()) {
                                        // Direct emission for guaranteed playback
                                        callback.invoke(
                                            newExtractorLink(
                                                source = name,
                                                name = "$name ($label)",
                                                url = m3u8Url,
                                                type = ExtractorLinkType.M3U8
                                            ) {
                                                this.referer = "https://$host/"
                                                this.quality = quality
                                            }
                                        )
                                        runCatching {
                                            generateM3u8(
                                                source = "$name ($label)",
                                                streamUrl = m3u8Url,
                                                referer = "https://$host/"
                                            ).forEach(callback)
                                        }
                                    }
                                }
                                return
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Fallback for legacy Dean Edwards / plain m3u8
        val ref = referer ?: "https://tv12.lk21official.cc/"
        val html = try {
            app.get(
                url,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Referer" to ref
                ),
                timeout = 10
            ).text
        } catch (_: Exception) {
            return
        }

        var unpacked = html
        if (html.contains("eval(function(p,a,c,k,e,")) {
            unpacked = DeanEdwardsHelper.unpack(html)
        }

        val m3u8Regex = Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""")
        m3u8Regex.findAll(unpacked).forEach { match ->
            val m3u8 = match.groupValues[1]
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "$name (Auto)",
                    url = m3u8,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = url
                    this.quality = Qualities.P1080.value
                }
            )
            runCatching {
                generateM3u8(
                    source = name,
                    streamUrl = m3u8,
                    referer = url,
                    headers = mapOf("Referer" to url)
                ).forEach(callback)
            }
        }
    }
}

// ─── STREAMP2P / PLAYERP2P EXTRACTOR (AES-128-CBC DECRYPTION) ───────────────
open class StreamP2PExtractor : ExtractorApi() {
    override var name = "StreamP2P"
    override var mainUrl = "https://live.playerp2p.online"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val ref = referer ?: "https://iwcsantodomingo.org/"
        val host = runCatching { URI(url).host }.getOrNull() ?: "ewa.playerp2p.live"

        val videoId = when {
            url.contains("#") -> url.substringAfter("#").substringBefore("&").trim()
            else -> url.trimEnd('/').substringAfterLast('/')
        }

        if (videoId.isNotBlank() && !videoId.startsWith("http")) {
            val refDomain = runCatching { URI(ref).host }.getOrNull() ?: "iwcsantodomingo.org"
            val apiUrl = "https://$host/api/v1/video?id=$videoId&w=1920&h=1080&r=$refDomain"

            val hexData = try {
                app.get(
                    apiUrl,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        "Referer" to url,
                        "Origin" to "https://$host"
                    ),
                    timeout = 15
                ).text.trim()
            } catch (_: Exception) {
                ""
            }

            if (hexData.length > 32 && hexData.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                try {
                    val keyBytes = "kiemtienmua911ca".toByteArray(Charsets.UTF_8)
                    val ivBytes = "1234567890oiuytr".toByteArray(Charsets.UTF_8)

                    val cipherBytes = ByteArray(hexData.length / 2) { i ->
                        hexData.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                    }

                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
                    val decryptedBytes = cipher.doFinal(cipherBytes)
                    val jsonStr = String(decryptedBytes, Charsets.UTF_8)

                    val json = JSONObject(jsonStr)
                    val cfNative = json.optString("cfNative")
                    val source = json.optString("source")
                    val mp4 = json.optString("mp4")

                    val added = mutableSetOf<String>()

                    if (cfNative.isNotBlank() && added.add(cfNative)) {
                        generateM3u8(
                            source = "$name (P2P)",
                            streamUrl = cfNative,
                            referer = "https://$host/",
                            headers = mapOf("Referer" to "https://$host/")
                        ).forEach(callback)
                    }

                    if (source.isNotBlank() && added.add(source)) {
                        generateM3u8(
                            source = "$name (Direct)",
                            streamUrl = source,
                            referer = "https://$host/",
                            headers = mapOf("Referer" to "https://$host/")
                        ).forEach(callback)
                    }

                    if (mp4.isNotBlank() && added.add(mp4)) {
                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = "$name MP4",
                                url = mp4,
                                type = ExtractorLinkType.VIDEO
                            ) {
                                this.referer = "https://$host/"
                                this.quality = Qualities.P1080.value
                            }
                        )
                    }

                    if (added.isNotEmpty()) return
                } catch (_: Exception) {}
            }
        }

        // Fallback to HTML scraping
        val html = try {
            app.get(
                url,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Referer" to ref
                ),
                timeout = 15
            ).text
        } catch (_: Exception) {
            return
        }

        var unpacked = html
        if (html.contains("eval(function(p,a,c,k,e,")) {
            unpacked = DeanEdwardsHelper.unpack(html)
        }

        val m3u8Regex = Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""")
        m3u8Regex.findAll(unpacked).forEach { match ->
            generateM3u8(
                source = name,
                streamUrl = match.groupValues[1],
                referer = url
            ).forEach(callback)
        }
    }
}

// ─── EMBEDPYROX EXTRACTOR ───────────────────────────────────────────────────
open class EmbedPyroxExtractor : ExtractorApi() {
    override var name = "EmbedPyrox"
    override var mainUrl = "https://embedpyrox.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val ref = referer ?: "https://iwcsantodomingo.org/"
        val html = try {
            app.get(
                url,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Referer" to ref
                ),
                timeout = 15
            ).text
        } catch (_: Exception) {
            return
        }

        var unpacked = html
        if (html.contains("eval(function(p,a,c,k,e,")) {
            unpacked = DeanEdwardsHelper.unpack(html)
        }

        val m3u8Regex = Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""")
        m3u8Regex.findAll(unpacked).forEach { match ->
            generateM3u8(
                source = name,
                streamUrl = match.groupValues[1],
                referer = url
            ).forEach(callback)
        }
    }
}
