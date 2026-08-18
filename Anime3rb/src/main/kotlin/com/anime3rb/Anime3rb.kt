package com.anime3rb

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class Anime3rb(val context: Context) : MainAPI() {
    override var mainUrl = "https://anime3rb.com"
    override var name = "Anime3rb"
    override val hasMainPage = true
    override var lang = "ar"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    companion object {
        private var savedCookies: String = ""
        private const val TAG = "Anime3rb"
        private val NON_DIGITS = Regex("[^0-9]")
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        private val TITLE_EP_REGEX = Regex("""الحلقة\s+\d+""")
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

        for (attr in listOf("data-cfsrc", "data-src", "src", "data-original", "data-lazy-src", "data-lazy", "data-image", "data-bg", "srcset", "data-srcset")) {
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

        // Check style for background-image: url(...)
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
        val cm = CookieManager.getInstance().getCookie(mainUrl)
        if (!cm.isNullOrBlank()) {
            savedCookies = cm
            return cm
        }
        return ""
    }

    private suspend fun getDocumentSmart(url: String): Document? {
        val result = loadVisibleWebViewCheck(url)
        return when (result) {
            is SmartResult.Success -> result.document
            is SmartResult.NeedsCaptcha -> {
                val activity = getSafeContext() as? Activity
                CloudflareSolver.solve(activity, url, USER_AGENT)
            }
            else -> null
        }
    }

    sealed class SmartResult {
        data class Success(val document: Document) : SmartResult()
        object NeedsCaptcha : SmartResult()
        object Error : SmartResult()
    }

    private suspend fun loadVisibleWebViewCheck(url: String): SmartResult {
        return suspendCoroutine { continuation ->
            Handler(Looper.getMainLooper()).post {
                val ctx = getSafeContext()
                val activity = ctx as? Activity
                if (activity == null || activity.isFinishing) {
                    continuation.resume(SmartResult.Error)
                    return@post
                }

                val dialog = Dialog(activity)
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setCancelable(false)
                dialog.window?.addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                )
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                dialog.window?.setDimAmount(0f)

                val params = WindowManager.LayoutParams()
                params.copyFrom(dialog.window?.attributes)
                params.width = 1
                params.height = 1
                params.gravity = Gravity.TOP or Gravity.START
                params.x = -10
                params.y = -10
                dialog.window?.attributes = params

                val webView = WebView(activity)
                dialog.setContentView(webView, ViewGroup.LayoutParams(1, 1))

                try {
                    webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        userAgentString = USER_AGENT
                        blockNetworkImage = false
                    }
                } catch (_: Exception) {}

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(webView, true)

                var isFinished = false
                val handler = Handler(Looper.getMainLooper())

                fun finish(result: SmartResult) {
                    if (isFinished) return
                    isFinished = true
                    handler.removeCallbacksAndMessages(null)
                    try { if (dialog.isShowing) dialog.dismiss() } catch (_: Exception) {}
                    try { webView.destroy() } catch (_: Exception) {}

                    if (result is SmartResult.Success) {
                        cookieManager.flush()
                        val newCookies = cookieManager.getCookie(url)
                        if (!newCookies.isNullOrBlank()) {
                            savedCookies = newCookies
                            try {
                                val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
                                prefs.edit().putString(COOKIE_KEY, newCookies).apply()
                            } catch (_: Exception) {}
                        }
                    }
                    continuation.resume(result)
                }

                val poller = object : Runnable {
                    override fun run() {
                        if (isFinished) return
                        val jsCheck = """
                        (function() {
                            const html = document.documentElement.innerHTML || '';
                            if (html.includes('challenge-platform') || html.includes('cf-turnstile') || document.getElementById('cf-wrapper')) return 'CAPTCHA';
                            if (document.querySelector('.video-card, .simple-title-card, .main-content, #videos, header, nav')) return 'SUCCESS::' + html;
                            return 'POLLING';
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
                    dialog.show()
                    webView.loadUrl(url)
                    handler.postDelayed(poller, 1000)
                    handler.postDelayed({ if (!isFinished) finish(SmartResult.Error) }, 20000)
                } catch (_: Exception) {
                    finish(SmartResult.Error)
                }
            }
        }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/" to "الرئيسية",
        "$mainUrl/titles?type=tv" to "مسلسلات أنمي",
        "$mainUrl/titles?type=movie" to "أفلام أنمي",
        "$mainUrl/seasons" to "أنميات الموسم",
        "$mainUrl/titles" to "قائمة الأنمي",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val homeSets = mutableListOf<HomePageList>()
        try {
            val isHomeRoot = request.data == "$mainUrl/" || request.data == mainUrl

            if (isHomeRoot && page == 1) {
                val doc = getDocumentSmart(request.data) ?: return null

                // 1. Pinned Anime (الأنميات المثبتة)
                doc.select("h2:contains(الأنميات المثبتة)").firstOrNull()?.let { header ->
                    val list = header.parent()?.parent()?.parent()
                        ?.select(".glide__slide:not(.glide__slide--clone) a.video-card, .glide__slide a")
                        ?.mapNotNull { toSearchResult(it) }
                    if (!list.isNullOrEmpty()) homeSets.add(HomePageList("الأنميات المثبتة", list))
                }

                // 2. Latest Episodes (أحدث الحلقات)
                val latest = doc.select("#videos a.video-card, a.video-card").mapNotNull { toSearchResult(it) }
                if (latest.isNotEmpty()) homeSets.add(HomePageList("أحدث الحلقات", latest.distinctBy { it.url }))

                // 3. Latest Added Anime (آخر الأنميات المضافة)
                doc.select("h3:contains(آخر الأنميات المضافة), h2:contains(آخر الأنميات)").firstOrNull()?.let { header ->
                    val list = header.parent()?.parent()?.parent()
                        ?.select(".glide__slide:not(.glide__slide--clone) a.video-card, .glide__slide a")
                        ?.mapNotNull { toSearchResult(it) }
                    if (!list.isNullOrEmpty()) homeSets.add(HomePageList("آخر الأنميات المضافة", list.distinctBy { it.url }))
                }

                // 4. Catalog cards fallback
                if (homeSets.isEmpty()) {
                    val cards = doc.select("a.simple-title-card, a.video-card").mapNotNull { toSearchResult(it) }
                    if (cards.isNotEmpty()) homeSets.add(HomePageList(request.name, cards))
                }
            } else {
                // Category pages & pagination (Series, Movies, Seasons, All Titles)
                val targetUrl = if (page <= 1) {
                    request.data
                } else {
                    if (request.data.contains("?")) "${request.data}&page=$page" else "${request.data}?page=$page"
                }

                val doc = getDocumentSmart(targetUrl) ?: return null
                val list = doc.select("a.simple-title-card, a.video-card, .grid a[href*='/titles/'], .grid a[href*='/episode/']").mapNotNull {
                    toSearchResult(it)
                }.distinctBy { it.url }

                if (list.isNotEmpty()) {
                    homeSets.add(HomePageList(request.name, list))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MainPage Error: ${e.message}")
        }
        return if (homeSets.isNotEmpty()) newHomePageResponse(homeSets) else null
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        return try {
            val rawTitle = element.selectFirst("h3.title-name, h4, h3, h2, .title")?.text() ?: element.text()
            val title = cleanTitleText(rawTitle)
            if (title.isBlank()) return null

            val href = toAbsoluteUrl(element.attr("href"))
            val posterUrl = getPosterUrl(element)

            val episodeText = cleanTitleText(element.select("p.number, .episode-number, span.ep").text())
            val episodeNum = episodeText.filter { it.isDigit() }.toIntOrNull()

            val isMovie = href.contains("/movie", ignoreCase = true) || title.contains("فيلم", ignoreCase = true)
            val tvType = if (isMovie) TvType.AnimeMovie else TvType.Anime

            newAnimeSearchResponse(title, href, tvType) {
                this.posterUrl = posterUrl
                if (episodeNum != null) {
                    addDubStatus(false, episodeNum)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val ctx = getSafeContext()
        val cookie = getSavedCookie(ctx)

        // Method 1: Livewire JSON Search
        try {
            val mainDoc = getDocumentSmart(mainUrl)
            if (mainDoc != null) {
                val scriptTag = mainDoc.selectFirst("script[src*=livewire.min.js]")
                val csrfToken = scriptTag?.attr("data-csrf") ?: ""

                val form = mainDoc.selectFirst("form[wire:id]")
                val snapshotRaw = form?.attr("wire:snapshot") ?: ""
                val snapshotStr = org.jsoup.parser.Parser.unescapeEntities(snapshotRaw, true)

                if (csrfToken.isNotBlank() && snapshotStr.isNotBlank()) {
                    val headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Accept" to "*/*",
                        "Content-Type" to "application/json",
                        "Origin" to mainUrl,
                        "Referer" to "$mainUrl/",
                        "Cookie" to cookie
                    )

                    val updateUrl = "$mainUrl/livewire/update"
                    val payload = mapOf(
                        "_token" to csrfToken,
                        "components" to listOf(
                            mapOf(
                                "snapshot" to snapshotStr,
                                "updates" to mapOf("query" to query),
                                "calls" to emptyList<Any>()
                            )
                        )
                    )

                    val postRes = app.post(updateUrl, headers = headers, json = payload)
                    if (postRes.code == 200) {
                        val responseJson = AppUtils.parseJson<Map<String, Any>>(postRes.text)
                        val components = responseJson["components"] as? List<Map<String, Any>>
                        val effects = components?.firstOrNull()?.get("effects") as? Map<String, Any>
                        val htmlContent = effects?.get("html") as? String

                        if (!htmlContent.isNullOrBlank()) {
                            val soupResults = Jsoup.parse(htmlContent)
                            val list = soupResults.select("a.simple-title-card, a.video-card").mapNotNull { item ->
                                val rawTitle = item.selectFirst("h4, h3, .title")?.text()?.trim() ?: return@mapNotNull null
                                val title = cleanTitleText(rawTitle)
                                val link = toAbsoluteUrl(item.attr("href"))
                                val img = getPosterUrl(item)

                                val ratingTag = item.selectFirst(".badge")?.text()?.trim() ?: ""
                                val isMovie = ratingTag.contains("Movie", true) || ratingTag.contains("Film", true) || title.contains("فيلم")
                                val type = if (isMovie) TvType.AnimeMovie else TvType.Anime

                                newAnimeSearchResponse(title, link, type) {
                                    this.posterUrl = img
                                }
                            }
                            if (list.isNotEmpty()) return list
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Livewire search failed: ${e.message}")
        }

        // Method 2: Direct Search via URL
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$mainUrl/titles?q=$encodedQuery"
            val doc = getDocumentSmart(searchUrl)
            if (doc != null) {
                val results = doc.select("a.simple-title-card, a.video-card, .grid a[href*='/titles/']").mapNotNull {
                    toSearchResult(it)
                }.distinctBy { it.url }
                if (results.isNotEmpty()) return results
            }
        } catch (_: Exception) {}

        return emptyList()
    }

    private fun cleanTitleText(text: String): String {
        return text.replace("\\n", " ")
            .replace("\n", " ")
            .replace(Regex("""بترجمة.*"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private suspend fun forceLoadAllEpisodes(url: String, timeoutMs: Long = 20000L): Document? =
        suspendCoroutine { cont ->
            Handler(Looper.getMainLooper()).post {
                val ctx = getSafeContext()

                val webView = WebView(ctx)
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    userAgentString = USER_AGENT
                    blockNetworkImage = false
                    loadsImagesAutomatically = true
                    mediaPlaybackRequiresUserGesture = true
                    javaScriptCanOpenWindowsAutomatically = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                }
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(webView, true)
                var finished = false

                fun finish(doc: Document?) {
                    if (finished) return
                    finished = true
                    try { webView.stopLoading(); webView.destroy() } catch (_: Exception) {}
                    try {
                        cookieManager.flush()
                        val newCookies = cookieManager.getCookie(url)
                        if (!newCookies.isNullOrEmpty()) savedCookies = newCookies
                    } catch (_: Exception) {}
                    cont.resume(doc)
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                        super.onPageFinished(view, loadedUrl)
                        var attempts = 0
                        val maxAttempts = 40
                        val handler = Handler(Looper.getMainLooper())
                        val checkRunnable = object : Runnable {
                            override fun run() {
                                if (finished) return
                                val jsCheck = """
                                    (function() {
                                        var count = document.querySelectorAll('.video-list a, .episodes-list a, a[href*="/episode/"]').length;
                                        if (count > 0) return document.documentElement.outerHTML;
                                        return null;
                                    })();
                                """.trimIndent()

                                view?.evaluateJavascript(jsCheck) { html ->
                                    if (html != null && html != "null" && html.length > 100) {
                                        var cleanHtml = html
                                        if (cleanHtml.startsWith("\"") && cleanHtml.endsWith("\"")) cleanHtml = cleanHtml.substring(1, cleanHtml.length - 1)
                                        cleanHtml = cleanHtml.replace("\\u003C", "<").replace("\\u003E", ">").replace("\\\"", "\"").replace("\\\\", "\\")
                                        finish(Jsoup.parse(cleanHtml))
                                    } else {
                                        attempts++
                                        if (attempts < maxAttempts) handler.postDelayed(this, 250)
                                        else finish(null)
                                    }
                                }
                            }
                        }
                        checkRunnable.run()
                    }
                }
                webView.loadUrl(url)
                Handler(Looper.getMainLooper()).postDelayed({ finish(null) }, timeoutMs)
            }
        }

    override suspend fun load(url: String): LoadResponse? {
        val fullUrl = toAbsoluteUrl(url)
        val doc = forceLoadAllEpisodes(fullUrl) ?: getDocumentSmart(fullUrl) ?: return null

        return try {
            var rawTitle = doc.selectFirst("h1, .title-name")?.text() ?: ""
            rawTitle = cleanTitleText(rawTitle)

            val title = TITLE_EP_REGEX.replace(rawTitle, "")
                .replace("( مسلسل )", "")
                .replace("( فيلم )", "")
                .trim()

            val poster = getPosterUrl(doc.selectFirst("img[alt*='بوستر'], .poster img, .poster, img.cover, .video-info img, [property='og:image']"))
                ?: doc.selectFirst("meta[property='og:image']")?.attr("content")?.let { toAbsoluteUrl(it) }
                ?: ""

            val elements = doc.select(".video-list a, .episodes-list a, a[href*='/episode/']")
            var episodes = elements.mapNotNull { element ->
                val rawHref = element.attr("href")
                if (rawHref.isNullOrBlank()) return@mapNotNull null
                val href = toAbsoluteUrl(rawHref)

                val videoData = element.selectFirst(".video-data")
                val epText = cleanTitleText(videoData?.selectFirst("span")?.text() ?: videoData?.children()?.getOrNull(0)?.text() ?: element.text())
                val epNum = NON_DIGITS.replace(epText, "").toIntOrNull()
                val epName = cleanTitleText(videoData?.selectFirst("p")?.text() ?: videoData?.children()?.getOrNull(1)?.text() ?: "")
                val imgAttr = getPosterUrl(element) ?: ""

                newEpisode(href) {
                    name = if (epName.isNotBlank()) epName else epText
                    episode = epNum
                    posterUrl = imgAttr
                }
            }

            if (episodes.size > 1) {
                val firstEpNum = episodes.first().episode ?: 0
                val lastEpNum = episodes.last().episode ?: 0
                if (firstEpNum > lastEpNum && lastEpNum != 0) {
                    episodes = episodes.reversed()
                }
            }

            var desc = ""
            if (episodes.isNotEmpty()) {
                try {
                    val sampleEpisodeUrl = episodes.first().data
                    val epDoc = app.get(sampleEpisodeUrl).document
                    desc = epDoc.select("div.py-4.flex.flex-col.gap-2 p, p.synopsis, .description").joinToString("\n") { it.text().trim() }
                    if (desc.isBlank()) {
                        desc = epDoc.select("meta[name=description]").attr("content").trim()
                    }
                } catch (_: Exception) {}
            }

            if (desc.isBlank()) {
                desc = doc.select("div.py-4.flex.flex-col.gap-2 p, p.synopsis, .description").joinToString("\n") { it.text().trim() }
            }

            val tags = doc.select("a[href*='/genre/'], a[href*='/genres/'], .badge").map { it.text().trim() }
            val year = Regex("""\b(19\d\d|20\d\d)\b""").find(doc.text())?.groupValues?.get(1)?.toIntOrNull()

            val isMovie = fullUrl.contains("/movie", true) || title.contains("فيلم", true) || episodes.size == 1

            if (isMovie && episodes.isNotEmpty()) {
                newMovieLoadResponse(title, fullUrl, TvType.AnimeMovie, episodes.first().data) {
                    this.posterUrl = poster
                    this.plot = desc
                    this.tags = tags
                    this.year = year
                }
            } else {
                newTvSeriesLoadResponse(title, fullUrl, TvType.Anime, episodes) {
                    this.posterUrl = poster
                    this.plot = desc
                    this.tags = tags
                    this.year = year
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Load error: ${e.message}")
            null
        }
    }

    private suspend fun hijackAndExtractRaw(
        url: String,
        timeoutMs: Long = 45_000L
    ): Pair<List<Pair<String, String>>, List<String>> = suspendCoroutine { cont ->
        Handler(Looper.getMainLooper()).post {
            val ctx = getSafeContext()

            val webView = WebView(ctx)
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = USER_AGENT
                blockNetworkImage = false
                mediaPlaybackRequiresUserGesture = false
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

            val extractedRaw = mutableListOf<Pair<String, String>>()
            val extractedEmbeds = mutableListOf<String>()
            var isDone = false
            val handler = Handler(Looper.getMainLooper())

            fun finish() {
                if (isDone) return
                isDone = true
                try {
                    handler.removeCallbacksAndMessages(null)
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView.destroy()
                } catch (_: Exception) {}
                cont.resume(Pair(extractedRaw.distinctBy { it.first }, extractedEmbeds.distinct()))
            }

            handler.postDelayed({ finish() }, timeoutMs)

            webView.webViewClient = object : WebViewClient() {
                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(v: WebView?, h: SslErrorHandler?, e: android.net.http.SslError?) {
                    h?.proceed()
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?
                ): android.webkit.WebResourceResponse? {
                    val reqUrl = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)

                    // 1. Intercept /player/
                    if (reqUrl.contains("/player/") && !reqUrl.contains("cf_token=")) {
                        Thread {
                            try {
                                val connection = URL(reqUrl).openConnection() as HttpURLConnection
                                connection.requestMethod = "GET"
                                request.requestHeaders?.forEach { (k, v) ->
                                    if (!k.equals("Accept-Encoding", true)) connection.setRequestProperty(k, v)
                                }
                                CookieManager.getInstance().getCookie(url)?.let { connection.setRequestProperty("Cookie", it) }
                                connection.setRequestProperty("Referer", url)

                                val playerHtml = (if (connection.responseCode < 400) connection.inputStream else connection.errorStream).bufferedReader().readText()
                                val jsonPattern = """var\s+video_sources\s*=\s*(\[[^;]+]);""".toRegex()
                                val jsonMatch = jsonPattern.find(playerHtml)

                                if (jsonMatch != null) {
                                    val jsonStr = jsonMatch.groupValues[1]
                                    val linksFromJson = AppUtils.parseJson<List<Map<String, Any?>>>(jsonStr)
                                    linksFromJson.forEach { item ->
                                        val src = item["src"]?.toString() ?: item["file"]?.toString()
                                        val label = item["label"]?.toString() ?: "Default"
                                        if (!src.isNullOrBlank()) extractedRaw.add(src to label)
                                    }

                                    if (extractedRaw.isNotEmpty()) {
                                        handler.post { finish() }
                                    }
                                }
                            } catch (_: Exception) {}
                        }.start()
                        return super.shouldInterceptRequest(view, request)
                    }

                    // 2. Intercept /sources with cf_token=
                    if (reqUrl.contains("/sources") && reqUrl.contains("cf_token=")) {
                        try {
                            val connection = URL(reqUrl).openConnection() as HttpURLConnection
                            connection.requestMethod = "GET"
                            request.requestHeaders?.forEach { (k, v) ->
                                if (!k.equals("Accept-Encoding", true)) connection.setRequestProperty(k, v)
                            }
                            CookieManager.getInstance().getCookie(reqUrl)?.let { connection.setRequestProperty("Cookie", it) }

                            val responseBytes = (if (connection.responseCode < 400) connection.inputStream else connection.errorStream).readBytes()
                            val jsonString = String(responseBytes, Charsets.UTF_8)

                            val linksFromJson = AppUtils.parseJson<List<Map<String, Any?>>>(jsonString)
                            linksFromJson.forEach { item ->
                                val src = item["src"]?.toString() ?: item["file"]?.toString()
                                val label = item["label"]?.toString() ?: "Default"
                                if (!src.isNullOrBlank()) extractedRaw.add(src to label)
                            }

                            if (extractedRaw.isNotEmpty()) {
                                handler.post { finish() }
                            }

                            val contentType = connection.contentType?.split(";")?.get(0) ?: "application/json"
                            return android.webkit.WebResourceResponse(contentType, "UTF-8", ByteArrayInputStream(responseBytes)).apply {
                                responseHeaders = mutableMapOf("Access-Control-Allow-Origin" to "*")
                            }
                        } catch (_: Exception) {}
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                    super.onPageFinished(view, loadedUrl)
                    val jsEmbedCheck = """
                        (function() {
                            var embeds = [];
                            document.querySelectorAll('iframe').forEach(function(f) {
                                if (f.src && !f.src.includes('cloudflare')) embeds.push(f.src);
                            });
                            return embeds.join('|||');
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(jsEmbedCheck) { res ->
                        val clean = res?.removeSurrounding("\"")
                        if (!clean.isNullOrBlank()) {
                            clean.split("|||").forEach { embed ->
                                if (embed.isNotBlank()) extractedEmbeds.add(embed)
                            }
                        }
                    }
                }
            }

            webView.loadUrl(url)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val (rawLinks, embedLinks) = hijackAndExtractRaw(data)

        var foundLinks = false

        // 1. Process Direct Video Sources
        rawLinks.forEach { (src, label) ->
            try {
                val qualityInt = extractQuality(label)
                val isM3u8 = src.contains(".m3u8", ignoreCase = true)

                callback(
                    newExtractorLink(
                        source = this.name,
                        name = "${this.name} $label",
                        url = src,
                        type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://video.vid3rb.com/"
                        this.quality = qualityInt
                    }
                )
                foundLinks = true
            } catch (_: Exception) {}
        }

        // 2. Process External Iframe Embed Servers
        embedLinks.forEach { embedUrl ->
            try {
                loadExtractor(embedUrl, data, subtitleCallback, callback)
                foundLinks = true
            } catch (_: Exception) {}
        }

        return foundLinks
    }

    private fun extractQuality(label: String): Int {
        return Regex("""(\d{3,4})p""", RegexOption.IGNORE_CASE)
            .find(label)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: when {
                label.contains("FHD", true) || label.contains("1080", true) -> Qualities.P1080.value
                label.contains("HD", true) || label.contains("720", true) -> Qualities.P720.value
                label.contains("SD", true) || label.contains("480", true) -> Qualities.P480.value
                label.contains("360", true) -> Qualities.P360.value
                else -> Qualities.Unknown.value
            }
    }
}
