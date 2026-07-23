package com.mts.kawanfilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import android.util.Log

class KawanFilmProvider : MainAPI() {
    override var mainUrl = "https://web.kawanfilm21.co"
    override var name = "KawanFilm"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "tv/" to "Serial TV",
        "category/box-office/" to "Box Office",
        "country/usa/" to "Hollywood",
        "country/india/" to "Bollywood",
        "country/korea/" to "Drama Korea",
        "country/china/" to "Mandarin"
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

        val isSeries = url.contains("/tv/") || url.contains("/series/") ||
            (document.selectFirst(".muvipro-player-tabs, ul.player-nav") == null &&
             document.select("a.gmr-numpost").isNotEmpty())

        if (isSeries) {
            val episodes = mutableListOf<Episode>()

            // Method 1: gmr-numpost links on page
            val epsElements = document.select("a.gmr-numpost")
            if (epsElements.isNotEmpty()) {
                epsElements.forEachIndexed { index, element ->
                    val epUrl = element.attr("href")
                    val epNum = element.text().trim().toIntOrNull() ?: (index + 1)
                    episodes.add(newEpisode(epUrl) {
                        this.episode = epNum
                        this.name = "Episode $epNum"
                    })
                }
            }

            // Method 2: gmr-listepisode / list-episode links on page
            if (episodes.isEmpty()) {
                val listEps = document.select(".gmr-listepisode a, .list-episode a")
                listEps.forEachIndexed { index, element ->
                    val epUrl = element.attr("href")
                    val epNum = element.text().trim().replace(Regex("[^0-9]"), "").toIntOrNull() ?: (index + 1)
                    episodes.add(newEpisode(epUrl) {
                        this.episode = epNum
                        this.name = element.text().trim()
                    })
                }
            }

            // Method 3: WP REST API -- /wp-json/wp/v2/episode/?parent={postId}
            if (episodes.isEmpty()) {
                try {
                    val bodyClasses = document.selectFirst("body")?.classNames() ?: emptySet()
                    val postId = bodyClasses.firstOrNull { it.startsWith("postid-") }?.removePrefix("postid-")
                    if (!postId.isNullOrBlank()) {
                        var page = 1
                        var keepFetching = true
                        while (keepFetching) {
                            val restUrl = "$mainUrl/wp-json/wp/v2/episode/?parent=$postId&per_page=100&page=$page&orderby=date&order=asc"
                            val restResp = app.get(restUrl, timeout = 20)
                            if (restResp.code != 200) break
                            val jsonArr = org.json.JSONArray(restResp.text)
                            if (jsonArr.length() == 0) break
                            for (i in 0 until jsonArr.length()) {
                                val ep = jsonArr.getJSONObject(i)
                                val epLink = ep.optString("link", "")
                                val epSlug = ep.optString("slug", "")
                                val epRendTitle = ep.optJSONObject("title")?.optString("rendered", epSlug) ?: epSlug
                                val epNumMatch = Regex("episode[- _]*(\d+)", RegexOption.IGNORE_CASE).find(epSlug)
                                val epNum = epNumMatch?.groupValues?.get(1)?.toIntOrNull() ?: (i + 1)
                                if (epLink.isNotBlank()) {
                                    episodes.add(newEpisode(epLink) {
                                        this.episode = epNum
                                        this.name = epRendTitle.trim()
                                    })
                                }
                            }
                            keepFetching = jsonArr.length() == 100
                            page++
                        }
                    }
                } catch (e: Exception) {
                    Log.e("KawanFilmProvider", "REST episode fetch error: ${e.message}")
                }
            }

            // Method 4: Fallback -- scan /eps/ listing
            if (episodes.isEmpty()) {
                try {
                    val slug = url.trimEnd('/').substringAfterLast("/")
                    val epsDoc = app.get("$mainUrl/eps/?parent=$slug", timeout = 15).document
                    epsDoc.select("article a[href*='/eps/'], .entry-title a[href*='/eps/']").forEachIndexed { index, el ->
                        val epUrl = el.attr("href")
                        if (epUrl.isNotBlank()) {
                            episodes.add(newEpisode(epUrl) {
                                this.episode = index + 1
                                this.name = el.text().ifBlank { "Episode ${index + 1}" }
                            })
                        }
                    }
                } catch (e: Exception) { }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.sortedBy { it.episode ?: 0 }) {
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
                return listOf(
                    attr("data-litespeed-src"),
                    attr("data-lazy-src"),
                    attr("data-src"),
                    attr("data-video"),
                    attr("data-embed"),
                    attr("data-url"),
                    attr("data-iframe"),
                    attr("src")
                ).firstOrNull { it.isNotBlank() && !it.equals("about:blank", true) && !it.startsWith("javascript", true) }
            }

            // 1. All direct iframes on the page
            doc.select("iframe").forEach { iframe ->
                val src = iframe.getIframeSrc()
                if (!src.isNullOrBlank()) {
                    val fixedSrc = fixUrl(src)
                    if (!fixedSrc.contains("youtube.com") && !fixedSrc.contains("youtu.be")) {
                        if (loadExtractor(fixedSrc, data, subtitleCallback, callback)) found = true
                    }
                }
            }

            // 2. Server tab links (MuviPro/GMR -- separate page per server)
            val serverTabLinks = mutableListOf<String>()
            doc.select(".gmr-server-wrap a, ul.muvipro-player-tabs a, ul.gmr-player-tabs a, .gmr-player-nav a, ul#gmr-tab a").forEach { a ->
                val href = a.attr("href")
                if (href.isNotBlank() && !href.startsWith("#")
                    && href != "javascript:void(0)"
                    && !href.contains("youtube.com")
                    && !href.contains("youtu.be")
                    && href != data) {
                    val resolved = fixUrl(href)
                    if (resolved.isNotBlank() && resolved != data) serverTabLinks.add(resolved)
                }
            }
            serverTabLinks.distinct().take(6).forEach { tabUrl ->
                try {
                    val tabDoc = app.get(tabUrl, timeout = 20, headers = mapOf("Referer" to data)).document
                    tabDoc.select("iframe").forEach { iframe ->
                        val src = iframe.getIframeSrc()
                        if (!src.isNullOrBlank()) {
                            val fixedSrc = fixUrl(src)
                            if (!fixedSrc.contains("youtube.com") && !fixedSrc.contains("youtu.be")) {
                                if (loadExtractor(fixedSrc, tabUrl, subtitleCallback, callback)) found = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("KawanFilmProvider", "Server tab error [$tabUrl]: ${e.message}")
                }
            }

            // 3. Muvipro AJAX tab content (action=muvipro_player_content)
            val postId = doc.selectFirst("div.gmr-server-wrap[data-id], div[data-id]")?.attr("data-id")
                ?: doc.selectFirst("body")?.classNames()
                    ?.firstOrNull { it.startsWith("postid-") }?.removePrefix("postid-")
            if (!postId.isNullOrBlank()) {
                doc.select("ul.muvipro-player-tabs a[href^='#p'], ul.nav-tabs a[href^='#p']").forEach { a ->
                    val tabName = a.attr("href").removePrefix("#").trim()
                    if (tabName.isNotBlank()) {
                        try {
                            val resHtml = app.post(
                                "$mainUrl/wp-admin/admin-ajax.php",
                                data = mapOf("action" to "muvipro_player_content", "tab" to tabName, "post_id" to postId),
                                headers = mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to data),
                                timeout = 15
                            ).text
                            val tabSrc = Jsoup.parse(resHtml).selectFirst("iframe").getIframeSrc()
                            if (!tabSrc.isNullOrBlank()) {
                                val fixedSrc = fixUrl(tabSrc)
                                if (!fixedSrc.contains("youtube.com")) {
                                    if (loadExtractor(fixedSrc, data, subtitleCallback, callback)) found = true
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("KawanFilmProvider", "AJAX tab error: ${e.message}")
                        }
                    }
                }
            }

            // 4. Dooplay/Muvipro [data-post][data-nume] AJAX buttons (fallback)
            if (!found) {
                doc.select("[data-post][data-nume]").take(8).forEach { btn ->
                    val post = btn.attr("data-post").ifEmpty { btn.attr("data-id") }
                    val nume = btn.attr("data-nume").ifEmpty { "1" }
                    val type = btn.attr("data-type").ifEmpty { "movie" }
                    if (post.isNotBlank()) {
                        listOf("doo_player_ajax", "dt_player_ajax", "zt_main_ajax").forEach { act ->
                            try {
                                val res = app.post(
                                    "$mainUrl/wp-admin/admin-ajax.php",
                                    data = mapOf("action" to act, "post" to post, "nume" to nume, "type" to type),
                                    headers = mapOf("Referer" to data, "X-Requested-With" to "XMLHttpRequest")
                                ).text
                                if (res.isNotBlank() && res != "0" && res != "false") {
                                    val embedUrl = if (res.trim().startsWith("{")) {
                                        val j = org.json.JSONObject(res)
                                        j.optString("embed_url", j.optString("url", j.optString("src", "")))
                                    } else {
                                        Jsoup.parse(res).selectFirst("iframe")?.attr("src") ?: ""
                                    }
                                    if (embedUrl.startsWith("http") || embedUrl.startsWith("//")) {
                                        val fixedEmbed = fixUrl(embedUrl)
                                        if (!fixedEmbed.contains("youtube.com")) {
                                            if (loadExtractor(fixedEmbed, data, subtitleCallback, callback)) found = true
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("KawanFilmProvider", "loadLinks error: ${e.message}")
        }
        return found
    }
}
