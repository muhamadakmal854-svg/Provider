package com.mts.animasu

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class I1WpCom : StreamWishExtractor() {
    override var name = "I1WpCom"
    override var mainUrl = "https://i1.wp.com"
}

class I0WpCom : StreamWishExtractor() {
    override var name = "I0WpCom"
    override var mainUrl = "https://i0.wp.com"
}

class I2WpCom : StreamWishExtractor() {
    override var name = "I2WpCom"
    override var mainUrl = "https://i2.wp.com"
}

class I3WpCom : StreamWishExtractor() {
    override var name = "I3WpCom"
    override var mainUrl = "https://i3.wp.com"
}

class AnimasuWork : StreamWishExtractor() {
    override var name = "AnimasuWork"
    override var mainUrl = "https://animasu.work"
}

class AnimasuDev : StreamWishExtractor() {
    override var name = "AnimasuDev"
    override var mainUrl = "https://animasu.dev"
}

class BloggerCom : ExtractorApi() {
    override var name = "BloggerCom"
    override var mainUrl = "https://blogger.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, referer = referer ?: mainUrl).document

        // Use for() loops for suspend functions - forEach{} breaks suspend context
        for (el in doc.select("video source[src], video[src], source[src]")) {
            val src = el.attr("src").trim()
            if (src.isNotBlank() && (src.startsWith("http") || src.startsWith("//"))) {
                val u = if (src.startsWith("//")) "https:$src" else src
                try { loadExtractor(u, url, subtitleCallback, callback) } catch (_: Exception) {}
            }
        }

        for (ifr in doc.select("iframe[src], iframe[data-src]")) {
            val s1 = ifr.attr("src").trim()
            val src = if (s1.isNotBlank()) s1 else ifr.attr("data-src").trim()
            if (src.isNotBlank() && (src.startsWith("http") || src.startsWith("//"))) {
                val u = if (src.startsWith("//")) "https:$src" else src
                try { loadExtractor(u, url, subtitleCallback, callback) } catch (_: Exception) {}
            }
        }

        // Regex is not suspend - forEach is OK here
        for (script in doc.select("script")) {
            val content = script.data()
            if (content.isBlank()) continue
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

class DraftBloggerCom : ExtractorApi() {
    override var name = "DraftBloggerCom"
    override var mainUrl = "https://draft.blogger.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, referer = referer ?: mainUrl).document

        // Use for() loops for suspend functions - forEach{} breaks suspend context
        for (el in doc.select("video source[src], video[src], source[src]")) {
            val src = el.attr("src").trim()
            if (src.isNotBlank() && (src.startsWith("http") || src.startsWith("//"))) {
                val u = if (src.startsWith("//")) "https:$src" else src
                try { loadExtractor(u, url, subtitleCallback, callback) } catch (_: Exception) {}
            }
        }

        for (ifr in doc.select("iframe[src], iframe[data-src]")) {
            val s1 = ifr.attr("src").trim()
            val src = if (s1.isNotBlank()) s1 else ifr.attr("data-src").trim()
            if (src.isNotBlank() && (src.startsWith("http") || src.startsWith("//"))) {
                val u = if (src.startsWith("//")) "https:$src" else src
                try { loadExtractor(u, url, subtitleCallback, callback) } catch (_: Exception) {}
            }
        }

        // Regex is not suspend - forEach is OK here
        for (script in doc.select("script")) {
            val content = script.data()
            if (content.isBlank()) continue
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
