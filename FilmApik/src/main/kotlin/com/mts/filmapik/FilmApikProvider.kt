package com.mts.filmapik

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class FilmApikProvider : MainAPI() {
    override var mainUrl = "http://167.172.70.31"
    override var name = "FilmApik"
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Film Terbaru",
        "$mainUrl/tv/" to "TV Series",
        "$mainUrl/genre/action/" to "Action",
        "$mainUrl/genre/horror/" to "Horror",
        "$mainUrl/genre/adventure/" to "Adventure",
        "$mainUrl/genre/comedy/" to "Comedy",
        "$mainUrl/genre/crime/" to "Crime",
        "$mainUrl/genre/drama/" to "Drama",
        "$mainUrl/genre/fantasy/" to "Fantasy",
        "$mainUrl/genre/mystery/" to "Mystery",
        "$mainUrl/genre/romance/" to "Romance",
        "$mainUrl/genre/science-fiction/" to "Science Fiction",
        "$mainUrl/genre/thriller/" to "Thriller",
        "$mainUrl/genre/animation/" to "Animation",
        "$mainUrl/country/korea/" to "Korea",
        "$mainUrl/country/china/" to "China"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val baseUrl = request.data.trimEnd('/')
        val url = if (page <= 1) {
            "$baseUrl/"
        } else {
            "$baseUrl/page/$page/"
        }

        val doc = app.get(url, referer = "$mainUrl/").document
        val items = doc.select("article.item, article.has-post-thumbnail, div.gmr-box-content")
            .mapNotNull { it.toSearchResponse() }
            .distinctBy { it.url }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = false
            ),
            hasNext = items.isNotEmpty()
        )
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val linkElem = this.selectFirst("a[itemprop=url], a[href*='/tv/'], a[href*='/movie/'], a[href]") ?: return null
        val href = fixUrlNull(linkElem.attr("href")) ?: return null
        
        val rawTitle = linkElem.attr("title").takeIf { it.isNotBlank() }
            ?: this.selectFirst("h2, h3, .entry-title")?.text()
            ?: linkElem.text()
        
        val cleanTitle = rawTitle.replace("Permalink to: ", "", ignoreCase = true).trim()
        if (cleanTitle.isBlank()) return null

        val imgElem = this.selectFirst("img")
        val poster = fixUrlNull(
            imgElem?.attr("src")?.takeIf { it.isNotBlank() && !it.contains("data:image") }
                ?: imgElem?.attr("srcset")?.split(",")?.firstOrNull()?.split(" ")?.firstOrNull()
                ?: imgElem?.attr("data-src")
        )

        val quality = this.selectFirst(".gmr-quality-item, .quality")?.text()?.trim()
        val isTv = href.contains("/tv/") || cleanTitle.contains("Season", true)

        return if (isTv) {
            newTvSeriesSearchResponse(cleanTitle, href, TvType.TvSeries) {
                this.posterUrl = poster
                if (!quality.isNullOrBlank()) {
                    this.addQuality(quality)
                }
            }
        } else {
            newMovieSearchResponse(cleanTitle, href, TvType.Movie) {
                this.posterUrl = poster
                if (!quality.isNullOrBlank()) {
                    this.addQuality(quality)
                }
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=${query}&post_type[]=post&post_type[]=tv"
        val doc = app.get(searchUrl, referer = "$mainUrl/").document
        return doc.select("article.item, article.has-post-thumbnail, div.gmr-box-content")
            .mapNotNull { it.toSearchResponse() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, referer = "$mainUrl/").document

        val title = doc.selectFirst("h1.entry-title, h1")?.text()
            ?.replace("Permalink to: ", "", ignoreCase = true)?.trim() ?: "FilmApik"
            
        val poster = fixUrlNull(
            doc.selectFirst(".content-poster img, article img, meta[property='og:image']")?.attr("src")
                ?: doc.selectFirst(".content-poster img, article img")?.attr("data-src")
        )

        val plot = doc.selectFirst(".entry-content p, .gmr-movie-synopsis p, meta[property='og:description']")?.text()?.trim()

        val year = doc.select(".gmr-moviedata").text().let { text ->
            Regex("""(20\d\d|19\d\d)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
        }

        val tags = doc.select("a[href*='/genre/'], a[href*='/country/']").map { it.text().trim() }.distinct()

        val isTv = url.contains("/tv/") || doc.selectFirst(".gmr-listseries") != null

        if (isTv) {
            val episodes = mutableListOf<Episode>()
            val episodeElems = doc.select(".gmr-listseries a")

            for (elem in episodeElems) {
                val epHref = fixUrlNull(elem.attr("href")) ?: continue
                if (epHref.contains("/tv/") || elem.text().contains("View All", true)) continue
                
                val epText = elem.text().trim()
                val epNum = Regex("""(?:eps|episode|s\d+eps)\s*(\d+)""", RegexOption.IGNORE_CASE)
                    .find(epText)?.groupValues?.get(1)?.toIntOrNull() ?: (episodes.size + 1)
                
                val seasonNum = Regex("""s(\d+)""", RegexOption.IGNORE_CASE)
                    .find(epText)?.groupValues?.get(1)?.toIntOrNull() ?: 1

                episodes.add(
                    newEpisode(epHref) {
                        this.name = if (epText.isNotBlank()) epText else "Episode $epNum"
                        this.episode = epNum
                        this.season = seasonNum
                    }
                )
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, referer = "$mainUrl/").document
        val iframeUrls = mutableListOf<String>()

        doc.select("iframe[src], iframe[data-src]").forEach { iframe ->
            val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }
            if (src.isNotBlank() && !src.contains("facebook") && !src.contains("twitter")) {
                iframeUrls.add(src)
            }
        }

        doc.select(".gmr-embed-responsive iframe, #player-1 iframe, #player-2 iframe").forEach { iframe ->
            val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }
            if (src.isNotBlank()) {
                iframeUrls.add(src)
            }
        }

        var foundLinks = false

        for (rawUrl in iframeUrls.distinct()) {
            val fixedUrl = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
            val mappedUrls = mutableListOf(fixedUrl)
            
            if (fixedUrl.contains("morencius.com") || fixedUrl.contains("mivalyo.com")) {
                val id = fixedUrl.substringAfter("/embed/").substringBefore("?")
                mappedUrls.add("https://vidhideplus.com/embed/$id")
            } else if (fixedUrl.contains("hglink.to") || fixedUrl.contains("jodwish.com") || fixedUrl.contains("boosterx.stream")) {
                val id = fixedUrl.substringAfter("/e/").substringAfter("/v/").substringBefore("?")
                mappedUrls.add("https://streamwish.to/e/$id")
            }

            for (targetUrl in mappedUrls) {
                loadExtractor(targetUrl, "$mainUrl/", subtitleCallback) { link ->
                    foundLinks = true
                    callback.invoke(link)
                }
            }
        }

        return foundLinks
    }
}
