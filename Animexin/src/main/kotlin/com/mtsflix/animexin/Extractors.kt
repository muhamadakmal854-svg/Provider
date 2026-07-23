package com.mtsflix.animexin

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink


// ─────────────────────────────────────────────────────────────────────────────
// Dailymotion variants
// ─────────────────────────────────────────────────────────────────────────────
class GeoDailymotionAnimexin : DailymotionAnimexin() {
    override val name = "GeoDailymotion"
    override val mainUrl = "https://geo.dailymotion.com"
}

open class DailymotionAnimexin : ExtractorApi() {
    override val name = "Dailymotion"
    override val mainUrl = "https://www.dailymotion.com"
    override val requiresReferer = false

    private val baseUrl = "https://www.dailymotion.com"
    private val videoIdRegex = "^[kx][a-zA-Z0-9]+$".toRegex()

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = getEmbedUrl(url) ?: return
        val id = getVideoId(embedUrl) ?: return
        val metaDataUrl = "$baseUrl/player/metadata/video/$id"
        val response = app.get(metaDataUrl, referer = embedUrl).text
        val qualityUrlRegex = Regex(""""url"\s*:\s*"([^"]+)"""")
        qualityUrlRegex.findAll(response).map { it.groupValues[1] }
            .filter { it.contains(".m3u8") }
            .forEach { videoUrl ->
                generateM3u8(name, videoUrl, baseUrl).forEach(callback)
            }
    }

    private fun getEmbedUrl(url: String): String? {
        if (url.contains("/embed/") || url.contains("/video/")) return url
        if (url.contains("geo.dailymotion.com")) {
            val videoId = url.substringAfter("video=")
            return "$baseUrl/embed/video/$videoId"
        }
        return null
    }

    private fun getVideoId(url: String): String? {
        val path = java.net.URI(url).path
        val id = path.substringAfterLast("/")
        return if (id.matches(videoIdRegex)) id else null
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// OK.ru / Odnoklassniki extractor
// ─────────────────────────────────────────────────────────────────────────────
class OkRuAnimexinSSL : OkRuAnimexin() {
    override val name = "OkRuSSL"
    override val mainUrl = "https://ok.ru"
}

class OkRuAnimexinHTTP : OkRuAnimexin() {
    override val name = "OkRuHTTP"
    override val mainUrl = "http://ok.ru"
}

open class OkRuAnimexin : ExtractorApi() {
    override val name = "OkRu"
    override val mainUrl = "https://ok.ru"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Origin" to mainUrl,
            "User-Agent" to USER_AGENT,
        )
        val embedUrl = url.replace("/video/", "/videoembed/")
        val videoReq = app.get(embedUrl, headers = headers).text
            .replace("\\&quot;", "\"").replace("\\\\", "\\")
            .replace(Regex("\\\\u([0-9A-Fa-f]{4})")) { m ->
                Integer.parseInt(m.groupValues[1], 16).toChar().toString()
            }
        val videosStr = Regex(""""videos":(\[[^]]*])""").find(videoReq)?.groupValues?.get(1)
            ?: return
        val qualityMap = mapOf(
            "MOBILE" to Qualities.P144, "LOWEST" to Qualities.P240,
            "LOW" to Qualities.P360, "SD" to Qualities.P480,
            "HD" to Qualities.P720, "FULL" to Qualities.P1080,
            "QUAD" to Qualities.P1440, "ULTRA" to Qualities.P2160
        )
        Regex(""""name":"([^"]+)","url":"([^"]+)"""").findAll(videosStr).forEach { m ->
            val qname = m.groupValues[1].uppercase()
            val vurl = m.groupValues[2].let { if (it.startsWith("//")) "https:$it" else it }
            val quality = qualityMap.entries.firstOrNull { qname.contains(it.key) }?.value
                ?: Qualities.Unknown
            callback(
                newExtractorLink(name, name, vurl, ExtractorLinkType.VIDEO) {
                    this.quality = quality.value
                    this.referer = "$mainUrl/"
                    this.headers = headers
                }
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Rumble extractor
// ─────────────────────────────────────────────────────────────────────────────
class RumbleAnimexin : ExtractorApi() {
    override val name = "Rumble"
    override val mainUrl = "https://rumble.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer ?: "$mainUrl/")
        val scriptData = response.document.selectFirst("script:containsData(mp4)")?.data()
            ?.substringAfter("{\"mp4")?.substringBefore("\"evt\":{")
            ?: return

        val processedUrls = mutableSetOf<String>()
        Regex(""""url":"(.*?)"""").findAll(scriptData).forEach { match ->
            val rawUrl = match.groupValues[1].replace("\\/", "/")
            if (rawUrl.isBlank() || !rawUrl.contains("rumble.com")) return@forEach
            if (!rawUrl.endsWith(".m3u8")) return@forEach
            if (!processedUrls.add(rawUrl)) return@forEach
            generateM3u8(name, rawUrl, mainUrl).forEach(callback)
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// StreamWish variants (for Animexin)
// ─────────────────────────────────────────────────────────────────────────────
class EmbedWishAnimexin : StreamWishExtractor() {
    override val name = "EmbedWish"
    override val mainUrl = "https://embedwish.com"
}

class StreamWishAnimexin : StreamWishExtractor() {
    override val name = "StreamWish"
    override val mainUrl = "https://streamwish.com"
}

class StreamWishToAnimexin : StreamWishExtractor() {
    override val name = "StreamWishTo"
    override val mainUrl = "https://streamwish.to"
}


// ─────────────────────────────────────────────────────────────────────────────
// FileLion / Filelion variants
// ─────────────────────────────────────────────────────────────────────────────
open class FileLionAnimexin : StreamWishExtractor() {
    override val name = "FileLion"
    override val mainUrl = "https://filelions.live"
}

class FilelionsLive : FileLionAnimexin() {
    override val name = "FilelionsLive"
    override val mainUrl = "https://filelions.live"
}

class FilelionsCom : FileLionAnimexin() {
    override val name = "FilelionsCom"
    override val mainUrl = "https://filelions.com"
}

class FilelionsTo : FileLionAnimexin() {
    override val name = "FilelionsTo"
    override val mainUrl = "https://filelions.to"
}

class FilelionsOnline : FileLionAnimexin() {
    override val name = "FilelionsOnline"
    override val mainUrl = "https://filelions.online"
}


// ─────────────────────────────────────────────────────────────────────────────
// DoodStream variants
// ─────────────────────────────────────────────────────────────────────────────
class DoodsPro : DoodLaExtractor() {
    override var name = "DoodsPro"
    override var mainUrl = "https://doods.pro"
}

class DoodStreamCom : DoodLaExtractor() {
    override var name = "DoodStream"
    override var mainUrl = "https://doodstream.com"
}

class Ds2Play : DoodLaExtractor() {
    override var name = "Ds2Play"
    override var mainUrl = "https://ds2play.com"
}
