package com.mgtv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup

class MGTV : MainAPI() {
    override var mainUrl = "https://w.mgtv.com"
    override var name = "MGTV"
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.AsianDrama,
        TvType.Movie
    )

    override val mainPage = mainPageOf(
        "trending" to "Trending"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val html = app.get(mainUrl).text
        val doc = Jsoup.parse(html)
        val items = doc.select("a[href*=/b/]").mapNotNull {
            val href = it.attr("href")
            val title = it.text().trim().ifBlank { it.select("img").attr("alt") }
            val img = it.select("img").attr("src")
            if (href.isBlank() || title.isBlank()) return@mapNotNull null
            newAnimeSearchResponse(title, if (href.startsWith("http")) href else "$mainUrl$href", TvType.AsianDrama) {
                this.posterUrl = img
            }
        }.distinctBy { it.url }
        return newHomePageResponse(request.name, items, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/s?k=${query.replace(" ", "%20")}"
        val html = app.get(searchUrl).text
        val doc = Jsoup.parse(html)
        return doc.select("a[href*=/b/]").mapNotNull {
            val href = it.attr("href")
            val title = it.text().trim().ifBlank { it.select("img").attr("alt") }
            val img = it.select("img").attr("src")
            if (href.isBlank() || title.isBlank()) return@mapNotNull null
            newAnimeSearchResponse(title, if (href.startsWith("http")) href else "$mainUrl$href", TvType.AsianDrama) {
                this.posterUrl = img
            }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val html = app.get(url).text
        val doc = Jsoup.parse(html)
        val title = doc.select("h1").text().trim().ifBlank { 
            doc.select("title").text().substringBefore(" - ").trim().ifBlank { "Drama" } 
        }
        val cover = doc.select("img").attr("src")
        val plot = doc.select("p.desc").text().trim()

        val episodeList = mutableListOf<Episode>()
        doc.select("a[href*=/b/]").forEachIndexed { idx, it ->
            val epHref = it.attr("href")
            val epName = it.text().trim().ifBlank { "Episode ${idx + 1}" }
            episodeList.add(
                newEpisode(if (epHref.startsWith("http")) epHref else "$mainUrl$epHref") {
                    this.name = epName
                    this.episode = idx + 1
                }
            )
        }

        if (episodeList.isEmpty()) {
            episodeList.add(newEpisode(url) {
                this.name = "Play"
                this.episode = 1
            })
        }

        return newTvSeriesLoadResponse(
            title,
            url,
            TvType.AsianDrama,
            episodeList
        ) {
            this.posterUrl = cover
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return false
    }
}
