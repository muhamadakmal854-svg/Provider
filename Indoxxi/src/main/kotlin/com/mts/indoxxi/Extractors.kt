package com.mts.indoxxi

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
import java.net.URLDecoder
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// ─── DEAN EDWARDS / PACKER UNPACKER HELPER ────────────────────────────────────
object DeanEdwardsHelper {
    fun unpack(packed: String): String {
        return try {
            val pattern = Regex("""eval\(function\(p,a,c,k,e,r\).+?\}\('(.*)',(\d+),(\d+),'([^']*)'\.split\('\|'\)""", RegexOption.DOT_MATCHES_ALL)
            val match = pattern.find(packed) ?: return packed
            val payload = match.groupValues[1]
            val radix = match.groupValues[2].toIntOrNull() ?: 36
            val count = match.groupValues[3].toIntOrNull() ?: 0
            val symtab = match.groupValues[4].split('|')

            fun baseN(num: Int, base: Int): String {
                val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                if (num == 0) return "0"
                var n = num
                val sb = StringBuilder()
                while (n > 0) {
                    sb.append(chars[n % base])
                    n /= base
                }
                return sb.reverse().toString()
            }

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

// ─── PUTARIN / PUTERIN AES-256-GCM EXTRACTOR ──────────────────────────────────
open class PutarinExtractor : ExtractorApi() {
    override var name = "Indoxxi Putarin"
    override var mainUrl = "https://puterin.biz"
    override val requiresReferer = true

    companion object {
        private fun hexToBytes(hex: String): ByteArray {
            val clean = hex.trim()
            val len = clean.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }

        fun decryptAesGcm(dBase64: String, keyHex: String): String {
            val rawD = Base64.decode(dBase64, Base64.DEFAULT)
            val iv = rawD.copyOfRange(0, 12)
            val ctTag = rawD.copyOfRange(12, rawD.size)
            val keyBytes = hexToBytes(keyHex)

            val keySpec = SecretKeySpec(keyBytes, "AES")
            val gcmSpec = GCMParameterSpec(128, iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            val decrypted = cipher.doFinal(ctTag)
            return String(decrypted, Charsets.UTF_8)
        }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = if (url.startsWith("//")) "https:$url" else url
        val baseDomain = try {
            val u = java.net.URI(embedUrl)
            "${u.scheme}://${u.host}"
        } catch (_: Exception) {
            "https://puterin.biz"
        }

        val html = try {
            app.get(
                embedUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Referer" to (referer ?: "https://indoxx1.one/")
                ),
                timeout = 15
            ).text
        } catch (_: Exception) {
            return
        }

        val pxMatch = Regex("""window\.__PX\s*=\s*(\{.+?\});""").find(html)
        if (pxMatch != null) {
            try {
                val pxJson = JSONObject(pxMatch.groupValues[1])
                val n = pxJson.optString("n")
                val d = pxJson.optString("d")

                val pkUrl = "$baseDomain/api/pk?n=$n"
                val kHex = app.get(
                    pkUrl,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        "Referer" to embedUrl
                    ),
                    timeout = 10
                ).text.trim()

                if (kHex.length >= 64) {
                    val decryptedJsonStr = decryptAesGcm(d, kHex)
                    val playerConfig = JSONObject(decryptedJsonStr)
                    val file = playerConfig.optString("file")
                    if (file.isNotBlank()) {
                        val fullStreamUrl = if (file.startsWith("http")) file else if (file.startsWith("//")) "https:$file" else "$baseDomain$file"
                        val isHls = playerConfig.optString("type").equals("hls", true) || fullStreamUrl.contains(".m3u8") || fullStreamUrl.contains("/api/hls")

                        if (isHls) {
                            generateM3u8(
                                name = "Indoxxi Putarin VIP",
                                streamUrl = fullStreamUrl,
                                referer = embedUrl,
                                headers = mapOf("Referer" to embedUrl, "Origin" to baseDomain)
                            ).forEach(callback)
                        } else {
                            callback.invoke(
                                newExtractorLink(
                                    source = "Indoxxi Putarin",
                                    name = "Indoxxi Putarin Fast",
                                    url = fullStreamUrl,
                                    type = ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = embedUrl
                                    this.quality = Qualities.P1080.value
                                }
                            )
                        }
                        return
                    }
                }
            } catch (_: Exception) {}
        }

        // Direct stream search fallback
        Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""").findAll(html).forEach { m ->
            generateM3u8("Indoxxi Putarin", m.groupValues[1], embedUrl).forEach(callback)
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
        val ref = referer ?: "https://indoxx1.one/"

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
                    name = "VidHide",
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
        val ref = referer ?: "https://indoxx1.one/"
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
                name = "EfekStream VIP",
                streamUrl = match.groupValues[1],
                referer = url,
                headers = mapOf("Referer" to url)
            ).forEach(callback)
        }
    }
}

// ─── STREAMWISH EXTRACTOR ───────────────────────────────────────────────────
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
        val ref = referer ?: "https://indoxx1.one/"
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
                name = "StreamWish",
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
        val ref = referer ?: "https://indoxx1.one/"
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
                name = "Filemoon",
                streamUrl = match.groupValues[1],
                referer = url,
                headers = mapOf("Referer" to url)
            ).forEach(callback)
        }
    }
}

// ─── ABYSS CDN / ABYSSPLAYER EXTRACTOR ──────────────────────────────────────
open class AbyssPlayer : ExtractorApi() {
    override var name = "AbyssPlayer"
    override var mainUrl = "https://abyssplayer.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val html = try {
            app.get(url, timeout = 15).text
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
                name = "AbyssPlayer",
                streamUrl = match.groupValues[1],
                referer = url
            ).forEach(callback)
        }
    }
}

// ─── STREAMP2P EXTRACTOR ────────────────────────────────────────────────────
open class StreamP2PExtractor : ExtractorApi() {
    override var name = "StreamP2P"
    override var mainUrl = "https://strp2p.site"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val ref = referer ?: "https://indoxx1.one/"
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
                name = "StreamP2P",
                streamUrl = match.groupValues[1],
                referer = url
            ).forEach(callback)
        }
    }
}
