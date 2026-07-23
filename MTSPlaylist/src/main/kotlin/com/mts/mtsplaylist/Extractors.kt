package com.mts.mtsplaylist

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

open class YoutubeMuxedExtractor : ExtractorApi() {
    override val name = "YouTube Muxed"
    override val mainUrl = "https://www.youtube.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val videoId = if (url.contains("v=")) {
            url.substringAfter("v=").substringBefore("&").substringBefore("?")
        } else if (url.contains("shorts/")) {
            url.substringAfter("shorts/").substringBefore("?").substringBefore("/")
        } else ""

        if (videoId.length < 8) return
        val cleanUrl = "https://www.youtube.com/watch?v=$videoId"
        val reqHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
            "Accept-Language" to "en-US,en;q=0.9",
            "Referer" to "https://www.youtube.com/"
        )

        try {
            val html = app.get(cleanUrl, headers = reqHeaders).text
            var found = false

            val hlsRegex = Regex("\"hlsManifestUrl\"\\s*:\\s*\"([^\"]+)\"")
            val hlsMatch = hlsRegex.find(html)
            if (hlsMatch != null) {
                val hlsUrl = hlsMatch.groupValues[1].replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&")
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name Live (M3U8)",
                        url = hlsUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.headers = reqHeaders
                        this.referer = "https://www.youtube.com/"
                    }
                )
                found = true
            }

            if (!found) {
                val sdRegex = Regex("ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\});(?:var|</script>)")
                val sdMatch = sdRegex.find(html)
                if (sdMatch != null) {
                    val jsonStr = sdMatch.groupValues[1]
                    val dataMap = parseJson<Map<String, Any>>(jsonStr)
                    val sd = dataMap["streamingData"] as? Map<String, Any>
                    if (sd != null) {
                        val hls = sd["hlsManifestUrl"] as? String
                        if (!hls.isNullOrBlank()) {
                            val cleanHls = hls.replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&")
                            callback.invoke(
                                newExtractorLink(
                                    source = name,
                                    name = "$name (M3U8)",
                                    url = cleanHls,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.headers = reqHeaders
                                    this.referer = "https://www.youtube.com/"
                                }
                            )
                            found = true
                        }

                        if (!found) {
                            val muxedFormats = (sd["formats"] as? List<Map<String, Any>>).orEmpty()
                            muxedFormats.forEach { f ->
                                val streamUrl = (f["url"] as? String)?.replace("\\/", "/")
                                if (!streamUrl.isNullOrBlank()) {
                                    val qualityLabel = (f["qualityLabel"] as? String) ?: (f["quality"] as? String) ?: "360p"
                                    val qVal = qualityLabel.replace("p", "").toIntOrNull() ?: Qualities.Unknown.value
                                    val isM3u8 = streamUrl.contains("m3u8")
                                    val linkType = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                    callback.invoke(
                                        newExtractorLink(
                                            source = name,
                                            name = "$name ($qualityLabel)",
                                            url = streamUrl,
                                            type = linkType
                                        ) {
                                            this.headers = reqHeaders
                                            this.referer = "https://www.youtube.com/"
                                            this.quality = qVal
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {}
    }
}
