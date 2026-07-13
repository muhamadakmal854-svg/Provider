package com.mts.klikxxid

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import android.util.Log

class KlikxxidProvider : MainAPI() {
    override var mainUrl = "https://klikxxi.me"
    override var name = "Klikxxid"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "" to "Latest Update",
        "genre/action/" to "Action",
        "genre/comedy/" to "Comedy",
        "type/series/" to "Tv Series"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val pageUrl = if (page == 1) {
            "$mainUrl/${request.data}"
        } else {
            "$mainUrl/${request.data}page/$page/"
        }
        val document = app.get(pageUrl).document
        val items = document.select("article.item-infinite, div.gmr-box-item, article.post")
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
        try {
            val doc = app.get(data).document
            val playerIdElement = doc.selectFirst("#muvipro_player_content_id")
            val postId = playerIdElement?.attr("data-id") ?: ""
            
            val actualPostId = if (postId.isNotBlank()) postId else doc.selectFirst("link[rel='shortlink']")?.attr("href")?.split("?p=")?.lastOrNull() ?: ""
            if (actualPostId.isBlank()) return false
            
            val tabs = mutableListOf<String>()
            val playerTabs = doc.select("ul.muvipro-player-tabs a, ul.player-nav a")
            playerTabs.forEach { a ->
                val href = a.attr("href")
                if (href.startsWith("#p")) {
                    tabs.add(href.replace("#", ""))
                }
            }
            
            if (tabs.isEmpty()) {
                for (i in 1..7) {
                    tabs.add("p$i")
                }
            }
            
            val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
            
            for (tab in tabs) {
                try {
                    val response = app.post(
                        ajaxUrl,
                        data = mapOf(
                            "action" to "muvipro_player_content",
                            "tab" to tab,
                            "post_id" to actualPostId
                        ),
                        headers = mapOf(
                            "X-Requested-With" to "XMLHttpRequest",
                            "Content-Type" to "application/x-www-form-urlencoded"
                        )
                    ).text
                    
                    if (response.isBlank()) continue
                    
                    val ajaxDoc = Jsoup.parse(response)
                    val iframe = ajaxDoc.select("iframe").first()
                    val iframeSrc = iframe?.attr("src") ?: ""
                    
                    if (iframeSrc.isNotBlank()) {
                        val fixedSrc = fixUrl(iframeSrc)
                        loadExtractor(fixedSrc, data, subtitleCallback, callback)
                    }
                } catch (e: Exception) {
                    Log.e("KlikxxiProvider", "Error loading links for tab $tab: ${e.message}")
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("KlikxxiProvider", "Error in loadLinks: ${e.message}")
            return false
        }
    }
}
