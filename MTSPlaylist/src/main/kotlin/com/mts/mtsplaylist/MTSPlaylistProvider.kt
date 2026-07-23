package com.mts.mtsplaylist

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.extractors.YoutubeExtractor

class MTSPlaylistProvider : MainAPI() {
    override var mainUrl              = "https://www.youtube.com/playlist?list=PLp-xgC9kjBlnVYEyyQFyeP1lXGDrqn1GZ"
    override var name                 = "MTSPlaylist"
    override var lang                 = "ms"
    override val hasMainPage          = true
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Others
    )

    override val mainPage = mainPageOf(
        "https://www.youtube.com/playlist?list=PLp-xgC9kjBlnVYEyyQFyeP1lXGDrqn1GZ" to "Playlist Video"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = mutableListOf<SearchResponse>()
        try {
            val doc = app.get(request.data).document
            doc.select("a[href*=/watch?v=], a[href*=/shorts/]").forEach { element ->
                val href = element.attr("href")
                val videoId = if (href.contains("/watch?v=")) {
                    href.substringAfter("v=").substringBefore("&").substringBefore("?")
                } else if (href.contains("/shorts/")) {
                    href.substringAfter("/shorts/").substringBefore("?").substringBefore("/")
                } else ""

                if (videoId.isNotBlank() && videoId.length >= 8) {
                    val title = element.text().ifBlank { element.attr("title") }.ifBlank { "Video $videoId" }
                    val poster = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                    val fullUrl = "https://www.youtube.com/watch?v=$videoId"

                    if (items.none { it.url == fullUrl }) {
                        items.add(newMovieSearchResponse(title, fullUrl, TvType.Movie) {
                            this.posterUrl = poster
                        })
                    }
                }
            }
        } catch (e: Exception) {}

        if (items.isEmpty()) {
        items.add(newMovieSearchResponse("Quintino Bawah Tanah - MTS Remix", "https://www.youtube.com/watch?v=2ZZPMdi15sI", TvType.Movie) { this.posterUrl = "https://img.youtube.com/vi/2ZZPMdi15sI/hqdefault.jpg" })
        items.add(newMovieSearchResponse("Sing Off Part 26 - MTS Mashup Remix", "https://www.youtube.com/watch?v=lzAzyHANWj0", TvType.Movie) { this.posterUrl = "https://img.youtube.com/vi/lzAzyHANWj0/hqdefault.jpg" })
        items.add(newMovieSearchResponse("Mojo Romancinta - MTS Hardstyle Festival Remix", "https://www.youtube.com/watch?v=2903NPTDqeM", TvType.Movie) { this.posterUrl = "https://img.youtube.com/vi/2903NPTDqeM/hqdefault.jpg" })
        items.add(newMovieSearchResponse("MONTAGEM XONADA - MTS Hardstyle Festival Remix", "https://www.youtube.com/watch?v=xe2_X8qrW7k", TvType.Movie) { this.posterUrl = "https://img.youtube.com/vi/xe2_X8qrW7k/hqdefault.jpg" })
        items.add(newMovieSearchResponse("Lupakan With Vocal 1 Minute - MTS Drumstep Hardstyle Remix", "https://www.youtube.com/watch?v=Tm7X6TgesCM", TvType.Movie) { this.posterUrl = "https://img.youtube.com/vi/Tm7X6TgesCM/hqdefault.jpg" })
        items.add(newMovieSearchResponse("Lepaskan - MTS Hardstyle Remix", "https://www.youtube.com/watch?v=sy96151jPqQ", TvType.Movie) { this.posterUrl = "https://img.youtube.com/vi/sy96151jPqQ/hqdefault.jpg" })
        items.add(newMovieSearchResponse("EEEE A - MTS Hardstyle Remix", "https://www.youtube.com/watch?v=V8MufaAjguE", TvType.Movie) { this.posterUrl = "https://img.youtube.com/vi/V8MufaAjguE/hqdefault.jpg" })
        items.add(newMovieSearchResponse("Bazaar - MTS Hardstyle Remix", "https://www.youtube.com/watch?v=tGMo6FmF47w", TvType.Movie) { this.posterUrl = "https://img.youtube.com/vi/tGMo6FmF47w/hqdefault.jpg" })
        items.add(newMovieSearchResponse("Risk It All - MTS Hardstyle Remix", "https://www.youtube.com/watch?v=4VYucnfxmAQ", TvType.Movie) { this.posterUrl = "https://img.youtube.com/vi/4VYucnfxmAQ/hqdefault.jpg" })
        items.add(newMovieSearchResponse("Die With A Smile - MTS Hardstyle Remix", "https://www.youtube.com/watch?v=B2qtbtR0JJc", TvType.Movie) { this.posterUrl = "https://img.youtube.com/vi/B2qtbtR0JJc/hqdefault.jpg" })
        items.add(newMovieSearchResponse("MTS - BADAI KILAT", "https://www.youtube.com/watch?v=n5w3SLK5Hrk", TvType.Movie) { this.posterUrl = "https://img.youtube.com/vi/n5w3SLK5Hrk/hqdefault.jpg" })
        items.add(newMovieSearchResponse("MTS - DONNA (ORIGINAL MIX)", "https://www.youtube.com/watch?v=oQoo2IYTaa8", TvType.Movie) { this.posterUrl = "https://img.youtube.com/vi/oQoo2IYTaa8/hqdefault.jpg" })
        items.add(newMovieSearchResponse("HUNTRIX  ft MTS - GOLDEN (HARSTYLE MASHUP)", "https://www.youtube.com/watch?v=Kf_kb0CLoJA", TvType.Movie) { this.posterUrl = "https://img.youtube.com/vi/Kf_kb0CLoJA/hqdefault.jpg" })
        items.add(newMovieSearchResponse("MTS - ROCKET", "https://www.youtube.com/watch?v=Od5Q2MVZU3Y", TvType.Movie) { this.posterUrl = "https://img.youtube.com/vi/Od5Q2MVZU3Y/hqdefault.jpg" })
        items.add(newMovieSearchResponse("SKRILLEX - TRY IT OUT (MTS REMIX)", "https://www.youtube.com/watch?v=JMk6AzJVmnM", TvType.Movie) { this.posterUrl = "https://img.youtube.com/vi/JMk6AzJVmnM/hqdefault.jpg" })
        }

        return newHomePageResponse(request.name, items, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val items = mutableListOf<SearchResponse>()
        try {
            val searchUrl = "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(query, "UTF-8")
            val doc = app.get(searchUrl).document
            doc.select("a[href*=/watch?v=]").forEach { element ->
                val href = element.attr("href")
                val videoId = href.substringAfter("v=").substringBefore("&").substringBefore("?")
                if (videoId.isNotBlank() && videoId.length >= 8) {
                    val title = element.text().ifBlank { element.attr("title") }.ifBlank { "Video $videoId" }
                    val poster = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                    val fullUrl = "https://www.youtube.com/watch?v=$videoId"
                    if (items.none { it.url == fullUrl }) {
                        items.add(newMovieSearchResponse(title, fullUrl, TvType.Movie) {
                            this.posterUrl = poster
                        })
                    }
                }
            }
        } catch (e: Exception) {}
        return items
    }

    override suspend fun load(url: String): LoadResponse? {
        val videoId = if (url.contains("v=")) {
            url.substringAfter("v=").substringBefore("&").substringBefore("?")
        } else if (url.contains("shorts/")) {
            url.substringAfter("shorts/").substringBefore("?").substringBefore("/")
        } else url
        val title = "YouTube Video ($videoId)"
        val poster = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        val cleanUrl = if (videoId.length >= 8) "https://www.youtube.com/watch?v=$videoId" else url

        return newMovieLoadResponse(title, cleanUrl, TvType.Movie, cleanUrl) {
            this.posterUrl = poster
            this.plot = "YouTube Video: $cleanUrl"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isClipped: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val videoId = if (data.contains("v=")) {
            data.substringAfter("v=").substringBefore("&").substringBefore("?")
        } else if (data.contains("shorts/")) {
            data.substringAfter("shorts/").substringBefore("?").substringBefore("/")
        } else ""

        val cleanUrl = if (videoId.length >= 8) "https://www.youtube.com/watch?v=$videoId" else data
        var found = false

        val cb = { link: ExtractorLink ->
            found = true
            callback.invoke(link)
        }

        try {
            YoutubeExtractor().getUrl(cleanUrl, mainUrl, subtitleCallback, cb)
        } catch (e: Exception) {}

        if (!found) {
            try {
                loadExtractor(cleanUrl, mainUrl, subtitleCallback, cb)
            } catch (e: Exception) {}
        }

        if (!found && videoId.length >= 8) {
            try {
                val reqHeaders = mapOf(
                    "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
                    "Accept-Language" to "en-US,en;q=0.9",
                    "Referer" to "https://www.youtube.com/"
                )
                val html = app.get(cleanUrl, headers = reqHeaders).text

                // 1. Check for HLS Manifest M3U8 URL (Live Streams & M3U8)
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

                // 2. Check ytInitialPlayerResponse formats & adaptiveFormats
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
                                val formats = (sd["formats"] as? List<Map<String, Any>>).orEmpty() + (sd["adaptiveFormats"] as? List<Map<String, Any>>).orEmpty()
                                formats.forEach { f ->
                                    val streamUrl = (f["url"] as? String)?.replace("\\/", "/")
                                    if (!streamUrl.isNullOrBlank()) {
                                        val qualityLabel = (f["qualityLabel"] as? String) ?: (f["quality"] as? String) ?: "Auto"
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
                                        found = true
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        return true
    }
}
