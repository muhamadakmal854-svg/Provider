package com.mts.donghub

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element


abstract class BaseFixProvider : MainAPI() {

    fun fixUrl(url: String, referer: String): String {
        if (url.isBlank()) return ""
        if (url.startsWith("http")) return url
        if (url.startsWith("//")) return "https:" + url
        return try {
            val u = java.net.URL(referer)
            if (url.startsWith("/")) {
                "${u.protocol}://${u.host}$url"
            } else {
                val path = u.path.substringBeforeLast("/")
                "${u.protocol}://${u.host}$path/$url"
            }
        } catch (_: Exception) {
            if (url.startsWith("/")) "$mainUrl$url" else "$mainUrl/$url"
        }
    }

    fun Element.extractPosterUrl(): String {
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

    fun Element.toSearchResponse(mainUrl: String): SearchResponse? {
        val a = (if (this.tagName() == "a") this else this.selectFirst("a")) ?: return null
        val href = a.attr("href").let { h -> if (h.startsWith("http")) h else "$mainUrl$h" }
        if (href.isBlank() || href == mainUrl || href.contains("javascript")) return null
        
        val img = this.selectFirst("img") ?: this.selectFirst("[data-src], [data-lazy-src], [data-original]")
        val title = this.selectFirst(
            ".entry-title, h2.entry-title, h2, h3, .title, .film-name, .movie-title, .item-title, .tt, .ttl, .bigor .tt, .name"
        )?.text()?.trim()
            ?: a.attr("title").trim().ifEmpty { img?.attr("alt")?.trim() ?: "" }
                .ifEmpty { img?.attr("title")?.trim() ?: "" }
                .ifEmpty { a.text().trim() }
        
        if (title.isBlank()) return null
        
        var src = img?.extractPosterUrl() ?: ""
        if (src.isEmpty()) src = this.extractPosterUrl()
        if (src.isEmpty()) {
            this.select("[style*=background], [style*=url]").forEach { el ->
                val u = el.extractPosterUrl()
                if (u.isNotEmpty()) { src = u; return@forEach }
            }
        }
        
        val hrefLower = href.lowercase()
        val typeLabel = this.selectFirst(".type, .label, .badge, [class*=type], [class*=label], .quality")?.text()?.lowercase() ?: ""
        
        val isTv = hrefLower.contains("/tvshows/") || hrefLower.contains("/series/") ||
                   hrefLower.contains("/episode/") || hrefLower.contains("/tv/") ||
                   typeLabel.contains("series") || typeLabel.contains("drama") ||
                   typeLabel.contains("episode") || typeLabel.contains("ongoing")
                   
        return if (isTv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = src }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = src }
        }
    }

    suspend fun parseMultiRowHome(
        entries: List<Pair<String, String>>,
        itemSelector: String
    ): HomePageResponse {
        val lists = entries.map { (path, label) ->
            val pageUrl = if (path.startsWith("http")) path else "$mainUrl/$path"
            val items = try {
                val doc = app.get(pageUrl, headers = mapOf("Referer" to mainUrl)).document
                doc.select(itemSelector).mapNotNull { it.toSearchResponse(mainUrl) }.distinctBy { it.url }
            } catch (_: Exception) {
                emptyList<SearchResponse>()
            }
            HomePageList(label, items)
        }.filter { it.list.isNotEmpty() }
        return newHomePageResponse(lists, hasNext = false)
    }
}

