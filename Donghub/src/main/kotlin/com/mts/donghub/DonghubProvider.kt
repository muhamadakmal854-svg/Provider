package com.mts.donghub

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import java.security.MessageDigest

class DonghubProvider : MainAPI() {

    override var mainUrl        = "https://donghub.vip"
    override var name           = "Donghub"
    override var lang           = "id"
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
        return doc.select(".listupd .bsx, .listupd .bs, .bsx, .bs, article.bs, .item, article, .animpost, article.animpost, .animepost, article.animepost, article.item, .film-poster, .item-anime, .epbox, .out-thumb, .milist, .post-item, .hentry").mapNotNull {
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
            if (epUrl.isNotBlank()) {
                val epNum = Regex("(?i)episode\\s*(\\d+)").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("(\\d+)").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()
                newEpisode(epUrl) {
                    this.name = epTitle
                    this.episode = epNum
                }
            } else null
        }
        val sortedEps = if (eps.any { it.episode != null }) {
            eps.sortedBy { it.episode ?: 9999 }
        } else {
            eps.reversed()
        }
        val isTvSeries = sortedEps.size > 1 || url.contains("/series/") || url.contains("/tvshows/") || url.contains("/tv/") || url.contains("/season/")
        return if (isTvSeries && sortedEps.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, sortedEps) {
                this.posterUrl = poster; this.plot = plot; this.tags = genres
            }
        } else {
            val movieUrl = sortedEps.firstOrNull()?.data ?: url
            newMovieLoadResponse(title, movieUrl, TvType.Movie, movieUrl) {
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
        parentCallback: (ExtractorLink) -> Unit
    ): Boolean {
        val isKlikxxi = this.name.contains("klikxxi", true) || this::class.java.simpleName.contains("klikxxi", true)
        val isStreamWish = false // Kept for unit test compatibility

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

        // Engine 4: Dynamic Player Detector Engine
        fun detectPlayerType(url: String): String {
            val u = url.lowercase()
            return when {
                listOf("streamwish", "wish", "hglink", "hgcloud", "gendeng", "fkupon", "desacinta", "layarotaku", "layarwibu", "nekonime", "layarecchi", "subsource", "doimg", "anchurl", "certaker", "listeamed", "bigwarp", "cloudatacdn", "push-sdk", "gradehg", "hgplus", "streamplay", "awish", "wishembed", "vikingfile").any { u.contains(it) } -> "streamwish"
                listOf("dood", "dsvplay", "doodcdn", "vide0", "ds2play", "ds2video", "doodstream", "doodla").any { u.contains(it) } -> "dood"
                u.contains("voe.sx") || u.contains("voe") -> "voe"
                u.contains("streamtape") -> "streamtape"
                u.contains("filemoon") -> "filemoon"
                u.contains("mp4upload") -> "mp4upload"
                listOf("abyssplayer.com", "abyss.to", "abysscdn.com", "iamcdn.net", "sssrr").any { u.contains(it) } -> "abyss"
                u.contains("gofile.io/d/") -> "gofile"
                u.contains(".m3u8") || u.contains("/hls/") -> "m3u8"
                u.contains(".mp4") -> "mp4"
                else -> "unknown"
            }
        }

        // Engine 5: Smart Link Validator Engine
        suspend fun validateAndEmitLink(link: ExtractorLink): Boolean {
            val url = link.url
            try {
                val headersMap = mutableMapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Range" to "bytes=0-1024"
                )
                if (link.referer.isNotEmpty()) {
                    headersMap["Referer"] = link.referer
                }
                headersMap.putAll(link.headers)

                val res = app.get(
                    url = url,
                    headers = headersMap,
                    verify = false,
                    timeout = 8
                )

                if (res.isSuccessful) {
                    val finalUrl = res.url
                    val contentType = res.headers["Content-Type"]?.lowercase() ?: ""
                    val isPlayable = contentType.contains("video") || 
                                     contentType.contains("mpegurl") || 
                                     contentType.contains("application/x-mpegurl") || 
                                     contentType.contains("application/octet-stream") ||
                                     finalUrl.contains(".m3u8") || 
                                     finalUrl.contains(".mp4")

                    if (isPlayable) {
                        parentCallback(
                            newExtractorLink(
                                source = link.source,
                                name = link.name,
                                url = finalUrl,
                                type = if (finalUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                this.referer = link.referer
                                this.quality = link.quality
                                this.headers = link.headers
                            }
                        )
                        return true
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SmartLinkValidator", "Validation failed for ${url}: ${e.message}")
            }
            
            if (!isKlikxxi) {
                val isDirectFormat = url.contains(".m3u8") || url.contains(".mp4") || url.contains("/hls/")
                if (isDirectFormat) {
                    parentCallback(link)
                    return true
                }
            }
            return false
        }

        // Engine 3: Stream Resolver Engine (Recursive Follower)
        suspend fun resolveAndValidateStream(link: ExtractorLink, depth: Int = 0): Boolean {
            if (depth > 5) return false
            val url = link.url
            try {
                val headersMap = mutableMapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Range" to "bytes=0-1024"
                )
                if (link.referer.isNotEmpty()) {
                    headersMap["Referer"] = link.referer
                }
                headersMap.putAll(link.headers)

                val res = app.get(
                    url = url,
                    headers = headersMap,
                    verify = false,
                    timeout = 8
                )

                if (res.isSuccessful) {
                    val finalUrl = res.url
                    val contentType = res.headers["Content-Type"]?.lowercase() ?: ""
                    val isPlayable = contentType.contains("video") || 
                                     contentType.contains("mpegurl") || 
                                     contentType.contains("application/x-mpegurl") || 
                                     contentType.contains("application/octet-stream") ||
                                     finalUrl.contains(".m3u8") || 
                                     finalUrl.contains(".mp4")

                    if (isPlayable) {
                        parentCallback(
                            newExtractorLink(
                                source = link.source,
                                name = link.name,
                                url = finalUrl,
                                type = if (finalUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                this.referer = link.referer
                                this.quality = link.quality
                                this.headers = link.headers
                            }
                        )
                        return true
                    } else {
                        val doc = res.document
                        val subSource = doc.selectFirst("source[src], video source[src], video[src]")?.attr("src")
                            ?: doc.selectFirst("iframe[src]")?.attr("src")
                        if (!subSource.isNullOrBlank()) {
                            val resolvedUrl = if (subSource.startsWith("http")) subSource else {
                                val u = java.net.URL(finalUrl)
                                if (subSource.startsWith("/")) "${u.protocol}://${u.host}$subSource" else "${finalUrl.substringBeforeLast("/")}/$subSource"
                            }
                            val nextLink = newExtractorLink(
                                source = link.source,
                                name = link.name,
                                url = resolvedUrl,
                                type = if (resolvedUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                this.referer = finalUrl
                                this.quality = link.quality
                                this.headers = link.headers
                            }
                            return resolveAndValidateStream(nextLink, depth + 1)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("StreamResolver", "Resolution failed for ${url}: ${e.message}")
            }
            return false
        }

        // Intercepting callback wrapper to validate/resolve all generated links
        val callback: (ExtractorLink) -> Unit = { link ->
            kotlinx.coroutines.runBlocking {
                val playerType = detectPlayerType(link.url)
                if (playerType == "unknown") {
                    resolveAndValidateStream(link)
                } else {
                    validateAndEmitLink(link)
                }
            }
        }

        val doc = app.get(data, headers = mapOf("Referer" to mainUrl)).document

        // Engine 1: Standard Scraper
        fun runStandardEngine(document: org.jsoup.nodes.Document): List<String> {
            val list = mutableListOf<String>()
            
            document.select("source[src], video source[src], video[src]").forEach { el ->
                val src = el.attr("src").trim()
                val finalUrl = fixUrl(src)
                if (finalUrl.isNotEmpty()) list.add(finalUrl)
            }

            document.select("iframe[src], iframe[data-src], iframe[data-litespeed-src], iframe[data-lazy-src], iframe.metaframe").forEach { iframe ->
                val src = iframe.attr("src")
                    .ifEmpty { iframe.attr("data-src") }
                    .ifEmpty { iframe.attr("data-litespeed-src") }
                    .ifEmpty { iframe.attr("data-lazy-src") }
                    .trim()
                val finalUrl = fixUrl(src)
                if (finalUrl.isNotEmpty()) list.add(finalUrl)
            }

            document.select("select option, .mirror option, .server option, select.mirror option, select.server option, .mobius option").forEach { el ->
                listOf("value", "data-src", "data-link", "data-embed", "data-video", "data-url", "data-id").forEach { attr ->
                    val v = el.attr(attr).trim()
                    val finalUrl = fixUrl(v)
                    if (finalUrl.isNotEmpty()) list.add(finalUrl)
                }
            }

            document.select("a, button, li, div, span, .opt-sp, .opt-single, .mirror-item, div#downloadb li, div.download li").forEach { el ->
                val href = el.attr("href").trim()
                if (href.isNotBlank() && !href.startsWith("#") && !href.contains("javascript", true)) {
                    val finalUrl = fixUrl(href)
                    if (finalUrl.isNotEmpty()) list.add(finalUrl)
                }
                listOf("data-src", "data-link", "data-embed", "data-video", "data-id", "data-url", "data-content").forEach { attr ->
                    val v = el.attr(attr).trim()
                    val finalUrl = fixUrl(v)
                    if (finalUrl.isNotEmpty() && !v.contains("data:image")) {
                        list.add(finalUrl)
                    }
                }
            }

            val ajaxBtns = document.select("[data-post][data-nume], ul#playeroptionsul > li, li.zetaflix_player_option, .mirror-item")
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
                                list.add(cleanUrl)
                                break
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            document.select("script").forEach { script ->
                val content = script.data()
                if (content.isNotBlank()) {
                    Regex("""https?://[a-zA-Z0-9.\-_]+/[a-zA-Z0-9.\-_\?&=\/~]+""").findAll(content).forEach { match ->
                        val url = match.value
                        if (!url.contains("google") && !url.contains("facebook") && !url.contains("analytics")) {
                            val finalUrl = fixUrl(url)
                            if (finalUrl.isNotEmpty()) list.add(finalUrl)
                        }
                    }
                }
            }

            return list
        }

        // Engine 2: Fallback Scraper
        fun runFallbackEngine(htmlContent: String): List<String> {
            val list = mutableListOf<String>()
            
            val commentsRegex = Regex("<!--(.*?)-->", RegexOption.DOT_MATCHES_ALL)
            commentsRegex.findAll(htmlContent).forEach { match ->
                val commentContent = match.groupValues[1]
                Regex("""(?:src|href)=["']([^"']+)["']""").findAll(commentContent).forEach { subMatch ->
                    val url = fixUrl(subMatch.groupValues[1])
                    if (url.isNotEmpty()) list.add(url)
                }
                Regex("""https?://[a-zA-Z0-9.\\-_]+/[a-zA-Z0-9.\\-_\\?&=\\/~]+""").findAll(commentContent).forEach { subMatch ->
                    val url = fixUrl(subMatch.value)
                    if (url.isNotEmpty() && !url.contains("google") && !url.contains("facebook")) {
                        list.add(url)
                    }
                }
            }

            Regex("""https?://[a-zA-Z0-9.\\-_]+/[a-zA-Z0-9.\\-_\\?&=\\/~]+\\.(?:m3u8|mp4)[a-zA-Z0-9.\\-_\\?&=\\/~]*""").findAll(htmlContent).forEach { match ->
                val url = fixUrl(match.value)
                if (url.isNotEmpty() && !url.contains("google") && !url.contains("facebook")) {
                    list.add(url)
                }
            }

            val hosterKeywords = listOf("streamwish", "dood", "voe.sx", "streamtape", "filemoon", "mp4upload", "gofile.io", "abyssplayer")
            Regex("""https?://[a-zA-Z0-9.\\-_]+/[a-zA-Z0-9.\\-_\\?&=\\/~]+""").findAll(htmlContent).forEach { match ->
                val url = match.value
                if (hosterKeywords.any { url.contains(it, true) }) {
                    val clean = fixUrl(url)
                    if (clean.isNotEmpty()) list.add(clean)
                }
            }

            return list
        }

        var targets = runStandardEngine(doc)
        if (targets.isEmpty()) {
            targets = runFallbackEngine(doc.outerHtml())
        }

        // Engine Selection & Processing Layer
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
                    decodedUrl = fixUrl(src)
                }
            } catch (_: Exception) {}

            val finalUrl = if (decodedUrl.isNotEmpty()) decodedUrl else cleanedRaw
            if (finalUrl.startsWith("http") || finalUrl.startsWith("//")) {
                var cleanUrlEscaped = (if (finalUrl.startsWith("//")) "https:$finalUrl" else finalUrl).replace(92.toChar().toString(), "")
                if (cleanUrlEscaped.contains("/f/") || cleanUrlEscaped.contains("/d/")) {
                    val isWishOrDood = listOf("streamwish", "wish", "hglink", "hgcloud", "gendeng", "fkupon", "desacinta", "layarotaku", "layarwibu", "nekonime", "layarecchi", "subsource", "doimg", "anchurl", "certaker", "listeamed", "bigwarp", "cloudatacdn", "push-sdk", "gradehg", "hgplus", "streamplay", "awish", "wishembed", "vikingfile", "dood", "dsvplay", "doodcdn", "vide0", "ds2play", "ds2video", "doodstream", "doodla").any { cleanUrlEscaped.contains(it, true) }
                    if (isWishOrDood) {
                        cleanUrlEscaped = cleanUrlEscaped
                            .replace("/f/", "/e/")
                            .replace("/d/", "/e/")
                    }
                }
                
                val playerType = detectPlayerType(cleanUrlEscaped)

                when (playerType) {
                    "gofile" -> {
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
                    }
                    "m3u8", "mp4" -> {
                        try {
                            val isM3u = playerType == "m3u8"
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
                    }
                    "abyss" -> {
                        try {
                            AbyssExtractor().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "AbyssExtractor failed: ${e.message}")
                        }
                    }
                    "streamwish" -> {
                        try {
                            com.lagradost.cloudstream3.extractors.StreamWishExtractor().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "StreamWish extraction failed for $cleanUrlEscaped: ${e.message}")
                        }
                    }
                    "dood" -> {
                        try {
                            com.lagradost.cloudstream3.extractors.DoodLaExtractor().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "DoodLaExtractor extraction failed for $cleanUrlEscaped: ${e.message}")
                        }
                    }
                    "voe" -> {
                        try {
                            com.lagradost.cloudstream3.extractors.Voe().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "Voe extraction failed: ${e.message}")
                        }
                    }
                    "streamtape" -> {
                        try {
                            com.lagradost.cloudstream3.extractors.StreamTape().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "StreamTape extraction failed: ${e.message}")
                        }
                    }
                    "filemoon" -> {
                        try {
                            com.lagradost.cloudstream3.extractors.FileMoon().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "FileMoon extraction failed: ${e.message}")
                        }
                    }
                    "mp4upload" -> {
                        try {
                            com.lagradost.cloudstream3.extractors.Mp4Upload().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "Mp4Upload extraction failed: ${e.message}")
                        }
                    }
                    else -> {
                        val isSameDomain = try {
                            val host1 = java.net.URL(cleanUrlEscaped).host.replace("www.", "")
                            val host2 = java.net.URL(mainUrl).host.replace("www.", "")
                            host1 == host2
                        } catch (_: Exception) { false }

                        if (isSameDomain && cleanUrlEscaped != data) {
                            try {
                                val subDoc = app.get(cleanUrlEscaped, referer = data).document
                                val subTargets = runStandardEngine(subDoc)
                                subTargets.forEach { subTarget ->
                                    if (subTarget != cleanUrlEscaped) {
                                        val subClean = subTarget.replace(92.toChar().toString(), "")
                                        val subType = detectPlayerType(subClean)
                                        if (subType == "m3u8" || subType == "mp4") {
                                            val isM3u = subType == "m3u8"
                                            callback(
                                                newExtractorLink(
                                                    source = "Direct Stream",
                                                    name = "Direct Stream",
                                                    url = subClean,
                                                    type = if (isM3u) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                                ) {
                                                    this.referer = cleanUrlEscaped
                                                }
                                            )
                                        } else if (subType != "unknown") {
                                            when (subType) {
                                                "abyss" -> AbyssExtractor().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                                "streamwish" -> com.lagradost.cloudstream3.extractors.StreamWishExtractor().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                                "dood" -> com.lagradost.cloudstream3.extractors.DoodLaExtractor().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                                "voe" -> com.lagradost.cloudstream3.extractors.Voe().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                                "streamtape" -> com.lagradost.cloudstream3.extractors.StreamTape().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                                "filemoon" -> com.lagradost.cloudstream3.extractors.FileMoon().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                                "mp4upload" -> com.lagradost.cloudstream3.extractors.Mp4Upload().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                            }
                                        } else {
                                            try {
                                                loadExtractor(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }

                        if (!cleanUrlEscaped.contains("googletagmanager") && !cleanUrlEscaped.contains("facebook") && 
                            !cleanUrlEscaped.contains("googleads") && !cleanUrlEscaped.contains("analytics") && 
                            !cleanUrlEscaped.contains("histats") && !cleanUrlEscaped.contains("doubleclick") &&
                            !cleanUrlEscaped.contains("adskeeper")) {
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
