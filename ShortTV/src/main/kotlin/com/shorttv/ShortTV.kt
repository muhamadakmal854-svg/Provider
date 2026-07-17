package com.shorttv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup

class ShortTV : MainAPI() {
    override var mainUrl = "https://www.shorttv.live"
    override var name = "ShortTV"
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
        val items = doc.select("a[href^=/drama/]").mapNotNull {
            val href = it.attr("href")
            val title = it.text().trim()
            val img = it.select("img").attr("src")
            if (href.isBlank() || title.isBlank()) return@mapNotNull null
            newAnimeSearchResponse(title, "$mainUrl$href", TvType.AsianDrama) {
                this.posterUrl = img
            }
        }.distinctBy { it.url }
        return newHomePageResponse(request.name, items, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val html = app.get(mainUrl).text
        val doc = Jsoup.parse(html)
        return doc.select("a[href^=/drama/]").mapNotNull {
            val href = it.attr("href")
            val title = it.text().trim()
            val img = it.select("img").attr("src")
            if (href.isBlank() || title.isBlank()) return@mapNotNull null
            if (!title.contains(query, ignoreCase = true)) return@mapNotNull null
            newAnimeSearchResponse(title, "$mainUrl$href", TvType.AsianDrama) {
                this.posterUrl = img
            }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val html = app.get(url).text
        val doc = Jsoup.parse(html)
        val title = doc.select("h1").text().trim().ifBlank { 
            doc.select("title").text().substringBefore(" - ShortTV").trim().ifBlank { "Drama" } 
        }
        val cover = doc.select("div.cover img").attr("src").ifBlank {
            doc.select("img").attr("src")
        }
        val plot = doc.select("p.intro").text().trim().ifBlank {
            doc.select("div.description").text().trim()
        }

        val dramaId = url.substringAfterLast("-").trim()
        val slug = url.substringAfter("/drama/").substringBeforeLast("-").trim()

        val epElements = doc.select("a[href^=/episode/]")
        val episodeList = if (!epElements.isEmpty()) {
            epElements.mapIndexed { idx, it ->
                val epHref = it.attr("href")
                val epName = it.text().trim().ifBlank { "Episode ${idx + 1}" }
                newEpisode("$mainUrl$epHref") {
                    this.name = epName
                    this.episode = idx + 1
                }
            }
        } else {
            (1..80).map { num ->
                val epUrl = "$mainUrl/episode/$slug-$dramaId-$num"
                newEpisode(epUrl) {
                    this.name = "Episode $num"
                    this.episode = num
                }
            }
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
        if (data.isBlank()) return false
        val html = app.get(data).text
        val matches = Regex("""(https?://[^\s"'\\]+\.m3u8[^\s"'\\]*)""").findAll(html)
        var found = false
        matches.map { it.value.replace("\\", "") }.distinct().forEach { m3u8 ->
            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = m3u8,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.headers = mapOf(
                        "Origin" to mainUrl,
                        "Referer" to "$mainUrl/"
                    )
                }
            )
            found = true
        }
        return found
    }
}
