package com.mts.xhaccess

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import java.security.MessageDigest

class XhaccessProvider : MainAPI() {

    override var mainUrl        = "https://xhaccess.com"
    override var name           = "X Videos Porn Videos"
    override var lang           = "ms"
    override val hasMainPage    = true
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
        "" to "Videos",
        "best/weekly" to "Best Videos",
        "shorts" to "Short Videos"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path = request.data
        val cleanPath = path.removePrefix("/").removeSuffix("/")
        val pageUrl = if (path.startsWith("http")) {
            val base = path.substringBefore("?").trimEnd('/')
            val query = path.substringAfter("?", "")
            val paged = if (page > 1) "$base/page/$page/" else "$base/"
            if (query.isNotEmpty()) "$paged?$query" else paged
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
        return scrapeList("$mainUrl/search/${query.trim().replace(" ", "+")}")
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
            val raw = style.substringAfter("url(")
            val cleaned = raw.replace(34.toChar().toString(), "").replace(39.toChar().toString(), "")
            val candidate = cleaned.substringBefore(")").trim()
            if (candidate.startsWith("http") || candidate.startsWith("//")) {
                return if (candidate.startsWith("//")) "https:$candidate" else candidate
            }
        }
        return ""
    }

    private fun xhVideoId(url: String): String {
        return url.substringBefore("?").substringBefore("#").trimEnd('/').substringAfterLast("/")
    }

    private fun isXhVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith(mainUrl.lowercase()) &&
            lower.contains("/videos/") &&
            !lower.contains("/creators/") &&
            !lower.contains("/my/") &&
            !lower.contains("/search/")
    }

    private suspend fun scrapeList(pageUrl: String): List<SearchResponse> {
        val doc = app.get(pageUrl, headers = mapOf(
            "Referer" to mainUrl,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        )).document

        val candidates = doc.select(
            "a[href*=/videos/], .thumb-list__item a[href*=/videos/], " +
            ".video-thumb a[href*=/videos/], .video-thumb--type-video a[href*=/videos/], " +
            ".container-c9dbd a[href*=/videos/]"
        )

        return candidates.mapNotNull { a ->
            val href = fixUrl(a.attr("href"))
            if (!isXhVideoUrl(href)) return@mapNotNull null

            val card = a.closest(".thumb-list__item, .video-thumb, .video-thumb--type-video, .container-c9dbd")
                ?: a.parent()
                ?: a
            val img = card.selectFirst("img") ?: a.selectFirst("img")
            val title = a.attr("title").trim()
                .ifEmpty { card.selectFirst(".title, .video-title, [class*=title], h2, h3")?.text()?.trim() ?: "" }
                .ifEmpty { img?.attr("alt")?.trim() ?: "" }
                .ifEmpty { img?.attr("title")?.trim() ?: "" }
                .ifEmpty { a.text().trim() }
            if (title.isBlank()) return@mapNotNull null

            var poster = img?.posterUrl() ?: ""
            if (poster.isBlank()) poster = card.posterUrl()
            if (poster.isBlank()) {
                card.select("[style*=background], [style*=url]").forEach { el ->
                    val found = el.posterUrl()
                    if (found.isNotBlank()) {
                        poster = found
                        return@forEach
                    }
                }
            }

            newMovieSearchResponse(title, href, TvType.NSFW) {
                posterUrl = poster
            }
        }.distinctBy { xhVideoId(it.url) }
    }
    
    override suspend fun load(url: String): LoadResponse? {
        val doc    = app.get(url, headers = mapOf("Referer" to mainUrl)).document
        val title  = doc.selectFirst("h1, h1.title, .video-title h1, .entry-title")
            ?.text()?.trim() ?: return null
        val poster = doc.selectFirst(".thumb img, .poster img, .video-poster img, img.wp-post-image")
            ?.let { img ->
                listOf("data-src","data-lazy-src","data-lazy","data-cfsrc","src")
                    .map { img.attr(it) }.firstOrNull { it.isNotBlank() && it.startsWith("http") }
            }
        val plot  = doc.selectFirst(".description p, .entry-content p, .video-description p")
            ?.text()?.trim()
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster; this.plot = plot
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
        val queuedTargets = mutableSetOf<String>()
        val emittedSourceUrls = mutableSetOf<String>()
        val callbackWrapper: (ExtractorLink) -> Unit = { link ->
            val key = link.url.substringBefore("#").trim()
            if (key.isNotBlank() && emittedSourceUrls.add(key)) {
                callback(link)
            }
        }

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

        fun isBlockedTarget(url: String): Boolean {
            val lower = url.lowercase()
            return lower.isBlank() ||
                lower.startsWith("javascript:") ||
                lower.startsWith("mailto:") ||
                lower.contains("data:image") ||
                lower.contains("googletagmanager") ||
                lower.contains("googleads") ||
                lower.contains("google-analytics") ||
                lower.contains("analytics") ||
                lower.contains("doubleclick") ||
                lower.contains("facebook.com/tr") ||
                lower.contains("histats") ||
                lower.contains("adskeeper")
        }

        fun normalizeTarget(url: String): String {
            return url.trim()
                .replace("\\/", "/")
                .replace("&amp;", "&")
                .substringBefore("#")
                .trimEnd('/')
        }

        fun queueTarget(rawUrl: String) {
            if (rawUrl.isBlank() || isBlockedTarget(rawUrl)) return
            val fixed = normalizeTarget(fixUrl(rawUrl))
            if (fixed.startsWith("http") && !isBlockedTarget(fixed) && queuedTargets.add(fixed.lowercase())) {
                targets.add(fixed)
            }
        }

        fun decodedRedirectValue(value: String): String {
            val cleaned = value.trim()
            if (cleaned.isBlank()) return ""
            return try {
                val decodedBytes = android.util.Base64.decode(cleaned, android.util.Base64.DEFAULT)
                String(decodedBytes, Charsets.UTF_8)
            } catch (_: Exception) {
                try {
                    java.net.URLDecoder.decode(cleaned, "UTF-8")
                } catch (_: Exception) {
                    cleaned
                }
            }
        }

        suspend fun isPlayableDirectStream(url: String, referer: String): Boolean {
            return try {
                val response = app.get(
                    url,
                    referer = referer,
                    headers = mapOf(
                        "Range" to "bytes=0-1",
                        "Accept" to "*/*"
                    )
                )
                response.isSuccessful
            } catch (_: Exception) {
                false
            }
        }

        fun sourceNameFor(url: String): String {
            val host = try { java.net.URL(url).host.removePrefix("www.") } catch (_: Exception) { "" }
            return when {
                host.contains("streamwish", true) || host.contains("wishembed", true) -> "StreamWish"
                host.contains("dood", true) || host.contains("dsvplay", true) -> "Dood"
                host.contains("voe", true) -> "Voe"
                host.contains("streamtape", true) -> "StreamTape"
                host.contains("filemoon", true) -> "FileMoon"
                host.contains("mp4upload", true) -> "Mp4Upload"
                host.isNotBlank() -> host.substringBefore(".").replaceFirstChar { it.uppercase() }
                else -> "Direct Stream"
            }
        }

        // 1. Direct video/source elements
        doc.select("source[src], video source[src], video[src]").forEach { el ->
            val src = el.attr("src").trim()
            queueTarget(src)
        }

        // 2. Direct iframes (check common attributes and classes)
        doc.select("iframe[src], iframe[data-src], iframe[data-litespeed-src], iframe[data-lazy-src], iframe.metaframe").forEach { iframe ->
            val src = iframe.attr("src")
                .ifEmpty { iframe.attr("data-src") }
                .ifEmpty { iframe.attr("data-litespeed-src") }
                .ifEmpty { iframe.attr("data-lazy-src") }
                .trim()
            queueTarget(src)
        }

        // 3. Option elements / Dropdowns (e.g. Server choices, mirror list)
        doc.select("select option, .mirror option, .server option, select.mirror option, select.server option, .mobius option").forEach { el ->
            listOf("value", "data-src", "data-link", "data-embed", "data-video", "data-url", "data-id").forEach { attr ->
                val v = el.attr(attr).trim()
                queueTarget(v)
            }
        }

        // 4. Clickable elements, links, buttons, lists
        doc.select("a, button, li, div, span, .opt-sp, .opt-single, .mirror-item, div#downloadb li, div.download li").forEach { el ->
            val href = el.attr("href").trim()
            if (href.isNotBlank() && !href.startsWith("#") && !href.contains("javascript", true)) {
                queueTarget(href)
            }
            listOf("data-src", "data-link", "data-embed", "data-video", "data-id", "data-url", "data-content").forEach { attr ->
                val v = el.attr(attr).trim()
                queueTarget(v)
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
                        queueTarget(embedUrl)
                    }
                } catch (_: Exception) {}
            }
        }

        // 5b. MuviPro / GMR AJAX Player loading
        val muviproPlayer = doc.selectFirst(".muvipro_player_content, #muvipro_player_content_id")
        val muviproPostId = muviproPlayer?.attr("data-id") ?: ""
        if (muviproPostId.isNotEmpty()) {
            val muviproTabs = doc.select(".muvipro-player-tabs a, .tab-content-ajax").mapNotNull { 
                val href = it.attr("href").orEmpty()
                if (href.startsWith("#")) {
                    href.substring(1)
                } else {
                    val id = it.attr("id").orEmpty()
                    if (id.isNotEmpty()) id else null
                }
            }.distinct()
            
            muviproTabs.forEach { tab ->
                try {
                    val pageBase = try {
                        val u = java.net.URL(data)
                        "${u.protocol}://${u.host}"
                    } catch (_: Exception) { mainUrl }
                    val response = app.post(
                        url = "$pageBase/wp-admin/admin-ajax.php",
                        data = mapOf(
                            "action" to "muvipro_player_content",
                            "tab" to tab,
                            "post_id" to muviproPostId
                        ),
                        referer = data,
                        headers = mapOf(
                            "X-Requested-With" to "XMLHttpRequest",
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        )
                    )
                    if (response.isSuccessful) {
                        val json = response.text
                        if (json.isNotBlank() && json != "0" && json != "false") {
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
                                queueTarget(embedUrl)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 6. Harvest URLs directly from JSON blobs and <script> tags
        doc.select("script[type=application/ld+json], script").forEach { script ->
            val content = script.data().ifBlank { script.html() }
            if (content.isBlank()) return@forEach
            listOf("contentUrl", "embedUrl", "url", "file", "src").forEach { key ->
                Regex(""""$key"\s*:\s*"([^"]+)"""").findAll(content).forEach { match ->
                    val url = match.groupValues[1].replace("\\/", "/")
                    if (url.startsWith("http") || url.startsWith("//")) {
                        queueTarget(url)
                    }
                }
            }
            Regex("""(?:file|src)\s*[:=]\s*["']([^"']+)["']""").findAll(content).forEach { match ->
                val url = match.groupValues[1].replace("\\/", "/")
                if (url.startsWith("http") || url.startsWith("//")) {
                    queueTarget(url)
                }
            }
        }

        doc.select("script").forEach { script ->
            val content = script.data()
            if (content.isNotBlank()) {
                Regex("""https?://[a-zA-Z0-9.\-_]+/[a-zA-Z0-9.\-_\?&=\/~]+""").findAll(content).forEach { match ->
                    val url = match.value
                    if (!url.contains("google") && !url.contains("facebook") && !url.contains("analytics")) {
                        queueTarget(url)
                    }
                }
            }
        }

        // 7. Process all collected targets (including base64 decoding & fallback routing)
        var targetIndex = 0
        while (targetIndex < targets.size) {
            val raw = targets[targetIndex++]
            val cleanedRaw = raw.trim()
            if (cleanedRaw.isBlank()) continue

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
                                                callbackWrapper(
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
                        if (isPlayableDirectStream(cleanUrlEscaped, data)) {
                            val sourceName = sourceNameFor(cleanUrlEscaped)
                            callbackWrapper(
                                newExtractorLink(
                                    source = sourceName,
                                    name = sourceName,
                                    url = cleanUrlEscaped,
                                    type = if (isM3u) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = data
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                        }
                    } catch (_: Exception) {}
                } else {
                    // Try to unwrap redirect parameters
                    listOf("link", "url", "u", "r", "to", "go", "target", "redirect", "redirect_url", "embed", "source").forEach { param ->
                        try {
                            val regex = Regex("[?&]" + param + "=([^&]+)")
                            val match = regex.find(cleanUrlEscaped)
                            val queryValue = match?.groupValues?.get(1)
                            if (queryValue != null && queryValue.isNotEmpty()) {
                                val decodedParam = decodedRedirectValue(queryValue)
                                val finalDecoded = fixUrl(decodedParam)
                                if (finalDecoded.startsWith("http") && !finalDecoded.contains("google") && !finalDecoded.contains("facebook")) {
                                    queueTarget(finalDecoded)
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
                                subDoc.select("iframe[src], iframe[data-src], iframe[data-litespeed-src], iframe[data-lazy-src], source[src], video[src], [data-embed], [data-link], [data-url], [data-video]").forEach { iframe ->
                                    val iframeSrc = iframe.attr("src")
                                        .ifEmpty { iframe.attr("data-src") }
                                        .ifEmpty { iframe.attr("data-litespeed-src") }
                                        .ifEmpty { iframe.attr("data-lazy-src") }
                                        .ifEmpty { iframe.attr("data-embed") }
                                        .ifEmpty { iframe.attr("data-link") }
                                        .ifEmpty { iframe.attr("data-url") }
                                        .ifEmpty { iframe.attr("data-video") }
                                        .trim()
                                    if (iframeSrc.isNotBlank()) {
                                        val finalIframeUrl = fixUrl(iframeSrc)
                                        if (finalIframeUrl.isNotEmpty() && finalIframeUrl != cleanUrlEscaped) {
                                            val cleanIf = finalIframeUrl.replace(92.toChar().toString(), "")
                                            if (cleanIf.contains("gofile.io/d/")) {
                                                queueTarget(cleanIf)
                                            } else if (cleanIf.contains(".m3u8") || cleanIf.contains(".mp4") || cleanIf.contains("/hls/")) {
                                                val isM3u = cleanIf.contains(".m3u8") || cleanIf.contains("/hls/")
                                                if (isPlayableDirectStream(cleanIf, cleanUrlEscaped)) {
                                                    val sourceName = sourceNameFor(cleanIf)
                                                    callbackWrapper(
                                                        newExtractorLink(
                                                            source = sourceName,
                                                            name = sourceName,
                                                            url = cleanIf,
                                                            type = if (isM3u) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                                        ) {
                                                            this.referer = cleanUrlEscaped
                                                        }
                                                    )
                                                }
                                            } else {
                                                queueTarget(cleanIf)
                                            }
                                        }
                                    }
                                }
                                subDoc.select("script").forEach { script ->
                                    Regex("""https?://[a-zA-Z0-9.\-_]+/[a-zA-Z0-9.\-_\?&=\/~%]+""")
                                        .findAll(script.data().ifBlank { script.html() })
                                        .forEach { match -> queueTarget(match.value) }
                                }
                            } catch (_: Exception) {}
                        }

                        // Smart Extractor Fallback Dispatcher
                        val isStreamWish = listOf("streamwish", "wish", "hglink", "hgcloud", "vidhide", "filelions", "vidguard", "vembed", "lulustream", "d0000d", "swhoi", "cdnwish", "playerwish", "gendeng", "fkupon", "desacinta", "layarotaku", "layarwibu", "nekonime", "layarecchi", "subsource", "doimg", "anchurl", "certaker", "listeamed", "bigwarp", "cloudatacdn", "push-sdk", "gradehg", "hgplus", "streamplay", "awish", "wishembed").any { cleanUrlEscaped.contains(it, true) }
                        val isDood = listOf("dood", "dsvplay", "doodcdn", "vide0", "ds2play", "ds2video", "doodstream", "doodla").any { cleanUrlEscaped.contains(it, true) }
                        val isVoe = cleanUrlEscaped.contains("voe.sx", true) || cleanUrlEscaped.contains("voe", true)
                        val isStreamtape = cleanUrlEscaped.contains("streamtape", true)
                        val isFilemoon = cleanUrlEscaped.contains("filemoon", true)
                        val isMp4Upload = cleanUrlEscaped.contains("mp4upload", true)
                        val isAbyss = listOf("abyssplayer.com", "abyss.to", "abysscdn.com", "iamcdn.net", "sssrr").any { cleanUrlEscaped.contains(it, true) }

                        when {
                            isAbyss -> {
                                try {
                                    AbyssExtractor().getUrl(cleanUrlEscaped, data, subtitleCallback, callbackWrapper)
                                } catch (e: Exception) {
                                    android.util.Log.e("FallbackExtractor", "AbyssExtractor failed: ${e.message}")
                                }
                            }
                            isStreamWish -> {
                                try {
                                    com.lagradost.cloudstream3.extractors.StreamWishExtractor().getUrl(cleanUrlEscaped, data, subtitleCallback, callbackWrapper)
                                } catch (e: Exception) {
                                    android.util.Log.e("FallbackExtractor", "StreamWish extraction failed for $cleanUrlEscaped: ${e.message}")
                                }
                            }
                            isDood -> {
                                try {
                                    com.lagradost.cloudstream3.extractors.DoodLaExtractor().getUrl(cleanUrlEscaped, data, subtitleCallback, callbackWrapper)
                                } catch (e: Exception) {
                                    android.util.Log.e("FallbackExtractor", "DoodLaExtractor extraction failed for $cleanUrlEscaped: ${e.message}")
                                }
                            }
                            isVoe -> {
                                try {
                                    com.lagradost.cloudstream3.extractors.Voe().getUrl(cleanUrlEscaped, data, subtitleCallback, callbackWrapper)
                                } catch (e: Exception) {
                                    android.util.Log.e("FallbackExtractor", "Voe extraction failed: ${e.message}")
                                }
                            }
                            isStreamtape -> {
                                try {
                                    com.lagradost.cloudstream3.extractors.StreamTape().getUrl(cleanUrlEscaped, data, subtitleCallback, callbackWrapper)
                                } catch (e: Exception) {
                                    android.util.Log.e("FallbackExtractor", "StreamTape extraction failed: ${e.message}")
                                }
                            }
                            isFilemoon -> {
                                try {
                                    com.lagradost.cloudstream3.extractors.FileMoon().getUrl(cleanUrlEscaped, data, subtitleCallback, callbackWrapper)
                                } catch (e: Exception) {
                                    android.util.Log.e("FallbackExtractor", "FileMoon extraction failed: ${e.message}")
                                }
                            }
                            isMp4Upload -> {
                                try {
                                    com.lagradost.cloudstream3.extractors.Mp4Upload().getUrl(cleanUrlEscaped, data, subtitleCallback, callbackWrapper)
                                } catch (e: Exception) {
                                    android.util.Log.e("FallbackExtractor", "Mp4Upload extraction failed: ${e.message}")
                                }
                            }
                            else -> {
                                try {
                                    loadExtractor(cleanUrlEscaped, data, subtitleCallback, callbackWrapper)
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
