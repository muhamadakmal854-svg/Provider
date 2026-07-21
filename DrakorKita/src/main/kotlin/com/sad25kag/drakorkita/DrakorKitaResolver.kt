package com.sad25kag.drakorkita

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Document

object DrakorKitaResolver {

    fun normalizeUrl(url: String, mainUrl: String): String {
        val trimmed = url.trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .replace("\\/", "/")
        return when {
            trimmed.isBlank() -> ""
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> mainUrl.trimEnd('/') + trimmed
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> trimmed
        }
    }

    fun extractEmbedCandidates(document: Document, mainUrl: String): List<String> {
        val candidates = linkedSetOf<String>()

        document.select("iframe[src], iframe[data-src], embed[src], video[src], source[src]").forEach { element ->
            val src = element.attr("src").ifBlank { element.attr("data-src") }
            normalizeUrl(src, mainUrl).takeIf { it.isNotBlank() }?.let(candidates::add)
        }

        document.select("a[href], button[data-src], button[data-url], div[data-src], div[data-url], li[data-src], li[data-url]").forEach { element ->
            listOf(
                element.attr("href"),
                element.attr("data-src"),
                element.attr("data-url"),
                element.attr("data-embed"),
                element.attr("data-link"),
                element.attr("data-stream")
            ).forEach { raw ->
                val normalized = normalizeUrl(raw, mainUrl)
                if (isEmbedCandidate(normalized)) {
                    candidates.add(normalized)
                }
            }
        }

        val html = document.html()
        val regexes = listOf(
            Regex("""https?:\\?/\\?/[^"'\s<>]+\.(?:m3u8|mp4|mkv)[^"'\s<>]*""", RegexOption.IGNORE_CASE),
            Regex("""https?:\\?/\\?/[^"'\s<>]*(?:embed|player|video|stream|drive|file|hydrax|p2p|strp2p|upn\.one)[^"'\s<>]*""", RegexOption.IGNORE_CASE),
            Regex("""//[^"'\s<>]+\.(?:m3u8|mp4|mkv)[^"'\s<>]*""", RegexOption.IGNORE_CASE)
        )

        regexes.forEach { regex ->
            regex.findAll(html).forEach { match ->
                val normalized = normalizeUrl(match.value, mainUrl)
                if (isEmbedCandidate(normalized)) {
                    candidates.add(normalized)
                }
            }
        }

        return candidates.toList()
    }

    fun isEmbedCandidate(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        val ignoredExtensions = listOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".css", ".js", ".svg", ".ico", ".woff", ".woff2", ".ttf", "disqus")
        if (ignoredExtensions.any { lower.contains(it) }) return false

        val knownKeywords = listOf(
            ".m3u8", ".mp4", "embed", "player", "stream", "video", "drive", "file",
            "dood", "streamwish", "filemoon", "vidhide", "mixdrop", "streamtape",
            "lulustream", "krakenfiles", "pixeldrain", "gofile", "mediafire", "hxfile",
            "hydrax", "p2p", "strp2p", "upn.one", "load.my.id", "nonton.bid"
        )
        return knownKeywords.any { lower.contains(it) }
    }

    suspend fun resolvePageLinks(
        document: Document,
        pageUrl: String,
        mainUrl: String,
        headers: Map<String, String>,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundAny = false

        suspend fun emitLink(
            source: String,
            name: String,
            url: String,
            referer: String = pageUrl,
            quality: Int = Qualities.Unknown.value,
            type: ExtractorLinkType = ExtractorLinkType.VIDEO
        ) {
            val normalized = normalizeUrl(url, mainUrl)
            if (normalized.isNotBlank()) {
                foundAny = true
                val linkType = if (normalized.contains(".m3u8", ignoreCase = true)) ExtractorLinkType.M3U8 else type
                callback(
                    newExtractorLink(
                        source = source,
                        name = name,
                        url = normalized,
                        type = linkType
                    ) {
                        this.referer = referer
                        this.quality = quality
                    }
                )
            }
        }

        val html = document.html()

        // 1. Check for Hydrax embeds / links
        val hydraxMatch = Regex("""https?:\\?/\\?/[^"'\s<>]*(?:hydrax|iamcdn|playhydrax|multi\.hydrax)[^"'\s<>]*""", RegexOption.IGNORE_CASE).find(html)
        if (hydraxMatch != null) {
            val hydraxUrl = normalizeUrl(hydraxMatch.value, mainUrl)
            if (hydraxUrl.isNotBlank()) {
                val loaded = loadExtractor(hydraxUrl, pageUrl, subtitleCallback, callback)
                if (loaded) {
                    foundAny = true
                } else {
                    emitLink("HYDRAX", "[HYDRAX] Server", hydraxUrl, referer = pageUrl)
                }
            }
        }

        // 2. Check for P2P / Stream embeds (stb.strp2p.com, player.upn.one, p2pstream, etc.)
        val p2pMatch = Regex("""https?:\\?/\\?/[^"'\s<>]*(?:p2p|strp2p|upn\.one|peer|hls\.p2p|p2pstream|playerp2p|fastdl\.p2pstream)[^"'\s<>]*""", RegexOption.IGNORE_CASE).find(html)
        if (p2pMatch != null) {
            val p2pUrl = normalizeUrl(p2pMatch.value, mainUrl)
            if (p2pUrl.isNotBlank()) {
                val loaded = loadExtractor(p2pUrl, pageUrl, subtitleCallback, callback)
                if (loaded) {
                    foundAny = true
                } else {
                    emitLink("P2P", "[P2P] Server", p2pUrl, referer = pageUrl)
                }
            }
        }

        // 3. Extract direct M3U8 / MP4 URLs from HTML / scripts
        val m3u8Matches = Regex("""https?:\\?/\\?/[^"'\s<>]+\.(?:m3u8|mp4)[^"'\s<>]*""", RegexOption.IGNORE_CASE).findAll(html)
        m3u8Matches.forEach { match ->
            val directUrl = normalizeUrl(match.value, mainUrl)
            if (directUrl.isNotBlank() && !directUrl.contains("favicon")) {
                val nameTag = if (directUrl.contains("p2p", ignoreCase = true) || directUrl.contains("strp2p", ignoreCase = true)) "[P2P] Server"
                             else if (directUrl.contains("hydrax", ignoreCase = true)) "[HYDRAX] Server"
                             else "DrakorKita Direct"
                emitLink("DrakorKita", nameTag, directUrl, referer = pageUrl)
            }
        }

        return foundAny
    }
}
