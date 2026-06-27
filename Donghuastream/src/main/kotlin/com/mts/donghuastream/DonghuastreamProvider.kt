package com.mts.donghuastream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class DonghuastreamProvider : MainAPI() {

    override var mainUrl        = "https://donghuastream.org"
    override var name           = "DonghuaStream"
    override var lang           = "en"
    override val hasMainPage    = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Anime, TvType.OVA)

    override val mainPage = mainPageOf(
        "" to "Terbaru",
        "schedule" to "Jadual",
        "anime/?status=ongoing" to "Ongoing",
        "anime/?status=completed" to "Completed",
        "anime" to "Semua Anime"
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
            "Accept"  to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        )).document
        return doc.select(".listupd .bsx, .listupd .bs, .bsx, .bs, article.bs, article, .animpost, article.animpost, .animepost, article.animepost, article.item, .film-poster, .item-anime, .epbox, .out-thumb, .milist, .post-item, .hentry").mapNotNull {
            val a     = (if (it.tagName() == "a") it else it.selectFirst("a")) ?: return@mapNotNull null
            val href  = a.attr("href").let { h -> if (h.startsWith("http")) h else "$mainUrl$h" }
            val img   = it.selectFirst("img") ?: it.selectFirst("[data-src], [data-lazy-src], [data-original]")
            val title = it.selectFirst(".tt, .ttl, h2, .bigor .tt, .mdl-animepost .info .name, .film-name, h3")
                ?.text()?.trim()
                ?: a.attr("title").trim().ifEmpty { img?.attr("alt")?.trim() ?: "" }.ifEmpty { img?.attr("title")?.trim() ?: "" }.ifEmpty { a.text().trim() }
            if (title.isBlank()) return@mapNotNull null
            var src   = img?.posterUrl() ?: ""
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
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = src }
        }.distinctBy { it.url }
    }
    
    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("Referer" to mainUrl)).document
        val title  = doc.selectFirst("h1.entry-title, .thumb img, .film-poster img, .animposx .entry-title")?.let {
            if (it.tagName() == "img") it.attr("alt").trim() else it.text().trim()
        }?.trim() ?: return null
        val poster = doc.selectFirst(".thumb img, .seriesthumb img, .film-poster img, .entry-thumb img, .cover img")
            ?.let { img ->
                listOf("data-src","data-lazy-src","data-lazy","data-cfsrc","data-original","src")
                    .map { img.attr(it) }
                    .firstOrNull { it.isNotBlank() && it.startsWith("http") }
            }
        val plot   = doc.selectFirst(".entry-content p, .synp .deskripsi, [itemprop=description], .film-description p")
            ?.text()?.trim()
        val genres = doc.select(".genxed a, .genre-info a, .info-content .spe a[href*=genre], .film-genres a")
            .map { it.text() }
        val eps = doc.select(".eplister ul li a, .episodelist ul li a, .clps li a, .ep-list li a").mapNotNull { a ->
            val epTitle = a.selectFirst(".epl-title, .epl-num, span")?.text()?.trim()
                ?: a.text().trim()
            val epUrl   = a.attr("href")
            if (epUrl.isNotBlank()) newEpisode(epUrl) { this.name = epTitle } else null
        }.reversed()
        return if (eps.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, eps) {
                this.posterUrl = poster; this.plot = plot; this.tags = genres
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster; this.plot = plot; this.tags = genres
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

        // 1. Direct video/source elements
        doc.select("source[src], video source[src], video[src]").forEach { el ->
            val src = el.attr("src").trim()
            if (src.startsWith("http") || src.startsWith("//")) {
                val finalUrl = if (src.startsWith("//")) "https:$src" else src
                targets.add(finalUrl)
            }
        }

        // 2. Direct iframes (check common attributes and classes)
        doc.select("iframe[src], iframe[data-src], iframe[data-litespeed-src], iframe[data-lazy-src], iframe.metaframe").forEach { iframe ->
            val src = iframe.attr("src")
                .ifEmpty { iframe.attr("data-src") }
                .ifEmpty { iframe.attr("data-litespeed-src") }
                .ifEmpty { iframe.attr("data-lazy-src") }
                .trim()
            if (src.startsWith("http") || src.startsWith("//")) {
                val finalUrl = if (src.startsWith("//")) "https:$src" else src
                targets.add(finalUrl)
            }
        }

        // 3. Option elements / Dropdowns (e.g. Server choices, mirror list)
        doc.select("select option, .mirror option, .server option, select.mirror option, select.server option, .mobius option").forEach { el ->
            listOf("value", "data-src", "data-link", "data-embed", "data-video", "data-url", "data-id").forEach { attr ->
                val v = el.attr(attr).trim()
                if (v.isNotBlank()) {
                    targets.add(v)
                }
            }
        }

        // 4. Clickable elements, links, buttons, lists (e.g. Samehadaku download list, Otakudesu lists, mirrors)
        doc.select("a, button, li, div, span, .opt-sp, .opt-single, .mirror-item, div#downloadb li, div.download li").forEach { el ->
            val href = el.attr("href").trim()
            if (href.isNotBlank() && !href.startsWith("#") && !href.contains("javascript", true)) {
                targets.add(href)
            }
            listOf("data-src", "data-link", "data-embed", "data-video", "data-id", "data-url", "data-content").forEach { attr ->
                val v = el.attr(attr).trim()
                if (v.isNotBlank() && !v.contains("data:image")) {
                    targets.add(v)
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
                        val cleanUrl = embedUrl.replace(92.toChar().toString(), "")
                        if (cleanUrl.startsWith("http") || cleanUrl.startsWith("//")) {
                            val finalUrl = if (cleanUrl.startsWith("//")) "https:$cleanUrl" else cleanUrl
                            targets.add(finalUrl)
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
                        targets.add(url)
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
                
                if (src.startsWith("http") || src.startsWith("//")) {
                    decodedUrl = if (src.startsWith("//")) "https:$src" else src
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
                                if (decodedParam.startsWith("http") && !decodedParam.contains("google") && !decodedParam.contains("facebook")) {
                                    try {
                                        loadExtractor(decodedParam, data, subtitleCallback, callback)
                                    } catch (_: Exception) {}
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    
                    if (!cleanUrlEscaped.contains("googletagmanager") && !cleanUrlEscaped.contains("facebook") && 
                        !cleanUrlEscaped.contains("googleads") && !cleanUrlEscaped.contains("analytics") && 
                        !cleanUrlEscaped.contains("histats") && !cleanUrlEscaped.contains("doubleclick") &&
                        !cleanUrlEscaped.contains("adskeeper")) {
                        
                        // Smart Extractor Fallback Dispatcher
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
