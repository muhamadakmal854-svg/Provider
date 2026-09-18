package com.mts.anichin

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class Anichin(val context: Context) : MainAPI() {
    override var mainUrl = "https://anichin.moe"
    override var name = "Anichin"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    companion object {
        private var savedCookies: String = ""
        private const val TAG = "Anichin"
        private const val COOKIE_KEY = "anichin_cf_cookies"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    private fun getSafeContext(): Context {
        return this.context
    }

    private fun toAbsoluteUrl(url: String): String {
        val clean = url.trim()
        if (clean.isBlank()) return ""
        return when {
            clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true) -> clean
            clean.startsWith("//") -> "https:$clean"
            clean.startsWith("/") -> "$mainUrl$clean"
            else -> "$mainUrl/$clean"
        }
    }

    private fun getPosterUrl(element: Element?): String? {
        if (element == null) return null
        val img = if (element.tagName().equals("img", true) || element.tagName().equals("source", true)) {
            element
        } else {
            element.selectFirst("img, picture source, [style*='background'], [style*='url']") ?: element
        }

        for (attr in listOf("data-lazy-src", "data-src", "data-original", "src", "data-cfsrc", "srcset", "data-srcset", "content")) {
            var v = img.attr(attr).trim()
            if (v.isNotBlank() && !v.startsWith("data:image", true) && !v.startsWith("data:text", true)) {
                if (attr.contains("srcset")) {
                    v = v.substringBefore(" ").substringBefore(",").trim()
                }
                if (v.isNotBlank() && !v.startsWith("data:", true)) {
                    return toAbsoluteUrl(v)
                }
            }
        }

        val style = img.attr("style").ifBlank { element.attr("style") }
        if (style.isNotBlank()) {
            val bgMatch = Regex("""url\(['"]?(.*?)['"]?\)""").find(style)
            if (bgMatch != null) {
                val bgUrl = bgMatch.groupValues[1].trim()
                if (bgUrl.isNotBlank() && !bgUrl.startsWith("data:", true)) {
                    return toAbsoluteUrl(bgUrl)
                }
            }
        }

        return null
    }

    private fun getSavedCookie(ctx: Context?): String {
        if (savedCookies.isNotBlank()) return savedCookies
        if (ctx != null) {
            try {
                val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
                val stored = prefs.getString(COOKIE_KEY, null)
                if (!stored.isNullOrBlank()) {
                    savedCookies = stored
                    return stored
                }
            } catch (_: Exception) {}
        }
        try {
            val cm = CookieManager.getInstance().getCookie(mainUrl)
            if (!cm.isNullOrBlank()) {
                savedCookies = cm
                return cm
            }
        } catch (_: Exception) {}
        return ""
    }

    sealed class SmartResult {
        data class Success(val document: Document) : SmartResult()
        object NeedsCaptcha : SmartResult()
        object Error : SmartResult()
    }

    private suspend fun getDocumentSmart(url: String): Document? {
        val targetUrl = toAbsoluteUrl(url)

        // 1. Direct HTTP GET with saved/existing cookies
        try {
            val cookie = getSavedCookie(getSafeContext())
            val headers = mutableMapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "$mainUrl/"
            )
            if (cookie.isNotBlank()) headers["Cookie"] = cookie

            val res = app.get(targetUrl, headers = headers, allowRedirects = true, timeout = 10)
            if (res.code == 200 && !res.text.contains("challenge-platform") && !res.text.contains("cf-turnstile") && !res.text.contains("Just a moment...")) {
                return res.document
            }
        } catch (_: Exception) {}

        // 2. Cloudflare solver via WebView / Dialog
        val activity = getSafeContext() as? Activity
        if (activity != null && !activity.isFinishing) {
            val result = loadVisibleWebViewCheck(targetUrl)
            if (result is SmartResult.Success) {
                return result.document
            } else if (result is SmartResult.NeedsCaptcha) {
                val solvedDoc = CloudflareSolver.solve(activity, targetUrl, USER_AGENT)
                if (solvedDoc != null) return solvedDoc
            }
        } else {
            // Fallback webview solver using application context on MainLooper
            val result = loadHiddenWebViewCheck(targetUrl)
            if (result is SmartResult.Success) {
                return result.document
            }
        }

        // 3. Fallback direct Jsoup parse
        return try {
            Jsoup.connect(targetUrl)
                .userAgent(USER_AGENT)
                .referrer("$mainUrl/")
                .timeout(10000)
                .get()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun loadVisibleWebViewCheck(url: String): SmartResult {
        val activity = getSafeContext() as? Activity ?: return SmartResult.Error
        if (activity.isFinishing) return SmartResult.Error

        return suspendCoroutine { continuation ->
            Handler(Looper.getMainLooper()).post {
                var isFinished = false
                var dialog: Dialog? = null

                fun finish(result: SmartResult) {
                    if (isFinished) return
                    isFinished = true
                    try {
                        if (dialog?.isShowing == true && !activity.isFinishing) {
                            dialog?.dismiss()
                        }
                    } catch (_: Exception) {}
                    continuation.resume(result)
                }

                try {
                    val webView = WebView(activity)
                    val settings = webView.settings
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.userAgentString = USER_AGENT

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(webView, true)

                    val newDialog = Dialog(activity)
                    newDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                    newDialog.setContentView(webView)
                    newDialog.setCancelable(true)
                    newDialog.setOnCancelListener { finish(SmartResult.Error) }

                    val window = newDialog.window
                    if (window != null) {
                        window.setGravity(Gravity.TOP or Gravity.START)
                        val layoutParams = WindowManager.LayoutParams().apply {
                            copyFrom(window.attributes)
                            width = 1
                            height = 1
                            x = -2000
                            y = -2000
                            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        }
                        window.attributes = layoutParams
                    }

                    dialog = newDialog
                    newDialog.show()

                    val handler = Handler(Looper.getMainLooper())
                    val poller = object : Runnable {
                        override fun run() {
                            if (isFinished) return

                            val jsCheck = """
                            (function() {
                                var body = document.body ? document.body.innerHTML : '';
                                if (body.indexOf('challenge-platform') !== -1 || body.indexOf('cf-turnstile') !== -1 || body.indexOf('Just a moment...') !== -1) {
                                    return 'CAPTCHA';
                                }
                                if (document.querySelector('.listupd, .bsx, article.bs, .entry-content, .player-wrapper, #content, .eplister, h1')) {
                                    return 'SUCCESS::' + document.documentElement.outerHTML;
                                }
                                return 'WAITING';
                            })();
                            """.trimIndent()

                            webView.evaluateJavascript(jsCheck) { result ->
                                if (isFinished) return@evaluateJavascript
                                val cleanResult = result?.removeSurrounding("\"")
                                when {
                                    cleanResult == "CAPTCHA" -> finish(SmartResult.NeedsCaptcha)
                                    cleanResult?.startsWith("SUCCESS::") == true -> {
                                        val html = cleanResult.substringAfter("SUCCESS::")
                                        val cleanHtml = html.replace("\\u003C", "<").replace("\\u003E", ">").replace("\\\"", "\"").replace("\\\\", "\\")
                                        finish(SmartResult.Success(Jsoup.parse(cleanHtml)))
                                    }
                                    else -> handler.postDelayed(this, 1000)
                                }
                            }
                        }
                    }

                    webView.webViewClient = object : WebViewClient() {
                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                            handler?.proceed()
                        }
                    }

                    webView.loadUrl(url)
                    handler.postDelayed(poller, 1000)
                    handler.postDelayed({ if (!isFinished) finish(SmartResult.Error) }, 25000)
                } catch (_: Exception) {
                    finish(SmartResult.Error)
                }
            }
        }
    }

    private suspend fun loadHiddenWebViewCheck(url: String): SmartResult {
        return suspendCoroutine { continuation ->
            Handler(Looper.getMainLooper()).post {
                var isFinished = false
                val webView = WebView(getSafeContext())

                fun finish(result: SmartResult) {
                    if (isFinished) return
                    isFinished = true
                    try {
                        webView.stopLoading()
                        webView.destroy()
                    } catch (_: Exception) {}
                    continuation.resume(result)
                }

                val settings = webView.settings
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = USER_AGENT

                val handler = Handler(Looper.getMainLooper())
                val poller = object : Runnable {
                    override fun run() {
                        if (isFinished) return

                        val jsCheck = """
                        (function() {
                            var body = document.body ? document.body.innerHTML : '';
                            if (body.indexOf('challenge-platform') !== -1 || body.indexOf('cf-turnstile') !== -1 || body.indexOf('Just a moment...') !== -1) {
                                return 'CAPTCHA';
                            }
                            if (document.querySelector('.listupd, .bsx, article.bs, .entry-content, .player-wrapper, #content, .eplister, h1')) {
                                return 'SUCCESS::' + document.documentElement.outerHTML;
                            }
                            return 'WAITING';
                        })();
                        """.trimIndent()

                        webView.evaluateJavascript(jsCheck) { result ->
                            if (isFinished) return@evaluateJavascript
                            val cleanResult = result?.removeSurrounding("\"")
                            when {
                                cleanResult == "CAPTCHA" -> finish(SmartResult.NeedsCaptcha)
                                cleanResult?.startsWith("SUCCESS::") == true -> {
                                    val html = cleanResult.substringAfter("SUCCESS::")
                                    val cleanHtml = html.replace("\\u003C", "<").replace("\\u003E", ">").replace("\\\"", "\"").replace("\\\\", "\\")
                                    finish(SmartResult.Success(Jsoup.parse(cleanHtml)))
                                }
                                else -> handler.postDelayed(this, 1000)
                            }
                        }
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                        handler?.proceed()
                    }
                }

                try {
                    webView.loadUrl(url)
                    handler.postDelayed(poller, 1000)
                    handler.postDelayed({ if (!isFinished) finish(SmartResult.Error) }, 25000)
                } catch (_: Exception) {
                    finish(SmartResult.Error)
                }
            }
        }
    }

    // Netflix-Style Main Page Configuration
    override val mainPage = mainPageOf(
        "$mainUrl/#spotlight" to "✨ Pilihan Utama (Spotlight)",
        "$mainUrl/#trending" to "🔥 Trending Hari Ini (Top 10)",
        "$mainUrl/anime/?status=&type=&order=update" to "⚡ Rilisan Terbaru (Update Harian)",
        "$mainUrl/anime/?order=popular" to "⭐ Terpopuler Sepanjang Masa",
        "$mainUrl/anime/?status=ongoing&order=update" to "🎬 Sedang Tayang (Ongoing)",
        "$mainUrl/anime/?status=completed&order=update" to "🏆 Tamat (Binge-Watch / Selesai)",
        "$mainUrl/anime/?type=movie&order=update" to "🍿 Donghua Movie (Film Layar Lebar)",
        "$mainUrl/anime/?order=rating" to "💎 Rating Tertinggi (Top Rated)",
        "$mainUrl/genres/cultivation/" to "⚔️ Kultivasi & Xianxia",
        "$mainUrl/genres/action/" to "💥 Aksi & Petualangan",
        "$mainUrl/genres/fantasy/" to "🔮 Fantasi & Sihir",
        "$mainUrl/genres/martial-arts/" to "🥋 Bela Diri (Wuxia)",
        "$mainUrl/genres/romance/" to "🌸 Romantis & Harem",
        "$mainUrl/genres/sci-fi/" to "🚀 Sci-Fi & Reinkarnasi"
    )

    private fun toSearchResult(element: Element): SearchResponse? {
        return try {
            val a = if (element.tagName().equals("a", true)) element else element.selectFirst("a[href]") ?: return null
            val href = toAbsoluteUrl(a.attr("href"))
            if (href.isBlank() || href == "$mainUrl/" || href.contains("/genres/") || href.contains("/schedule/")) return null

            val img = a.selectFirst("img") ?: element.selectFirst("img")

            // Strict title extraction to avoid duplicated texts
            var rawTitle = element.selectFirst(".tt h2, h2[itemprop='headline'], .tt h3, .info h2 a, .info h2, h2 a, h2, h3 a, h3, .title, .entry-title")?.text()?.trim().orEmpty()
            if (rawTitle.isBlank()) {
                rawTitle = a.attr("title").trim()
            }
            if (rawTitle.isBlank()) {
                rawTitle = img?.attr("alt")?.trim().orEmpty()
            }
            if (rawTitle.isBlank()) {
                rawTitle = a.text().trim()
            }

            rawTitle = rawTitle.lines().firstOrNull()?.trim() ?: ""
            if (rawTitle.isBlank()) return null

            // Clean title of repetitive suffix
            val cleanTitle = rawTitle
                .replace("- Fansub Donghua Subtitle Indonesia", "", ignoreCase = true)
                .replace("Subtitle Indonesia", "", ignoreCase = true)
                .replace("Sub Indo", "", ignoreCase = true)
                .replace("– Anichin", "", ignoreCase = true)
                .replace("- Anichin", "", ignoreCase = true)
                .replace("Anichin", "", ignoreCase = true)
                .trim()

            val poster = getPosterUrl(img ?: element)

            val isMovie = href.contains("/movie", true) || href.contains("-movie-", true)
            val type = if (isMovie) TvType.AnimeMovie else TvType.Anime

            val epText = element.selectFirst(".epx, .bt .ep, .ep")?.text()?.trim()
            val epNum = epText?.filter { it.isDigit() }?.toIntOrNull()

            newAnimeSearchResponse(cleanTitle, href, type) {
                this.posterUrl = poster
                if (epNum != null) {
                    addDubStatus(false, epNum)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val targetUrl = when {
            request.data.endsWith("#spotlight") || request.data.endsWith("#trending") -> {
                if (page > 1) return null
                "$mainUrl/"
            }
            page <= 1 -> request.data
            request.data.contains("?") -> {
                val base = request.data.substringBefore("?").trimEnd('/')
                val query = request.data.substringAfter("?")
                "$base/page/$page/?$query"
            }
            request.data.endsWith("/") -> "${request.data}page/$page/"
            else -> "${request.data}/page/$page/"
        }

        val doc = getDocumentSmart(targetUrl) ?: return null

        val cards = when {
            request.data.endsWith("#spotlight") -> {
                val slides = doc.select(".swiper-wrapper .swiper-slide, .slider .slide, .bigslider .slide").mapNotNull {
                    toSearchResult(it)
                }
                if (slides.isNotEmpty()) slides else doc.select(".listupd .bsx, .bsx").take(15).mapNotNull { toSearchResult(it) }
            }
            request.data.endsWith("#trending") -> {
                val trendBox = doc.select(".bixbox").firstOrNull {
                    val h = it.selectFirst(".releases h2, .releases h3, h2, h3")?.text()?.lowercase() ?: ""
                    h.contains("terpopuler") || h.contains("popular") || h.contains("trending")
                }
                val items = (trendBox?.select(".bsx, article, .item") ?: doc.select(".popularslider .bsx, .popconslide .bsx")).mapNotNull {
                    toSearchResult(it)
                }
                if (items.isNotEmpty()) items else doc.select(".listupd .bsx, .bsx").take(10).mapNotNull { toSearchResult(it) }
            }
            else -> {
                doc.select(".listupd .bsx, .bsx, article.bs, .item, .animpost, .listupd article").mapNotNull {
                    toSearchResult(it)
                }
            }
        }.distinctBy { it.url }

        return if (cards.isNotEmpty()) {
            newHomePageResponse(request.name, cards)
        } else {
            null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/?s=$encodedQuery"

        val doc = getDocumentSmart(searchUrl) ?: return emptyList()

        return doc.select(".listupd .bsx, .bsx, article.bs, .item, .animpost, .listupd article").mapNotNull {
            toSearchResult(it)
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val fullUrl = toAbsoluteUrl(url)
        var doc = getDocumentSmart(fullUrl) ?: return null

        // If user opened an individual episode directly, navigate to parent series to load all episodes
        if (fullUrl.contains("-episode-", true) || fullUrl.contains("-ep-", true)) {
            val parentSeriesUrl = doc.selectFirst(".ts-breadcrumb li:nth-last-child(2) a, .naveps .nvsc a, a:contains(Semua Episode)")?.attr("href")?.let { toAbsoluteUrl(it) }
            if (!parentSeriesUrl.isNullOrBlank() && parentSeriesUrl != fullUrl && parentSeriesUrl != "$mainUrl/") {
                val parentDoc = getDocumentSmart(parentSeriesUrl)
                if (parentDoc != null && parentDoc.select(".eplister ul li a, .episodelist ul li a").isNotEmpty()) {
                    doc = parentDoc
                }
            }
        }

        val rawTitle = doc.selectFirst("h1.entry-title, h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property='og:title']")?.attr("content")?.trim()
            ?: return null

        val title = rawTitle.replace("- Fansub Donghua Subtitle Indonesia", "", ignoreCase = true)
            .replace("Subtitle Indonesia", "", ignoreCase = true)
            .replace("Sub Indo", "", ignoreCase = true)
            .replace("– Anichin", "", ignoreCase = true)
            .replace("- Anichin", "", ignoreCase = true)
            .replace("Anichin", "", ignoreCase = true)
            .trim()

        val poster = getPosterUrl(doc.selectFirst(".thumb img, .poster img, img[itemprop='image'], meta[property='og:image']"))
            ?: doc.selectFirst("meta[property='og:image']")?.attr("content")?.let { toAbsoluteUrl(it) }
            ?: ""

        val plot = doc.select(".entry-content, .sinopsis, .desc, .entry-content p").joinToString("\n") {
            it.text().trim()
        }.ifBlank {
            doc.selectFirst("meta[property='og:description']")?.attr("content")?.trim() ?: ""
        }

        val tags = doc.select(".genxed a, a[href*='/genres/'], .set a").map { it.text().trim() }.distinct()
        val year = doc.selectFirst(".spe span:contains(Released), .year, .meta")?.text()?.let {
            Regex("""\b(19\d\d|20\d\d)\b""").find(it)?.groupValues?.get(1)?.toIntOrNull()
        }

        // Extract Episode List
        val containerElements = doc.select(".eplister ul li a, .episodelist ul li a, #daftarepisode li a, .clps li a, .ep-list li a")
        val epElements = if (containerElements.isNotEmpty()) {
            containerElements
        } else {
            doc.select(".entry-content ul li a[href*='-episode-'], #content .eplister a")
        }

        val backdrop = doc.selectFirst(".bigcover img, .bigcover, [style*='background-image']")?.let { getPosterUrl(it) }
            ?: poster

        val rawEpisodes = epElements.mapNotNull { el ->
            val href = toAbsoluteUrl(el.attr("href"))
            if (href.isBlank() || href == fullUrl || href == "$mainUrl/") return@mapNotNull null
            if (!href.contains("-episode-") && !href.contains("-ep-")) return@mapNotNull null

            val numText = el.selectFirst(".epl-num")?.text()?.trim()
            val epNum = if (!numText.isNullOrBlank() && numText.all { it.isDigit() }) {
                numText.toIntOrNull()
            } else {
                val fullText = el.text().trim()
                Regex("""Episode\s*(\d+)""", RegexOption.IGNORE_CASE).find(fullText)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: Regex("""-episode-(\d+)""", RegexOption.IGNORE_CASE).find(href)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: Regex("""\b(\d+)\b""").find(fullText)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }

            // Extract Release Date
            val rawDate = el.selectFirst(".epl-date, .date, .time, .metadate")?.text()?.trim().orEmpty()
                .ifBlank { el.parent()?.selectFirst(".epl-date, .date, .time, .metadate")?.text()?.trim().orEmpty() }

            val parsedDate = if (rawDate.isNotBlank()) {
                try {
                    SimpleDateFormat("MMMM d, yyyy", Locale.US).parse(rawDate)?.time
                        ?: SimpleDateFormat("d MMMM yyyy", Locale.US).parse(rawDate)?.time
                        ?: SimpleDateFormat("MMMM d, yyyy", Locale("id", "ID")).parse(rawDate)?.time
                        ?: SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).parse(rawDate)?.time
                        ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(rawDate)?.time
                } catch (_: Exception) {
                    null
                }
            } else null

            // Extract Episode Thumbnail / Card Image
            val epImg = getPosterUrl(el.selectFirst("img") ?: el.parent()?.selectFirst("img"))

            newEpisode(href) {
                this.name = if (epNum != null) "Episode $epNum" else "Episode"
                this.episode = epNum
                this.posterUrl = (epImg ?: backdrop).ifBlank { poster.ifBlank { null } }
                if (parsedDate != null) {
                    this.date = parsedDate
                }
                this.description = if (rawDate.isNotBlank()) "Rilis: $rawDate" else null
            }
        }.distinctBy { it.data }

        val isMovie = rawEpisodes.isEmpty() || fullUrl.contains("/movie", true) || fullUrl.contains("-movie-", true)

        return if (isMovie) {
            newMovieLoadResponse(title, fullUrl, TvType.AnimeMovie, fullUrl) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.year = year
            }
        } else {
            // Strictly sort episodes in ascending order (Episode 1, 2, ... N)
            val sortedEpisodes = rawEpisodes.sortedWith(
                compareBy<Episode> { it.episode == null }
                    .thenBy { it.episode ?: 0 }
            )

            newTvSeriesLoadResponse(title, fullUrl, TvType.Anime, sortedEpisodes) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val pageUrl = toAbsoluteUrl(data)
        val doc = getDocumentSmart(pageUrl) ?: return false

        var foundAny = false

        suspend fun resolveStream(rawSrc: String, serverName: String) {
            val src = rawSrc.trim()
            if (src.isBlank() || src.startsWith("javascript:") || src.startsWith("about:")) return

            when {
                // 1. OK.ru (Odnoklassniki) - semak ini dahulu sebelum padanan umum anichin-player!
                src.contains("ok.ru") || src.contains("odnoklassniki") || src.contains("racaty.my.id/empire") || src.contains("videoplayer.vip") || (src.contains("anichin-player.web.id") && src.contains("ok=")) -> {
                    extractOkRuDirect(src, pageUrl, serverName, callback)
                    foundAny = true
                }
                // 2. Dailymotion & Anichin player embed
                src.contains("dailymotion.com") || src.contains("geo.dailymotion.com") || (src.contains("anichin-player.web.id") && (src.contains("url=") || src.contains("video="))) || src.contains("video=") -> {
                    extractDailymotionDirect(src, pageUrl, serverName, callback)
                    foundAny = true
                }
                // 3. Rumble
                src.contains("rumble.com") -> {
                    extractRumbleDirect(src, pageUrl, serverName, callback)
                    foundAny = true
                }
                // 4. TurboVIP / Turboviplay
                src.contains("turbovidhls") || src.contains("turboviplay") -> {
                    extractTurboVipDirect(src, pageUrl, serverName, callback)
                    foundAny = true
                }
                // 5. PixelDrain
                src.contains("pixeldrain.com") -> {
                    extractPixelDrainDirect(src, serverName, callback)
                    foundAny = true
                }
                // 6. StreamRuby / RubyVidHub
                src.contains("rubyvidhub.com") || src.contains("streamruby") -> {
                    extractStreamRubyDirect(src, pageUrl, serverName, callback)
                    foundAny = true
                }
                // 7. VidHide / EarnVids / Morencius
                src.contains("morencius.com") || src.contains("vidhide") || src.contains("earnstream") || src.contains("dintezuvio") -> {
                    extractVidHideDirect(src, pageUrl, serverName, callback)
                    foundAny = true
                }
                // 8. Anichin Internal Stream
                src.contains("anichin.stream") || src.contains("rpmvid.com") -> {
                    extractAnichinStream(src, pageUrl, serverName, subtitleCallback, callback)
                    foundAny = true
                }
                // 9. Generic Extractor (Playmogo, Dood, StreamWish, Filemoon, dll.)
                else -> {
                    try {
                        loadExtractor(src, pageUrl, subtitleCallback, callback)
                        foundAny = true
                    } catch (_: Exception) {}
                }
            }
        }

        // 1. Parse Server Select / Mirror Dropdowns & Tab Options
        val mirrorOptions = doc.select("select.mirror option, select[name='server'] option, .mirror option, ul.mirror li, .server-list li, select option, .mobius option")
        for (opt in mirrorOptions) {
            val rawVal = opt.attr("value").ifBlank { opt.attr("data-embed") }.ifBlank { opt.attr("data-src") }.ifBlank { opt.attr("data-content") }.ifBlank { opt.attr("href") }.trim()
            val serverName = opt.text().trim().ifBlank { "Server" }
            if (rawVal.isNotBlank() && !rawVal.equals("null", true) && !serverName.contains("Select Video", true) && !serverName.contains("Pilih Server", true)) {
                try {
                    val decodedIframe = if (rawVal.startsWith("<iframe", true)) {
                        rawVal
                    } else if (rawVal.length > 20 && !rawVal.startsWith("http")) {
                        try {
                            String(Base64.decode(rawVal, Base64.DEFAULT), Charsets.UTF_8)
                        } catch (_: Exception) {
                            rawVal
                        }
                    } else {
                        rawVal
                    }

                    val src = Regex("""src=['"]([^'"]+)['"]""").find(decodedIframe)?.groupValues?.getOrNull(1)
                        ?: if (decodedIframe.startsWith("http")) decodedIframe else ""

                    if (src.isNotBlank() && src.startsWith("http")) {
                        resolveStream(src, serverName)
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Direct Iframes on Page
        doc.select("iframe[src], iframe[data-src]").forEach { ifr ->
            val src = toAbsoluteUrl(ifr.attr("src").ifBlank { ifr.attr("data-src") })
            if (src.isNotBlank() && !src.contains("cbox", true) && !src.startsWith("about:") && !src.startsWith("javascript:")) {
                resolveStream(src, "Player")
            }
        }

        // 3. Download Links & External Mirrors
        doc.select(".soradl a, .soraurlx a, .moredl a, .dlx a, a[href*='pixeldrain'], a[href*='mediafire'], a[href*='terabox']").forEach { a ->
            val href = a.attr("href").trim()
            if (href.startsWith("http") && !href.contains("javascript:")) {
                val text = a.text().trim().ifBlank { "Mirror" }
                resolveStream(href, text)
            }
        }

        return foundAny
    }

    // Direct Dailymotion Extractor (Fixed 403 Forbidden with Session Cookies and Full Headers)
    private suspend fun extractDailymotionDirect(
        videoUrlOrId: String,
        refererUrl: String,
        serverName: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val videoId = when {
                videoUrlOrId.contains("url=") -> videoUrlOrId.substringAfter("url=").substringBefore("&")
                videoUrlOrId.contains("video=") -> videoUrlOrId.substringAfter("video=").substringBefore("&")
                videoUrlOrId.contains("/video/") -> videoUrlOrId.substringAfter("/video/").substringBefore("?").substringBefore("/")
                videoUrlOrId.contains("geo.dailymotion.com") -> videoUrlOrId.substringAfter("video=").substringBefore("&")
                !videoUrlOrId.contains("/") && !videoUrlOrId.contains(".") && !videoUrlOrId.contains("=") -> videoUrlOrId
                else -> Regex("""(?:video|url)[=/]([a-zA-Z0-9]+)""").find(videoUrlOrId)?.groupValues?.getOrNull(1)
                    ?: Regex("""([a-zA-Z0-9]{7})""").find(videoUrlOrId)?.groupValues?.getOrNull(1)
                    ?: ""
            }

            if (videoId.isNotBlank()) {
                val metaUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
                val res = app.get(
                    metaUrl,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to "https://geo.dailymotion.com/"
                    ),
                    timeout = 10
                )
                val text = res.text
                val cookiesMap = res.cookies
                val cookieHeader = if (cookiesMap.isNotEmpty()) {
                    cookiesMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                } else ""

                val streamHeaders = mutableMapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "https://geo.dailymotion.com/"
                )
                if (cookieHeader.isNotBlank()) {
                    streamHeaders["Cookie"] = cookieHeader
                }

                var foundHls = false
                val jsonAutoMatch = Regex("auto.+?\"url\"\\s*:\\s*\"([^\"]+)\"").find(text)
                val masterUrl = jsonAutoMatch?.groupValues?.getOrNull(1)?.replace("\\/", "/")

                if (!masterUrl.isNullOrBlank()) {
                    foundHls = true
                    callback(
                        newExtractorLink(
                            source = this.name,
                            name = "${this.name} - $serverName Dailymotion Auto HLS",
                            url = masterUrl,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = "https://geo.dailymotion.com/"
                            this.headers = streamHeaders
                        }
                    )

                    try {
                        M3u8Helper.generateM3u8(
                            source = "${this.name} - $serverName Dailymotion",
                            streamUrl = masterUrl,
                            referer = "https://geo.dailymotion.com/",
                            headers = streamHeaders
                        ).forEach(callback)
                    } catch (_: Exception) {}
                }

                if (!foundHls) {
                    val m3u8Regex = Regex("""https?:\\/\\/[^"'\s]+\.m3u8[^"'\s]*""")
                    val foundM3u8s = m3u8Regex.findAll(text).map { it.value.replace("\\/", "/") }.distinct().toList()
                    for (m3u8Url in foundM3u8s) {
                        callback(
                            newExtractorLink(
                                source = this.name,
                                name = "${this.name} - $serverName Dailymotion HLS",
                                url = m3u8Url,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.referer = "https://geo.dailymotion.com/"
                                this.headers = streamHeaders
                            }
                        )
                        try {
                            M3u8Helper.generateM3u8(
                                source = "${this.name} - $serverName Dailymotion",
                                streamUrl = m3u8Url,
                                referer = "https://geo.dailymotion.com/",
                                headers = streamHeaders
                            ).forEach(callback)
                        } catch (_: Exception) {}
                    }
                }

                Regex("""https?:\\/\\/[^"'\s]+\.mp4[^"'\s]*""").findAll(text).map { it.value.replace("\\/", "/") }.distinct().forEach { mp4Url ->
                    callback(
                        newExtractorLink(
                            source = this.name,
                            name = "${this.name} - $serverName Dailymotion MP4",
                            url = mp4Url,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = "https://geo.dailymotion.com/"
                            this.headers = streamHeaders
                        }
                    )
                }
            }
        } catch (_: Exception) {}
    }

    // Direct OK.ru (Odnoklassniki) Extractor with All Qualities & DNS Sinkhole Bypass
    private suspend fun extractOkRuDirect(
        okUrl: String,
        refererUrl: String,
        serverName: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val okId = when {
                okUrl.contains("ok=") -> okUrl.substringAfter("ok=").substringBefore("&")
                okUrl.contains("empire/") -> okUrl.substringAfter("empire/").substringBefore("?").substringBefore("/")
                okUrl.contains("/videoembed/") -> okUrl.substringAfter("/videoembed/").substringBefore("?").substringBefore("/")
                okUrl.contains("/video/") -> okUrl.substringAfter("/video/").substringBefore("?").substringBefore("/")
                else -> Regex("""\b(\d{10,})\b""").find(okUrl)?.groupValues?.getOrNull(1)
            }

            val embedUrl = if (!okId.isNullOrBlank()) {
                "https://ok.ru/videoembed/$okId"
            } else if (okUrl.contains("/video/")) {
                okUrl.replace("/video/", "/videoembed/")
            } else {
                okUrl
            }

            val fallbackWorkerUrl = if (!okId.isNullOrBlank()) {
                "https://box-prox.jeannefrankli-n2-7-2-0-5.workers.dev/https://ok.ru/videoembed/$okId"
            } else {
                "https://box-prox.jeannefrankli-n2-7-2-0-5.workers.dev/$embedUrl"
            }

            val mediaHeaders = mapOf(
                "Accept" to "*/*",
                "Connection" to "keep-alive",
                "Origin" to "https://ok.ru",
                "User-Agent" to USER_AGENT,
                "Referer" to embedUrl
            )

            val html = try {
                app.get(
                    embedUrl,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to "https://ok.ru/"
                    ),
                    timeout = 5
                ).text
            } catch (_: Throwable) {
                null
            } ?: try {
                app.get(
                    fallbackWorkerUrl,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to "https://ok.ru/"
                    ),
                    timeout = 10
                ).text
            } catch (_: Throwable) {
                null
            } ?: return

            val dataOptionsMatch = Regex("""data-options=['"]([^'"]+)['"]""").find(html)
            if (dataOptionsMatch != null) {
                val rawOptions = dataOptionsMatch.groupValues[1]
                    .replace("&quot;", "\"")
                    .replace("&#039;", "'")
                    .replace("&amp;", "&")
                    .replace("\\u0026", "&")
                    .replace("\\u003d", "=")
                    .replace("\\u003D", "=")
                    .replace("\\u002F", "/")
                    .replace("\\/", "/")

                // 1. HLS Master Playlist
                val hlsMatch = Regex("""["']hlsMasterPlaylistUrl["']\s*:\s*["']([^"']+)["']""").find(rawOptions)
                    ?: Regex("""["']hlsMasterPlaylistUrl["']\s*:\s*["']([^"']+)["']""").find(html)
                if (hlsMatch != null) {
                    val hlsUrl = hlsMatch.groupValues[1]
                        .replace("\\/", "/")
                        .replace("\\u0026", "&")
                        .replace("&amp;", "&")

                    callback(
                        newExtractorLink(
                            source = this.name,
                            name = "${this.name} - $serverName OK.ru HLS",
                            url = hlsUrl,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = embedUrl
                            this.headers = mediaHeaders
                        }
                    )

                    try {
                        M3u8Helper.generateM3u8(
                            source = "${this.name} - $serverName OK.ru",
                            streamUrl = hlsUrl,
                            referer = embedUrl,
                            headers = mediaHeaders
                        ).forEach(callback)
                    } catch (_: Exception) {}
                }

                // 2. Kualiti Video Individu (1080p -> 144p)
                val videoBlockMatch = Regex(""""videos"\s*:\s*(\[[^]]+\])""").find(rawOptions)
                    ?: Regex(""""videos"\s*:\s*(\[[^]]+\])""").find(html)
                if (videoBlockMatch != null) {
                    val videosStr = videoBlockMatch.groupValues[1]
                    val vMatches = Regex("""\{[^}]*?"name"\s*:\s*"([^"]+)"[^}]*?"url"\s*:\s*"([^"]+)"[^}]*?\}""").findAll(videosStr)
                    for (vm in vMatches) {
                        val qName = vm.groupValues[1].uppercase()
                        val rawVideoUrl = vm.groupValues[2]
                            .replace("\\/", "/")
                            .replace("\\u0026", "&")
                            .replace("&amp;", "&")
                        val fullVideoUrl = if (rawVideoUrl.startsWith("//")) "https:$rawVideoUrl" else rawVideoUrl

                        val qualityLabel = when (qName) {
                            "MOBILE" -> "144p"
                            "LOWEST" -> "240p"
                            "LOW" -> "360p"
                            "SD" -> "480p"
                            "HD" -> "720p"
                            "FULL" -> "1080p"
                            "QUAD" -> "1440p"
                            "ULTRA" -> "4K"
                            else -> qName
                        }

                        val qualityValue = when (qualityLabel) {
                            "1080p" -> Qualities.P1080.value
                            "720p" -> Qualities.P720.value
                            "480p" -> Qualities.P480.value
                            "360p" -> Qualities.P360.value
                            "240p" -> Qualities.P240.value
                            else -> Qualities.Unknown.value
                        }

                        callback(
                            newExtractorLink(
                                source = this.name,
                                name = "${this.name} - $serverName OK.ru $qualityLabel",
                                url = fullVideoUrl,
                                type = ExtractorLinkType.VIDEO
                            ) {
                                this.referer = embedUrl
                                this.quality = qualityValue
                                this.headers = mediaHeaders
                            }
                        )
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // Direct Rumble Extractor (HLS Master & Multi-Quality MP4)
    private suspend fun extractRumbleDirect(
        rumbleUrl: String,
        refererUrl: String,
        serverName: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "https://rumble.com/"
            )
            val res = app.get(rumbleUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to refererUrl), timeout = 10)
            val text = res.text

            // 1. HLS Master Playlist
            Regex("""(https?:[\\/]+[^\s"']+\.m3u8[^\s"']*)""").findAll(text).forEach { m ->
                val cleanUrl = m.groupValues[1].replace("\\/", "/")
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = "${this.name} - $serverName Rumble HLS",
                        url = cleanUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://rumble.com/"
                        this.headers = headers
                    }
                )
                try {
                    M3u8Helper.generateM3u8(
                        source = "${this.name} - $serverName Rumble",
                        streamUrl = cleanUrl,
                        referer = "https://rumble.com/",
                        headers = headers
                    ).forEach(callback)
                } catch (_: Exception) {}
            }

            // 2. Direct MP4 Video Streams
            val mp4Matches = Regex("""(https?:[\\/]+[^\s"']+\.mp4[^\s"']*)""").findAll(text).map {
                it.groupValues[1].replace("\\/", "/")
            }.distinct().toList()

            for (cleanUrl in mp4Matches) {
                val qualityLabel = when {
                    cleanUrl.contains(".Faa.mp4", ignoreCase = true) -> "1080p"
                    cleanUrl.contains(".gaa.mp4", ignoreCase = true) -> "720p"
                    cleanUrl.contains(".caa.mp4", ignoreCase = true) -> "480p"
                    cleanUrl.contains(".baa.mp4", ignoreCase = true) -> "360p"
                    else -> "MP4"
                }
                val qualityValue = when (qualityLabel) {
                    "1080p" -> Qualities.P1080.value
                    "720p" -> Qualities.P720.value
                    "480p" -> Qualities.P480.value
                    "360p" -> Qualities.P360.value
                    else -> Qualities.Unknown.value
                }
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = "${this.name} - $serverName Rumble $qualityLabel",
                        url = cleanUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://rumble.com/"
                        this.quality = qualityValue
                        this.headers = headers
                    }
                )
            }
        } catch (_: Exception) {}
    }

    // Direct TurboVIP Extractor
    private suspend fun extractTurboVipDirect(
        turboUrl: String,
        refererUrl: String,
        serverName: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val headers = mapOf("User-Agent" to USER_AGENT, "Referer" to turboUrl)
            val res = app.get(turboUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to refererUrl), timeout = 8)
            val text = res.text

            Regex("""(https?:[\\/]+[^\s"']+\.m3u8[^\s"']*)""").findAll(text).forEach { m ->
                val cleanUrl = m.groupValues[1].replace("\\/", "/")
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = "${this.name} - $serverName TurboVIP HLS",
                        url = cleanUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = turboUrl
                        this.headers = headers
                    }
                )
                try {
                    M3u8Helper.generateM3u8(
                        source = "${this.name} - $serverName TurboVIP",
                        streamUrl = cleanUrl,
                        referer = turboUrl,
                        headers = headers
                    ).forEach(callback)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    // Direct PixelDrain Extractor
    private suspend fun extractPixelDrainDirect(
        url: String,
        serverName: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val fileId = when {
                url.contains("/u/") -> url.substringAfter("/u/").substringBefore("?").substringBefore("/")
                url.contains("/file/") -> url.substringAfter("/file/").substringBefore("?").substringBefore("/")
                else -> Regex("""pixeldrain\.com/(?:u|file)/([a-zA-Z0-9]+)""").find(url)?.groupValues?.getOrNull(1)
            }
            if (!fileId.isNullOrBlank()) {
                val directStreamUrl = "https://pixeldrain.com/api/file/$fileId"
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = "${this.name} - $serverName PixelDrain",
                        url = directStreamUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://pixeldrain.com/"
                        this.quality = Qualities.P1080.value
                    }
                )
            }
        } catch (_: Exception) {}
    }

    // Direct StreamRuby Extractor
    private suspend fun extractStreamRubyDirect(
        url: String,
        refererUrl: String,
        serverName: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val res = app.get(url, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to refererUrl), timeout = 8)
            val text = res.text

            val unpacked = if (text.contains("eval(function(p,a,c,k,e,d)")) {
                try { getAndUnpack(text) } catch (_: Exception) { text }
            } else text

            val m3u8Match = Regex("""(?:file|sources?)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").find(unpacked)
                ?: Regex("""(https?:[\\/]+[^\s"']+\.m3u8[^\s"']*)""").find(unpacked)

            if (m3u8Match != null) {
                val m3u8Url = m3u8Match.groupValues[1].replace("\\/", "/")
                val headers = mapOf("User-Agent" to USER_AGENT, "Referer" to url)
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = "${this.name} - $serverName StreamRuby HLS",
                        url = m3u8Url,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = url
                        this.headers = headers
                    }
                )
                try {
                    M3u8Helper.generateM3u8(
                        source = "${this.name} - $serverName",
                        streamUrl = m3u8Url,
                        referer = url,
                        headers = headers
                    ).forEach(callback)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    // Direct VidHide / EarnVids Extractor
    private suspend fun extractVidHideDirect(
        url: String,
        refererUrl: String,
        serverName: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val res = app.get(url, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to refererUrl), timeout = 8)
            val text = res.text

            val unpacked = if (text.contains("eval(function(p,a,c,k,e,d)")) {
                try { getAndUnpack(text) } catch (_: Exception) { text }
            } else text

            val m3u8Match = Regex("""(?:file|sources?)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").find(unpacked)
                ?: Regex("""(https?:[\\/]+[^\s"']+\.m3u8[^\s"']*)""").find(unpacked)

            if (m3u8Match != null) {
                val m3u8Url = m3u8Match.groupValues[1].replace("\\/", "/")
                val headers = mapOf("User-Agent" to USER_AGENT, "Referer" to url)
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = "${this.name} - $serverName VidHide HLS",
                        url = m3u8Url,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = url
                        this.headers = headers
                    }
                )
                try {
                    M3u8Helper.generateM3u8(
                        source = "${this.name} - $serverName",
                        streamUrl = m3u8Url,
                        referer = url,
                        headers = headers
                    ).forEach(callback)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    // Anichin Stream Extractor
    private suspend fun extractAnichinStream(
        streamUrl: String,
        refererUrl: String,
        serverName: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            if (streamUrl.contains("video=") || streamUrl.contains("dailymotion")) {
                extractDailymotionDirect(streamUrl, refererUrl, serverName, callback)
            }

            val res = app.get(streamUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to refererUrl), timeout = 8)
            val text = res.text

            val doc = res.document
            doc.select("iframe[src]").forEach { ifr ->
                val innerSrc = toAbsoluteUrl(ifr.attr("src"))
                if (innerSrc.isNotBlank() && !innerSrc.contains("cbox", true) && !innerSrc.startsWith("about:") && !innerSrc.startsWith("javascript:")) {
                    if (innerSrc.contains("video=") || innerSrc.contains("dailymotion") || (innerSrc.contains("anichin-player.web.id") && !innerSrc.contains("ok="))) {
                        extractDailymotionDirect(innerSrc, streamUrl, serverName, callback)
                    } else if (innerSrc.contains("ok.ru") || innerSrc.contains("ok=")) {
                        extractOkRuDirect(innerSrc, streamUrl, serverName, callback)
                    } else {
                        try {
                            loadExtractor(innerSrc, streamUrl, subtitleCallback, callback)
                        } catch (_: Exception) {}
                    }
                }
            }

            val hlsMatch = Regex("""/hls/([a-zA-Z0-9_-]+\.m3u8)""").find(text)
            if (hlsMatch != null) {
                val fullHls = "https://anichin.stream/hls/${hlsMatch.groupValues[1]}"
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = "${this.name} - $serverName",
                        url = fullHls,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://anichin.stream/"
                    }
                )
                return
            }

            Regex("""(https?:[^"'\s<>]+\.m3u8[^"'\s<>]*)""").findAll(text).forEach { m ->
                val cleanUrl = m.groupValues[1].replace("\\/", "/")
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = "${this.name} - $serverName HLS",
                        url = cleanUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = streamUrl
                    }
                )
            }

            if (text.contains("eval(function(p,a,c,k,e,d)")) {
                val unpacked = try { getAndUnpack(text) } catch (_: Exception) { text }
                val m3u8Inside = Regex("""(["'])(/[^"']+\.m3u8)\1""").find(unpacked)?.groupValues?.getOrNull(2)
                    ?: Regex("""(["'])(https?://[^"']+\.m3u8)\1""").find(unpacked)?.groupValues?.getOrNull(2)

                if (!m3u8Inside.isNullOrBlank()) {
                    val directHls = if (m3u8Inside.startsWith("http")) m3u8Inside else "https://anichin.stream$m3u8Inside"
                    callback(
                        newExtractorLink(
                            source = this.name,
                            name = "${this.name} - $serverName",
                            url = directHls,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = "https://anichin.stream/"
                        }
                    )
                }
            }
        } catch (_: Exception) {}
    }
}
