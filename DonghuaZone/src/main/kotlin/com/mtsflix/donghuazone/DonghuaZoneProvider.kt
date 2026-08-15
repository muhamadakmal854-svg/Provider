package com.mtsflix.donghuazone

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import org.jsoup.nodes.Element

class DonghuaZoneProvider : MainAPI() {
    override var mainUrl = "https://www.donghuazone.com"
    override var name = "DonghuaZone"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "" to "Latest Episode",
        "search/label/Ongoing" to "Ongoing",
        "search/label/Movie" to "Movie",
        "search/label/Completed" to "Completed",
        "search/label/Action" to "Action",
        "search/label/Adventure" to "Adventure",
        "search/label/Fantasy" to "Fantasy",
        "search/label/Romance" to "Romance",
        "search/label/Donghua" to "Donghua"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path = request.data
        val url = if (path.isEmpty()) {
            if (page <= 1) "$mainUrl/" else "$mainUrl/page/$page/"
        } else {
            if (path.contains("?")) {
                if (page <= 1) "$mainUrl/$path" else "$mainUrl/$path&page=$page"
            } else {
                if (page <= 1) "$mainUrl/$path" else "$mainUrl/$path?page=$page"
            }
        }
        val document = app.get(url).document
        val items = document.select(".post-outer-container, article.post-outer-container, article.post")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3.post-title, .home-title, .grid2-tt, h2, h3")?.text()?.trim()
            ?: this.selectFirst("a[title]")?.attr("title")?.trim()
            ?: return null
        if (title.isBlank()) return null

        val a = this.selectFirst("a[href]") ?: return null
        val href = fixUrl(a.attr("href"))

        val imgElem = this.selectFirst("img[data-src], img[src]")
        val posterUrl = imgElem?.let {
            val src = it.attr("data-src").ifEmpty { it.attr("src") }
            if (src.startsWith("data:")) null else fixUrlNull(src)
        }

        val type = if (title.contains("Movie", true)) TvType.AnimeMovie else TvType.Anime

        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search?q=$query").document
        return document.select(".post-outer-container, article.post-outer-container, article.post")
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.title-stream, h1.post-title, h1.entry-title, h1")?.text()?.trim() ?: return null
        val posterUrl = document.selectFirst(".post-thumbnail, .post-body img, .entry-content img")?.let {
            val src = it.attr("data-src").ifEmpty { it.attr("src") }
            if (src.startsWith("data:")) null else fixUrlNull(src)
        }
        val plot = document.selectFirst(".sinoposis p, .post-body p")?.text()?.trim()
        val isMovie = title.contains("Movie", true) || url.contains("Movie", true)

        val ignoreGenres = setOf(
            "movie", "ongoing", "completed", "action", "adventure",
            "fantasy", "romance", "cultivation", "martial arts", "donghua", "episode", "3d"
        )

        var seriesLabel: String? = null
        document.select("a[href*='/search/label/']").forEach { a ->
            val href = a.attr("href")
            if (href.contains("/search/label/")) {
                val raw = href.substringAfter("/search/label/").substringBefore("?").replace("%20", " ").trim()
                if (raw.isNotEmpty() && !ignoreGenres.contains(raw.lowercase())) {
                    seriesLabel = raw
                }
            }
        }

        val episodes = mutableListOf<Episode>()
        if (!seriesLabel.isNullOrBlank()) {
            val feedUrl = "$mainUrl/feeds/posts/default/-/$seriesLabel?alt=json&max-results=100"
            try {
                val jsonText = app.get(feedUrl).text
                val json = parseJson<BloggerFeedResponse>(jsonText)
                json.feed?.entry?.reversed()?.forEachIndexed { index, entry ->
                    val epTitle = entry.title?.t ?: "Episode ${index + 1}"
                    val epHref = entry.link?.firstOrNull { it.rel == "alternate" }?.href
                    if (!epHref.isNullOrBlank()) {
                        episodes.add(newEpisode(epHref) {
                            this.name = epTitle
                            this.episode = index + 1
                        })
                    }
                }
            } catch (e: Exception) {
                // Fallback if feed API fails
            }
        }

