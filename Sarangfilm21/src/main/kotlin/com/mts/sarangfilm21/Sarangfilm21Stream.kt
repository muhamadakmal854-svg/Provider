package com.mts.sarangfilm21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import android.util.Log

class Sarangfilm21Provider : MainAPI() {

    override var mainUrl        = "https://sarangfilm.diy"
    override var name           = "Sarangfilm21"
    override var lang           = "id"
    override val hasMainPage    = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override val mainPage = mainPageOf(
        "" to "Film Terbaru",
        "category/action/" to "Action",
        "category/horror/" to "Horror",
        "category/comedy/" to "Comedy",
        "category/science-fiction/" to "Science Fiction",
        "category/coming-soon/" to "Coming Soon",
        "category/film-jepang/" to "Film Jepang",
        "country/korea/" to "Korea",
        "category/film-semi/" to "Film Semi",
        "country/indonesia/" to "Indonesia",
        "category/animation/" to "Animation"
    )

    private fun Element.toSearchResult(): SearchResponse? {
        val a = (if (this.tagName() == "a") this else this.selectFirst("a")) ?: return null
        val href = a.attr("href").let { h -> if (h.startsWith("http")) h else "$mainUrl$h" }
        val img = this.selectFirst("img") ?: this.selectFirst("[data-src], [data-lazy-src], [data-original]")
        var title = this.selectFirst(".entry-title, h2, h3, .title")?.text()?.trim()
        if (title.isNullOrBlank()) {
            title = a.attr("title").trim().ifEmpty { img?.attr("alt")?.trim() ?: "" }
        }
        if (title.isBlank()) return null
        
        val poster = img?.let { getPosterUrl(it) }
        val isSeries = href.contains("/tv/") || href.contains("/series/") || href.contains("/serial-tv/")
        
        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path = request.data
        val pageUrl = if (page == 1) {
            if (path.isEmpty()) "$mainUrl/" else "$mainUrl/$path"
        } else {
            val cleanPath = path.removeSuffix("/")
            if (cleanPath.isEmpty()) "$mainUrl/page/$page/" else "$mainUrl/$cleanPath/page/$page/"
        }
        val doc = app.get(pageUrl, headers = mapOf("Referer" to mainUrl, "User-Agent" to USER_AGENT), timeout = 30).document
        val items = doc.select(".gmr-item-modulepost, .gmr-item-archivepost, article.item, article.post").mapNotNull {
            it.toSearchResult()
        }.distinctBy { it.url }
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=${query.replace(" ", "+")}"
        val doc = app.get(searchUrl, headers = mapOf("Referer" to mainUrl, "User-Agent" to USER_AGENT), timeout = 30).document
        return doc.select(".gmr-item-modulepost, .gmr-item-archivepost, article.item, article.post").mapNotNull {
            it.toSearchResult()
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("Referer" to mainUrl, "User-Agent" to USER_AGENT), timeout = 30).document
        val title = doc.selectFirst("h1.entry-title, .entry-title")?.text()?.trim() ?: return null
        val poster = doc.selectFirst(".thumb img, .film-poster img, .entry-thumb img")?.let { getPosterUrl(it) }
        val plot = doc.selectFirst(".entry-content p, .synopsis p")?.text()?.trim()
        val genres = doc.select(".genxed a, .genre-info a, .film-genres a").map { it.text().trim() }
        
        val isSeries = url.contains("/tv/") || url.contains("/series/") || doc.select(".eplister ul li a").isNotEmpty()
        
        return if (isSeries) {
            val episodes = doc.select(".eplister ul li a, .episodelist ul li a").mapNotNull { a ->
                val epUrl = a.attr("href")
                val epName = a.text().trim()
                if (epUrl.isNotBlank()) newEpisode(epUrl) { this.name = epName } else null
            }.reversed()
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = genres
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = genres
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
        val extractedUrls = mutableSetOf<String>()

        suspend fun processExtractorUrl(rawUrl: String) {
            val cleanUrl = fixUrl(rawUrl, data, mainUrl)
            if (cleanUrl.isBlank() || extractedUrls.contains(cleanUrl) || cleanUrl.startsWith("about:blank", true)) return
            extractedUrls.add(cleanUrl)

            val isAbyss = listOf("abyssplayer.com", "abyss.to", "abysscdn.com", "iamcdn.net", "sssrr").any { cleanUrl.contains(it, true) }
            val isStreamWish = listOf("streamwish", "mwish", "wishembed", "morencius", "filelinks", "strwish", "embedpyrox").any { cleanUrl.contains(it, true) }

            when {
                isAbyss -> {
                    try {
                        AbyssExtractor().getUrl(cleanUrl, data, subtitleCallback, callback)
                        found = true
                    } catch (e: Exception) {
                        Log.e("Sarangfilm21Provider", "AbyssExtractor error: ${e.message}")
                    }
                }
                isStreamWish -> {
                    try {
                        SarangStreamWishExtractor().getUrl(cleanUrl, data, subtitleCallback, callback)
                        found = true
                    } catch (_: Exception) {
                        try {
                            loadExtractor(cleanUrl, data, subtitleCallback, callback)
                            found = true
                        } catch (_: Exception) {}
                    }
                }
                else -> {
                    try {
                        loadExtractor(cleanUrl, data, subtitleCallback, callback)
                        found = true
                    } catch (_: Exception) {}
                }
            }
        }

        try {
            val doc = app.get(data, headers = mapOf("Referer" to mainUrl, "User-Agent" to USER_AGENT), timeout = 30).document

            // 1. Direct iframe on page
            val mainIframeSrc = doc.selectFirst("iframe").getIframeSrc()
            if (!mainIframeSrc.isNullOrBlank()) {
                processExtractorUrl(mainIframeSrc)
            }

            // 2. Muvipro AJAX player tabs (#p1, #p2, #p3, #p4, #p5)
            val postId = doc.selectFirst("div.gmr-server-wrap[data-id]")?.attr("data-id")
                ?: doc.selectFirst("div[data-id]")?.attr("data-id")
                
            if (!postId.isNullOrBlank()) {
                val playerTabs = doc.select("ul.muvipro-player-tabs a[href^='#p'], ul.nav-tabs a[href^='#p'], ul#gmr-tab a[href^='#p']")
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
                            
                            val tabDoc = Jsoup.parse(resHtml)
                            val tabIframeSrc = tabDoc.selectFirst("iframe").getIframeSrc()
                            if (!tabIframeSrc.isNullOrBlank()) {
                                processExtractorUrl(tabIframeSrc)
                            }
                        } catch (e: Exception) {
                            Log.e("Sarangfilm21Provider", "Error loading tab $tabName: ${e.message}")
                        }
                    }
                }
            }

            // 3. Additional player nav links
            doc.select("ul.muvipro-player-tabs a, ul.player-nav a, .gmr-player-nav a").forEach { a ->
                val href = a.attr("href")
                if (href.isNotBlank() && !href.startsWith("#") && href != "javascript:void(0)" && href != data) {
                    try {
                        val tabUrl = fixUrl(href, data, mainUrl)
                        val tabDoc = app.get(tabUrl, headers = mapOf("Referer" to mainUrl, "User-Agent" to USER_AGENT), timeout = 30).document
                        val tabIframe = tabDoc.selectFirst("iframe").getIframeSrc()
                        if (!tabIframe.isNullOrBlank()) {
                            processExtractorUrl(tabIframe)
                        }
                    } catch (e: Exception) {
                        Log.e("Sarangfilm21Provider", "Error loading tab link: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Sarangfilm21Provider", "Error in loadLinks: ${e.message}")
        }
        return found
    }
}