class DonghubProvider : BaseFixProvider() {
    override var mainUrl        = "https://donghub.vip"
    override var name           = "Donghub"
    override var lang           = "en"
    override var hasMainPage    = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "explore?order=popular" to "Popular Today",
        "explore?order=latest" to "Latest Release",
        "explore?order=recommend" to "Recommendation"
    )

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
        return ""
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = if (page > 1) {
            "$mainUrl/${request.data}&page=$page"
        } else {
            "$mainUrl/${request.data}"
        }
        val items = scrapeList(pageUrl)
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return scrapeList("$mainUrl/explore?q=${query.replace(" ", "+")}")
    }

    private suspend fun scrapeList(pageUrl: String): List<SearchResponse> {
        val doc = app.get(pageUrl, headers = mapOf("Referer" to mainUrl)).document
        return doc.select(".mv, div.mv, article.item, .item, .movie-item").mapNotNull {
            val a     = (if (it.tagName() == "a") it else it.selectFirst("a")) ?: return@mapNotNull null
            val href  = a.attr("href").let { h -> if (h.startsWith("http")) h else "$mainUrl$h" }
            if (href.isBlank() || href == mainUrl || href.contains("javascript")) return@mapNotNull null
            val img   = it.selectFirst("img") ?: it.selectFirst("[data-src], [data-lazy-src], [data-original]")
            val title = it.selectFirst(".item-title, .title, h3, h2, .film-name, .mv-desc")?.text()?.trim()
                ?: a.attr("title").trim().ifEmpty { img?.attr("alt")?.trim() ?: "" }.ifEmpty { img?.attr("title")?.trim() ?: "" }.ifEmpty { a.text().trim() }
            if (title.isBlank()) return@mapNotNull null
            val src   = img?.posterUrl() ?: ""
            
            val hrefLower = href.lowercase()
            val typeLabel = it.selectFirst(".type, .label, .badge, [class*=type], [class*=label], .quality")?.text()?.lowercase() ?: ""
            val isTv = hrefLower.contains("/series/") || hrefLower.contains("/tv/") || typeLabel.contains("series") || typeLabel.contains("eps")
            if (isTv) {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = src }
            } else {
                newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = src }
            }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc    = app.get(url, headers = mapOf("Referer" to mainUrl)).document
        val title  = doc.selectFirst(".sheader .data h1, h1.entry-title, .heading-name a, .data h1, h1, h2")
            ?.text()?.trim() ?: return null
        val poster = doc.selectFirst(".poster img, .sheader .poster img, .film-poster img, [class*=poster] img, img.mv-poster")
            ?.let { img ->
                listOf("data-src","data-lazy-src","data-lazy","data-cfsrc","src")
                    .map { img.attr(it) }
                    .firstOrNull { it.isNotBlank() && it.startsWith("http") }
            }
        val plot   = doc.selectFirst(".wp-content p, .description p, .info-content p, .film-description, p[style*=text-align:justify]")
            ?.text()?.trim()
        val year   = doc.selectFirst(".date, .extra .year, [itemprop=dateCreated], .film-stats span, [class*=year]")
            ?.text()?.filter { it.isDigit() }?.let {
                if (it.length >= 4) it.substring(0, 4).toIntOrNull() else null
            }
        val genres = doc.select(".sgeneros a, .genres a, .genre a, .film-genres a, a[href*='/genre/']").map { it.text().trim() }
        val isTv   = url.contains("/series/") || url.contains("/tv/") ||
                     doc.select(".episodes-list li, .episodios li, #seasons .se-c, .vid-episodes-content a").isNotEmpty()
        return if (isTv) {
            val eps = doc.select(".episodes-list li a, .episodios li a, #episodes .episodiotitle a, .vid-episodes-content a").mapIndexed { i, a ->
                val href = a.attr("href")
                val cleanUrl = if (href.startsWith("http")) href else "$mainUrl$href"
                newEpisode(cleanUrl) {
                    this.name    = a.text().trim()
                    this.episode = i + 1
                }
            }.reversed() // Ensure chronological order (from Episode 1 upwards)
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
        val targets = mutableListOf<String>()

        fun fixUrl(url: String): String {
            if (url.isBlank()) return ""
            if (url.startsWith("http")) return url
            if (url.startsWith("//")) return "https:$url"
            return try {
                val u = java.net.URL(data)
                if (url.startsWith("/")) {
                    "${u.protocol}://${u.host}$url"
                } else {
                    val path = u.path.substringBeforeLast("/")
                    "${u.protocol}://${u.host}$path/$url"
                }
            } catch (_: Exception) {
                if (url.startsWith("/")) "$mainUrl$url" else "$mainUrl/$url"
            }
        }

        // 1. Process iframe sources
        doc.select("iframe[src], iframe[data-src], iframe[data-lazy-src]").forEach { iframe ->
            val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }.ifEmpty { iframe.attr("data-lazy-src") }.trim()
            val finalUrl = fixUrl(src)
            if (finalUrl.isNotEmpty()) targets.add(finalUrl)
        }

        // 2. Select option dropdowns (e.g. servers)
        doc.select("select option, .mirror option, .server option").forEach { el ->
            listOf("value", "data-src", "data-link", "data-embed", "data-video", "data-url").forEach { attr ->
                val v = el.attr(attr).trim()
                if (v.isNotBlank()) {
                    val finalUrl = fixUrl(v)
                    if (finalUrl.isNotEmpty()) targets.add(finalUrl)
                }
            }
        }

        // 3. Extractor loading
        targets.distinct().forEach { cleanUrl ->
            val cleanUrlEscaped = cleanUrl.replace(" ", "%20")
            val isStreamWish = listOf("streamwish", "wish", "hglink", "hgcloud", "gendeng", "fkupon", "desacinta", "layarotaku", "layarwibu", "nekonime", "layarecchi", "subsource", "doimg", "anchurl", "certaker", "listeamed", "bigwarp", "cloudatacdn", "push-sdk", "gradehg", "hgplus", "streamplay", "awish", "wishembed").any { cleanUrlEscaped.contains(it, true) }
            val isDood = listOf("dood", "dsvplay", "doodcdn", "vide0", "ds2play", "ds2video", "doodstream", "doodla").any { cleanUrlEscaped.contains(it, true) }
            val isVoe = cleanUrlEscaped.contains("voe.sx", true) || cleanUrlEscaped.contains("voe", true)
            val isStreamtape = cleanUrlEscaped.contains("streamtape", true)
            val isFilemoon = cleanUrlEscaped.contains("filemoon", true)
            val isMp4Upload = cleanUrlEscaped.contains("mp4upload", true)

            // Custom D.Tube Extractor
            val isDTube = cleanUrlEscaped.contains("d.tube", true)

            when {
                isDTube -> {
                    try {
                        val videoId = cleanUrlEscaped.substringAfter("/v/").substringBefore("?").substringBefore("/")
                            .ifEmpty { cleanUrlEscaped.substringAfter("?v=").substringBefore("&") }
                        if (videoId.isNotBlank()) {
                            val apiRes = app.get("https://api.d.tube/videos/$videoId", timeout = 15).text
                            val json = org.json.JSONObject(apiRes)
                            val videoUrl = json.optString("video_url", "")
                            if (videoUrl.isNotBlank()) {
                                val isM3u = videoUrl.contains(".m3u8")
                                callback(
                                    newExtractorLink(
                                        source = "D.Tube",
                                        name = "D.Tube HLS",
                                        url = videoUrl,
                                        type = if (isM3u) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                    ) {
                                        this.referer = cleanUrlEscaped
                                    }
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }
                isStreamWish -> {
                    try {
                        com.lagradost.cloudstream3.extractors.StreamWishExtractor().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                    } catch (_: Exception) {}
                }
                isDood -> {
                    try {
                        com.lagradost.cloudstream3.extractors.DoodLaExtractor().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                    } catch (_: Exception) {}
                }
                isVoe -> {
                    try {
                        com.lagradost.cloudstream3.extractors.Voe().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                    } catch (_: Exception) {}
                }
                isStreamtape -> {
                    try {
                        com.lagradost.cloudstream3.extractors.StreamTape().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                    } catch (_: Exception) {}
                }
                isFilemoon -> {
                    try {
                        com.lagradost.cloudstream3.extractors.FileMoon().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                    } catch (_: Exception) {}
                }
                isMp4Upload -> {
                    try {
                        com.lagradost.cloudstream3.extractors.Mp4Upload().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                    } catch (_: Exception) {}
                }
                else -> {
                    try {
                        loadExtractor(cleanUrlEscaped, data, subtitleCallback, callback)
                    } catch (_: Exception) {}
                }
            }
        }

        return true
    }
}
