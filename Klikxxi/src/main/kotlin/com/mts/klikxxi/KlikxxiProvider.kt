package com.mts.klikxxi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import android.util.Log

class KlikxxiProvider : MainAPI() {
    override var mainUrl = "https://za-ydf.org"
    override var name = "Klikxxi"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "category/movie/" to "Box Office",
        "category/anime/" to "Animasi",
        "category/serial-tv/" to "Serial TV",
        "" to "Update Terbaru",
        "category/semi/" to "SEMI",
        "category/bokep-indo/" to "Bokep Indo",
        "category/vivamax/" to "VIVAMAX",
        "category/jav-sub-indo/" to "Jav Sub Indo"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val path = request.data
        var pageUrl = if (page == 1) {
            if (path.isEmpty()) "$mainUrl/" else "$mainUrl/$path"
        } else {
            if (path.isEmpty()) "$mainUrl/page/$page/" else "$mainUrl/$path/page/$page/"
        }
        if (pageUrl.startsWith("https://")) {
            pageUrl = "https://" + pageUrl.substring(8).replace("//", "/")
        } else if (pageUrl.startsWith("http://")) {
            pageUrl = "http://" + pageUrl.substring(7).replace("//", "/")
        }
        val document = app.get(pageUrl, timeout = 30).document
        val items = document.select("article.item-infinite, div.gmr-box-item, article.post, article.item")
        val homeItems = items.mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, homeItems, hasNext = homeItems.isNotEmpty())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = this.selectFirst("a") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val img = this.selectFirst("img") ?: return null
        val posterUrl = img.let { i ->
            listOf("data-src", "data-lazy-src", "src").map { i.attr(it) }.firstOrNull { it.isNotBlank() }
        }?.let { fixUrlNull(it) }
        val title = this.selectFirst("h2, h3, .entry-title, .title")?.text()?.trim()
            ?: img.attr("alt").trim().ifEmpty { a.text().trim() }
            
        val isSeries = href.contains("/tv/") || href.contains("/series/") || this.selectFirst(".gmr-icon-tv, .gmr-duration-item")?.text()?.contains("Ep") == true

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = app.get(searchUrl).document
        return document.select("article.item-infinite, div.gmr-box-item, article.post").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title, .title-content")?.text()?.trim() ?: throw Exception("Title not found")
        val poster = document.selectFirst(".gmr-poster-img img, .poster img")?.let { i ->
            listOf("data-src", "data-lazy-src", "src").map { i.attr(it) }.firstOrNull { it.isNotBlank() }
        }?.let { fixUrlNull(it) }
        
        val plot = document.selectFirst(".entry-content p, .synopsis p")?.text()?.trim()

        val isSeries = url.contains("/tv/") || url.contains("/series/") || (document.selectFirst(".muvipro-player-tabs, ul.player-nav") == null && document.select("a.gmr-numpost").isNotEmpty())

        if (isSeries) {
            val episodes = mutableListOf<Episode>()
            val epsElements = document.select("a.gmr-numpost")
            if (epsElements.isNotEmpty()) {
                epsElements.forEachIndexed { index, element ->
                    val epUrl = element.attr("href")
                    val epNum = element.text().trim().toIntOrNull() ?: (index + 1)
                    episodes.add(
                        newEpisode(epUrl) {
                            this.episode = epNum
                            this.name = "Episode $epNum"
                        }
                    )
                }
            } else {
                val listEps = document.select(".gmr-listepisode a, .list-episode a")
                listEps.forEachIndexed { index, element ->
                    val epUrl = element.attr("href")
                    val epNum = element.text().trim().replace(Regex("[^0-9]"), "").toIntOrNull() ?: (index + 1)
                    episodes.add(
                        newEpisode(epUrl) {
                            this.episode = epNum
                            this.name = element.text().trim()
                        }
                    )
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.reversed()) {
                this.posterUrl = poster
                this.plot = plot
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCdn: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var found = false
        try {
            val doc = app.get(data, timeout = 30).document
            
            // 1. Extract direct iframe from the current page
            val mainIframeSrc = doc.selectFirst("iframe")?.attr("src")
            if (!mainIframeSrc.isNullOrBlank()) {
                val fixedSrc = fixUrl(mainIframeSrc)
                if (loadExtractor(fixedSrc, data, subtitleCallback, callback)) {
                    found = true
                }
            }
            
            // 2. Look for player tabs
            val playerTabs = doc.select("ul.muvipro-player-tabs a, ul.player-nav a, .gmr-player-nav a")
            playerTabs.forEach { a ->
                val href = a.attr("href")
                if (href.isNotBlank() && !href.startsWith("#") && href != data) {
                    try {
                        val tabUrl = fixUrl(href)
                        val tabDoc = app.get(tabUrl, timeout = 30).document
                        val tabIframe = tabDoc.selectFirst("iframe")?.attr("src")
                        if (!tabIframe.isNullOrBlank()) {
                            val fixedSrc = fixUrl(tabIframe)
                            if (loadExtractor(fixedSrc, tabUrl, subtitleCallback, callback)) {
                                found = true
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("KlikxxiProvider", "Error loading tab link: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("KlikxxiProvider", "Error in loadLinks: ${e.message}")
        }
        return found
    }
}
