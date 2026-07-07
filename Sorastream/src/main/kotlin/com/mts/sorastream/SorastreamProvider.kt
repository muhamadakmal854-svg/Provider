package com.mts.sorastream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import java.security.MessageDigest

class SorastreamProvider : MainAPI() {

    override var mainUrl        = "https://sorastream.fun"
    override var name           = "Watch Full Movies and TV Shows Online Free Download With Any Subtitles"
    override var lang           = "en"
    override val hasMainPage    = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "movies" to "Filem Terbaru",
        "tvshows" to "TV Series Terbaru",
        "genre/action" to "Aksi",
        "genre/horror" to "Seram",
        "genre/comedy" to "Komedi"
    )

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
        return newHomePageResponse(request.name, scrapeList(pageUrl))
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

    private suspend fun scrapeList(pageUrl: String): List<SearchResponse> {
        val doc = app.get(pageUrl, headers = mapOf(
            "Referer" to mainUrl,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        )).document
        return doc.select(".movie-list, div.movie-list, .text-center, div.text-center, .col-md-2, div.col-md-2, .col-sm-4, div.col-sm-4, .col-xs-6, div.col-xs-6, .container, div.container, .card, div.card, article.item, .item, .movie-item, .post-item, div.module-item, div.ml-item, .box-item, article, .post, .entry, .film-poster-ahref").mapNotNull {
            val a = (if (it.tagName() == "a") it else it.selectFirst("a")) ?: return@mapNotNull null
            val href = a.attr("href").let { h -> if (h.startsWith("http")) h else "$mainUrl$h" }
            if (href.isBlank() || href == mainUrl || href.contains("javascript")) return@mapNotNull null
            val img = it.selectFirst("img") ?: it.selectFirst("[data-src], [data-lazy-src], [data-original]")
            val title = it.selectFirst(
                ".entry-title, h2.entry-title, h2, h3, .title, .film-name, .movie-title, .item-title"
            )?.text()?.trim()
                ?: a.attr("title").trim().ifEmpty { img?.attr("alt")?.trim() ?: "" }
                    .ifEmpty { img?.attr("title")?.trim() ?: "" }
                    .ifEmpty { a.text().trim() }
            if (title.isBlank()) return@mapNotNull null
            var src = img?.posterUrl() ?: ""
            if (src.isEmpty()) src = it.posterUrl()
            if (src.isEmpty()) {
                it.select("[style*=background], [style*=url]").forEach { el ->
                    val u = el.posterUrl()
                    if (u.isNotEmpty()) { src = u; return@forEach }
                }
            }
            // Smart type detection based on URL pattern and page metadata
            val hrefLower = href.lowercase()
            val typeLabel = it.selectFirst(
                ".type, .label, .badge, [class*=type], [class*=label], .quality"
            )?.text()?.lowercase() ?: ""
            when {
                hrefLower.contains("/movie") || hrefLower.contains("/film") ||
                hrefLower.contains("/movies/") ->
                    newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = src }
                hrefLower.contains("/tvshows/") || hrefLower.contains("/series/") ||
                hrefLower.contains("/episode/") || hrefLower.contains("/tv/") ||
                typeLabel.contains("series") || typeLabel.contains("drama") ||
                typeLabel.contains("episode") ->
                    newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = src }
                else ->
                    newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = src }
            }
        }.distinctBy { it.url }
    }
    
    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("Referer" to mainUrl)).document
        val title = doc.selectFirst(
            ".sheader .data h1, h1.entry-title, .data h1, h1, .heading-name, .film-name"
        )?.text()?.trim() ?: return null
        val poster = doc.selectFirst(
            ".poster img, .sheader .poster img, .film-poster img, [class*=poster] img, " +
            ".entry-thumbnail img, .thumb img, img.wp-post-image, .cover img"
        )?.let { img ->
            listOf("data-src", "data-lazy-src", "data-lazy", "data-cfsrc", "src")
                .map { img.attr(it) }
                .firstOrNull { it.isNotBlank() && it.startsWith("http") }
        }
        val plot = doc.selectFirst(
            ".description p, .wp-content p, .entry-content p, [itemprop=description], " +
            ".film-description, .synops p, .overview"
        )?.text()?.trim()
        val year = doc.selectFirst(
            ".date, .extra .year, [itemprop=dateCreated], .film-stats span, [class*=year]"
        )?.text()?.filter { it.isDigit() }?.let {
            if (it.length >= 4) it.substring(0, 4).toIntOrNull() else null
        }
        val genres = doc.select(
            ".sgeneros a, .genres a, .genre a, .film-genres a, [class*=genre] a, .categories a"
        ).map { it.text() }.filter { it.isNotBlank() }
        val isTv = url.contains("/tvshows/") || url.contains("/series/") ||
                   url.contains("/tv/") || url.contains("/season/") ||
                   doc.select(
                       ".episodes-list li, .episodios li, #seasons .se-c, " +
                       ".eplister li, .episodelist li, .clps li, #episodes li"
                   ).isNotEmpty()
        return if (isTv) {
            val eps = doc.select(
                ".episodes-list li a, .episodios li a, #episodes .episodiotitle a, " +
                ".eplister ul li a, .episodelist ul li a, .ep-list li a, .clps li a, " +
                "[class*=episode-list] li a, [class*=episode] a[href]"
            ).mapIndexed { i, a ->
                newEpisode(fixUrl(a.attr("href"))) {
                    this.name = a.selectFirst(".epl-title, .epl-num, span, .episode-title")
                        ?.text()?.trim() ?: a.text().trim()
                    this.episode = i + 1
                }
            }.filter { it.data.isNotBlank() }.distinctBy { it.data }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, eps) {
                this.posterUrl = poster; this.plot = plot; this.year = year; this.tags = genres
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster; this.plot = plot; this.year = year; this.tags = genres
            }
        }
    }
    
    private fun sha256(input: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
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

        // 0. Process player page tabs (e.g. ?player= or ?server= or sub-pages)
        val playerTabs = mutableListOf<String>()
        doc.select("ul.muvipro-player-tabs li a, ul.gmr-player-tabs li a, .gmr-player-nav a, .gmr-player-tabs a, ul.nav-tabs li a, .gmr-server-wrap a").forEach { el ->
            val href = el.attr("href").trim()
            if (href.isNotBlank() && !href.startsWith("#") && !href.contains("javascript", true)) {
                val resolved = fixUrl(href)
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
                    val finalUrl = fixUrl(src)
                    if (finalUrl.isNotEmpty()) targets.add(finalUrl)
                }
            } catch (_: Exception) {}
        }

        // 1. Direct video/source elements
        doc.select("source[src], video source[src], video[src]").forEach { el ->
            val src = el.attr("src").trim()
            val finalUrl = fixUrl(src)
            if (finalUrl.isNotEmpty()) targets.add(finalUrl)
        }

        // 2. Direct iframes (check common attributes and classes)
        doc.select("iframe[src], iframe[data-src], iframe[data-litespeed-src], iframe[data-lazy-src], iframe.metaframe").forEach { iframe ->
            val src = iframe.attr("src")
                .ifEmpty { iframe.attr("data-src") }
                .ifEmpty { iframe.attr("data-litespeed-src") }
                .ifEmpty { iframe.attr("data-lazy-src") }
                .trim()
            val finalUrl = fixUrl(src)
            if (finalUrl.isNotEmpty()) targets.add(finalUrl)
        }

        // 3. Option elements / Dropdowns (e.g. Server choices, mirror list)
        doc.select("select option, .mirror option, .server option, select.mirror option, select.server option, .mobius option").forEach { el ->
            listOf("value", "data-src", "data-link", "data-embed", "data-video", "data-url", "data-id").forEach { attr ->
                val v = el.attr(attr).trim()
                if (v.isBlank()) return@forEach
                // If the value is a real URL, add directly
                if (v.startsWith("http") || v.startsWith("//")) {
                    targets.add(if (v.startsWith("//")) "https:$v" else v)
                    return@forEach
                }
                // Try base64 decode (for mobius/mirror selectors with encoded iframes)
                try {
                    val decoded = android.util.Base64.decode(v, android.util.Base64.DEFAULT)
                    val htmlContent = String(decoded, Charsets.UTF_8)
                    val parsedIfr = Jsoup.parse(htmlContent).selectFirst("iframe, IFRAME, [src]")
                    val iframeSrc = parsedIfr?.attr("src")?.ifEmpty { parsedIfr.attr("data-src") } ?: ""
                    if (iframeSrc.isNotBlank()) {
                        val href = fixUrl(iframeSrc)
                        if (href.isNotEmpty()) targets.add(href)
                    }
                } catch (_: Exception) {
                    // Not base64, try as-is via fixUrl
                    val finalUrl = fixUrl(v)
                    if (finalUrl.isNotEmpty()) targets.add(finalUrl)
                }
            }
        }


        // 4. Clickable elements, links, buttons, lists
        doc.select("a, button, li, div, span, .opt-sp, .opt-single, .mirror-item, div#downloadb li, div.download li").forEach { el ->
            val href = el.attr("href").trim()
            if (href.isNotBlank() && !href.startsWith("#") && !href.contains("javascript", true)) {
                val finalUrl = fixUrl(href)
                if (finalUrl.isNotEmpty()) targets.add(finalUrl)
            }
            listOf("data-src", "data-link", "data-embed", "data-video", "data-id", "data-url", "data-content").forEach { attr ->
                val v = el.attr(attr).trim()
                val finalUrl = fixUrl(v)
                if (finalUrl.isNotEmpty() && !v.contains("data:image")) {
                    targets.add(finalUrl)
                }
            }
        }

        // 5. AJAX Options (ZetaFlix, DooPlay, Flavor themes)
        val ajaxBtns = doc.select("[data-post][data-nume], ul#playeroptionsul > li, li.zetaflix_player_option, .mirror-item")
        val ajaxOptions = ajaxBtns.mapNotNull {
            val post = it.attr("data-post")
            val nume = it.attr("data-nume")
            val type = it.attr("data-type").ifEmpty { "movie" }
            if (post.isNotEmpty() && nume.isNotEmpty()) {
                Triple(post, nume, type)
            } else {
                null
            }
        }.distinct()

        ajaxOptions.forEach { (post, nume, type) ->
            val actions = listOf(
                "zt_main_ajax", "doo_player_ajax", "wp_ajax_doo_player", 
                "action_player", "playvideo", "zeta_player_ajax",
                "get_player_source", "ajax_player", "player_ajax", "bootstrap_ajax"
            )
            for (action in actions) {
                try {
                    val pageBase = try {
                        val u = java.net.URL(data)
                        "${u.protocol}://${u.host}"
                    } catch (_: Exception) { mainUrl }
                    val response = app.post(
                        url = "$pageBase/wp-admin/admin-ajax.php",
                        data = mapOf(
                            "action" to action,
                            "post" to post,
                            "nume" to nume,
                            "type" to type
                        ),
                        referer = data,
                        headers = mapOf(
                            "X-Requested-With" to "XMLHttpRequest",
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        )
                    )
                    if (!response.isSuccessful) continue
                    val json = response.text
                    if (json.isBlank() || json == "0" || json == "false" || json == "null") continue

                    // Extract iframe or URL from response HTML/JSON
                    val parsedDoc = Jsoup.parse(json)
                    val iframeSrc = parsedDoc.selectFirst("iframe[src], iframe[data-src]")?.let { 
                        it.attr("src").ifEmpty { it.attr("data-src") } 
                    }

                    val embedUrl = iframeSrc
                        ?: Regex("""src=["']([^"']+)["']""").find(json)?.groupValues?.get(1)
                        ?: Regex("""href=["']([^"']+)["']""").find(json)?.groupValues?.get(1)
                        ?: Regex("""["'](https?:[^"']+)["']""").find(json)?.groupValues?.get(1)
                        ?: if (json.trim().startsWith("http")) json.trim() else null

                    if (embedUrl != null) {
                        val cleanUrl = fixUrl(embedUrl)
                        if (cleanUrl.isNotEmpty()) {
                            targets.add(cleanUrl)
                            break // Found link for this button, skip other actions
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 6. Harvest URLs directly from <script> tags
        doc.select("script").forEach { script ->
            val content = script.data()
            if (content.isNotBlank()) {
                Regex("""https?://[a-zA-Z0-9.\-_]+/[a-zA-Z0-9.\-_\?&=\/~]+""").findAll(content).forEach { match ->
                    val url = match.value
                    if (!url.contains("google") && !url.contains("facebook") && !url.contains("analytics")) {
                        val finalUrl = fixUrl(url)
                        if (finalUrl.isNotEmpty()) targets.add(finalUrl)
                    }
                }
            }
        }

        // 7. Process all collected targets (including base64 decoding & fallback routing)
        targets.distinct().forEach { raw ->
            val cleanedRaw = raw.trim()
            if (cleanedRaw.isBlank()) return@forEach

            // Attempt base64 decoding
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
                    decodedUrl = fixUrl(src)
                }
            } catch (_: Exception) {}

            val finalUrl = if (decodedUrl.isNotEmpty()) decodedUrl else cleanedRaw
            if (finalUrl.startsWith("http") || finalUrl.startsWith("//")) {
                val cleanUrl = if (finalUrl.startsWith("//")) "https:$finalUrl" else finalUrl
                val cleanUrlEscaped = cleanUrl.replace(92.toChar().toString(), "")
                
                if (cleanUrlEscaped.contains("gofile.io/d/")) {
                    try {
                        val contentId = cleanUrlEscaped.substringAfter("/d/").substringBefore("/").substringBefore("?")
                        if (contentId.isNotEmpty()) {
                            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                            val accResponse = app.post(
                                url = "https://api.gofile.io/accounts",
                                headers = mapOf(
                                    "User-Agent" to userAgent,
                                    "Accept" to "*/*",
                                    "Referer" to "https://gofile.io/",
                                    "Origin" to "https://gofile.io"
                                )
                            )
                            if (accResponse.isSuccessful) {
                                val responseText = accResponse.text
                                val apiToken = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(responseText)?.groupValues?.get(1)
                                if (apiToken != null) {
                                    val timeSlot = System.currentTimeMillis() / 1000 / 14400
                                    val salt = "5d4f7g8sd45fsd"
                                    val tokenData = "$userAgent::en-US::$apiToken::$timeSlot::$salt"
                                    val websiteToken = sha256(tokenData)
                                    
                                    val contentUrl = "https://api.gofile.io/contents/$contentId?contentFilter=&page=1&pageSize=1000&sortField=name&sortDirection=1"
                                    val contentResponse = app.get(
                                        url = contentUrl,
                                        headers = mapOf(
                                            "User-Agent" to userAgent,
                                            "Accept" to "*/*",
                                            "Referer" to "https://gofile.io/",
                                            "Origin" to "https://gofile.io",
                                            "Authorization" to "Bearer $apiToken",
                                            "X-Website-Token" to websiteToken,
                                            "X-BL" to "en-US"
                                        )
                                    )
                                    if (contentResponse.isSuccessful) {
                                        val contentText = contentResponse.text
                                        Regex("\"link\"\\s*:\\s*\"([^\"]+)\"").findAll(contentText).forEach { match ->
                                            val link = match.groupValues[1]
                                            if (link.startsWith("http")) {
                                                callback(
                                                    newExtractorLink(
                                                        source = "Gofile",
                                                        name = "Gofile",
                                                        url = link,
                                                        type = if (link.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                                    ) {
                                                        this.referer = "https://gofile.io/"
                                                        this.quality = Qualities.Unknown.value
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                } else if (cleanUrlEscaped.contains(".m3u8") || cleanUrlEscaped.contains(".mp4") || cleanUrlEscaped.contains("/hls/")) {
                    try {
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
                    } catch (_: Exception) {}
                } else {
                    // Try to unwrap redirect parameters
                    listOf("link", "url", "r", "to", "go").forEach { param ->
                        try {
                            val regex = Regex("[?&]" + param + "=([^&]+)")
                            val match = regex.find(cleanUrlEscaped)
                            val queryValue = match?.groupValues?.get(1)
                            if (queryValue != null && queryValue.isNotEmpty()) {
                                val decodedParam = try {
                                    val decodedBytes = android.util.Base64.decode(queryValue, android.util.Base64.DEFAULT)
                                    String(decodedBytes, Charsets.UTF_8)
                                } catch (_: Exception) {
                                    java.net.URLDecoder.decode(queryValue, "UTF-8")
                                }
                                val finalDecoded = fixUrl(decodedParam)
                                if (finalDecoded.startsWith("http") && !finalDecoded.contains("google") && !finalDecoded.contains("facebook")) {
                                    try {
                                        loadExtractor(finalDecoded, data, subtitleCallback, callback)
                                    } catch (_: Exception) {}
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    
                    if (!cleanUrlEscaped.contains("googletagmanager") && !cleanUrlEscaped.contains("facebook") && 
                        !cleanUrlEscaped.contains("googleads") && !cleanUrlEscaped.contains("analytics") && 
                        !cleanUrlEscaped.contains("histats") && !cleanUrlEscaped.contains("doubleclick") &&
                        !cleanUrlEscaped.contains("adskeeper")) {
                        
                        // Same-domain deep scan (Auto Iframe Scanning for wrapper player pages on same domain)
                        val isSameDomain = try {
                            val host1 = java.net.URL(cleanUrlEscaped).host.replace("www.", "")
                            val host2 = java.net.URL(mainUrl).host.replace("www.", "")
                            host1 == host2
                        } catch (_: Exception) { false }

                        if (isSameDomain && cleanUrlEscaped != data) {
                            try {
                                val subDoc = app.get(cleanUrlEscaped, referer = data).document
                                subDoc.select("iframe[src], iframe[data-src], iframe[data-litespeed-src]").forEach { iframe ->
                                    val iframeSrc = iframe.attr("src").ifEmpty { iframe.attr("data-src") }.ifEmpty { iframe.attr("data-litespeed-src") }.trim()
                                    if (iframeSrc.isNotBlank()) {
                                        val finalIframeUrl = fixUrl(iframeSrc)
                                        if (finalIframeUrl.isNotEmpty() && finalIframeUrl != cleanUrlEscaped) {
                                            val cleanIf = finalIframeUrl.replace(92.toChar().toString(), "")
                                            if (cleanIf.contains("gofile.io/d/")) {
                                                // Handle Gofile inside sub iframe
                                            } else if (cleanIf.contains(".m3u8") || cleanIf.contains(".mp4") || cleanIf.contains("/hls/")) {
                                                val isM3u = cleanIf.contains(".m3u8") || cleanIf.contains("/hls/")
                                                callback(
                                                    newExtractorLink(
                                                        source = "Direct Stream",
                                                        name = "Direct Stream",
                                                        url = cleanIf,
                                                        type = if (isM3u) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                                    ) {
                                                        this.referer = cleanUrlEscaped
                                                    }
                                                )
                                            } else {
                                                loadExtractor(cleanIf, cleanUrlEscaped, subtitleCallback, callback)
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }

                        // Smart Extractor Fallback Dispatcher
                        val isStreamWish = listOf("streamwish", "wish", "hglink", "hgcloud", "gendeng", "fkupon", "desacinta", "layarotaku", "layarwibu", "nekonime", "layarecchi", "subsource", "doimg", "anchurl", "certaker", "listeamed", "bigwarp", "cloudatacdn", "push-sdk", "gradehg", "hgplus", "streamplay", "awish", "wishembed").any { cleanUrlEscaped.contains(it, true) }
                        val isDood = listOf("dood", "dsvplay", "doodcdn", "vide0", "ds2play", "ds2video", "doodstream", "doodla").any { cleanUrlEscaped.contains(it, true) }
                        val isVoe = cleanUrlEscaped.contains("voe.sx", true) || cleanUrlEscaped.contains("voe", true)
                        val isStreamtape = cleanUrlEscaped.contains("streamtape", true)
                        val isFilemoon = cleanUrlEscaped.contains("filemoon", true)
                        val isMp4Upload = cleanUrlEscaped.contains("mp4upload", true)
                        val isAbyss = listOf("abyssplayer.com", "abyss.to", "abysscdn.com", "iamcdn.net", "sssrr").any { cleanUrlEscaped.contains(it, true) }

                        when {
                            isAbyss -> {
                                try {
                                    AbyssExtractor().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                                } catch (e: Exception) {
                                    android.util.Log.e("FallbackExtractor", "AbyssExtractor failed: ${e.message}")
                                }
                            }
                            isStreamWish -> {
                                try {
                                    com.lagradost.cloudstream3.extractors.StreamWishExtractor().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                                } catch (e: Exception) {
                                    android.util.Log.e("FallbackExtractor", "StreamWish extraction failed for $cleanUrlEscaped: ${e.message}")
                                }
                            }
                            isDood -> {
                                try {
                                    com.lagradost.cloudstream3.extractors.DoodLaExtractor().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                                } catch (e: Exception) {
                                    android.util.Log.e("FallbackExtractor", "DoodLaExtractor extraction failed for $cleanUrlEscaped: ${e.message}")
                                }
                            }
                            isVoe -> {
                                try {
                                    com.lagradost.cloudstream3.extractors.Voe().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                                } catch (e: Exception) {
                                    android.util.Log.e("FallbackExtractor", "Voe extraction failed: ${e.message}")
                                }
                            }
                            isStreamtape -> {
                                try {
                                    com.lagradost.cloudstream3.extractors.StreamTape().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                                } catch (e: Exception) {
                                    android.util.Log.e("FallbackExtractor", "StreamTape extraction failed: ${e.message}")
                                }
                            }
                            isFilemoon -> {
                                try {
                                    com.lagradost.cloudstream3.extractors.FileMoon().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                                } catch (e: Exception) {
                                    android.util.Log.e("FallbackExtractor", "FileMoon extraction failed: ${e.message}")
                                }
                            }
                            isMp4Upload -> {
                                try {
                                    com.lagradost.cloudstream3.extractors.Mp4Upload().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                                } catch (e: Exception) {
                                    android.util.Log.e("FallbackExtractor", "Mp4Upload extraction failed: ${e.message}")
                                }
                            }
                            else -> {
                                try {
                                    loadExtractor(cleanUrlEscaped, data, subtitleCallback, callback)
                                } catch (e: Exception) {
                                    android.util.Log.e("Extractor", "Standard loadExtractor failed for $cleanUrlEscaped: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }
        }

        return true
    }
}

class AbyssExtractor : ExtractorApi() {
    override val name = "Abyss"
    override val mainUrl = "https://abyssplayer.com"
    override val requiresReferer = true

    private fun decryptAesCtr(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val spec = javax.crypto.spec.SecretKeySpec(key, "AES")
        val parameterSpec = javax.crypto.spec.IvParameterSpec(iv)
        val cipher = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, spec, parameterSpec)
        return cipher.doFinal(ciphertext)
    }

    private fun md5(input: ByteArray): ByteArray {
        val md = java.security.MessageDigest.getInstance("MD5")
        return md.digest(input)
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
        callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
    ) {
        try {
            val cleanUrl = url.replace(92.toChar().toString(), "")
            val pageHtml = app.get(cleanUrl, headers = mapOf("Referer" to (referer ?: mainUrl))).text

            val base64Str = Regex("const datas\\s*=\\s*\"([^\"]+)\"").find(pageHtml)?.groupValues?.get(1) ?: return
            val decodedBytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
            val latin1Str = String(decodedBytes, Charsets.ISO_8859_1)

            val json = org.json.JSONObject(latin1Str)
            val slug = json.getString("slug")
            val userId = json.getString("user_id")
            val md5Id = json.getString("md5_id")
            val media = json.getString("media")

            val keyStr = "$userId:$slug:$md5Id"
            val keyBytesStr = md5(keyStr.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            val key = keyBytesStr.toByteArray(Charsets.UTF_8)
            val iv = key.sliceArray(0 until 16)

            val mediaCiphertext = android.util.Base64.decode(media, android.util.Base64.DEFAULT)
            val decryptedMediaBytes = decryptAesCtr(mediaCiphertext, key, iv)
            val decryptedMediaStr = String(decryptedMediaBytes, Charsets.UTF_8)

            val mediaJson = org.json.JSONObject(decryptedMediaStr)
            val mp4 = mediaJson.getJSONObject("mp4")
            val sources = mp4.getJSONArray("sources")
            val domainsObj = if (mp4.has("domains")) mp4.getJSONObject("domains") else if (mediaJson.has("domains")) mediaJson.getJSONObject("domains") else org.json.JSONObject()

            for (i in 0 until sources.length()) {
                val src = sources.getJSONObject(i)
                val size = src.getLong("size")
                val resId = src.getInt("res_id")
                val label = src.getString("label")
                val sub = src.getString("sub")

                val domain = domainsObj.getString(sub)

                val pathStr = "/mp4/$md5Id/$resId/$size?v=$slug"
                
                val sizeStr = size.toString()
                val digitBytes = sizeStr.map { it.toString().toInt().toByte() }.toByteArray()
                val sizeHashHex = md5(digitBytes).joinToString("") { "%02x".format(it) }
                
                val pathKey = sizeHashHex.toByteArray(Charsets.UTF_8)
                val pathIv = pathKey.sliceArray(0 until 16)

                val pathBytes = pathStr.toByteArray(Charsets.UTF_8)
                val encryptedPathBytes = decryptAesCtr(pathBytes, pathKey, pathIv)

                val b64Once = android.util.Base64.encodeToString(encryptedPathBytes, android.util.Base64.NO_WRAP)
                val b64Twice = android.util.Base64.encodeToString(b64Once.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                val cleanPath = b64Twice.replace("=", "").replace("\n", "").replace("\r", "")

                val finalStreamUrl = "https://$domain/sora/$size/$cleanPath"

                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name - $label",
                        url = finalStreamUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = cleanUrl
                        this.quality = when (label.lowercase()) {
                            "360p" -> Qualities.P360.value
                            "480p" -> Qualities.P480.value
                            "720p" -> Qualities.P720.value
                            "1080p" -> Qualities.P1080.value
                            else -> Qualities.Unknown.value
                        }
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
