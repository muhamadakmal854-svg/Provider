package com.mts.rebahin

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
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
        "$mainUrl/genre/adventure" to "Adventure",
        "$mainUrl/genre/comedy" to "Comedy",
        "$mainUrl/genre/crime" to "Crime",
        "$mainUrl/genre/drama" to "Drama",
        "$mainUrl/genre/fantasy" to "Fantasy",
        "$mainUrl/genre/mystery" to "Mystery",
        "$mainUrl/genre/romance" to "Romance",
        "$mainUrl/genre/science-fiction" to "Science Fiction",
        "$mainUrl/genre/thriller" to "Thriller",
        "$mainUrl/genre/animation" to "Animation",
        "$mainUrl/country/kr" to "Korea",
        "$mainUrl/country/cn" to "China",
        "$mainUrl/country/jp" to "Japan",
        "$mainUrl/country/ph" to "Philippines"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val baseUrl = request.data.trimEnd('/')
        val url = if (page <= 1) {
            baseUrl
        } else {
            "$baseUrl?page=$page"
        }

        val res = app.get(url, referer = "$mainUrl/").text
        val items = parseRebahinItems(res)

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = false
            ),
            hasNext = items.isNotEmpty()
        )
    }

    private fun parseRebahinItems(html: String): List<SearchResponse> {
        val items = mutableListOf<SearchResponse>()
        val regex = Regex("id.*?:\s*["\\]*([^"]+)["\\]*,\s*type.*?:\s*["\\]*([^"]+)["\\]*,\s*title.*?:\s*["\\]*([^"]+)["\\]*")

        regex.findAll(html).forEach { match ->
            val id = match.groupValues[1].replace("\\", "")
            val type = match.groupValues[2].replace("\\", "")
            val title = match.groupValues[3].replace("\\", "")

            if (id.isNotBlank() && title.isNotBlank() && !title.contains("REBAHIN", true)) {
                val isTv = type.equals("tv", ignoreCase = true)
                val itemUrl = if (isTv) "$mainUrl/tv/$id" else "$mainUrl/movies/$id"

                val item = if (isTv) {
                    newTvSeriesSearchResponse(title, itemUrl, TvType.TvSeries)
                } else {
                    newMovieSearchResponse(title, itemUrl, TvType.Movie)
                }
                items.add(item)
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

        val title = Regex("title.*?:\s*["\\]*([^"]+)["\\]*").find(res)?.groupValues?.get(1)?.replace("\\", "")
            ?: Regex("<h1[^>]*>([^<]+)</h1>").find(res)?.groupValues?.get(1)?.trim()
            ?: "Rebahin"

        val poster = Regex("posterUrl.*?:\s*["\\]*([^"]+)["\\]*").find(res)?.groupValues?.get(1)?.replace("\\", "")
            ?: Regex("posterPath.*?:\s*["\\]*([^"]+)["\\]*").find(res)?.groupValues?.get(1)?.replace("\\", "")?.let {
                if (it.startsWith("/")) "https://image.tmdb.org/t/p/w500$it" else it
            }

        val plot = Regex("overview.*?:\s*["\\]*([^"]+)["\\]*").find(res)?.groupValues?.get(1)?.replace("\\", "")

        val year = Regex("releaseYear.*?:\s*(\d+)").find(res)?.groupValues?.get(1)?.toIntOrNull()

        val isTv = url.contains("/tv/") || res.contains("episodes")

        if (isTv) {
            val episodes = mutableListOf<Episode>()
            val epRegex = Regex("episodeNumber.*?:\s*(\d+).*?seasonNumber.*?:\s*(\d+).*?name.*?:\s*["\\]*([^"]+)["\\]*")

            epRegex.findAll(res).forEach { match ->
                val epNum = match.groupValues[1].toIntOrNull() ?: 1
                val seasonNum = match.groupValues[2].toIntOrNull() ?: 1
                val epName = match.groupValues[3].replace("\\", "")

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
        val playbackUrls = mutableListOf<String>()

        val regex = Regex("playbackUrl.*?:\s*["\\]*([^"]+)["\\]*")
        regex.findAll(res).forEach { match ->
            val link = match.groupValues[1].replace("\\/", "/").replace("\\", "")
            if (link.isNotBlank() && link.startsWith("http")) {
                playbackUrls.add(link)
            }
        }

        val mediaRegex = Regex("https?://[^\\s"'<>]+\\.(?:m3u8|mp4)[^\\s"'< >]*")
        mediaRegex.findAll(res).forEach { match ->
            val link = match.value.replace("\\/", "/").replace("\\", "")
            if (link.isNotBlank() && !link.contains("advertisement") && !link.contains("logo")) {
                playbackUrls.add(link)
            }
        }

        var foundLinks = false

        for (link in playbackUrls.distinct()) {
            val isM3u8 = link.contains(".m3u8", ignoreCase = true)
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    link,
                    if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.P1080.value
                    this.headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to "$mainUrl/"
                    )
                }
            )
            foundLinks = true
        }

        return foundLinks
    }
}
