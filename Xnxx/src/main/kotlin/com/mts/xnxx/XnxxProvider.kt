package com.mts.xnxx

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class XnxxProvider : MainAPI() {

    override var mainUrl        = "https://www.xnxx.com"
    override var name           = "Free Porn, Sex, Tube Videos, XXX Pics, Pussy in Porno Movies"
    override var lang           = "en"
    override val hasMainPage    = true
    override val supportedTypes = setOf(TvType.NSFW)

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
        for (attr in listOf("data-src","data-lazy-src","data-lazy","data-cfsrc","data-original","src")) {
            val v = this.attr(attr)
            if (v.isNotBlank() && !v.contains("data:image") &&
                (v.startsWith("http") || v.startsWith("//"))) {
                return if (v.startsWith("//")) "https:$v" else v
            }
        }
        return ""
    }

    private suspend fun scrapeList(pageUrl: String): List<SearchResponse> {
        val doc = app.get(pageUrl, headers = mapOf("Referer" to mainUrl)).document
        return doc.select("article.item, .item, .result-item, .film-poster-ahref, div.module-item, div.ml-item, .movie-item, .post-item, .item-post, .box-item, .data-item, .g-item").mapNotNull {
            val a   = it.selectFirst("h3 a, h2 a, .title a, a") ?: return@mapNotNull null
            val img = it.selectFirst(".poster img, img") ?: it.selectFirst("[data-src], [data-lazy-src], [data-original]")
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
                newTvSeriesSearchResponse(a.text().trim(), href, TvType.TvSeries) { posterUrl = src }
            } else {
                newMovieSearchResponse(a.text().trim(), href, TvType.Movie) { posterUrl = src }
            }
        }
    }
    
    override suspend fun load(url: String): LoadResponse? {
        val doc    = app.get(url, headers = mapOf("Referer" to mainUrl)).document
        val title  = doc.selectFirst(".sheader .data h1, h1.entry-title, .data h1")?.text()?.trim() ?: return null
        val poster = doc.selectFirst(".poster img, .sheader .poster img")?.let { img ->
            listOf("data-src","data-lazy-src","data-lazy","data-cfsrc","src")
                .map { img.attr(it) }.firstOrNull { it.isNotBlank() && it.startsWith("http") }
        }
        val plot   = doc.selectFirst(".wp-content p, .description p")?.text()?.trim()
        val year   = doc.selectFirst(".date, [itemprop=dateCreated]")?.text()
            ?.filter { it.isDigit() }?.let {
                if (it.length >= 4) it.substring(0, 4).toIntOrNull() else null
            }
        val genres = doc.select(".sgeneros a").map { it.text() }
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

        // 7. Process all collected targets (including base64 decoding)
        targets.distinct().forEach { raw ->
            val cleanedRaw = raw.trim()
            if (cleanedRaw.isBlank()) return@forEach

            // Attempt base64 decoding (gunakan filter Kotlin untuk buang whitespace tanpa regex)
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
                if (!cleanUrlEscaped.contains("googletagmanager") && !cleanUrlEscaped.contains("facebook") && 
                    !cleanUrlEscaped.contains("googleads") && !cleanUrlEscaped.contains("analytics") && 
                    !cleanUrlEscaped.contains("histats") && !cleanUrlEscaped.contains("doubleclick") &&
                    !cleanUrlEscaped.contains("adskeeper")) {
                    try {
                        loadExtractor(cleanUrlEscaped, data, subtitleCallback, callback)
                    } catch (_: Exception) {}
                }
            }
        }

        return true
    }
}
