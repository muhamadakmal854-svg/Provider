package com.mts.pencurimovie

import com.lagradost.api.Log
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*

class PencurimoviesubmalayProvider : MainAPI() {
    companion object {
        var context: android.content.Context? = null
    }
    override var mainUrl = "https://ww11.pencurimovie.sbs"
    override var name = "PencuriMovie"
    override val hasMainPage = true
    override var lang = "ms"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "movies" to "Filem Terbaru",
        "series" to "TV Series",
        "country/malaysia" to "Malaysia Movies",
        "country/indonesia" to "Indonesia Movies",
        "country/indonesian" to "Indonesian Movies",
        "country/india" to "India Movies",
        "country/japan" to "Japan Movies"
    )

    private fun Element.getImageUrl(): String {
        return this.attr("data-src").ifEmpty {
            this.attr("data-original").ifEmpty {
                this.attr("data-lazy-src").ifEmpty {
                    this.attr("src").ifEmpty {
                        this.attr("content")
                    }
                }
            }
        }
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
        val document = app.get(pageUrl).documentLarge
        val home = document.select("div.module-item, div.ml-item, div.display-item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = this.selectFirst("a.play2, a") ?: return null
        val title = anchor.attr("title").ifEmpty {
            anchor.attr("oldtitle").ifEmpty {
                this.selectFirst("h3")?.text()?.trim() ?: ""
            }
        }.substringBefore("(").trim()
        
        if (title.isEmpty()) return null
        
        val href = fixUrl(anchor.attr("href"))
        
        val img = this.selectFirst("img")
        val posterUrl = img?.getImageUrl() ?: ""
        
        val quality = getQualityFromString(
            this.selectFirst("span.item-quality, span.mli-quality, span.quality")?.text() ?: ""
        )
        
        val type = if (href.contains("tvshows") || href.contains("series")) TvType.TvSeries else TvType.Movie
        
        return if (type == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").documentLarge
        return document.select("div.module-item, div.ml-item, div.display-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        
        val title = document.selectFirst("div.details-title h3, div.mvic-desc h3, .sheader .data h1, h1.entry-title, .heading-name a, .data h1")?.text()?.trim()
            ?.substringBefore("(")?.trim().orEmpty()
            
        val poster = document.selectFirst("div.content-poster img, .poster img, .sheader .poster img, .film-poster img, [class*=poster] img, meta[property='og:image']")?.getImageUrl()
        
        val description = document.selectFirst("div.details-desc, div.desc p.f-desc, div.text, .wp-content p, .description p, .info-content p, .film-description")?.text()?.trim()
        
        val tvtag = if (url.contains("tvshows") || url.contains("series") || url.contains("/episodes/")) TvType.TvSeries else TvType.Movie
        
        val trailer = document.selectFirst("div.modal-trailer iframe")?.attr("src")
            ?: document.selectFirst("meta[property='og:video'], meta[itemprop='embedUrl']")?.attr("content")
            
        val genre = document.select("div.details-genre a, div.mvic-info p:contains(Genre) a, .sgeneros a, .genres a, .genre a, .film-genres a").map {
            it.text().trim()
        }.filter { it.isNotEmpty() && !it.equals("Genres", true) }
        
        val rating = document.selectFirst("span.imdb-r, span.details-rating, div.details-rating, .date, .film-stats span")
            ?.text()?.trim()?.toDoubleOrNull()
            
        val duration = document.selectFirst("span[itemprop=duration], span.runtime, .runtime, .film-stats span")
            ?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()

        val actors = document.select("div.mvic-info p:contains(Actors) a, .sdata a[href*='/cast/']").map { it.text().trim() }
        
        val year = document.selectFirst("a[href*='/release/'], div.mvic-info p:contains(Release) a, .date, .extra .year, [itemprop=dateCreated], .film-stats span")
            ?.text()?.trim()?.toIntOrNull()
            
        val recommendation = document.select("div.module-item, div.ml-item, div.display-item").mapNotNull {
            it.toSearchResult()
        }

        return if (tvtag == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            
            // Try new layout: ul.episodes-list
            val episodeLists = document.select("ul.episodes-list")
            if (episodeLists.isNotEmpty()) {
                episodeLists.forEach { ul ->
                    val seasonId = ul.attr("id")
                    val seasonNumber = seasonId.substringAfter("season-listep-").toIntOrNull() ?: 1
                    ul.select("li").forEach { li ->
                        val a = li.selectFirst("a")
                        if (a != null) {
                            val epHref = fixUrl(a.attr("href"))
                            val epNum = li.selectFirst("span.ep-num")?.text()?.toIntOrNull()
                            val epTitle = li.selectFirst("span.ep-title")?.text()?.trim().orEmpty()
                            val epThumb = li.selectFirst("img")?.getImageUrl()
                            episodes.add(
                                newEpisode(epHref) {
                                    this.episode = epNum
                                    this.name = epTitle
                                    this.season = seasonNumber
                                    this.posterUrl = epThumb
                                }
                            )
                        }
                    }
                }
            }
            
            // Try old layout: div.tvseason
            if (episodes.isEmpty()) {
                document.select("div.tvseason").forEach { info ->
                    val season = info.select("strong").text().substringAfter("Season").trim().toIntOrNull()
                    info.select("div.les-content a").forEach { it ->
                        val epName = it.text().substringAfter("-").trim()
                        val epHref = fixUrl(it.attr("href"))
                        val epNum = it.text().substringAfter("Episode").substringBefore("-").trim().toIntOrNull()
                        episodes.add(
                            newEpisode(epHref) {
                                this.episode = epNum
                                this.name = epName
                                this.season = season
                            }
                        )
                    }
                }
            }

            // Fallback to standard theme selectors if empty
            if (episodes.isEmpty()) {
                document.select(".episodes-list li a, .episodios li a, #episodes .episodiotitle a, #seasons .se-c li a, .tvshows-list li a").forEachIndexed { i, a ->
                    val epHref = fixUrl(a.attr("href"))
                    val epName = a.text().trim()
                    episodes.add(
                        newEpisode(epHref) {
                            this.episode = i + 1
                            this.name = epName
                        }
                    )
                }
            }

            val sortedEpisodes = episodes.distinctBy { it.data }.sortedWith(
                compareBy<Episode> { it.season ?: 1 }.thenBy { it.episode ?: 1 }
            )

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, sortedEpisodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genre
                this.year = year
                addTrailer(trailer)
                addActors(actors)
                this.recommendations = recommendation
                this.duration = duration ?: 0
                if (rating != null) addScore(rating.toString(), 10)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genre
                this.year = year
                addTrailer(trailer)
                addActors(actors)
                this.recommendations = recommendation
                this.duration = duration ?: 0
                if (rating != null) addScore(rating.toString(), 10)
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
                val finalUrl = fixUrl(v)
                if (finalUrl.isNotEmpty()) targets.add(finalUrl)
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

        # 6. Harvest URLs directly from <script> tags
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

        # 7. Process all collected targets (including base64 decoding & fallback routing)
        targets.distinct().forEach { raw ->
            val cleanedRaw = raw.trim()
            if (cleanedRaw.isBlank()) return@forEach

            # Attempt base64 decoding
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
                        
                        # Same-domain deep scan (Auto Iframe Scanning for wrapper player pages on same domain)
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
                                                # Handle Gofile inside sub iframe
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

                        # Smart Extractor Fallback Dispatcher
                        val isStreamWish = listOf("streamwish", "wish", "hglink", "hgcloud", "gendeng", "fkupon", "desacinta", "layarotaku", "layarwibu", "nekonime", "layarecchi", "subsource", "doimg", "anchurl", "certaker", "listeamed", "bigwarp", "cloudatacdn", "push-sdk", "gradehg", "hgplus", "streamplay", "awish", "wishembed").any { cleanUrlEscaped.contains(it, true) }
                        val isDood = listOf("dood", "dsvplay", "doodcdn", "vide0", "ds2play", "ds2video", "doodstream", "doodla").any { cleanUrlEscaped.contains(it, true) }
                        val isVoe = cleanUrlEscaped.contains("voe.sx", true) || cleanUrlEscaped.contains("voe", true)
                        val isStreamtape = cleanUrlEscaped.contains("streamtape", true)
                        val isFilemoon = cleanUrlEscaped.contains("filemoon", true)
                        val isMp4Upload = cleanUrlEscaped.contains("mp4upload", true)

                        when {
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
