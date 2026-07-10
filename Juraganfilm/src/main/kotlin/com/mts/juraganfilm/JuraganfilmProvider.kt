package com.mts.juraganfilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document


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

class JuraganfilmProvider : BaseFixProvider() {
    override var mainUrl        = "https://tv47.juragan.film"
    override var name           = "Juraganfilm"
    override var lang           = "id"
    override var hasMainPage    = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "Film Box Office" to "Film Box Office",
        "FIlm Seri Ongoing" to "FIlm Seri Ongoing",
        "Film Seri China" to "Film Seri China",
        "Film Seri Drakor" to "Film Seri Drakor",
        "Latest Movie" to "Latest Movie"
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
        val doc = app.get(mainUrl, headers = mapOf("Referer" to mainUrl)).document
        val items = parseSection(doc, request.data)
        return newHomePageResponse(request.name, items)
    }

    private fun parseSection(doc: Document, title: String): List<SearchResponse> {
        val h3 = doc.select("h3").find { it.text().trim().equals(title, ignoreCase = true) } ?: return emptyList()
        val container = h3.parent?.findNextSibling() ?: h3.findNextSibling() ?: return emptyList()
        val items = container.select("article, .gmr-item-modulepost, div[itemtype*='schema.org/']")
        return items.mapNotNull { card ->
            val a = card.selectFirst("a[itemprop='url']") ?: card.selectFirst("a") ?: return@mapNotNull null
            val href = a.attr("href").let { h -> if (h.startsWith("http")) h else "$mainUrl$h" }
            val titleText = card.selectFirst("a[itemprop='url'] h2, h2, .entry-title, a")?.text()
                ?.replace("Sub Ind", "")?.replace("EPS", "")?.trim() ?: ""
            if (titleText.isBlank() || href.isBlank() || href.contains("youtube.com")) return@mapNotNull null
            
            // Select real poster image instead of flag icons
            val img = card.selectFirst("img.mv-poster, [class*=poster] img, img[class*=poster]")
                ?: card.select("img").firstOrNull { im -> !im.attr("src").contains("flagsapi.com") }
                ?: card.selectFirst("img")
            var src = img?.posterUrl() ?: ""
            
            val isTv = href.contains("/film-seri/") || titleText.contains("EPS", ignoreCase = true)
            if (isTv) {
                newTvSeriesSearchResponse(titleText, href, TvType.TvSeries) { this.posterUrl = src }
            } else {
                newMovieSearchResponse(titleText, href, TvType.Movie) { this.posterUrl = src }
            }
        }.distinctBy { it.url }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return scrapeList("$mainUrl/?s=${query.replace(" ", "+")}")
    }

    private suspend fun scrapeList(pageUrl: String): List<SearchResponse> {
        val doc = app.get(pageUrl, headers = mapOf("Referer" to mainUrl)).document
        return doc.select("article, .gmr-item-modulepost").mapNotNull { card ->
            val a = card.selectFirst("a[itemprop='url']") ?: card.selectFirst("a") ?: return@mapNotNull null
            val href = a.attr("href").let { h -> if (h.startsWith("http")) h else "$mainUrl$h" }
            val titleText = card.selectFirst("h2, .entry-title")?.text()?.replace("Sub Ind", "")?.replace("EPS", "")?.trim() ?: ""
            if (titleText.isBlank() || href.isBlank() || href.contains("youtube.com")) return@mapNotNull null
            
            val img = card.selectFirst("img.mv-poster, [class*=poster] img, img[class*=poster]")
                ?: card.select("img").firstOrNull { im -> !im.attr("src").contains("flagsapi.com") }
                ?: card.selectFirst("img")
            var src = img?.posterUrl() ?: ""
            
            val isTv = href.contains("/film-seri/") || titleText.contains("EPS", ignoreCase = true)
            if (isTv) {
                newTvSeriesSearchResponse(titleText, href, TvType.TvSeries) { this.posterUrl = src }
            } else {
                newMovieSearchResponse(titleText, href, TvType.Movie) { this.posterUrl = src }
            }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("Referer" to mainUrl)).document
        val title = doc.selectFirst("meta[property='og:title']")?.attr("content")?.substringBefore("Sub Indo")?.substringBefore("(")?.trim()
            ?: doc.selectFirst("h1.entry-title")?.text()?.trim() ?: return null
            
        val poster = doc.selectFirst("meta[property='og:image']")?.attr("content")?.ifBlank { null }
            ?: doc.selectFirst("img.wp-post-image")?.attr("src")
            
        val plot = doc.selectFirst("div[itemprop='description'] p, div.entry-content p")?.text()?.trim()
        
        val year = doc.selectFirst("meta[property='og:title']")?.attr("content")?.let {
            val match = Regex("\\((20\\d{2}|19\\d{2})\\)").find(it)
            match?.groupValues?.get(1)?.toIntOrNull()
        }
        
        val genres = doc.select("a[rel='category tag'], a[href*='/genre/']").map { it.text().trim() }.distinct()
        
        val isTv = url.contains("/film-seri/") || doc.select(".jf-eps-wrap").isNotEmpty()
        
        return if (isTv) {
            val eps = mutableListOf<Episode>()
            val epsWrap = doc.selectFirst(".jf-eps-wrap")
            if (epsWrap != null) {
                val items = epsWrap.select("a, span")
                var epNum = 1
                items.forEach { el ->
                    val txt = el.text().trim()
                    if (txt.toIntOrNull() != null) {
                        val epHref = el.attr("href")
                        val resolvedHref = if (epHref.isBlank()) url else {
                            if (epHref.startsWith("http")) epHref else "$mainUrl$epHref"
                        }
                        eps.add(newEpisode(resolvedHref) {
                            this.name = "Episode $txt"
                            this.episode = txt.toInt()
                        })
                        epNum++
                    }
                }
            } else {
                eps.add(newEpisode(url) {
                    this.name = "Episode 1"
                    this.episode = 1
                })
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, eps.distinctBy { it.data }) {
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

        // 1. Resolve custom Juraganfilm File player iframes
        doc.select("iframe[src*='/file/']").forEach { iframe ->
            val src = iframe.attr("src").trim()
            val finalUrl = fixUrl(src)
            if (finalUrl.isNotBlank()) {
                try {
                    val resText = app.get(finalUrl, headers = mapOf("Referer" to data)).text
                    val match = Regex("(?:SOURCES|sources|playlist)\\s*=\\s*(\\[[\\s\\S]*?\\])").find(resText)
                    val jsonStr = match?.groupValues?.get(1)
                    if (jsonStr != null) {
                        val arr = org.json.JSONArray(jsonStr)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val link = obj.optString("link", "").ifEmpty { obj.optString("file", "") }
                            if (link.isNotBlank()) {
                                val label = obj.optString("label", "Server")
                                val isM3u = link.contains(".m3u8") || obj.optString("type", "").contains("hls")
                                callback(
                                    newExtractorLink(
                                        source = "Juraganfilm",
                                        name = "Juraganfilm - $label",
                                        url = link,
                                        type = if (isM3u) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                    ) {
                                        this.referer = finalUrl
                                    }
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Resolve other standard fallback iframes
        doc.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("src").trim()
            val finalUrl = fixUrl(src)
            if (finalUrl.isNotEmpty() && !finalUrl.contains("/file/")) {
                targets.add(finalUrl)
            }
        }

        // 3. Select option dropdowns (e.g. servers)
        doc.select("select option, .mirror option, .server option").forEach { el ->
            listOf("value", "data-src", "data-link", "data-embed", "data-video", "data-url").forEach { attr ->
                val v = el.attr(attr).trim()
                if (v.isNotBlank()) {
                    val finalUrl = fixUrl(v)
                    if (finalUrl.isNotEmpty() && !finalUrl.contains("/file/")) targets.add(finalUrl)
                }
            }
        }

        // 4. Load standard extractors
        targets.distinct().forEach { cleanUrl ->
            try {
                loadExtractor(cleanUrl, data, subtitleCallback, callback)
            } catch (_: Exception) {}
        }

        return true
    }
}
