package com.mts.oploverz

import com.lagradost.cloudstream3.ExtractorApi
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
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
        val doc = app.get(url, referer = referer).document
        
        doc.select("video source[src], video[src], iframe[src]").forEach { el ->
            val src = el.attr("src")
            if (src.isNotBlank()) {
                val finalUrl = if (src.startsWith("//")) "https:$src" else src
                if (finalUrl.startsWith("http")) {
                    try {
                        loadExtractor(finalUrl, url, subtitleCallback, callback)
                    } catch (_: Exception) {}
                }
            }
        }
        
        doc.select("script").forEach { script ->
            val content = script.data()
            if (content.isNotBlank()) {
                Regex("""https?://[a-zA-Z0-9.\\-_]+googlevideo\\.com/[a-zA-Z0-9.\\-_\\?&=\\/~]+""").findAll(content).forEach { match ->
                    val videoUrl = match.value
                    callback(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = videoUrl,
                            type = if (videoUrl.contains(".m3u8") || videoUrl.contains("playlist")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = url
                            this.quality = Qualities.P720.value
                        }
                    )
                }
            }
        }
    }
}
