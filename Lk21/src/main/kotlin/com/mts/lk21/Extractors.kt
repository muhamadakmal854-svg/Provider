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
import org.json.JSONObject
import java.net.URLDecoder
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
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
        val ref = referer ?: "https://iwcsantodomingo.org/"

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

// ─── STREAMP2P / PLAYERP2P EXTRACTOR ────────────────────────────────────────
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