        if (episodes.isEmpty()) {
            episodes.add(newEpisode(url) {
                this.name = title
            })
        }

        return if (isMovie) {
            newMovieLoadResponse(title, url, TvType.AnimeMovie, url) {
                this.posterUrl = posterUrl
                this.plot = plot
            }
        } else {
            newAnimeLoadResponse(title, url, TvType.Anime) {
                this.posterUrl = posterUrl
                this.plot = plot
                addEpisodes(DubStatus.Subbed, episodes)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val html = document.html()
        val foundServers = mutableListOf<String>()

        val regex = Regex("""changeServer\([^,]+,\s*["']([^"']+)["']\)""")
        regex.findAll(html).forEach { m ->
            val serverUrl = m.groupValues[1].trim()
            if (serverUrl.isNotBlank() && !foundServers.contains(serverUrl)) {
                foundServers.add(serverUrl)
            }
        }

        document.select("iframe").forEach { iframe ->
            val src = iframe.attr("data-src").ifEmpty { iframe.attr("src") }.trim()
            if (src.isNotBlank() && !foundServers.contains(src)) {
                foundServers.add(src)
            }
        }

        var count = 0
        for (serverUrl in foundServers) {
            val fixedUrl = fixUrl(serverUrl)

            if (fixedUrl.contains("dailymotion")) {
                val videoId = if (fixedUrl.contains("video=")) {
                    fixedUrl.substringAfter("video=").substringBefore("&").substringBefore("#")
                } else if (fixedUrl.contains("/embed/video/")) {
                    fixedUrl.substringAfter("/embed/video/").substringBefore("?").substringBefore("/")
                } else if (fixedUrl.contains("/video/")) {
                    fixedUrl.substringAfter("/video/").substringBefore("?").substringBefore("/")
                } else null

                if (!videoId.isNullOrBlank()) {
                    try {
                        val metaUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
                        val metaJsonText = app.get(metaUrl, headers = mapOf("Referer" to mainUrl)).text
                        val metaJson = parseJson<DailymotionMetaResponse>(metaJsonText)
                        val m3u8Url = metaJson.qualities?.auto?.firstOrNull()?.url
                        if (!m3u8Url.isNullOrBlank()) {
                            callback(
                                ExtractorLink(
                                    name,
                                    "DailyMotion (HD)",
                                    m3u8Url,
                                    referer = "https://www.dailymotion.com/",
                                    quality = Qualities.P1080.value,
                                    isM3u8 = true
                                )
                            )
                            count++
                        }
                    } catch (e: Exception) {
                        // Fallback
                    }
                    val stdEmbedUrl = "https://www.dailymotion.com/embed/video/$videoId"
                    loadExtractor(stdEmbedUrl, data, subtitleCallback, callback)
                    count++
                    continue
                }
            }

            loadExtractor(fixedUrl, data, subtitleCallback, callback)
            count++
        }

        return count > 0
    }

    data class BloggerFeedResponse(@JsonProperty("feed") val feed: BloggerFeed?)
    data class BloggerFeed(@JsonProperty("entry") val entry: List<BloggerEntry>?)
    data class BloggerEntry(@JsonProperty("title") val title: BloggerText?, @JsonProperty("link") val link: List<BloggerLink>?)
    data class BloggerText(@JsonProperty("$" + "t") val t: String?)
    data class BloggerLink(@JsonProperty("rel") val rel: String?, @JsonProperty("href") val href: String?)
    data class DailymotionMetaResponse(@JsonProperty("qualities") val qualities: DailymotionQualities?)
    data class DailymotionQualities(@JsonProperty("auto") val auto: List<DailymotionAutoStream>?)
    data class DailymotionAutoStream(@JsonProperty("url") val url: String?)
}
