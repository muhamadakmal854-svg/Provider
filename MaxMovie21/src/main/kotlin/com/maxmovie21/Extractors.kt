package com.maxmovie21

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
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import javax.crypto.Cipher
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

// ─── ASIASTREAM EXTRACTOR (watch.asiastream.cc) ──────────────────────────────
open class AsiaStream : ExtractorApi() {
    override var name = "AsiaStream"
    override var mainUrl = "https://watch.asiastream.cc"
    override val requiresReferer = true

    companion object {
        const val EXTRACTOR_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val ref = referer ?: "https://94.26.35.96/"
        val html = try {
            app.get(
                url,
                headers = mapOf(
                    "User-Agent" to EXTRACTOR_UA,
                    "Referer" to ref
                ),
                timeout = 15
            ).text
        } catch (_: Exception) {
            return
        }

        val defaultHeaders = mapOf(
            "User-Agent" to EXTRACTOR_UA,
            "Referer" to "https://watch.asiastream.cc/",
            "Origin" to "https://watch.asiastream.cc"
        )

        // 1. Parse sniff(slug, uid, md5, ...)
        val sniffMatch = Regex("""sniff\s*\(\s*"[^"]+"\s*,\s*"(\d+)"\s*,\s*"([a-f0-9]+)"""").find(html)
            ?: Regex("""m3u8/(\d+)/([a-f0-9]+)/master\.txt""").find(html)

        if (sniffMatch != null) {
            val (uid, md5) = sniffMatch.destructured
            val baseMasterUrl = "$mainUrl/m3u8/$uid/$md5/master.txt?s=1&cache=1"
            val masterUrl = "$baseMasterUrl&ext=.m3u8"

            val masterContent = try {
                app.get(
                    baseMasterUrl,
                    headers = defaultHeaders,
                    timeout = 15
                ).text
            } catch (_: Exception) {
                ""
            }

            if (masterContent.startsWith("#EXTM3U")) {
                // Adaptive master playlist link
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name (Auto)",
                        url = masterUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://watch.asiastream.cc/"
                        this.quality = Qualities.Unknown.value
                        this.headers = defaultHeaders
                    }
                )

                // Parse individual streams from master.txt
                val lines = masterContent.lines()
                var currentQuality = Qualities.P720.value
                var currentLabel = "720p"

                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                        val resMatch = Regex("""RESOLUTION=(\d+x\d+)""").find(trimmed)
                        val height = resMatch?.groupValues?.get(1)?.split("x")?.getOrNull(1)?.toIntOrNull()
                        if (height != null) {
                            currentLabel = "${height}p"
                            currentQuality = when {
                                height >= 1080 -> Qualities.P1080.value
                                height >= 720 -> Qualities.P720.value
                                height >= 480 -> Qualities.P480.value
                                height >= 360 -> Qualities.P360.value
                                else -> Qualities.P240.value
                            }
                        }
                    } else if (trimmed.startsWith("http")) {
                        val streamM3u8Url = if (trimmed.contains("?")) "$trimmed&ext=.m3u8" else "$trimmed?ext=.m3u8"
                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = "$name $currentLabel",
                                url = streamM3u8Url,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.referer = "https://watch.asiastream.cc/"
                                this.quality = currentQuality
                                this.headers = defaultHeaders
                            }
                        )
                    }
                }
                return
            }
        }

        // 2. Direct .m3u8 regex in HTML
        val m3u8Regex = Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""")
        m3u8Regex.findAll(html).forEach { match ->
            val m3u8 = match.groupValues[1]
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "$name Direct",
                    url = m3u8,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = url
                    this.headers = defaultHeaders
                }
            )
            runCatching {
                generateM3u8(
                    source = name,
                    streamUrl = m3u8,
                    referer = url,
                    headers = defaultHeaders
                ).forEach(callback)
            }
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
        val targetUrl = url.replace("/d/", "/v/").replace("/download/", "/v/").replace("/embed/", "/v/")
        val ref = referer ?: "https://94.26.35.96/"

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
                    "Referer" to (referer ?: "https://94.26.35.96/")
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
        val ref = referer ?: "https://94.26.35.96/"
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
        val ref = referer ?: "https://94.26.35.96/"
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

// ─── BYSEQ / FILEMOON EXTRACTOR ─────────────────────────────────────────────
open class ByseqExtractor : ExtractorApi() {
    override var name = "Byseq"
    override var mainUrl = "https://byseqekaho.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val ref = referer ?: "https://94.26.35.96/"
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
        val ref = referer ?: "https://94.26.35.96/"
        val host = runCatching { URI(url).host }.getOrNull() ?: "ewa.playerp2p.live"

        val videoId = when {
            url.contains("#") -> url.substringAfter("#").substringBefore("&").trim()
            else -> url.trimEnd('/').substringAfterLast('/')
        }

        if (videoId.isNotBlank() && !videoId.startsWith("http")) {
            val refDomain = runCatching { URI(ref).host }.getOrNull() ?: "94.26.35.96"
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
                        runCatching {
                            generateM3u8(
                                source = "$name (P2P)",
                                streamUrl = cfNative,
                                referer = "https://$host/",
                                headers = mapOf("Referer" to "https://$host/")
                            ).forEach(callback)
                        }
                    }

                    if (source.isNotBlank() && added.add(source)) {
                        runCatching {
                            generateM3u8(
                                source = "$name (Direct)",
                                streamUrl = source,
                                referer = "https://$host/",
                                headers = mapOf("Referer" to "https://$host/")
                            ).forEach(callback)
                        }
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
        val ref = referer ?: "https://94.26.35.96/"
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
