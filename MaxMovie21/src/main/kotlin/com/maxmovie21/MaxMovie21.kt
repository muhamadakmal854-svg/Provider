package com.maxmovie21

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * MaxMovie21 — Muvipro WordPress theme
 * URL: https://162.244.93.196/
 *
 * Player mechanism:
 *   - The page exposes `#muvipro_player_content_id` with a `data-id` holding the WP post_id.
 *   - Tab buttons have href="#p1", "#p2" etc.
 *   - Content is loaded via AJAX:
 *     POST /wp-admin/admin-ajax.php
 *       action=muvipro_player_content
 *       tab=p1
 *       post_id=<id>
 *   - Response is raw HTML containing the iframe embed URL.
 */
class MaxMovie21 : MainAPI() {
    override var mainUrl              = "https://162.244.93.196"
    override var name                 = "MaxMovie21"
    override val hasMainPage          = true
    override var lang                 = "id"
    override val hasDownloadSupport   = false
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)

    private val ajaxUrl get()         = "$mainUrl/wp-admin/admin-ajax.php"

    override val mainPage = mainPageOf(
        ""                             to "Film Terbaru",
        "21lap"                        to "21 Lap",
        "21ptx"                        to "21 PTX",
        "21-sub-indo"                  to "21 Sub Indo",
        "21barat"                      to "21 Barat",
        "21ptx/21-ptx-sub-indo"        to "21 PTX Sub Indo",
        "21ptx/zkor21"                 to "Z Kor 21",
    )

    // ─── Helpers ──────────────────────────────────────────────────────────

    /** Extract the clean movie-card items from a listing page */
    private fun Element.toSearchResult(): SearchResponse? {
        // Card anchor: <a href="..." title="..."> … <img …> …
        val href   = attr("abs:href").takeIf { it.isNotBlank() } ?: return null
        val title  = attr("title")
            .ifBlank { selectFirst("h1,h2,h3,.entry-title")?.text() }
            ?.trim()
        if (title.isNullOrBlank()) return null

        // Skip non-content links
        if (href.contains("/category/") || href.contains("/tag/") ||
            href.contains("/page/")     || href.contains("/quality/") ||
            href.contains("whatsapp")   || href.contains("t.me")     ||
            href.contains("youtube.com")) return null

        val poster = selectFirst("img")?.let {
            it.attr("data-src")
                .ifBlank { it.attr("src") }
        }?.let { fixUrl(it) }

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path = request.data
        val url  = if (path.isEmpty()) {
            if (page > 1) "$mainUrl/page/$page/" else "$mainUrl/"
        } else {
            if (page > 1) "$mainUrl/$path/page/$page/" else "$mainUrl/$path/"
        }

        val doc     = app.get(url, headers = mapOf("User-Agent" to UA)).document
        val results = doc.select("article a[href][title]")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        return newHomePageResponse(request.name, results, hasNextPage = true)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page <= 1) "$mainUrl/?s=${query.encodeUrl()}"
                  else           "$mainUrl/page/$page/?s=${query.encodeUrl()}"

        val doc     = app.get(url, headers = mapOf("User-Agent" to UA)).document
        val results = doc.select("article a[href][title]")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        return results.toSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc     = app.get(url, headers = mapOf("User-Agent" to UA)).document
        val title   = doc.selectFirst("h1.entry-title, h1.page-title, h1")?.text()?.trim().orEmpty()
        val poster  = doc.selectFirst("meta[property='og:image']")?.attr("content")
            ?: doc.selectFirst(".gmr-movie-on img, .thumb img")?.attr("src")
        val plot    = doc.selectFirst(".entry-content p, .synopsis, .gmr-movie-content")?.text()?.trim()
        val tags    = doc.select(".gmr-tags a, .entry-categories a, .genre a").map { it.text() }

        // Determine post_id (needed for AJAX player)
        val postId  = extractPostId(doc)

        // Discover all player tab names (p1, p2, …) from the tab list
        val tabs = doc.select("ul.muvipro-player-tabs > li > a[href^='#']")
            .map { it.attr("href").removePrefix("#") }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("p1") }   // fallback

        return newMovieLoadResponse(title, url, TvType.Movie, buildData(postId, tabs)) {
            this.posterUrl = poster?.let { fixUrl(it) }
            this.plot      = plot
            this.tags      = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // data = JSON: {"postId":"12345","tabs":["p1","p2",...]}
        val parsed  = tryParseJson<PlayerData>(data) ?: return false
        val postId  = parsed.postId
        val tabs    = parsed.tabs.ifEmpty { listOf("p1") }

        tabs.amap { tab ->
            try {
                val html = app.post(
                    ajaxUrl,
                    headers = mapOf(
                        "User-Agent"   to UA,
                        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                        "Referer"      to mainUrl,
                        "X-Requested-With" to "XMLHttpRequest",
                    ),
                    data = mapOf(
                        "action"  to "muvipro_player_content",
                        "tab"     to tab,
                        "post_id" to postId,
                    )
                ).text

                if (html.isBlank()) {
                    Log.e("MaxMovie21", "Empty AJAX response for tab=$tab postId=$postId")
                    return@amap
                }

                // Parse the returned HTML fragment for iframes / embed URLs
                val fragment = org.jsoup.Jsoup.parseBodyFragment(html)

                // 1. iframe src
                fragment.select("iframe[src]").forEach { iframe ->
                    val src = iframe.attr("abs:src").ifBlank { iframe.attr("src") }
                    if (src.isNotBlank() && src.startsWith("http")) {
                        loadExtractor(src, mainUrl, subtitleCallback, callback)
                    }
                }

                // 2. data-src (lazy iframes)
                fragment.select("[data-src]").forEach { el ->
                    val src = el.attr("data-src")
                    if (src.isNotBlank() && src.startsWith("http")) {
                        loadExtractor(src, mainUrl, subtitleCallback, callback)
                    }
                }

                // 3. direct MP4 / M3U8 links in <source> or <video>
                fragment.select("source[src], video[src]").forEach { el ->
                    val src = el.attr("src")
                    if (src.isNotBlank()) {
                        loadExtractor(src, mainUrl, subtitleCallback, callback)
                    }
                }

                // 4. Regex fallback for embed URLs inside script/href
                val embedRegex = Regex("""(https?://[^\s"'<>]+(?:embed|play|stream|player|watch|iframe)[^\s"'<>]*)""", RegexOption.IGNORE_CASE)
                embedRegex.findAll(html).forEach { m ->
                    val src = m.groupValues[1]
                    if (src.contains("youtube.com") || src.contains("youtu.be")) return@forEach
                    loadExtractor(src, mainUrl, subtitleCallback, callback)
                }

            } catch (e: Exception) {
                Log.e("MaxMovie21", "loadLinks tab=$tab error: ${e.message}")
            }
        }
        return true
    }

    // ─── Internal helpers ─────────────────────────────────────────────────

    private fun extractPostId(doc: Document): String {
        // Method 1: from script JSON
        doc.select("script").forEach { script ->
            val txt = script.data()
            val m   = Regex(""""post_id"\s*:\s*"?(\d+)"?""").find(txt)
            if (m != null) return m.groupValues[1]
        }
        // Method 2: from body class "postid-XXXXX"
        val bodyClass = doc.body()?.className() ?: ""
        val m = Regex("""postid-(\d+)""").find(bodyClass)
        if (m != null) return m.groupValues[1]
        // Method 3: from #muvipro_player_content_id data-id
        val dataId = doc.select("#muvipro_player_content_id").attr("data-id")
        if (dataId.isNotBlank()) return dataId

        return ""
    }

    private fun buildData(postId: String, tabs: List<String>): String {
        return """{"postId":"$postId","tabs":${tabs.joinToString(",", "[", "]") { "\"$it\"" }}}"""
    }

    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")

    private fun List<SearchResponse>.toSearchResponseList(): SearchResponseList =
        com.lagradost.cloudstream3.toNewSearchResponseList(this)

    data class PlayerData(
        val postId: String,
        val tabs: List<String>
    )

    companion object {
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }
}
