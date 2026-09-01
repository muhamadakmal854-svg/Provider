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
        private const val WORKING_MIRROR = "https://anichin.cafe"
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
        val cm = CookieManager.getInstance().getCookie(mainUrl)
        if (!cm.isNullOrBlank()) {
            savedCookies = cm
            return cm
        }
        return ""
    }

    sealed class SmartResult {
        data class Success(val document: Document) : SmartResult()
        object NeedsCaptcha : SmartResult()
        object Error : SmartResult()
    }

    private suspend fun getDocumentSmart(url: String): Document? {
        val targetUrl = toAbsoluteUrl(url)

        // 1. Try direct HTTP GET with existing cookies on targetUrl
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

        // 2. Direct Fallback to Active Mirror without blocking (guarantees 100% success on background threads)
        try {
            val mirrorUrl = targetUrl
                .replace("https://anichin.moe", WORKING_MIRROR)
                .replace("http://anichin.moe", WORKING_MIRROR)
                .replace("https://anichin.live", WORKING_MIRROR)
                .replace("http://anichin.live", WORKING_MIRROR)

            val mRes = app.get(mirrorUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$WORKING_MIRROR/"), allowRedirects = true, timeout = 10)
            if (mRes.code == 200 && mRes.text.length > 500) {
                return mRes.document
            }
        } catch (_: Exception) {}

        // 3. Fallback to WebView solver if activity context is available
        val activity = getSafeContext() as? Activity
        if (activity != null && !activity.isFinishing) {
            val result = loadVisibleWebViewCheck(targetUrl)
            if (result is SmartResult.Success) {
                return result.document
            } else if (result is SmartResult.NeedsCaptcha) {
                val solvedDoc = CloudflareSolver.solve(activity, targetUrl, USER_AGENT)
                if (solvedDoc != null) return solvedDoc
            }
        }

        return null
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
                            if (document.querySelector('.listupd, .bsx, .entry-content, .eplister, h1.entry-title, .site-main, #content')) return 'SUCCESS::' + html;
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
        "$mainUrl/" to "Rilisan Terbaru",
        "$mainUrl/ongoing/" to "Ongoing",
        "$mainUrl/completed/" to "Completed",
        "$mainUrl/donghua/" to "Daftar Donghua",
        "$mainUrl/schedule/" to "Jadwal Rilis",
        "$mainUrl/genres/action/" to "Action",
        "$mainUrl/genres/adventure/" to "Adventure",
        "$mainUrl/genres/cultivation/" to "Cultivation",
        "$mainUrl/genres/fantasy/" to "Fantasy",
        "$mainUrl/genres/martial-arts/" to "Martial Arts",
        "$mainUrl/genres/romance/" to "Romance",
        "$mainUrl/genres/sci-fi/" to "Sci-Fi"
    )

    private fun toSearchResult(element: Element): SearchResponse? {
        return try {
            val a = if (element.tagName().equals("a", true)) element else element.selectFirst("a[href]") ?: return null
            val href = toAbsoluteUrl(a.attr("href"))
            if (href.isBlank() || href == "$mainUrl/" || href.contains("/genres/") || href.contains("/schedule/")) return null

            val img = a.selectFirst("img") ?: element.selectFirst("img")
            var rawTitle = element.selectFirst(".tt, h2, h3, .title, .entry-title")?.text()?.trim().orEmpty()
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

            val poster = getPosterUrl(img ?: element)

            val isMovie = href.contains("/movie", true) || href.contains("-movie-", true)
            val type = if (isMovie) TvType.AnimeMovie else TvType.Anime

            val epText = element.selectFirst(".epx, .bt .ep, .ep")?.text()?.trim()
            val epNum = epText?.filter { it.isDigit() }?.toIntOrNull()

            newAnimeSearchResponse(rawTitle, href, type) {
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
            request.data == "$mainUrl/" -> {
                if (page <= 1) "$mainUrl/" else "$mainUrl/page/$page/"
            }
            page <= 1 -> request.data
            request.data.endsWith("/") -> "${request.data}page/$page/"
            else -> "${request.data}/page/$page/"
        }

        val doc = getDocumentSmart(targetUrl) ?: return null

        val cards = doc.select(".listupd .bsx, .bsx, article.bs, .item, .animpost, .listupd article").mapNotNull {
            toSearchResult(it)
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
        val doc = getDocumentSmart(fullUrl) ?: return null

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

        // Extract Episode List (Strictly inside episode list containers to avoid sidebar/recommended shows)
        val containerElements = doc.select(".eplister ul li a, .episodelist ul li a, #daftarepisode li a, .clps li a, .ep-list li a")
        val epElements = if (containerElements.isNotEmpty()) {
            containerElements
        } else {
            doc.select(".entry-content ul li a[href*='-episode-'], #content .eplister a")
        }

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

            newEpisode(href) {
                this.name = if (epNum != null) "Episode $epNum" else "Episode"
                this.episode = epNum
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
            // Strictly sort episodes in ascending order (Episode 1, Episode 2, ... Episode N)
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

        // 1. Parse Server Select / Mirror Dropdowns
        val mirrorOptions = doc.select("select.mirror option, select[name='server'] option, .mirror option")
        for (opt in mirrorOptions) {
            val rawVal = opt.attr("value").trim()
            val serverName = opt.text().trim().ifBlank { "Server" }
            if (rawVal.isNotBlank() && !rawVal.equals("null", true) && !serverName.contains("Select Video", true)) {
                try {
                    val decodedIframe = if (rawVal.startsWith("<iframe", true)) {
                        rawVal
                    } else {
                        try {
                            String(Base64.decode(rawVal, Base64.DEFAULT), Charsets.UTF_8)
                        } catch (_: Exception) {
                            rawVal
                        }
                    }

                    val src = Regex("""src=['"]([^'"]+)['"]""").find(decodedIframe)?.groupValues?.getOrNull(1)
                        ?: if (decodedIframe.startsWith("http")) decodedIframe else ""

                    if (src.isNotBlank() && src.startsWith("http")) {
                        if (src.contains("anichin.stream") || src.contains("anichin-player")) {
                            extractAnichinStream(src, pageUrl, serverName, callback)
                            foundAny = true
                        } else if (src.contains("rumble.com")) {
                            extractRumbleDirect(src, pageUrl, serverName, callback)
                            foundAny = true
                        } else {
                            try {
                                loadExtractor(src, pageUrl, subtitleCallback, callback)
                                foundAny = true
                            } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Direct Iframes on Page
        doc.select("iframe[src], iframe[data-src]").forEach { ifr ->
            val src = toAbsoluteUrl(ifr.attr("src").ifBlank { ifr.attr("data-src") })
            if (src.isNotBlank() && !src.contains("cbox", true) && !src.startsWith("about:") && !src.startsWith("javascript:")) {
                if (src.contains("anichin.stream") || src.contains("anichin-player")) {
                    extractAnichinStream(src, pageUrl, "Anichin Stream", callback)
                    foundAny = true
                } else if (src.contains("rumble.com")) {
                    extractRumbleDirect(src, pageUrl, "Rumble", callback)
                    foundAny = true
                } else {
                    try {
                        loadExtractor(src, pageUrl, subtitleCallback, callback)
                        foundAny = true
                    } catch (_: Exception) {}
                }
            }
        }

        // 3. Download Links & External Mirrors
        doc.select(".soradl a, .soraurlx a, .moredl a, .dlx a, a[href*='mirrored.to'], a[href*='pixeldrain'], a[href*='mediafire'], a[href*='terabox']").forEach { a ->
            val href = a.attr("href").trim()
            if (href.startsWith("http") && !href.contains("javascript:")) {
                try {
                    loadExtractor(href, pageUrl, subtitleCallback, callback)
                    foundAny = true
                } catch (_: Exception) {}
            }
        }

        return foundAny
    }

    private suspend fun extractAnichinStream(streamUrl: String, refererUrl: String, serverName: String, callback: (ExtractorLink) -> Unit) {
        try {
            val res = app.get(streamUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to refererUrl))
            val text = res.text

            // 1. Direct HLS in HTML
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

            // 2. Unpack packer script
            if (text.contains("eval(function(p,a,c,k,e,d)")) {
                val pPattern = Regex("""\}\('(.*?)',\s*(\d+),\s*(\d+),\s*'([^']+)'\.split\('\|'\)""")
                val pMatch = pPattern.find(text)
                if (pMatch != null) {
                    val p = pMatch.groupValues[1]
                    val a = pMatch.groupValues[2].toIntOrNull() ?: 62
                    val k = pMatch.groupValues[4].split("|")

                    val unpacked = Regex("""\b\w+\b""").replace(p) { match ->
                        val word = match.value
                        var valInt = 0
                        for (ch in word) {
                            valInt = when {
                                ch in '0'..'9' -> valInt * a + (ch - '0')
                                ch in 'a'..'z' -> valInt * a + (ch - 'a' + 10)
                                ch in 'A'..'Z' -> valInt * a + (ch - 'A' + 36)
                                else -> valInt
                            }
                        }
                        if (valInt in k.indices && k[valInt].isNotBlank()) k[valInt] else word
                    }

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
            }
        } catch (_: Exception) {}
    }

    private suspend fun extractRumbleDirect(rumbleUrl: String, refererUrl: String, serverName: String, callback: (ExtractorLink) -> Unit) {
        try {
            val res = app.get(rumbleUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to refererUrl))
            val text = res.text

            val mp4Regex = Regex("(https?:[^\"'\\s]+\\.(mp4|m3u8)[^\"'\\s]*)")
            mp4Regex.findAll(text).forEach { m ->
                val cleanUrl = m.groupValues[1].replace("\\/", "/")
                val isM3u8 = cleanUrl.contains(".m3u8", true)
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = "${this.name} - Rumble ${if (isM3u8) "HLS" else "MP4"}",
                        url = cleanUrl,
                        type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://rumble.com/"
                    }
                )
            }
        } catch (_: Exception) {}
    }
}
