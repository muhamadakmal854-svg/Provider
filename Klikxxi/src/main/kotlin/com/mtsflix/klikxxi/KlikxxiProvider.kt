package com.mtsflix.klikxxi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import android.util.Log

class KlikxxiProvider : MainAPI() {
    override var mainUrl = "https://forumikatolik.net"
    override var name = "Klikxxi"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "category/movie/" to "Movies",
        "category/serial-tv/" to "Serial TV",
        "category/anime/" to "Anime",
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
            val cleanPath = path.removeSuffix("/")
            if (cleanPath.isEmpty()) "$mainUrl/page/$page/" else "$mainUrl/$cleanPath/page/$page/"
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
        val img = this.selectFirst("img")
        val posterUrl = img?.let { i ->
            listOf("data-src", "data-lazy-src", "data-original", "src").map { i.attr(it) }.firstOrNull { it.isNotBlank() && !it.startsWith("data:") }
        }?.let { fixUrlNull(it) }

        var rawTitle = this.selectFirst("h2.entry-title, h3.entry-title, h2, h3, .entry-title, .title")?.text()?.trim()
        if (rawTitle.isNullOrBlank()) {
            rawTitle = a.attr("title").ifEmpty { img?.attr("alt").orEmpty().ifEmpty { a.text() } }
        }
        val title = rawTitle
            .removePrefix("Permalink ke: ")
            .removePrefix("Permalink to: ")
            .removePrefix("Download ")
            .split("Sub Indo")[0]
            .split("Full Movie")[0]
            .split("Full Episode")[0]
            .trim()
            
        if (title.isBlank() || href.isBlank()) return null

        val isSeries = href.contains("/tv/") || href.contains("/series/") || href.contains("/serial-tv/") || href.contains("full-episode", true) || title.contains("Season", true)

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

            fun Element?.getIframeSrc(): String? {
                if (this == null) return null
                val candidates = listOf(
                    attr("data-litespeed-src"),
                    attr("data-lazy-src"),
                    attr("data-src"),
                    attr("data-video"),
                    attr("data-embed"),
                    attr("data-url"),
                    attr("data-iframe"),
                    attr("src")
                )
                return candidates.firstOrNull { it.isNotBlank() && !it.equals("about:blank", true) && !it.startsWith("javascript", true) }
            }
            
            // 1. Direct iframe on page (including Litespeed data-litespeed-src)
            val mainIframeSrc = doc.selectFirst("iframe").getIframeSrc()
            if (!mainIframeSrc.isNullOrBlank()) {
                val fixedSrc = fixUrl(mainIframeSrc)
                if (loadExtractor(fixedSrc, data, subtitleCallback, callback)) {
                    found = true
                }
            }

            // 2. Muvipro AJAX player tabs (#p1, #p2, #p3, etc.)
            val postId = doc.selectFirst("div.gmr-server-wrap[data-id]")?.attr("data-id")
                ?: doc.selectFirst("div[data-id]")?.attr("data-id")
                
            if (!postId.isNullOrBlank()) {
                val playerTabs = doc.select("ul.muvipro-player-tabs a[href^='#p'], ul.nav-tabs a[href^='#p']")
                playerTabs.forEach { a ->
                    val tabName = a.attr("href").removePrefix("#").trim()
                    if (tabName.isNotBlank()) {
                        try {
                            val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
                            val resHtml = app.post(
                                ajaxUrl,
                                data = mapOf(
                                    "action" to "muvipro_player_content",
                                    "tab" to tabName,
                                    "post_id" to postId
                                ),
                                headers = mapOf(
                                    "X-Requested-With" to "XMLHttpRequest",
                                    "Referer" to data
                                ),
                                timeout = 15
                            ).text
                            
                            val tabIframeSrc = Jsoup.parse(resHtml).selectFirst("iframe").getIframeSrc()
                            if (!tabIframeSrc.isNullOrBlank()) {
                                val fixedSrc = fixUrl(tabIframeSrc)
                                if (loadExtractor(fixedSrc, data, subtitleCallback, callback)) {
                                    found = true
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("KlikxxiProvider", "Error loading tab $tabName: ${e.message}")
                        }
                    }
                }
            }

            // 3. Player tabs with URLs (e.g. ?player=2, ?player=3 or full URLs)
            val playerTabs = doc.select("ul.muvipro-player-tabs a, ul.player-nav a, .gmr-player-nav a, ul#gmr-tab a")
            playerTabs.forEach { a ->
                val href = a.attr("href")
                if (href.isNotBlank() && !href.startsWith("#") && href != "javascript:void(0)" && href != data) {
                    try {
                        val tabUrl = fixUrl(href)
                        val tabDoc = app.get(tabUrl, timeout = 30).document
                        val tabIframe = tabDoc.selectFirst("iframe").getIframeSrc()
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
