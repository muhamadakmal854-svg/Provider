package com.mtsflix.kissasian

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Kissasian Provider (KissasianTV.my)
 * Supports Asian Dramas, Movies, and Shows.
 *
 * Intermediate player chain:
 * Episode Page -> kisskh.space -> Vidmoly / Streamtape / Mixdrop -> m3u8
 */
class Kissasian : MainAPI() {
    override var mainUrl              = "https://kissasiantv.my"
    override var name                 = "Kissasian"
    override val hasMainPage          = true
    override var lang                 = "id"
    override val hasDownloadSupport   = false
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.AsianDrama, TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        ""                      to "Terbaru",
        "ongoing-popular-drama" to "Ongoing Series",
        "most-popular-drama"    to "Most Popular",
        "genre/action"          to "Action",
        "genre/romance"         to "Romance",
        "genre/comedy"          to "Comedy",
        "genre/drama"           to "Drama",
        "genre/thriller"        to "Thriller",
        "genre/fantasy"         to "Fantasy",
        "genre/historical"      to "Historical"
    )

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun Element.toSearchResult(): SearchResponse? {
        val a = if (tagName() == "a") this else selectFirst("a[href]") ?: return null
        val href = a.attr("abs:href").ifBlank { a.attr("href") }
        if (href.isBlank() || href == mainUrl || href.contains("javascript")) return null

        val img = selectFirst("img") ?: selectFirst("[data-original], [data-src], [data-lazy-src]")
        val title = selectFirst("h2, h3, .title, .film-name, .entry-title")?.text()?.trim()
            ?: a.attr("title").trim()
            ?: img?.attr("alt")?.trim()
            ?: a.text().trim()

        if (title.isBlank() || title.lowercase().contains("banner") || title.lowercase().contains("iklan")) return null

        val poster = img?.let {
            it.attr("data-original")
                .ifBlank { it.attr("data-src") }
                .ifBlank { it.attr("data-lazy-src") }
                .ifBlank { it.attr("src") }
        }?.let { fixUrl(it) }

        val isMovie = href.contains("/movie/") || href.contains("/movies/")
        return if (isMovie) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.AsianDrama) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path = request.data
        val url = if (path.isEmpty()) {
            if (page > 1) "$mainUrl/page/$page/" else "$mainUrl/"
        } else {
            if (page > 1) "$mainUrl/$path/page/$page/" else "$mainUrl/$path/"
        }

        val doc = app.get(url, headers = mapOf("User-Agent" to UA, "Referer" to mainUrl)).document
        val results = doc.select("article, .item, .ml-item, .list-episode-item-2 li, li:has(img)")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        return newHomePageResponse(request.name, results, hasNext = true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.encodeUrl()}"
        val doc = app.get(url, headers = mapOf("User-Agent" to UA, "Referer" to mainUrl)).document
        return doc.select("article, .item, .ml-item, .list-episode-item-2 li, li:has(img)")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = mapOf("User-Agent" to UA, "Referer" to mainUrl)).document
        val title = doc.selectFirst("h1.entry-title, h1, .heading-name, meta[property='og:title']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text()
        }?.replace(" - REBAHIN", "")?.trim().orEmpty()

        val poster = doc.selectFirst("meta[property='og:image']")?.attr("content")
            ?: doc.selectFirst(".poster img, .sheader .poster img, .film-poster img, img.wp-post-image, img")?.let {
                it.attr("data-original").ifBlank { it.attr("data-src") }.ifBlank { it.attr("src") }
            }
        val plot = doc.selectFirst("meta[property='og:description']")?.attr("content")
            ?: doc.selectFirst(".description p, .entry-content p, .synopsis, .overview")?.text()?.trim()

        val tags = doc.select(".genres a, .genre a, .categories a").map { it.text() }.filter { it.isNotBlank() }

        // Check episode list
        val epLinks = doc.select("ul.list-episode-item-2 li a, ul.all-episode li a, .list-episode-item a, a[href*='-ep-'], a[href*='hd-korean-movie']")
            .mapNotNull { a ->
                val href = a.attr("abs:href").ifBlank { a.attr("href") }
                if (href.isBlank()) return@mapNotNull null
                val epText = a.selectFirst(".epl-num, span")?.text() ?: a.text()
                val epNum = Regex("""ep[a-z]*\s*(\d+)""", RegexOption.IGNORE_CASE).find(epText)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""-ep-(\d+)""", RegexOption.IGNORE_CASE).find(href)?.groupValues?.get(1)?.toIntOrNull()
                    ?: 1
                
                newEpisode(href) {
                    this.name = epText.trim()
                    this.episode = epNum
                }
            }
            .distinctBy { it.data }
            .reversed()

        return if (epLinks.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.AsianDrama, epLinks) {
                this.posterUrl = poster?.let { fixUrl(it) }
                this.plot = plot
                this.tags = tags
            }
        } else {
            val isEpisodePage = url.contains("-ep-") || url.contains("hd-korean-movie")
            if (isEpisodePage) {
                val eps = listOf(newEpisode(url) {
                    this.name = title
                    this.episode = 1
                })
                newTvSeriesLoadResponse(title, url, TvType.AsianDrama, eps) {
                    this.posterUrl = poster?.let { fixUrl(it) }
                    this.plot = plot
                    this.tags = tags
                }
            } else {
                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster?.let { fixUrl(it) }
                    this.plot = plot
                    this.tags = tags
                }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val doc = app.get(data, headers = mapOf("User-Agent" to UA, "Referer" to mainUrl)).document
            val intermediateEmbeds = mutableListOf<String>()

            // 1. Collect initial embeds from episode page
            doc.select("iframe[src], iframe[data-src]").forEach { iframe ->
                val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }.trim()
                if (src.isNotBlank() && src.startsWith("http")) intermediateEmbeds.add(src)
            }
            doc.select("li[data-video], .linkserver").forEach { li ->
                val src = li.attr("data-video").trim()
                if (src.isNotBlank() && src.startsWith("http")) intermediateEmbeds.add(src)
            }

            val finalVideoEmbeds = mutableListOf<String>()

            // 2. Resolve intermediate player pages (e.g. kisskh.space)
            intermediateEmbeds.distinct().forEach { embedUrl ->
                if (embedUrl.contains("kisskh.space") || embedUrl.contains("embed") || embedUrl.contains("player")) {
                    try {
                        val embDoc = app.get(embedUrl, headers = mapOf("User-Agent" to UA, "Referer" to data)).document
                        embDoc.select("li[data-video], .linkserver").forEach { li ->
                            val v = li.attr("data-video").trim()
                            if (v.isNotBlank() && v.startsWith("http")) finalVideoEmbeds.add(v)
                        }
                        embDoc.select("iframe[src], iframe[data-src]").forEach { iframe ->
                            val v = iframe.attr("src").ifBlank { iframe.attr("data-src") }.trim()
                            if (v.isNotBlank() && v.startsWith("http")) finalVideoEmbeds.add(v)
                        }
                    } catch (e: Exception) {
                        Log.e("Kissasian", "Error fetching intermediate embed $embedUrl: ${e.message}")
                        finalVideoEmbeds.add(embedUrl)
                    }
                } else {
                    finalVideoEmbeds.add(embedUrl)
                }
            }

            // 3. Process final video embeds — PRIORITIZE Vidmoly first as primary working server
            val sortedEmbeds = finalVideoEmbeds.distinct().sortedByDescending { it.contains("vidmoly") }

            sortedEmbeds.forEach { videoUrl ->
                if (videoUrl.contains("vidmoly")) {
                    extractVidmoly(videoUrl, data, callback)
                } else {
                    loadExtractor(videoUrl, data, subtitleCallback, callback)
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("Kissasian", "loadLinks error: ${e.message}")
            return false
        }
    }

    private suspend fun extractVidmoly(url: String, referer: String, callback: (ExtractorLink) -> Unit) {
        try {
            val html = app.get(url, headers = mapOf("User-Agent" to UA, "Referer" to referer)).text
            val match = Regex("""file\s*:\s*["'](https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)["']""").find(html)
                ?: Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'>]*)""").find(html)

            if (match != null) {
                val m3u8Url = match.groupValues[1]
                M3u8Helper.generateM3u8(
                    "Vidmoly",
                    m3u8Url,
                    url,
                    headers = mapOf("User-Agent" to UA, "Referer" to url)
                ).forEach(callback)
            }
        } catch (e: Exception) {
            Log.e("Kissasian", "Vidmoly extraction error: ${e.message}")
        }
    }

    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")

    companion object {
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }
}
