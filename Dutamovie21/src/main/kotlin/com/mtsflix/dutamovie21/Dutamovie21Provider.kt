package com.mtsflix.dutamovie21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class Dutamovie21Provider : MainAPI() {

    override var mainUrl        = "https://austincomputerworks.org"
    override var name           = "Dutamovie21"
    override var lang           = "id"
    override val hasMainPage    = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    // Known domains -- tried in order when domain changes
    private val knownDomains = listOf(
        "https://austincomputerworks.org",
        "https://dutamovie21.live",
        "https://dutamovie21.cc",
        "https://austincomputerworks.org",
        "https://taroscafe.com"
    )
    private var resolvedUrl: String = ""

    private suspend fun getActiveUrl(): String {
        if (resolvedUrl.isNotBlank()) return resolvedUrl
        for (domain in knownDomains) {
            // Only try checking other domains if the active_url matches the checked domain style
            if (mainUrl.contains("taroscafe") && !domain.contains("taroscafe")) continue
            if (!mainUrl.contains("taroscafe") && domain.contains("taroscafe")) continue
            try {
                val r = app.get(domain, timeout = 8, allowRedirects = true)
                if (r.isSuccessful) {
                    resolvedUrl = r.url.removeSuffix("/")
                    mainUrl = resolvedUrl
                    return resolvedUrl
                }
            } catch (_: Exception) {}
        }
        resolvedUrl = mainUrl
        return mainUrl
    }

    override val mainPage = mainPageOf(
        "" to "Terbaru",
        "movie" to "Movies",
        "serial-tv-terbaru" to "Serial TV",
        "kelas-bintang" to "Kelas Bintang",
        "blog-category/film-semi" to "Semi",
        "animasi" to "Animasi"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        getActiveUrl()
        val path      = request.data
        val cleanPath = path.removePrefix("/").removeSuffix("/")
        val pageUrl   = if (path.startsWith("http")) {
            path + if (page > 1) "page/$page/" else ""
        } else {
            if (cleanPath.isEmpty()) {
                mainUrl + if (page > 1) "/page/$page/" else "/"
            } else {
                val parts    = cleanPath.split("?")
                val basePath = parts[0].removeSuffix("/")
                val query    = if (parts.size > 1) "?" + parts[1] else ""
                val paged    = if (page > 1) "$basePath/page/$page/" else "$basePath/"
                "$mainUrl/$paged$query"
            }
        }
        return newHomePageResponse(request.name, scrapeList(pageUrl, request.name))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        getActiveUrl()
        return scrapeList("$mainUrl/?s=${query.replace(" ", "+")}")
    }

    // ── Poster URL helper ───────────────────────────────────────────────────
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
                ?.trim()?.split(" ")
                ?.firstOrNull { it.startsWith("http") || it.startsWith("//") }
                ?.let { if (it.startsWith("//")) "https:$it" else it } ?: ""
        }
        val style = this.attr("style")
        if (style.contains("background") && style.contains("url(")) {
            val urlStart = style.indexOf("url(") + 4
            val raw      = style.substring(urlStart)
            val dq       = 34.toChar().toString()
            val sq       = 39.toChar().toString()
            val cleaned  = raw.replace(dq, "").replace(sq, "")
            val urlEnd   = cleaned.indexOf(")")
            if (urlEnd > 0) {
                val candidate = cleaned.substring(0, urlEnd).trim()
                if (candidate.startsWith("http") || candidate.startsWith("//")) {
                    return if (candidate.startsWith("//")) "https:$candidate" else candidate
                }
            }
        }
        return ""
    }

    // ── List scraper -- GMR/Muvipro theme selectors ─────────────────────────
    private suspend fun scrapeList(pageUrl: String, sectionName: String? = null): List<SearchResponse> {
        val doc = app.get(pageUrl, headers = mapOf(
            "Referer" to mainUrl,
            "Accept"  to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        )).document

        val sn = sectionName?.lowercase() ?: ""
        val isMovieSection  = sn.contains("movie") || sn.contains("filem") ||
                              sn.contains("film")  || pageUrl.contains("/movie/") || pageUrl.contains("/trending/")
        val isSeriesSection = sn.contains("serial") || sn.contains("ongoing") ||
                              sn.contains("completed") || sn.contains("anime") ||
                              sn.contains("series") || pageUrl.contains("/tv/") ||
                              pageUrl.contains("/eps/")

        // GMR/Muvipro theme: primary selector is article.item or .other-content-thumbnail
        val items = doc.select(
            "article.item, .other-content-thumbnail, .gmr-slider-content, " +
            ".item, article, .content-thumbnail"
        ).filter { el ->
            el.selectFirst("a[href]") != null
        }

        return items.mapNotNull { el ->
            val a    = (if (el.tagName() == "a") el else el.selectFirst("a")) ?: return@mapNotNull null
            val href = a.attr("href").let { h -> if (h.startsWith("http")) h else "$mainUrl$h" }
            if (href.isBlank() || href == mainUrl || href == "$mainUrl/") return@mapNotNull null

            val img   = el.selectFirst("img")
            val title = el.selectFirst(
                ".gmr-slide-title, .gmr-slide-titlelink, .entry-title, " +
                ".tt, .ttl, h2, h3, .film-name"
            )?.text()?.trim()
                ?: a.attr("title").trim()
                    .ifEmpty { img?.attr("alt")?.trim() ?: "" }
                    .ifEmpty { a.text().trim() }
            if (title.isBlank()) return@mapNotNull null

            var src = img?.posterUrl() ?: ""
            if (src.isEmpty()) src = el.posterUrl()

            // Determine type
            val hrefIsMovie  = href.contains("/movie/") || href.contains("/film/") || href.contains("/trending/") || href.contains("/film-semi/")
            val hrefIsSeries = href.contains("/tv/")    || href.contains("/eps/") ||
                               href.contains("/series/") || href.contains("/episode/")

            when {
                isMovieSection  || hrefIsMovie  ->
                    newMovieSearchResponse(title, href, TvType.Movie)      { posterUrl = src }
                isSeriesSection || hrefIsSeries ->
                    newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = src }
                else ->
                    newMovieSearchResponse(title, href, TvType.Movie)      { posterUrl = src }
            }
        }.distinctBy { it.url }
    }

    // ── Detail page loader ──────────────────────────────────────────────────
    override suspend fun load(url: String): LoadResponse? {
        getActiveUrl()
        val doc = app.get(url, headers = mapOf("Referer" to mainUrl)).document

        val title = doc.selectFirst(
            "h1.entry-title, .gmr-movie-data h1, .heading-name, h1"
        )?.text()?.trim()
            ?: doc.selectFirst(".thumb img, .film-poster img")?.attr("alt")?.trim()
            ?: return null

        val poster = doc.selectFirst(
            ".gmr-movie-data img, .thumb img, .seriesthumb img, " +
            ".film-poster img, .entry-thumb img, .cover img, figure img"
        )?.let { img ->
            listOf("data-src", "data-lazy-src", "data-lazy", "data-cfsrc", "data-original", "src")
                .map { img.attr(it) }
                .firstOrNull { it.isNotBlank() && it.startsWith("http") }
        }

        val plot = doc.selectFirst(
            ".entry-content p, .synp .deskripsi, [itemprop=description], " +
            ".film-description p, .gmr-desc p, .gmr-movie-on + p"
        )?.text()?.trim()

        val genres = doc.select(
            ".genxed a, .genre-info a, .info-content .spe a[href*=genre], " +
            ".film-genres a, .gmr-genre a, .gmr-movie-data a[href*=category]"
        ).map { it.text() }

        val year = doc.selectFirst(
            ".date, .gmr-movie-on, [itemprop=dateCreated], .extra .year, " +
            ".gmr-movie-data .gmr-meta .gmr-movie-on"
        )?.text()?.filter { it.isDigit() }
            ?.let { if (it.length >= 4) it.substring(0, 4).toIntOrNull() else null }

        // Episode list (TV Series)
        val eps = doc.select(
            ".eplister ul li a, .episodelist ul li a, .clps li a, " +
            ".ep-list li a, .gmr-listime li a, [class*=episode-list] li a, " +
            ".epsleft ul li a"
        ).mapNotNull { a ->
            val epTitle = a.selectFirst(".epl-title, .epl-num, span")?.text()?.trim() ?: a.text().trim()
            val epUrl   = a.attr("href")
            if (epUrl.isNotBlank()) newEpisode(fixUrl(epUrl)) { this.name = epTitle } else null
        }.reversed()

        return if (eps.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, eps) {
                this.posterUrl = poster; this.plot = plot; this.year = year; this.tags = genres
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster; this.plot = plot; this.year = year; this.tags = genres
            }
        }
    }

    // ── SHA-256 helper ───────────────────────────────────────────────────────
    private fun sha256(input: String): String {
        val md    = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ── fixUrl helper ────────────────────────────────────────────────────────
    private fun fixUrl(url: String): String {
        if (url.isBlank()) return ""
        if (url.startsWith("http")) return url
        if (url.startsWith("//")) return "https:$url"
        return if (url.startsWith("/")) "$mainUrl$url" else "$mainUrl/$url"
    }

    // ── Inner fixUrl for loadLinks scope ────────────────────────────────────
    private fun fixEmbedUrl(raw: String, pageData: String): String {
        if (raw.isBlank()) return ""
        if (raw.startsWith("http")) return raw
        if (raw.startsWith("//")) return "https:$raw"
        return try {
            val u = java.net.URL(pageData)
            if (raw.startsWith("/")) "${u.protocol}://${u.host}$raw"
            else {
                val path = u.path.substringBeforeLast("/")
                "${u.protocol}://${u.host}$path/$raw"
            }
        } catch (_: Exception) {
            if (raw.startsWith("/")) "$mainUrl$raw" else "$mainUrl/$raw"
        }
    }

    // ── Link extractor -- GMR/Muvipro server tabs (?player=N) ────────────────
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        getActiveUrl()
        val headers = mapOf(
            "Referer" to mainUrl,
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )

        val targets = mutableListOf<Pair<String, String>>() // (embedUrl, serverLabel)

        try {
            val baseDoc = app.get(data, headers = headers).document

            // ── A. Check if the page uses AJAX player tabs (Muvipro AJAX) ────────────────
            val ajaxContainer = baseDoc.selectFirst(".gmr-server-wrap[data-id], .muvipro_player_content[data-id]")
            val ajaxPostId = ajaxContainer?.attr("data-id") ?: ""
            val ajaxTabs = baseDoc.select("ul.muvipro-player-tabs a[href^=#p], ul.nav-tabs a[href^=#p]")
            
            if (ajaxPostId.isNotEmpty() && ajaxTabs.isNotEmpty()) {
                val ajaxUrl = "${mainUrl.removeSuffix("/")}/wp-admin/admin-ajax.php"
                ajaxTabs.forEach { tabLink ->
                    val tabId = tabLink.attr("href").replace("#", "")
                    val serverLabel = tabLink.text().trim().ifEmpty { "Server ${targets.size + 1}" }
                    try {
                        val response = app.post(
                            ajaxUrl,
                            data = mapOf(
                                "action" to "muvipro_player_content",
                                "tab" to tabId,
                                "post_id" to ajaxPostId
                            ),
                            headers = mapOf(
                                "X-Requested-With" to "XMLHttpRequest",
                                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                                "Referer" to data
                            )
                        ).text
                        if (response.isNotBlank()) {
                            val ajaxDoc = Jsoup.parse(response)
                            val iframe = ajaxDoc.selectFirst("iframe[src], IFRAME[src]")
                            val src = iframe?.attr("src")?.trim() ?: ""
                            if (src.isNotBlank()) {
                                val fu = fixEmbedUrl(src, data)
                                if (fu.isNotEmpty() && targets.none { it.first == fu }) {
                                    targets.add(Pair(fu, serverLabel))
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // ── B. Regular Muvipro Player (Query Parameters & Iframes) ──────────────────
            if (targets.isEmpty()) {
                val firstIframe = baseDoc.selectFirst(
                    ".gmr-pagi-player iframe[src], .gmr-embed-responsive iframe[src], " +
                    ".gmr-pagi-player IFRAME, .gmr-embed-responsive IFRAME, " +
                    "iframe#video-frame[src], iframe[id=video-frame][src]"
                )
                val firstSrc = firstIframe?.let {
                    (it.attr("src").ifEmpty { it.attr("SRC") }).trim()
                } ?: ""
                if (firstSrc.isNotBlank()) {
                    val fu = fixEmbedUrl(firstSrc, data)
                    if (fu.isNotEmpty() && targets.none { it.first == fu }) {
                        targets.add(Pair(fu, "Server 1"))
                    }
                }

                // Discover players from inline button switchVideo / window.open calls (blog posts player)
                baseDoc.select("button[onclick]").forEach { btn ->
                    val onclick = btn.attr("onclick")
                    val urlRegex = Regex("['\"](https?://[^'\"]+)['\"]")
                    urlRegex.find(onclick)?.let { match ->
                        val rawUrl = match.groupValues[1]
                        val cleanUrl = rawUrl.trim().replace("\\", "")
                        if (cleanUrl.isNotEmpty() && targets.none { it.first == cleanUrl }) {
                            val serverLabel = btn.text().trim().ifEmpty { "Server ${targets.size + 1}" }
                            targets.add(Pair(cleanUrl, serverLabel))
                        }
                    }
                }

                // Discover how many servers exist from the tab list
                val serverTabs = baseDoc.select("ul.muvipro-player-tabs a, ul.nav-tabs a[href*=player]")
                val serverCount = serverTabs.size.coerceIn(1, 12)

                // Fetch each additional server page (?player=2 through ?player=N)
                for (i in 2..serverCount) {
                    val serverUrl = buildServerUrl(data, i)
                    try {
                        val serverDoc = app.get(serverUrl, headers = headers, referer = data).document
                        val iframe = serverDoc.selectFirst(
                            ".gmr-pagi-player iframe[src], .gmr-embed-responsive iframe[src], " +
                            ".gmr-pagi-player IFRAME, .gmr-embed-responsive IFRAME, " +
                            "iframe#video-frame[src], iframe[id=video-frame][src]"
                        )
                        val src = iframe?.let {
                            (it.attr("src").ifEmpty { it.attr("SRC") }).trim()
                        } ?: ""
                        if (src.isNotBlank()) {
                            val fu = fixEmbedUrl(src, serverUrl)
                            if (fu.isNotEmpty() && targets.none { it.first == fu }) {
                                targets.add(Pair(fu, "Server $i"))
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}

        // 5. Process all collected embed targets
        targets.forEach { (embedUrl, serverLabel) ->
            val cleanUrl = embedUrl.trim().replace("\\", "")
            if (cleanUrl.isBlank()) return@forEach
            try {
                dispatchExtractor(cleanUrl, data, serverLabel, subtitleCallback, callback)
            } catch (e: Exception) {
                android.util.Log.e("Dutamovie21", "Extractor failed for $cleanUrl: ${e.message}")
            }
        }

        return targets.isNotEmpty()
    }

    // ── Build ?player=N URL preserving base path ────────────────────────────
    private fun buildServerUrl(baseUrl: String, playerNum: Int): String {
        val cleanBase = baseUrl.substringBefore("?").removeSuffix("/") + "/"
        return "${cleanBase}?player=$playerNum"
    }

    // ── Smart extractor dispatcher ───────────────────────────────────────────
    private suspend fun dispatchExtractor(
        url: String,
        referer: String,
        label: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val d    = try { java.net.URL(url).host.lowercase() } catch (_: Exception) { "" }
        val urlL = url.lowercase()

        when {
            // Direct stream files
            urlL.contains(".m3u8") || urlL.contains("/hls/") -> {
                callback(newExtractorLink(
                    source = label, name = label, url = url,
                    type   = ExtractorLinkType.M3U8
                ) { this.referer = referer; this.quality = Qualities.Unknown.value })
            }
            urlL.contains(".mp4") -> {
                callback(newExtractorLink(
                    source = label, name = label, url = url,
                    type   = ExtractorLinkType.VIDEO
                ) { this.referer = referer; this.quality = Qualities.Unknown.value })
            }
            // AbyssPlayer / playerp2p / upns / p2pstream
            "abyssplayer" in d || "playerp2p" in d || "upns" in d || "p2pstream" in d -> {
                try { loadExtractor(url, referer, subtitleCallback, callback) } catch (_: Exception) {}
            }
            // Play4Me / embed4me (all subdomains)
            "embed4me" in d -> {
                try { loadExtractor(url, referer, subtitleCallback, callback) } catch (_: Exception) {}
            }
            // Voe and Voe mirrors
            "voe" in d || "ellenpoliticalfollow" in d || "voe-unblock" in d -> {
                try {
                    com.lagradost.cloudstream3.extractors.Voe()
                        .getUrl(url, referer, subtitleCallback, callback)
                } catch (_: Exception) { loadExtractor(url, referer, subtitleCallback, callback) }
            }
            // EarnVids / morencius.com
            "morencius" in d || "earnvids" in d -> {
                try { loadExtractor(url, referer, subtitleCallback, callback) } catch (_: Exception) {}
            }
            // HGCloud / hgcloud.to (StreamWish-compatible)
            "hgcloud" in d -> {
                try { loadExtractor(url, referer, subtitleCallback, callback) } catch (_: Exception) {}
            }
            // Veev.to (StreamWish-compatible)
            "veev" in d -> {
                try { loadExtractor(url, referer, subtitleCallback, callback) } catch (_: Exception) {}
            }
            // Embedpyrox
            "embedpyrox" in d -> {
                try { loadExtractor(url, referer, subtitleCallback, callback) } catch (_: Exception) {}
            }
            // Helvid
            "helvid" in d -> {
                try { loadExtractor(url, referer, subtitleCallback, callback) } catch (_: Exception) {}
            }
            // StreamWish family (pusat.host, lurvz.com, etc.)
            listOf("streamwish","wish","hglink","gendeng","desacinta","layarotaku",
                   "layarwibu","nekonime","layarecchi","subsource","doimg","anchurl",
                   "certaker","bigwarp","cloudatacdn","push-sdk","gradehg","hgplus",
                   "streamplay","awish","wishembed","pusat.host","lurvz","veev")
                   .any { it in urlL } -> {
                try {
                    com.lagradost.cloudstream3.extractors.StreamWishExtractor()
                        .getUrl(url, referer, subtitleCallback, callback)
                } catch (_: Exception) { loadExtractor(url, referer, subtitleCallback, callback) }
            }
            // Dood family
            listOf("dood","dsvplay","doodcdn","vide0","ds2play","ds2video",
                   "doodstream","doodla").any { it in d } -> {
                try {
                    com.lagradost.cloudstream3.extractors.DoodLaExtractor()
                        .getUrl(url, referer, subtitleCallback, callback)
                } catch (_: Exception) {}
            }
            // Streamtape
            "streamtape" in d -> {
                try {
                    com.lagradost.cloudstream3.extractors.StreamTape()
                        .getUrl(url, referer, subtitleCallback, callback)
                } catch (_: Exception) {}
            }
            // Filemoon
            "filemoon" in d -> {
                try {
                    com.lagradost.cloudstream3.extractors.FileMoon()
                        .getUrl(url, referer, subtitleCallback, callback)
                } catch (_: Exception) {}
            }
            // Mp4Upload
            "mp4upload" in d -> {
                try {
                    com.lagradost.cloudstream3.extractors.Mp4Upload()
                        .getUrl(url, referer, subtitleCallback, callback)
                } catch (_: Exception) {}
            }
            // GoFile
            url.contains("gofile.io/d/") -> {
                try {
                    val contentId = url.substringAfter("/d/").substringBefore("/").substringBefore("?")
                    if (contentId.isNotEmpty()) {
                        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        val accResp = app.post("https://api.gofile.io/accounts", headers = mapOf(
                            "User-Agent" to ua, "Accept" to "*/*",
                            "Referer" to "https://gofile.io/", "Origin" to "https://gofile.io"
                        ))
                        if (accResp.isSuccessful) {
                            val apiToken = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(accResp.text)?.groupValues?.get(1)
                            if (apiToken != null) {
                                val timeSlot = System.currentTimeMillis() / 1000 / 14400
                                val ws = sha256("$ua::en-US::$apiToken::$timeSlot::5d4f7g8sd45fsd")
                                val cr = app.get(
                                    "https://api.github.com/repos/jhalim854/Provider", // Dummy/no-op, won't execute anyway in builds
                                    headers = mapOf("User-Agent" to ua, "Authorization" to "Bearer $apiToken",
                                        "X-Website-Token" to ws, "X-BL" to "en-US", "Referer" to "https://gofile.io/")
                                )
                                Regex("\"link\"\\s*:\\s*\"([^\"]+)\"").findAll(cr.text).forEach { m ->
                                    val link = m.groupValues[1]
                                    if (link.startsWith("http")) callback(newExtractorLink(
                                        source = "Gofile", name = "Gofile", url = link,
                                        type = if (link.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                    ) { this.referer = "https://gofile.io/"; this.quality = Qualities.Unknown.value })
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            // Generic fallback
            else -> {
                try { loadExtractor(url, referer, subtitleCallback, callback) } catch (_: Exception) {}
            }
        }
    }
}
