package com.mts.oploverz

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

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

        doc.select("video source[src], video[src], source[src]").forEach { el ->
            val src = el.attr("src").trim()
            if (src.isNotBlank() && (src.startsWith("http") || src.startsWith("//"))) {
                val u = if (src.startsWith("//")) "https:$src" else src
                try { loadExtractor(u, url, subtitleCallback, callback) } catch (_: Exception) {}
            }
        }

        doc.select("iframe[src], iframe[data-src]").forEach { ifr ->
            val s1 = ifr.attr("src").trim()
            val src = if (s1.isNotBlank()) s1 else ifr.attr("data-src").trim()
            if (src.isNotBlank() && (src.startsWith("http") || src.startsWith("//"))) {
                val u = if (src.startsWith("//")) "https:$src" else src
                try { loadExtractor(u, url, subtitleCallback, callback) } catch (_: Exception) {}
            }
        }

        doc.select("script").forEach { script ->
            val content = script.data()
            if (content.isBlank()) return@forEach
            val rx = Regex("""https?://\S+\.(?:mp4|m3u8|webm)\S*""")
            rx.findAll(content).forEach { m ->
                val videoUrl = m.value
                callback(
                    newExtractorLink(
                        source = name, name = name, url = videoUrl,
                        type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = url
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }
    }
}
