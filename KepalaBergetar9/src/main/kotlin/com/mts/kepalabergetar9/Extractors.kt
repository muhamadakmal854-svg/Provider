package com.mts.kepalabergetar9

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class VkspeedExtractor : ExtractorApi() {
    override var name = "Vkspeed"
    override var mainUrl = "https://vkspeed.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = if (!url.contains("/embed-") && url.contains(".html")) {
            val id = url.substringAfterLast("/").substringBefore(".html")
            "$mainUrl/embed-$id.html"
        } else {
            url
        }

        val ref = referer ?: "https://kepalabergetar9.net/"
        val res = try {
            app.get(
                cleanUrl,
                headers = mapOf(
                    "Referer" to ref,
                    "User-Agent" to USER_AGENT
                )
            )
        } catch (_: Throwable) {
            return
        }
        val html = res.text

        // 1. Direct M3U8 or MP4 in page
        val m3u8Regex = Regex("""(?:file|source)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""")
        val mp4Regex = Regex("""(?:file|source)\s*:\s*["']([^"']+\.mp4[^"']*)["']""")

        val directM3u8 = m3u8Regex.find(html)?.groupValues?.getOrNull(1)
        if (!directM3u8.isNullOrBlank()) {
            M3u8Helper.generateM3u8(
                name,
                directM3u8,
                cleanUrl,
                headers = mapOf("Referer" to cleanUrl)
            ).forEach(callback)
            return
        }

        val directMp4 = mp4Regex.find(html)?.groupValues?.getOrNull(1)
        if (!directMp4.isNullOrBlank()) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = directMp4,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = cleanUrl
                }
            )
            return
        }

        // 2. Packed JavaScript (eval(function(p,a,c,k,e,d)...))
        if (html.contains("eval(function(p,a,c,k,e,d)")) {
            try {
                val unpacked = getAndUnpack(html)
                val unpackedM3u8 = m3u8Regex.find(unpacked)?.groupValues?.getOrNull(1)
                    ?: Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""").find(unpacked)?.groupValues?.getOrNull(1)
                if (!unpackedM3u8.isNullOrBlank()) {
                    M3u8Helper.generateM3u8(
                        name,
                        unpackedM3u8,
                        cleanUrl,
                        headers = mapOf("Referer" to cleanUrl)
                    ).forEach(callback)
                    return
                }

                val unpackedMp4 = mp4Regex.find(unpacked)?.groupValues?.getOrNull(1)
                    ?: Regex("""["'](https?://[^"']+\.mp4[^"']*)["']""").find(unpacked)?.groupValues?.getOrNull(1)
                if (!unpackedMp4.isNullOrBlank()) {
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = unpackedMp4,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = cleanUrl
                        }
                    )
                    return
                }
            } catch (_: Throwable) {}
        }

        // 3. Check video source or iframe
        val doc = Jsoup.parse(html)
        doc.select("video source, video").forEach { v ->
            val vSrc = v.attr("src")
            if (vSrc.isNotBlank()) {
                val isHls = vSrc.contains(".m3u8", ignoreCase = true)
                if (isHls) {
                    M3u8Helper.generateM3u8(
                        name,
                        vSrc,
                        cleanUrl,
                        headers = mapOf("Referer" to cleanUrl)
                    ).forEach(callback)
                } else {
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = vSrc,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = cleanUrl
                        }
                    )
                }
            }
        }
    }
}

class DouvideoExtractor : ExtractorApi() {
    override var name = "Douvideo"
    override var mainUrl = "https://douvideo.me.uk"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = Regex("""id=([a-zA-Z0-9]+)""").find(url)?.groupValues?.getOrNull(1)
        if (!id.isNullOrBlank()) {
            val vkspeedUrl = "https://vkspeed.com/embed-$id.html"
            VkspeedExtractor().getUrl(vkspeedUrl, url, subtitleCallback, callback)
            return
        }

        val ref = referer ?: "https://kepalabergetar9.net/"
        try {
            val res = app.get(url, headers = mapOf("Referer" to ref, "User-Agent" to USER_AGENT))
            val doc = Jsoup.parse(res.text)
            doc.select("iframe[src]").forEach { ifr ->
                val src = ifr.attr("src")
                loadExtractor(src, url, subtitleCallback, callback)
            }
        } catch (_: Throwable) {}
    }
}
