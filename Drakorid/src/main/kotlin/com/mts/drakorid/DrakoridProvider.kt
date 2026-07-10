@file:Suppress("DEPRECATION")
package __PACKAGE__

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

    @Suppress("DEPRECATION")
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
        return HomePageResponse(lists)
    }
}

class DrakoridProvider : BaseFixProvider() {
    override var mainUrl        = "https://drakorid.cam"
    override var name           = "Drakorid"
    override var lang           = "id"
    override var hasMainPage    = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "?section=hot-series" to "Hot Series Update",
        "?section=latest-release" to "Latest Release",
        "genres/action" to "Action",
        "genres/adventure" to "Adventure",
        "genres/business" to "Business",
        "genres/comedy" to "Comedy",
        "genres/crime" to "Crime",
        "genres/documentary" to "Documentary",
        "genres/drama" to "Drama",
        "genres/family" to "Family",
        "genres/fantasy" to "Fantasy",
        "genres/food" to "Food",
        "genres/historical" to "Historical",
        "genres/horror" to "Horror",
        "genres/law" to "Law",
        "genres/life" to "Life",
        "genres/medical" to "Medical",
        "genres/melodrama" to "Melodrama",
        "genres/military" to "Military",
        "genres/music" to "Music",
        "genres/mystery" to "Mystery",
        "genres/political" to "Political",
        "genres/psychological" to "Psychological",
        "genres/romance" to "Romance",
        "genres/sci-fi" to "Sci-Fi",
        "genres/shounen" to "Shounen",
        "genres/sitcom" to "Sitcom",
        "genres/sports" to "Sports",
        "genres/supernatural" to "Supernatural",
        "genres/thriller" to "Thriller",
        "genres/war" to "War",
        "genres/wuxia" to "Wuxia",
        "genres/youth" to "Youth"
    )

    fun fixUrl(url: String, referer: String = mainUrl): String {
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

    private fun Element.toSearchResponse(mainUrl: String): SearchResponse? {
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
            it.toSearchResponse(mainUrl)
        }.distinctBy { it.url }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path = request.data
        if (path.contains("section=hot-series")) {
            val doc = app.get(mainUrl, headers = mapOf("Referer" to mainUrl)).document
            val hotBox = doc.select("div.bixbox").firstOrNull { box ->
                val hdr = box.selectFirst("h1, h2, h3, h4, .title")
                hdr != null && hdr.text().contains("Hot Series", ignoreCase = true)
            }
            val items = if (hotBox != null) {
                hotBox.select("article, .bsx, .bs").mapNotNull { it.toSearchResponse(mainUrl) }.distinctBy { it.url }
            } else {
                emptyList()
            }
            return HomePageResponse(listOf(HomePageList(request.name, items)))
        } else if (path.contains("section=latest-release")) {
            val pageUrl = if (page > 1) {
                "$mainUrl/series/page/$page/?status=&type=&order=update"
            } else {
                "$mainUrl/series/?status=&type=&order=update"
            }
            val items = scrapeList(pageUrl)
            return HomePageResponse(listOf(HomePageList(request.name, items)))
        } else {
            val cleanPath = path.removePrefix("/").removeSuffix("/")
            val pageUrl = if (page > 1) {
                "$mainUrl/$cleanPath/page/$page/"
            } else {
                "$mainUrl/$cleanPath/"
            }
            val items = scrapeList(pageUrl)
            return HomePageResponse(listOf(HomePageList(request.name, items)))
        }
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
            ).mapIndexed { i, a ->
                newEpisode(fixUrl(a.attr("href"))) {
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

        // 0. Process player page tabs
        val playerTabs = mutableListOf<String>()
        doc.select("ul.muvipro-player-tabs li a, ul.gmr-player-tabs li a, .gmr-player-nav a, .gmr-player-tabs a, ul.nav-tabs li a, .gmr-server-wrap a").forEach { el ->
            val href = el.attr("href").trim()
            if (href.isNotBlank() && !href.startsWith("#") && !href.contains("javascript", true)) {
                val resolved = fixUrl(href, data)
                if (resolved.contains("?player=") || resolved.contains("?server=") || resolved.contains("&player=") || resolved.contains("&server=")) {
                    playerTabs.add(resolved)
                }
            }
        }
        playerTabs.distinct().forEach { tabUrl ->
            try {
                val tabDoc = app.get(tabUrl, headers = mapOf("Referer" to data)).document
                tabDoc.select("iframe[src], iframe[data-src], iframe[data-litespeed-src], iframe[data-lazy-src]").forEach { iframe ->
                    val src = iframe.attr("src")
                        .ifEmpty { iframe.attr("data-src") }
                        .ifEmpty { iframe.attr("data-litespeed-src") }
                        .ifEmpty { iframe.attr("data-lazy-src") }
                        .trim()
                    val finalUrl = fixUrl(src, tabUrl)
                    if (finalUrl.isNotEmpty()) targets.add(finalUrl)
                }
            } catch (_: Exception) {}
        }

        // 1. Direct video/source elements
        doc.select("source[src], video source[src], video[src]").forEach { el ->
            val src = el.attr("src").trim()
            val finalUrl = fixUrl(src, data)
            if (finalUrl.isNotEmpty()) targets.add(finalUrl)
        }

        // 2. Direct iframes
        doc.select("iframe[src], iframe[data-src], iframe[data-litespeed-src], iframe[data-lazy-src], iframe.metaframe").forEach { iframe ->
            val src = iframe.attr("src")
                .ifEmpty { iframe.attr("data-src") }
                .ifEmpty { iframe.attr("data-litespeed-src") }
                .ifEmpty { iframe.attr("data-lazy-src") }
                .trim()
            val finalUrl = fixUrl(src, data)
            if (finalUrl.isNotEmpty()) targets.add(finalUrl)
        }

        // 3. Option elements / Dropdowns (e.g. Server choices, mirror list)
        doc.select("select option, .mirror option, .server option, select.mirror option, select.server option, .mobius option").forEach { el ->
            listOf("value", "data-src", "data-link", "data-embed", "data-video", "data-url", "data-id").forEach { attr ->
                val v = el.attr(attr).trim()
                if (v.isBlank()) return@forEach
                if (v.startsWith("http") || v.startsWith("//")) {
                    targets.add(if (v.startsWith("//")) "https:$v" else v)
                    return@forEach
                }
                // Try base64 decode
                try {
                    val decoded = android.util.Base64.decode(v, android.util.Base64.DEFAULT)
                    val htmlContent = String(decoded, Charsets.UTF_8)
                    val parsedIfr = Jsoup.parse(htmlContent).selectFirst("iframe, IFRAME, [src]")
                    val iframeSrc = parsedIfr?.attr("src")?.ifEmpty { parsedIfr.attr("data-src") } ?: ""
                    if (iframeSrc.isNotBlank()) {
                        val href = fixUrl(iframeSrc, data)
                        if (href.isNotEmpty()) targets.add(href)
                    }
                } catch (_: Exception) {
                    val finalUrl = fixUrl(v, data)
                    if (finalUrl.isNotEmpty()) targets.add(finalUrl)
                }
            }
        }

        // 4. Clickable elements
        doc.select("a, button, li, div, span, .opt-sp, .opt-single, .mirror-item, div#downloadb li, div.download li").forEach { el ->
            val href = el.attr("href").trim()
            if (href.isNotBlank() && !href.startsWith("#") && !href.contains("javascript", true)) {
                val finalUrl = fixUrl(href, data)
                if (finalUrl.isNotEmpty()) targets.add(finalUrl)
            }
            listOf("data-src", "data-link", "data-embed", "data-video", "data-id", "data-url").forEach { attr ->
                val v = el.attr(attr).trim()
                val finalUrl = fixUrl(v, data)
                if (finalUrl.isNotEmpty() && !v.contains("data:image")) {
                    targets.add(finalUrl)
                }
            }
        }

        // 5. Script regex URL extraction
        doc.select("script").forEach { script ->
            val code = script.html()
            if (code.isNotBlank()) {
                val regex = Regex("(https?:)?//[^\\s\"'<>]+")
                regex.findAll(code).forEach { match ->
                    val rawUrl = match.value
                    val finalUrl = fixUrl(rawUrl, data)
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

        // 6. Process all collected targets
        targets.distinct().forEach { raw ->
            val cleanedRaw = raw.trim()
            if (cleanedRaw.isBlank()) return@forEach

            var decodedUrl = ""
            try {
                val base64Str = cleanedRaw.filter { !it.isWhitespace() }
                val decoded = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
                val html = String(decoded, Charsets.UTF_8)
                val src = Jsoup.parse(html).selectFirst(
                    "iframe[src], iframe[data-litespeed-src], iframe[data-lazy-src], iframe[data-src], source[src]"
                )?.let { ifr ->
                    ifr.attr("src").ifEmpty { ifr.attr("data-litespeed-src").ifEmpty { ifr.attr("data-lazy-src").ifEmpty { ifr.attr("data-src") } } }
                } ?: if (html.startsWith("http")) html else ""
                
                if (src.isNotEmpty()) {
                    decodedUrl = fixUrl(src, data)
                }
            } catch (_: Exception) {}

            val finalUrl = if (decodedUrl.isNotEmpty()) decodedUrl else cleanedRaw
            if (finalUrl.startsWith("http") || finalUrl.startsWith("//")) {
                val cleanUrl = if (finalUrl.startsWith("//")) "https:$finalUrl" else finalUrl
                val cleanUrlEscaped = cleanUrl.replace(92.toChar().toString(), "")
                
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
                    val isWrapper = cleanUrlEscaped.contains("kisskh", true) || cleanUrlEscaped.contains("megaplay", true)
                    if (isWrapper) {
                        try {
                            val subDoc = app.get(cleanUrlEscaped, referer = data).document
                            subDoc.select("iframe[src], iframe[data-src], iframe[data-litespeed-src]").forEach { iframe ->
                                val iframeSrc = iframe.attr("src").ifEmpty { iframe.attr("data-src") }.ifEmpty { iframe.attr("data-litespeed-src") }.trim()
                                if (iframeSrc.isNotBlank()) {
                                    val finalIframeUrl = fixUrl(iframeSrc, cleanUrlEscaped)
                                    val cleanIf = finalIframeUrl.replace(92.toChar().toString(), "")
                                    loadExtractor(cleanIf, cleanUrlEscaped, subtitleCallback, callback)
                                }
                            }
                        } catch (_: Exception) {}
                    } else {
                        val isStreamWish = listOf("streamwish", "wish", "hglink", "hgcloud", "gendeng", "fkupon", "desacinta", "layarotaku", "layarwibu", "nekonime", "layarecchi", "subsource", "doimg", "anchurl", "certaker", "listeamed", "bigwarp", "cloudatacdn", "push-sdk", "gradehg", "hgplus", "streamplay", "awish", "wishembed").any { cleanUrlEscaped.contains(it, true) }
                        val isDood = listOf("dood", "dsvplay", "doodcdn", "vide0", "ds2play", "ds2video", "doodstream", "doodla").any { cleanUrlEscaped.contains(it, true) }
                        val isVoe = cleanUrlEscaped.contains("voe.sx", true) || cleanUrlEscaped.contains("voe", true)
                        val isStreamtape = cleanUrlEscaped.contains("streamtape", true)
                        val isFilemoon = cleanUrlEscaped.contains("filemoon", true)
                        val isMp4Upload = cleanUrlEscaped.contains("mp4upload", true)
                        val isAbyss = listOf("abyssplayer.com", "abyss.to", "abysscdn.com", "iamcdn.net", "sssrr").any { cleanUrlEscaped.contains(it, true) }
                        val isSeekPlayer = cleanUrlEscaped.contains("seekplayer", true)
                        val isTamilEmbed = cleanUrlEscaped.contains("tamilembed", true)

                        when {
                            isTamilEmbed -> {
                                try {
                                    TamilEmbed().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                                } catch (_: Exception) {}
                            }
                            isSeekPlayer -> {
                                try {
                                    SeekplayerVip().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                                } catch (_: Exception) {}
                            }
                            isAbyss -> {
                                try {
                                    AbyssExtractor().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
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
            }
        }

        return true
    }
}
