package com.mts.animixplay

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

class AnimixplayProvider : BaseFixProvider() {
    override var mainUrl        = "https://animixplay.com.ro"
    override var name           = "Animixplay"
    override var lang           = "en"
    override var hasMainPage    = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "anime/?order=popular" to "Popular Today",
        "" to "Latest Release",
        "anime/?status=ongoing" to "Ongoing",
        "anime/?status=completed" to "Completed",
        "anime/?status=upcoming" to "Upcoming",
        "anime/?status=hiatus" to "Hiatus"
    )

    fun fixAnimixplayUrl(url: String, referer: String = mainUrl): String {
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

    private fun Element.toAnimixplaySearchResponse(mainUrl: String): SearchResponse? {
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
        
        var src = img?.posterUrl() ?: ""
        if (src.isEmpty()) src = this.posterUrl()
        if (src.isEmpty()) {
            this.select("[style*=background], [style*=url]").forEach { el ->
                val u = el.posterUrl()
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

    private suspend fun scrapeList(pageUrl: String): List<SearchResponse> {
        val doc = app.get(pageUrl, headers = mapOf(
            "Referer" to mainUrl,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        )).document
        return doc.select(".listupd .bsx, .listupd .bs, .bsx, .bs, article.bs, article, .card, div.card, article.item, .item, .movie-item, .post-item, div.module-item, div.ml-item, .box-item, .post, .entry, .film-poster-ahref").mapNotNull {
            it.toAnimixplaySearchResponse(mainUrl)
        }.distinctBy { it.url }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path = request.data
        val cleanPath = path.removePrefix("/").removeSuffix("/")
        val pageUrl = if (path.startsWith("http")) {
            path + if (page > 1) "page/$page/" else ""
        } else {
            if (cleanPath.isEmpty()) {
                mainUrl + if (page > 1) "/page/$page/" else "/"
            } else {
                val parts = cleanPath.split("?")
                val basePath = parts[0].removeSuffix("/")
                val query = if (parts.size > 1) "?" + parts[1] else ""
                val pagedPath = if (page > 1) "$basePath/page/$page/" else "$basePath/"
                "$mainUrl/$pagedPath$query"
            }
        }
        val items = scrapeList(pageUrl)
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return scrapeList("$mainUrl/?s=${query.replace(" ", "+")}")
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("Referer" to mainUrl)).document
        var currentDoc = doc
        var targetUrl = url

        // Parent redirection logic for episode pages
        val isEpisodePage = !url.contains("/anime/") && !url.contains("/series/") && !url.contains("/tvshows/") && !url.contains("/movies/")
        if (isEpisodePage) {
            val parentLink = doc.select("a[href]").map { it.attr("href") }.firstOrNull { href ->
                val h = href.lowercase()
                (h.contains("/anime/") && !h.endsWith("/anime/") && !h.endsWith("/anime")) ||
                (h.contains("/series/") && !h.endsWith("/series/") && !h.endsWith("/series")) ||
                (h.contains("/tvshows/") && !h.endsWith("/tvshows/") && !h.endsWith("/tvshows"))
            }
            if (!parentLink.isNullOrBlank()) {
                val resolved = if (parentLink.startsWith("http")) parentLink else {
                    val base = mainUrl.removeSuffix("/")
                    if (parentLink.startsWith("/")) "$base$parentLink" else "$base/$parentLink"
                }
                try {
                    val parentDoc = app.get(resolved, headers = mapOf("Referer" to url)).document
                    val newTitle = parentDoc.selectFirst(".sheader .data h1, h1.entry-title, .data h1, h1, .heading-name, .film-name")
                    if (newTitle != null) {
                        currentDoc = parentDoc
                        targetUrl = resolved
                    }
                } catch (_: Exception) {}
            }
        }

        val title = currentDoc.selectFirst(
            ".sheader .data h1, h1.entry-title, .data h1, h1, .heading-name, .film-name"
        )?.text()?.trim() ?: return null
        val poster = currentDoc.selectFirst(
            ".poster img, .sheader .poster img, .film-poster img, [class*=poster] img, " +
            ".entry-thumbnail img, .thumb img, img.wp-post-image, .cover img"
        )?.let { img ->
            listOf("data-src", "data-lazy-src", "data-lazy", "data-cfsrc", "src")
                .map { img.attr(it) }
                .firstOrNull { it.isNotBlank() && it.startsWith("http") }
        }
        val plot = currentDoc.selectFirst(
            ".description p, .wp-content p, .entry-content p, [itemprop=description], " +
            ".film-description, .synops p, .overview"
        )?.text()?.trim()
        val year = currentDoc.selectFirst(
            ".date, .extra .year, [itemprop=dateCreated], .film-stats span, [class*=year]"
        )?.text()?.filter { it.isDigit() }?.let {
            if (it.length >= 4) it.substring(0, 4).toIntOrNull() else null
        }
        val genres = currentDoc.select(
            ".sgeneros a, .genres a, .genre a, .film-genres a, [class*=genre] a, .categories a"
        ).map { it.text() }.filter { it.isNotBlank() }
        val isTv = targetUrl.contains("/tvshows/") || targetUrl.contains("/series/") ||
                   targetUrl.contains("/tv/") || targetUrl.contains("/season/") ||
                   targetUrl.contains("/anime/") ||
                   currentDoc.select(
                       ".episodes-list li, .episodios li, #seasons .se-c, " +
                       ".eplister li, .episodelist li, .clps li, #episodes li, " +
                       "#daftarepisode li, #daftarepisode, .epcheck li"
                   ).isNotEmpty()
        return if (isTv) {
            val eps = currentDoc.select(
                ".episodes-list li a, .episodios li a, #episodes .episodiotitle a, " +
                ".eplister ul li a, .episodelist ul li a, .ep-list li a, .clps li a, " +
                "[class*=episode-list] li a, [class*=episode] a[href], " +
                "#daftarepisode li a, #daftarepisode a, .epcheck li a, [id*=episode] li a, [id*=episode] a"
            ).reversed().mapIndexed { i, a ->
                newEpisode(fixAnimixplayUrl(a.attr("href"))) {
                    this.name = a.selectFirst(".epl-title, .epl-num, span, .episode-title")
                        ?.text()?.trim() ?: a.text().trim()
                    this.episode = i + 1
                }
            }.filter { it.data.isNotBlank() }.distinctBy { it.data }
            newTvSeriesLoadResponse(title, targetUrl, TvType.TvSeries, eps) {
                this.posterUrl = poster; this.plot = plot; this.year = year; this.tags = genres
            }
        } else {
            newMovieLoadResponse(title, targetUrl, TvType.Movie, targetUrl) {
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

        // 0. Parse buttons containing loadMi Base64 iframe html
        doc.select("button[onclick*='loadMi']").forEach { button ->
            val onclick = button.attr("onclick")
            val base64 = onclick.substringAfter("value:").substringAfter("'").substringBefore("'").trim()
            if (base64.isNotEmpty()) {
                try {
                    val decodedBytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                    val html = String(decodedBytes, Charsets.UTF_8)
                    val parsedIfr = Jsoup.parse(html).selectFirst("iframe[src], iframe[data-src], iframe[data-lazy-src]")
                    val src = parsedIfr?.attr("src")?.ifEmpty { parsedIfr.attr("data-src") }?.ifEmpty { parsedIfr.attr("data-lazy-src") } ?: ""
                    if (src.isNotBlank()) {
                        val finalUrl = fixAnimixplayUrl(src, data)
                        if (finalUrl.isNotEmpty()) targets.add(finalUrl)
                    }
                } catch (_: Exception) {}
            }
        }

        // 1. Direct video/source elements
        doc.select("source[src], video source[src], video[src]").forEach { el ->
            val src = el.attr("src").trim()
            val finalUrl = fixAnimixplayUrl(src, data)
            if (finalUrl.isNotEmpty()) targets.add(finalUrl)
        }

        // 2. Direct iframes
        doc.select("iframe[src], iframe[data-src], iframe[data-litespeed-src], iframe[data-lazy-src], iframe.metaframe").forEach { iframe ->
            val src = iframe.attr("src")
                .ifEmpty { iframe.attr("data-src") }
                .ifEmpty { iframe.attr("data-litespeed-src") }
                .ifEmpty { iframe.attr("data-lazy-src") }
                .trim()
            val finalUrl = fixAnimixplayUrl(src, data)
            if (finalUrl.isNotEmpty()) targets.add(finalUrl)
        }

        // 3. Script regex URL extraction
        doc.select("script").forEach { script ->
            val code = script.html()
            if (code.isNotBlank()) {
                val regex = Regex("(https?:)?//[^\\s\"'<>]+")
                regex.findAll(code).forEach { match ->
                    val rawUrl = match.value
                    val finalUrl = fixAnimixplayUrl(rawUrl, data)
                    if (finalUrl.isNotBlank() && (
                        finalUrl.contains(".mp4") || finalUrl.contains(".m3u8") ||
                        finalUrl.contains(".mkv") || finalUrl.contains("/embed/") ||
                        finalUrl.contains("/player/") || finalUrl.contains("/e/") ||
                        finalUrl.contains("/v/")
                    )) {
                        targets.add(finalUrl)
                    }
                }
            }
        }

        // 4. Process all collected targets
        targets.distinct().forEach { raw ->
            val cleanedRaw = raw.trim()
            if (cleanedRaw.isBlank()) return@forEach

            val cleanUrlEscaped = cleanedRaw.replace(92.toChar().toString(), "")
            if (cleanUrlEscaped.contains(".m3u8") || cleanUrlEscaped.contains(".mp4") || cleanUrlEscaped.contains("/hls/")) {
                val isM3u = cleanUrlEscaped.contains(".m3u8") || cleanUrlEscaped.contains("/hls/")
                callback(
                    newExtractorLink(
                        source = "Direct Stream",
                        name = "Direct Stream",
                        url = cleanUrlEscaped,
                        type = if (isM3u) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = data
                        this.quality = Qualities.Unknown.value
                    }
                )
            } else {
                val isTamilEmbed = cleanUrlEscaped.contains("tamilembed", true)
                val isStreamWish = listOf("streamwish", "wish", "hglink", "hgcloud", "gendeng", "fkupon", "desacinta", "layarotaku", "layarwibu", "nekonime", "layarecchi", "subsource", "doimg", "anchurl", "certaker", "listeamed", "bigwarp", "cloudatacdn", "push-sdk", "gradehg", "hgplus", "streamplay", "awish", "wishembed").any { cleanUrlEscaped.contains(it, true) }
                val isDood = listOf("dood", "dsvplay", "doodcdn", "vide0", "ds2play", "ds2video", "doodstream", "doodla").any { cleanUrlEscaped.contains(it, true) }
                val isVoe = cleanUrlEscaped.contains("voe.sx", true) || cleanUrlEscaped.contains("voe", true)
                val isStreamtape = cleanUrlEscaped.contains("streamtape", true)
                val isFilemoon = cleanUrlEscaped.contains("filemoon", true)
                val isMp4Upload = cleanUrlEscaped.contains("mp4upload", true)

                when {
                    isTamilEmbed -> {
                        try {
                            TamilEmbed().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
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
        }

        return true
    }
}

class TamilEmbed : ExtractorApi() {
    override var name = "TamilEmbed"
    override var mainUrl = "https://tamilembed.lol"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
        callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
    ) {
        try {
            val doc = app.get(
                url = url,
                headers = mapOf(
                    "Referer" to (referer ?: ""),
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                ),
                timeout = 15
            ).document
            val bloggerIfr = doc.selectFirst("iframe[src*=blogger.com]")
            val bloggerUrl = bloggerIfr?.attr("src")?.trim()
            if (!bloggerUrl.isNullOrBlank()) {
                val cleanUrl = if (bloggerUrl.startsWith("//")) "https:$bloggerUrl" else bloggerUrl
                com.lagradost.cloudstream3.utils.loadExtractor(cleanUrl, url, subtitleCallback, callback)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
