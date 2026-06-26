package com.mts.oploverz

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class AcscdnCom : StreamWishExtractor() {
    override val name = "AcscdnCom"
    override val mainUrl = "https://acscdn.com"
}

class BloggerCom : ExtractorApi() {
    override val name = "BloggerCom"
    override val mainUrl = "https://blogger.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, referer = referer ?: mainUrl).document

        // Try direct video/iframe sources
        val sources = doc.select("video source[src], video[src], source[src]")
        for (el in sources) {
            val src = el.attr("src").trim()
            if (src.startsWith("http") || src.startsWith("//")) {
                val finalUrl = if (src.startsWith("//")) "https:$src" else src
                try { loadExtractor(finalUrl, url, subtitleCallback, callback) } catch (_: Exception) {}
            }
        }

        // Try iframes
        val iframes = doc.select("iframe[src], iframe[data-src], iframe[data-lazy-src]")
        for (ifr in iframes) {
            val src = ifr.attr("src").ifEmpty { ifr.attr("data-src").ifEmpty { ifr.attr("data-lazy-src") } }.trim()
            if (src.startsWith("http") || src.startsWith("//")) {
                val finalUrl = if (src.startsWith("//")) "https:$src" else src
                try { loadExtractor(finalUrl, url, subtitleCallback, callback) } catch (_: Exception) {}
            }
        }

        // Scan scripts for video URLs
        val videoUrlRegex = Regex("(https?://[\w./-]+\.(?:m3u8|mp4|webm)[\w?&=./-]*)")
        val srcUrlRegex = Regex("["'](https?://[\w./-]+/(?:embed|play|video|v|e)/[\w?&=./-]+)["']")
        for (script in doc.select("script")) {
            val content = script.data()
            if (content.isBlank()) continue

            videoUrlRegex.findAll(content).forEach { m ->
                val videoUrl = m.groupValues[1]
                callback(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = videoUrl,
                        type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = url
                        this.quality = Qualities.Unknown.value
                    }
                )
            }

            srcUrlRegex.findAll(content).forEach { m ->
                val embedUrl = m.groupValues[1]
                try { loadExtractor(embedUrl, url, subtitleCallback, callback) } catch (_: Exception) {}
            }
        }
    }
}
