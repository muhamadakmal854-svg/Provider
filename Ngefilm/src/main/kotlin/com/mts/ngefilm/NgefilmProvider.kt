package __PACKAGE__

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class NgefilmProvider : MainAPI() {
    override var mainUrl = "https://new31.ngefilm.site"
    override var name = "NgeFilm21"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    private val UA_BROWSER = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
    private val RPM_KEY = "6b69656d7469656e6d75613931316361" 
    private val RPM_IV = "313233343536373839306f6975797472"

    private fun Element.getImageAttr(): String? {
        var url = this.attr("data-src").ifEmpty { this.attr("src") }
        if (url.isEmpty()) {
            val srcset = this.attr("srcset")
            if (srcset.isNotEmpty()) {
                url = srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull() ?: ""
            }
        }
        return if (url.isNotEmpty()) {
            httpsify(url).replace(Regex("-\\d+x\\d+"), "")
        } else null
    }

    private val categories = listOf(
        Pair("Latest Update", ""),
        Pair("Top Rating", "?s=&search=advanced&post_type=&index=&orderby=rating&genre=&movieyear=&country=&quality="),
        Pair("Indonesia Category", "/country/indonesia"),
        Pair("Western Category", "/country/usa"),
        Pair("Malaysia Category", "/country/malaysia"),
        Pair("Korean Category", "/country/korea"),
        Pair("Philippines Category", "/country/philippines"),
        Pair("Japan Category", "/country/japan"),
        Pair("Vietnam Category", "/country/viet-nam"),
        Pair("Chinese Category", "/country/china"),
        Pair("Canada Category", "/country/canada"),
        Pair("France Category", "/country/france"),
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val homeItems = coroutineScope {
            categories.map { (title, urlPath) ->
                async {
                    val finalUrl = if (urlPath.isEmpty()) {
                        "$mainUrl/page/$page/"
                    } else if (urlPath.contains("?")) {
                        val split = urlPath.split("?")
                        "$mainUrl/page/$page/?${split[1]}"
                    } else {
                        "$mainUrl$urlPath/page/$page/"
                    }

                    try {
                        val document = app.get(finalUrl).document
                        val items = document.select("article.item-infinite").mapNotNull { it.toSearchResult() }
                        if (items.isNotEmpty()) HomePageList(title, items) else null
                    } catch (e: Exception) { null }
                }
            }.awaitAll().filterNotNull()
        }
        return newHomePageResponse(homeItems, hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst(".entry-title a")?.text() ?: return null
        val href = this.selectFirst(".entry-title a")?.attr("href") ?: ""
        val qualityText = this.selectFirst(".gmr-quality-item")?.text()?.trim() ?: "HD"
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = this@toSearchResult.selectFirst(".content-thumbnail img")?.getImageAttr()
            addQuality(qualityText)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/?s=$query&post_type[]=post&post_type[]=tv").document
            .select("article.item-infinite").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
        val poster = document.selectFirst(".gmr-movie-data figure img")?.getImageAttr()
        val plotText = document.selectFirst("div.entry-content[itemprop='description'] p")?.text()?.trim() 
            ?: document.selectFirst("div.entry-content p")?.text()?.trim()
            ?: document.selectFirst("meta[property='og:description']")?.attr("content")
        val yearText = document.selectFirst(".gmr-moviedata a[href*='year']")?.text()?.toIntOrNull()
        val ratingText = document.selectFirst("[itemprop='ratingValue']")?.text()?.trim()
        val tagsList = document.select(".gmr-moviedata a[href*='genre']").map { it.text() }
        val actorsList = document.select("[itemprop='actors'] a").map { it.text() }
        val trailerUrl = document.selectFirst("a.gmr-trailer-popup")?.attr("href")

        val epElements = document.select(".gmr-listseries a").filter { it.attr("href").contains("/eps/") }
        val isSeries = epElements.isNotEmpty()
        val type = if (isSeries) TvType.TvSeries else TvType.Movie

        if (isSeries) {
            val episodes = epElements.mapNotNull { 
                newEpisode(fixUrl(it.attr("href"))) { 
                    this.name = it.attr("title").removePrefix("Permalink ke ")
                    this.episode = Regex("(\\d+)").find(it.text())?.groupValues?.get(1)?.toIntOrNull()
                }
            }
            return newTvSeriesLoadResponse(title, url, type, episodes) { 
                this.posterUrl = poster; this.plot = plotText; this.year = yearText
                this.score = Score.from10(ratingText); this.tags = tagsList
                this.actors = actorsList.map { ActorData(Actor(it)) }
                if (!trailerUrl.isNullOrEmpty()) this.trailers.add(TrailerData(trailerUrl, null, false))
            }
        } else {
            return newMovieLoadResponse(title, url, type, url) { 
                this.posterUrl = poster; this.plot = plotText; this.year = yearText
                this.score = Score.from10(ratingText); this.tags = tagsList
                this.actors = actorsList.map { ActorData(Actor(it)) }
                if (!trailerUrl.isNullOrEmpty()) this.trailers.add(TrailerData(trailerUrl, null, false))
            }
        }
    }

    
    private fun sha256(input: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val vodCache = java.util.concurrent.ConcurrentHashMap<String, String>()
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

        val priorityList = listOf(
            "hydrax", "turbovip", "cast", "doodstream", "voe", "streamtape", 
            "vidguard", "mixdrop", "filemoon", "vidsrc", "upstream", "streamwish", 
            "vudeo", "supervideo", "streamhide", "vidlox", "dropload", "vidoza", 
            "embedrise", "userload", "faststream", "pelisnow", "rabbitstream", 
            "vizcloud", "mega", "mediafire", "terabox", "google", "dropbox", "onedrive"
        )

        fun getPriorityRank(url: String): Int {
            val u = url.lowercase()
            for (i in priorityList.indices) {
                val keyword = priorityList[i]
                val matches = when (keyword) {
                    "doodstream" -> listOf("doodstream", "dood", "dsvplay", "doodcdn", "vide0", "ds2play", "ds2video", "doodstream", "doodla")
                    "streamwish" -> listOf("streamwish", "wish", "hglink", "hgcloud", "gendeng", "fkupon", "desacinta", "layarotaku", "layarwibu", "nekonime", "layarecchi", "subsource", "doimg", "anchurl", "certaker", "listeamed", "bigwarp", "cloudatacdn", "push-sdk", "gradehg", "hgplus", "streamplay", "awish", "wishembed")
                    "google" -> listOf("google", "gdrive", "drive.google")
                    else -> listOf(keyword)
                }
                if (matches.any { u.contains(it) }) {
                    return i
                }
            }
            return 999
        }

        // Layer 1: VOD Source Detector Engine
        fun classifySource(url: String): String {
            val rank = getPriorityRank(url)
            if (rank == 999) return "unknown"
            val keyword = priorityList[rank]
            return when (keyword) {
                "mega", "mediafire", "terabox", "google", "dropbox", "onedrive" -> "cloud"
                "vidsrc", "rabbitstream", "vizcloud", "hydrax", "turbovip", "cast", "pelisnow", "embedrise" -> "embed"
                else -> "hosting"
            }
        }

        // Layer 5: Smart Player Compatibility Engine
        fun getPlayerType(url: String): String {
            val u = url.lowercase()
            return when {
                u.contains("iframe") || u.contains("/e/") || u.contains("/embed/") -> "iframe"
                u.contains(".m3u8") || u.contains("/hls/") -> "m3u8"
                u.contains(".mp4") -> "mp4"
                else -> "js-encrypted"
            }
        }

        // Layer 6: Validation & Cleaning Engine
        suspend fun validateAndEmitLink(link: ExtractorLink): Boolean {
            val url = link.url
            
            // Layer 7: Cache & Reuse Engine check
            val cachedDirect = vodCache[url]
            if (cachedDirect != null && cachedDirect == "DEAD") return false
            if (cachedDirect != null) {
                parentCallback(
                    newExtractorLink(
                        source = link.source,
                        name = link.name,
                        url = cachedDirect,
                        type = if (cachedDirect.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = link.referer
                        this.quality = link.quality
                        this.headers = link.headers
                    }
                )
                return true
            }

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
                        vodCache[url] = finalUrl
                        
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
                android.util.Log.e("VODValidator", "Validation failed for ${url}: ${e.message}")
            }
            
            if (!isKlikxxi) {
                val isDirectFormat = url.contains(".m3u8") || url.contains(".mp4") || url.contains("/hls/")
                if (isDirectFormat) {
                    parentCallback(link)
                    return true
                }
            }
            
            vodCache[url] = "DEAD"
            return false
        }

        // Layer 3: Stream Resolver Engine (Core Recursive resolver)
        suspend fun resolveAndValidateStream(link: ExtractorLink, depth: Int = 0): Boolean {
            if (depth > 5) return false
            val url = link.url
            
            val cachedDirect = vodCache[url]
            if (cachedDirect != null && cachedDirect == "DEAD") return false
            if (cachedDirect != null) {
                parentCallback(
                    newExtractorLink(
                        source = link.source,
                        name = link.name,
                        url = cachedDirect,
                        type = if (cachedDirect.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = link.referer
                        this.quality = link.quality
                        this.headers = link.headers
                    }
                )
                return true
            }

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
                        vodCache[url] = finalUrl
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
                android.util.Log.e("VODResolver", "Resolution failed for ${url}: ${e.message}")
            }
            
            vodCache[url] = "DEAD"
            return false
        }

        // Retry helper with max 3 retries
        suspend fun resolveStreamWithRetry(link: ExtractorLink, retries: Int = 3): Boolean {
            for (i in 0 until retries) {
                val success = resolveAndValidateStream(link)
                if (success) return true
            }
            return false
        }

        // Intercepting callback wrapper to validate/resolve all generated links with Retry
        val callback: (ExtractorLink) -> Unit = { link ->
            kotlinx.coroutines.runBlocking {
                val sourceClass = classifySource(link.url)
                if (sourceClass == "unknown") {
                    resolveStreamWithRetry(link, 3)
                } else {
                    validateAndEmitLink(link)
                }
            }
        }

        val doc = app.get(data, headers = mapOf("Referer" to mainUrl)).document

        // Layer 2: Unified Link Extractor (Standard Scraper part)
        suspend fun runStandardEngine(document: org.jsoup.nodes.Document): List<String> {
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
                    Regex("""https?://[a-zA-Z0-9.\\-_]+/[a-zA-Z0-9.\\-_\\?&=\\/~]+""").findAll(content).forEach { match ->
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

        // Layer 2: Unified Link Extractor (Fallback Scraper part)
        fun runFallbackEngine(htmlContent: String): List<String> {
            val list = mutableListOf<String>()
            
            val commentsRegex = Regex("<!--(.*?)-->", RegexOption.DOT_MATCHES_ALL)
            commentsRegex.findAll(htmlContent).forEach { match ->
                val commentContent = match.groupValues[1]
                Regex("""(?:src|href)=["']([^"']+)["']""").findAll(commentContent).forEach { subMatch ->
                    val url = fixUrl(subMatch.groupValues[1])
                    if (url.isNotEmpty()) list.add(url)
                }
                Regex("""https?://[a-zA-Z0-9.\\\\-_]+/[a-zA-Z0-9.\\\\-_\\\\?&=\\\\/~]+""").findAll(commentContent).forEach { subMatch ->
                    val url = fixUrl(subMatch.value)
                    if (url.isNotEmpty() && !url.contains("google") && !url.contains("facebook")) {
                        list.add(url)
                    }
                }
            }

            Regex("""https?://[a-zA-Z0-9.\\\\-_]+/[a-zA-Z0-9.\\\\-_\\\\?&=\\\\/~]+\\\\.(?:m3u8|mp4)[a-zA-Z0-9.\\\\-_\\\\?&=\\\\/~]*""").findAll(htmlContent).forEach { match ->
                val url = fixUrl(match.value)
                if (url.isNotEmpty() && !url.contains("google") && !url.contains("facebook")) {
                    list.add(url)
                }
            }

            val hosterKeywords = listOf("streamwish", "dood", "voe.sx", "streamtape", "filemoon", "mp4upload", "gofile.io", "abyssplayer")
            Regex("""https?://[a-zA-Z0-9.\\\\-_]+/[a-zA-Z0-9.\\\\-_\\\\?&=\\\\/~]+""").findAll(htmlContent).forEach { match ->
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

        // Layer 4: Multi-Host Fallback Engine - Sort targets by fallback priority
        val sortedTargets = targets.distinct().sortedBy { getPriorityRank(it) }

        // Engine Selection & Processing Layer
        sortedTargets.forEach { raw ->
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
                
                val playerType = getPlayerType(cleanUrlEscaped)
                val sourceClass = classifySource(cleanUrlEscaped)

                // Route through appropriate extractors or resolving paths
                when {
                    sourceClass == "cloud" && cleanUrlEscaped.contains("gofile.io") -> {
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
                    playerType == "m3u8" || playerType == "mp4" -> {
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
                    cleanUrlEscaped.contains("abyss") -> {
                        try {
                            AbyssExtractor().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "AbyssExtractor failed: ${e.message}")
                        }
                    }
                    cleanUrlEscaped.contains("streamwish") -> {
                        try {
                            com.lagradost.cloudstream3.extractors.StreamWishExtractor().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "StreamWish failed: ${e.message}")
                        }
                    }
                    cleanUrlEscaped.contains("dood") -> {
                        try {
                            com.lagradost.cloudstream3.extractors.DoodLaExtractor().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "DoodLaExtractor failed: ${e.message}")
                        }
                    }
                    cleanUrlEscaped.contains("voe") -> {
                        try {
                            com.lagradost.cloudstream3.extractors.Voe().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "Voe failed: ${e.message}")
                        }
                    }
                    cleanUrlEscaped.contains("streamtape") -> {
                        try {
                            com.lagradost.cloudstream3.extractors.StreamTape().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "StreamTape failed: ${e.message}")
                        }
                    }
                    cleanUrlEscaped.contains("filemoon") -> {
                        try {
                            com.lagradost.cloudstream3.extractors.FileMoon().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "FileMoon failed: ${e.message}")
                        }
                    }
                    cleanUrlEscaped.contains("mp4upload") -> {
                        try {
                            com.lagradost.cloudstream3.extractors.Mp4Upload().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "Mp4Upload failed: ${e.message}")
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
                                        val subType = getPlayerType(subClean)
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
                                        } else if (subClean.contains("abyss") || subClean.contains("streamwish") || subClean.contains("dood") || subClean.contains("voe") || subClean.contains("streamtape") || subClean.contains("filemoon") || subClean.contains("mp4upload")) {
                                            when {
                                                subClean.contains("abyss") -> AbyssExtractor().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                                subClean.contains("streamwish") -> com.lagradost.cloudstream3.extractors.StreamWishExtractor().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                                subClean.contains("dood") -> com.lagradost.cloudstream3.extractors.DoodLaExtractor().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                                subClean.contains("voe") -> com.lagradost.cloudstream3.extractors.Voe().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                                subClean.contains("streamtape") -> com.lagradost.cloudstream3.extractors.StreamTape().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                                subClean.contains("filemoon") -> com.lagradost.cloudstream3.extractors.FileMoon().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                                subClean.contains("mp4upload") -> com.lagradost.cloudstream3.extractors.Mp4Upload().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
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
