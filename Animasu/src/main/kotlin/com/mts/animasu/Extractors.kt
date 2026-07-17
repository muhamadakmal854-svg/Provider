package com.mts.animasu

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Odnoklassniki
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
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
    private val googleVideoReferer = "https://youtube.googleapis.com/"
    private val rpcId = "WcwnYd"

    data class ResolvedVideo(
        val url: String,
        val quality: Int
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixedUrl = if (url.startsWith("//")) "https:$url" else url
        val resolvedVideos = extractDirectVideos(fixedUrl, referer)
        for (video in resolvedVideos) {
            val directReferer = if (video.url.contains("googlevideo.com/", true)) {
                googleVideoReferer
            } else {
                fixedUrl
            }
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    video.url,
                    if (video.url.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.referer = directReferer
                    this.headers = mapOf(
                        "Referer" to directReferer,
                        "User-Agent" to USER_AGENT,
                        "Accept" to "*/*"
                    )
                    this.quality = video.quality
                }
            )
        }
    }

    suspend fun extractDirectVideos(url: String, referer: String?): List<ResolvedVideo> {
        val fixedUrl = if (url.startsWith("//")) "https:$url" else url

        if (fixedUrl.contains("blogger.googleusercontent.com", true)) {
            return listOf(
                ResolvedVideo(
                    fixedUrl,
                    itagToQuality(
                        Regex("[?&]itag=(\\d+)")
                            .find(fixedUrl)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.toIntOrNull()
                    )
                )
            )
        }

        val token = Regex("[?&]token=([^&]+)")
            .find(fixedUrl)
            ?.groupValues
            ?.getOrNull(1)
            ?: return emptyList()

        val page = app.get(
            fixedUrl,
            referer = referer ?: "$mainUrl/",
            headers = mapOf(
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "User-Agent" to USER_AGENT
            )
        )
        val html = page.text
        val cookies = page.cookies

        val fSid = Regex("FdrFJe\\\":\\\"(-?\\d+)\\\"")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?: ""
        val bl = Regex("cfb2h\\\":\\\"([^\"]+)\\\"")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?: return emptyList()
        val hl = Regex("lang=\\\"([^\"]+)\\\"")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.ifBlank { null }
            ?: "en-US"
        val reqId = (10000..99999).random()

        val payload = "[[[\"$rpcId\",\"[\\\"$token\\\",\\\"\\\",0]\",null,\"generic\"]]]"
        val apiUrl = "$mainUrl/_/BloggerVideoPlayerUi/data/batchexecute" +
            "?rpcids=$rpcId&source-path=%2Fvideo.g&f.sid=$fSid&bl=$bl&hl=$hl&_reqid=$reqId&rt=c"

        val response = app.post(
            apiUrl,
            data = mapOf("f.req" to payload),
            referer = fixedUrl,
            cookies = cookies,
            headers = mapOf(
                "Origin" to mainUrl,
                "Accept" to "*/*",
                "Content-Type" to "application/x-www-form-urlencoded;charset=UTF-8",
                "X-Same-Domain" to "1",
                "User-Agent" to USER_AGENT
            )
        ).text

        return Regex("""https://[^\\s\"']+""")
            .findAll(decodeUnicodeEscapes(response))
            .map { it.value }
            .plus(
                Regex("""https://[^\\s\"']+""")
                    .findAll(response)
                    .map { it.value }
                )
            .map { normalizeVideoUrl(it) }
            .filter {
                it.contains("googlevideo.com/videoplayback") ||
                    it.contains("blogger.googleusercontent.com")
            }
            .distinct()
            .map { videoUrl ->
                ResolvedVideo(
                    videoUrl,
                    itagToQuality(
                        Regex("[?&]itag=(\\d+)")
                            .find(videoUrl)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.toIntOrNull()
                    )
                )
            }
            .toList()
    }

    private fun decodeUnicodeEscapes(input: String): String {
        val unicodeRegex = Regex("\\\\u([0-9a-fA-F]{4})")
        var output = input
        repeat(2) {
            output = unicodeRegex.replace(output) { match ->
                match.groupValues[1].toInt(16).toChar().toString()
            }
        }
        output = output.replace("\\\\/", "/")
        output = output.replace("\\\\=", "=")
        output = output.replace("\\\\&", "&")
        output = output.replace("\\\\\\\\", "\\")
        output = output.replace("\\\\\"", "\"")
        return output
    }

    private fun normalizeVideoUrl(input: String): String {
        return decodeUnicodeEscapes(input)
            .replace("\\u003d", "=")
            .replace("\\u0026", "&")
            .replace("\\u002F", "/")
            .replace("\\/", "/")
            .replace("\\", "")
    }

    private fun itagToQuality(itag: Int?): Int {
        return when (itag) {
            18 -> Qualities.P360.value
            22 -> Qualities.P720.value
            37 -> Qualities.P1080.value
            59 -> Qualities.P480.value
            43 -> Qualities.P360.value
            36 -> Qualities.P240.value
            17 -> Qualities.P144.value
            137 -> Qualities.P1080.value
            136 -> Qualities.P720.value
            135 -> Qualities.P480.value
            134 -> Qualities.P360.value
            133 -> Qualities.P240.value
            160 -> Qualities.P144.value
            else -> Qualities.Unknown.value
        }
    }
}

class OkRu : Odnoklassniki() {
    override var name = "OkRu"
    override var mainUrl = "https://ok.ru"
}

class GoogleVideo : ExtractorApi() {
    override var name = "GoogleVideo"
    override var mainUrl = "https://googlevideo.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        callback(
            newExtractorLink(
                source = name,
                name = name,
                url = url,
                type = ExtractorLinkType.VIDEO
            ) {
                this.quality = Qualities.Unknown.value
            }
        )
    }
}
