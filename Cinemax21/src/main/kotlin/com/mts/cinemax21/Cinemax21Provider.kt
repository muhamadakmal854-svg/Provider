package com.mts.cinemax21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class Cinemax21Provider : MainAPI() {

    override var mainUrl        = "https://cinemax21.live"
    override var name           = "Cinemax21 — Streaming Film Gratis No 1 Di Indonesia Secara Legal"
    override var lang           = "id"
    override val hasMainPage    = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "all" to "All",
        "action" to "Action",
        "box-office" to "Box Office",
        "comedy" to "Comedy",
        "crime" to "Crime",
        "horror" to "Horror",
        "romance" to "Romance",
        "thriller" to "Thriller",
        "trending" to "Trending"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path = request.data
        // We always scrape the home page because all genres are loaded there and filtered client-side.
        val pageUrl = if (page > 1) "$mainUrl/page/$page/" else "$mainUrl/"
        return newHomePageResponse(request.name, scrapeList(pageUrl, path))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return scrapeList("$mainUrl/?s=${query.replace(" ", "+")}")
    }
    
    private fun Element.posterUrl(): String {
        for (attr in listOf("data-src", "data-lazy-src", "data-lazy", "data-cfsrc",
                             "data-original", "data-image", "data-bg", "src")) {
            val v = this.attr(attr)
            if (v.isNotBlank() && !v.contains("data:image") &&
                (v.startsWith("http") || v.startsWith("//"))) {
                return if (v.startsWith("//")) "https:$v" else v
            }
        }
        val srcset = this.attr("srcset")
        if (srcset.isNotBlank()) {
            return srcset.trim().split(",").firstOrNull()
                ?.trim()?.split(" ")?.firstOrNull { it.startsWith("http") || it.startsWith("//") }?.let {
                    if (it.startsWith("//")) "https:$it" else it
                } ?: ""
        }
        val style = this.attr("style")
        if (style.contains("background") && style.contains("url(")) {
            val urlStart = style.indexOf("url(") + 4
            val raw = style.substring(urlStart)
            val dq = 34.toChar().toString()
            val sq = 39.toChar().toString()
            val cleaned = raw.replace(dq, "").replace(sq, "")
            val urlEnd = cleaned.indexOf(")")
            if (urlEnd > 0) {
                val candidate = cleaned.substring(0, urlEnd).trim()
                if (candidate.startsWith("http") || candidate.startsWith("//")) {
                    return if (candidate.startsWith("//")) "https:$candidate" else candidate
                }
            }
        }
        return ""
    }

    private suspend fun scrapeList(pageUrl: String, genre: String? = null): List<SearchResponse> {
        val doc = app.get(pageUrl, headers = mapOf("Referer" to mainUrl)).document
        return doc.select(".card, div.card, article.item, .item, .result-item, .film-poster-ahref, div.module-item, div.ml-item, .movie-item, .post-item, .item-post, .box-item, .data-item, .g-item").mapNotNull {
            val cardGenre = it.attr("data-genre").lowercase()
            if (genre != null && genre != "all" && !cardGenre.contains(genre)) {
                return@mapNotNull null
            }
            val a   = (if (it.tagName() == "a") it else it.selectFirst("h3 a, h2 a, .title a, a")) ?: return@mapNotNull null
            val img = it.selectFirst(".poster img, img") ?: it.selectFirst("[data-src], [data-lazy-src], [data-original]")
            val title = it.selectFirst(".title, .title-sm, h3, h2, .entry-title, .film-name")?.text()?.trim()
                ?: a.attr("title").trim().ifEmpty { img?.attr("alt")?.trim() ?: "" }.ifEmpty { img?.attr("title")?.trim() ?: "" }.ifEmpty { a.text().trim() }
            if (title.isBlank()) return@mapNotNull null
            var src = img?.posterUrl() ?: ""
            if (src.isEmpty()) {
                src = it.posterUrl()
            }
            if (src.isEmpty()) {
                var foundBg = ""
                it.select("[style*=background], [style*=url]").forEach { el ->
                    val url = el.posterUrl()
                    if (url.isNotEmpty()) {
                        foundBg = url
                        return@forEach
                    }
                }
                src = foundBg
            }
            val href = a.attr("href").let { h -> if (h.startsWith("http")) h else "$mainUrl$h" }
            
            if (href.contains("/tvshows/") || href.contains("/episode/")) {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = src }
            } else {
                newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = src }
            }
        }
    }
    
    override suspend fun load(url: String): LoadResponse? {
        val doc    = app.get(url, headers = mapOf("Referer" to mainUrl)).document
        val title  = doc.selectFirst(".sheader .data h1, h1.entry-title, .data h1, .video-details h1, h1")?.text()?.trim() ?: return null
        val poster = doc.selectFirst(".poster img, .sheader .poster img, .video-poster img, img.wp-post-image")?.let { img ->
            listOf("data-src","data-lazy-src","data-lazy","data-cfsrc","src")
                .map { img.attr(it) }.firstOrNull { it.isNotBlank() && it.startsWith("http") }
        }
        val plot   = doc.selectFirst(".wp-content p, .description p, .video-description p, .synopsis p")?.text()?.trim()
        val year   = Regex("""\b(19\d\d|20\d\d)\b""").find(title)?.value?.toIntOrNull()
            ?: doc.selectFirst(".date, [itemprop=dateCreated], .video-info, .meta-info")?.text()
                ?.filter { it.isDigit() }?.let {
                    if (it.length >= 4) it.substring(0, 4).toIntOrNull() else null
                }
        val genres = doc.select(".sgeneros a, .video-genres a, .genres a").map { it.text() }
        val isTv   = url.contains("/tvshows/")
        return if (isTv) {
            val eps = doc.select("#episodes .episodiotitle a").mapIndexed { i, a ->
                newEpisode(a.attr("href")) {
                    this.name = a.text().trim(); this.episode = i + 1
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, eps) {
                this.posterUrl = poster; this.plot = plot; this.year = year; this.tags = genres
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster; this.plot = plot; this.year = year; this.tags = genres
            }
        }
    }
    
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = mapOf("Referer" to mainUrl)).document
        val targets = mutableListOf<Pair<String, String>>()

        // 1. Scrape server buttons
        doc.select(".server-switcher button.server-btn[data-src], #serverButtons button[data-src], button.server-btn[data-src]").forEach { btn ->
            val src = btn.attr("data-src").trim()
            val name = btn.text().trim().ifEmpty { "Server" }
            if (src.isNotEmpty() && (src.startsWith("http") || src.startsWith("//"))) {
                val finalUrl = if (src.startsWith("//")) "https:$src" else src
                targets.add(finalUrl to name)
            }
        }

        // 2. Fall back to other embedded iframes or video sources if no buttons found
        if (targets.isEmpty()) {
            doc.select("iframe[src], iframe[data-src], iframe[data-lazy-src], iframe.metaframe").forEach { iframe ->
                val src = iframe.attr("src")
                    .ifEmpty { iframe.attr("data-src") }
                    .ifEmpty { iframe.attr("data-lazy-src") }
                    .trim()
                if (src.isNotEmpty() && (src.startsWith("http") || src.startsWith("//"))) {
                    val finalUrl = if (src.startsWith("//")) "https:$src" else src
                    targets.add(finalUrl to "Iframe")
                }
            }
            doc.select("source[src], video source[src], video[src]").forEach { el ->
                val src = el.attr("src").trim()
                if (src.isNotEmpty() && (src.startsWith("http") || src.startsWith("//"))) {
                    val finalUrl = if (src.startsWith("//")) "https:$src" else src
                    targets.add(finalUrl to "Direct Source")
                }
            }
        }

        // 3. Process all targets
        for ((rawUrl, serverName) in targets.distinctBy { it.first }) {
            val cleanUrl = rawUrl.trim().replace(92.toChar().toString(), "")
            if (cleanUrl.contains(".m3u8") || cleanUrl.contains(".mp4") || cleanUrl.contains("/hls/")) {
                val isM3u = cleanUrl.contains(".m3u8") || cleanUrl.contains("/hls/")
                callback(
                    newExtractorLink(
                        source = "Cinemax21",
                        name = "Cinemax21 - $serverName",
                        url = cleanUrl,
                        type = if (isM3u) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://cinemax21.live/"
                        this.headers = mapOf(
                            "Referer" to "https://cinemax21.live/",
                            "Origin" to "https://cinemax21.live"
                        )
                        this.quality = Qualities.Unknown.value
                    }
                )
            } else {
                val resolvedLinks = mutableListOf<ExtractorLink>()
                loadExtractor(cleanUrl, "https://cinemax21.live/", subtitleCallback) { link ->
                    resolvedLinks.add(link)
                }
                for (link in resolvedLinks) {
                    callback(
                        newExtractorLink(
                            source = link.source,
                            name = "${link.name} ($serverName)",
                            url = link.url,
                            type = link.type
                        ) {
                            this.referer = link.referer
                            this.headers = link.headers
                            this.quality = link.quality
                        }
                    )
                }
            }
        }

        return true
    }
}

