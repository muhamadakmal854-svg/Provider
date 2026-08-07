package com.mts.rebahin

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class RebahinProvider : MainAPI() {
    override var mainUrl = "https://165.232.44.215"
    override var name = "Rebahin"
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "$mainUrl/movies" to "Movies Terbaru",
        "$mainUrl/tv" to "TV Series",
        "$mainUrl/genre/action" to "Action",
        "$mainUrl/genre/horror" to "Horror",
        "$mainUrl/genre/drama" to "Drama"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}?page=$page"
        val res = app.get(url, referer = "$mainUrl/").text
        val items = parseRebahinItems(res)
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    private fun parseRebahinItems(html: String): List<SearchResponse> {
        val items = mutableListOf<SearchResponse>()
        val cleanHtml = html.replace(chr(92).toString() + chr(34).toString(), chr(34).toString()).replace(chr(92).toString() + chr(47).toString(), chr(47).toString())

        // 1. Next.js JSON RSC Payload Parser
        val itemRegex = Regex(""""id"\s*:\s*"([^"]+)".*?"type"\s*:\s*"([^"]+)".*?"title"\s*:\s*"([^"]+)"""")
        itemRegex.findAll(cleanHtml).forEach { match ->
            val id = match.groupValues[1]
            val type = match.groupValues[2]
            val title = match.groupValues[3]

            if (id.isNotBlank() && title.isNotBlank() && !title.startsWith("REBAHIN", true)) {
                val isTv = type.equals("tv", ignoreCase = true)
                val itemUrl = if (isTv) "$mainUrl/tv/$id" else "$mainUrl/movies/$id"

                // Find poster for this ID
                val posterRegex = Regex(""""id"\s*:\s*"{id}".*?"posterPath"\s*:\s*"([^"]+)"""")
                var poster = posterRegex.find(cleanHtml)?.groupValues?.get(1)
                if (poster != null && poster.startsWith("/")) {
                    poster = "https://image.tmdb.org/t/p/w500$poster"
                }

                val item = if (isTv) {
                    newTvSeriesSearchResponse(title, itemUrl, TvType.TvSeries) {
                        this.posterUrl = poster
                    }
                } else {
                    newMovieSearchResponse(title, itemUrl, TvType.Movie) {
                        this.posterUrl = poster
                    }
                }
                items.add(item)
            }
        }

        // 2. DOM HTML Jsoup Fallback
        if (items.isEmpty()) {
            val doc = Jsoup.parse(html)
            doc.select("a[href^='/movies/'], a[href^='/tv/']").forEach { a ->
                val href = a.attr("href")
                val title = a.select(".font-medium, span, img").text().ifBlank { a.attr("title") }
                val poster = a.select("img").attr("src").let {
                    if (it.startsWith("/")) "https://image.tmdb.org/t/p/w500$it" else it
                }

                if (href.isNotBlank() && title.isNotBlank() && !title.contains("REBAHIN", true)) {
                    val isTv = href.startsWith("/tv/")
                    val fullUrl = if (href.startsWith("http")) href else "$mainUrl$href"

                    val item = if (isTv) {
                        newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries) {
                            this.posterUrl = poster.ifBlank { null }
                        }
                    } else {
                        newMovieSearchResponse(title, fullUrl, TvType.Movie) {
                            this.posterUrl = poster.ifBlank { null }
                        }
                    }
                    items.add(item)
                }
            }
        }

        return items.distinctBy { it.url }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchApiUrl = "$mainUrl/api/search?q=${query}"
        val res = app.get(searchApiUrl, referer = "$mainUrl/").text
        return parseRebahinItems(res)
    }

    override suspend fun load(url: String): LoadResponse {
        val res = app.get(url, referer = "$mainUrl/").text
        val cleanHtml = res.replace(chr(92).toString() + chr(34).toString(), chr(34).toString()).replace(chr(92).toString() + chr(47).toString(), chr(47).toString())

        val title = Regex(""""title"\s*:\s*"([^"]+)"""").find(cleanHtml)?.groupValues?.get(1)
            ?: Regex("""<h1[^>]*>([^<]+)</h1>""").find(cleanHtml)?.groupValues?.get(1)?.trim()
            ?: "Rebahin"

        val poster = Regex(""""posterUrl"\s*:\s*"([^"]+)"""").find(cleanHtml)?.groupValues?.get(1)
            ?: Regex(""""posterPath"\s*:\s*"([^"]+)"""").find(cleanHtml)?.groupValues?.get(1)?.let {
                if (it.startsWith("/")) "https://image.tmdb.org/t/p/w500$it" else it
            }

        val plot = Regex(""""overview"\s*:\s*"([^"]+)"""").find(cleanHtml)?.groupValues?.get(1)

        val year = Regex(""""releaseYear"\s*:\s*(\d+)""").find(cleanHtml)?.groupValues?.get(1)?.toIntOrNull()

        val isTv = url.contains("/tv/") || cleanHtml.contains("episodes")

        if (isTv) {
            val episodes = mutableListOf<Episode>()
            val epRegex = Regex(""""episodeNumber"\s*:\s*(\d+).*?"seasonNumber"\s*:\s*(\d+).*?"name"\s*:\s*"([^"]+)"""")

            epRegex.findAll(cleanHtml).forEach { match ->
                val epNum = match.groupValues[1].toIntOrNull() ?: 1
                val seasonNum = match.groupValues[2].toIntOrNull() ?: 1
                val epName = match.groupValues[3]

                val epUrl = "$url?season=$seasonNum&episode=$epNum"
                episodes.add(
                    newEpisode(epUrl) {
                        this.name = epName
                        this.episode = epNum
                        this.season = seasonNum
                    }
                )
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = app.get(data, referer = "$mainUrl/").text
        val cleanHtml = res.replace(chr(92).toString() + chr(34).toString(), chr(34).toString()).replace(chr(92).toString() + chr(47).toString(), chr(47).toString())

        // Extract playbackUrl from RSC payload
        val regex = Regex(""""playbackUrl"\s*:\s*"([^"]+)"""")
        val playbackUrl = regex.find(cleanHtml)?.groupValues?.get(1)

        if (playbackUrl != null) {
            val target = if (playbackUrl.startsWith("http")) playbackUrl else "$mainUrl$playbackUrl"
            loadExtractor(target, data, subtitleCallback, callback)
            return true
        }

        // Fallback: search for any m3u8 or mp4 in page
        val mediaRegex = Regex("""https?://[^\s"'<>]+\.(?:m3u8|mp4)[^\s"'<>]*""")
        mediaRegex.findAll(cleanHtml).forEach { m ->
            callback.invoke(
                newExtractorLink(this.name, this.name, m.value, ExtractorLinkType.VIDEO) {
                    this.referer = "$mainUrl/"
                }
            )
        }

        return true
    }
}
