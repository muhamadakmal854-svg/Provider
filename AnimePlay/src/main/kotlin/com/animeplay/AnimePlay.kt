package com.animeplay

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder

class AnimePlay : MainAPI() {
    override var mainUrl = "https://animeplay.org"
    override var name = "AnimePlay"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
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

        for (attr in listOf("data-src", "data-lazy-src", "data-original", "src", "data-cfsrc", "srcset", "data-srcset", "content")) {
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

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Update Terbaru",
        "$mainUrl/best-rating/" to "Best Rating",
        "$mainUrl/order-by-title/" to "Daftar A-Z",
        "$mainUrl/category/action/" to "Action",
        "$mainUrl/category/adventure/" to "Adventure",
        "$mainUrl/category/comedy/" to "Comedy",
        "$mainUrl/category/crime/" to "Crime",
        "$mainUrl/category/drama/" to "Drama",
        "$mainUrl/category/fantasy/" to "Fantasy",
        "$mainUrl/category/mystery/" to "Mystery",
        "$mainUrl/category/romance/" to "Romance",
        "$mainUrl/category/science-fiction/" to "Science Fiction",
        "$mainUrl/category/thriller/" to "Thriller"
    )

    private fun toSearchResult(element: Element): SearchResponse? {
        return try {
            val a = element.selectFirst("h2.entry-title a, .entry-title a, a[rel='bookmark'], a.item-title, h3 a, h2 a")
                ?: element.select("a[href]").firstOrNull {
                    val href = it.attr("href")
                    href.isNotBlank() && !href.contains("youtube.com") && !href.contains("youtu.be") &&
                            href != "$mainUrl/" && !href.startsWith("#") && !href.contains("javascript:")
                } ?: return null

            val href = toAbsoluteUrl(a.attr("href"))
            if (href.isBlank() || href == "$mainUrl/" || href.contains("/category/") || href.contains("/year/")) return null

            val rawTitleEl = element.selectFirst("h2.entry-title, .entry-title, .title, h2, h3")
            var title = rawTitleEl?.text()?.trim() ?: a.attr("title").trim().ifEmpty { a.text().trim() }
            title = title.replace("Permalink ke:", "", ignoreCase = true)
                .replace("Nonton Film", "", ignoreCase = true)
                .replace("Nonton Anime", "", ignoreCase = true)
                .trim()
            if (title.isBlank()) return null

            val poster = getPosterUrl(element)

            val isMovie = href.contains("/movie", true) ||
                    (!href.contains("/tv/", true) && !href.contains("/eps/", true) && !href.contains("/episode/", true))
            val type = if (isMovie) TvType.AnimeMovie else TvType.Anime

            val epText = element.selectFirst(".gmr-episode-text, .episode, .ep, .label, .gmr-postitem-season")?.text()?.trim()
            val epNum = epText?.filter { it.isDigit() }?.toIntOrNull()

            newAnimeSearchResponse(title, href, type) {
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
        val targetUrl = if (page <= 1) {
            request.data
        } else {
            val base = request.data.removeSuffix("/")
            "$base/page/$page/"
        }

        val doc = app.get(targetUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).document

        val cards = doc.select(".gmr-item-modulepost, article.item, article.item-infinite, .gmr-box-item, div.item-infinite, .gmr-module-posts .item, .item").mapNotNull {
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

        val doc = app.get(searchUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).document

        return doc.select(".gmr-item-modulepost, article.item, article.item-infinite, .gmr-box-item, div.item-infinite, .gmr-module-posts .item, .item").mapNotNull {
            toSearchResult(it)
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val fullUrl = toAbsoluteUrl(url)
        val doc = app.get(fullUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).document

        val rawTitle = doc.selectFirst("h1.entry-title, .gmr-movie-data h1, h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property='og:title']")?.attr("content")?.trim()
            ?: return null

        val title = rawTitle.replace("Nonton Anime Sub Indo", "", ignoreCase = true)
            .replace("Nonton Anime TV", "", ignoreCase = true)
            .replace("Nonton Anime", "", ignoreCase = true)
            .replace("- ANIMEPLAY", "", ignoreCase = true)
            .replace("ANIMEPLAY", "", ignoreCase = true)
            .trim()

        val poster = doc.selectFirst("meta[property='og:image']")?.attr("content")?.let { toAbsoluteUrl(it) }
            ?: getPosterUrl(doc.selectFirst(".gmr-poster-thumbnail img, .entry-content img, img[itemprop='image']"))
            ?: ""

        val plot = doc.select(".entry-content p, .gmr-moviedata, div[itemprop='description']").joinToString("\n") {
            it.text().trim()
        }.ifBlank {
            doc.selectFirst("meta[property='og:description']")?.attr("content")?.trim() ?: ""
        }

        val tags = doc.select("a[href*='/category/'], a[href*='/genre/'], a[href*='/tag/']").map { it.text().trim() }.distinct()
        val year = doc.selectFirst("a[href*='/year/'], .gmr-movie-data")?.text()?.let {
            Regex("""\b(19\d\d|20\d\d)\b""").find(it)?.groupValues?.get(1)?.toIntOrNull()
        }

        // Extract Episode List for TV Series
        val epElements = doc.select(".gmr-listseries a, ul.episodelist a, .gmr-box-item a, a[href*='/eps/'], a[href*='/episode/'], .gmr-episode-list a")
        val episodes = epElements.mapNotNull { el ->
            val href = toAbsoluteUrl(el.attr("href"))
            if (href.isBlank() || href == fullUrl || href == "$mainUrl/") return@mapNotNull null
            if (!href.contains("/eps/") && !href.contains("/episode/")) return@mapNotNull null

            val epName = el.text().trim()
            val epNum = Regex("""(?:Episode|Eps|Ep|S\d+\s*Eps?)\s*(\d+)""", RegexOption.IGNORE_CASE)
                .find(epName)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: Regex("""\b(\d+)\b""").find(epName)?.groupValues?.getOrNull(1)?.toIntOrNull()

            newEpisode(href) {
                this.name = if (epNum != null) "Episode $epNum" else epName
                this.episode = epNum
            }
        }.distinctBy { it.data }

        val isMovie = episodes.isEmpty() || fullUrl.contains("/movie", true)

        return if (isMovie) {
            newMovieLoadResponse(title, fullUrl, TvType.AnimeMovie, fullUrl) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.year = year
            }
        } else {
            // Sort episodes in ascending order
            val sortedEpisodes = if (episodes.size > 1 && (episodes.first().episode ?: 0) > (episodes.last().episode ?: 0)) {
                episodes.reversed()
            } else {
                episodes
            }

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
        val doc = app.get(pageUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).document

        val candidateUrls = mutableSetOf<String>()

        // 1. Direct Iframes
        doc.select("iframe[src], iframe[data-src], iframe[data-litespeed-src]").forEach { ifr ->
            val src = ifr.attr("src").ifEmpty { ifr.attr("data-src").ifEmpty { ifr.attr("data-litespeed-src") } }.trim()
            if (src.isNotBlank() && !src.startsWith("about:") && !src.startsWith("javascript:")) {
                candidateUrls.add(toAbsoluteUrl(src))
            }
        }

        // 2. Server Tabs / Buttons
        doc.select(".gmr-player-nav a[href], ul.muvipro-player-tabs li a[href], .gmr-server-wrap a[href], .server-item a[href]").forEach { tab ->
            val href = tab.attr("href").trim()
            if (href.isNotBlank() && !href.startsWith("#") && !href.startsWith("javascript:")) {
                val resolvedTab = toAbsoluteUrl(href)
                if (resolvedTab != pageUrl) {
                    try {
                        val tabDoc = app.get(resolvedTab, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to pageUrl)).document
                        tabDoc.select("iframe[src], iframe[data-src], iframe[data-litespeed-src]").forEach { ifr ->
                            val src = ifr.attr("src").ifEmpty { ifr.attr("data-src").ifEmpty { ifr.attr("data-litespeed-src") } }.trim()
                            if (src.isNotBlank() && !src.startsWith("about:") && !src.startsWith("javascript:")) {
                                candidateUrls.add(toAbsoluteUrl(src))
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // 3. Data attributes for embeds
        doc.select("[data-video], [data-embed], [data-url], [data-src]").forEach { el ->
            listOf("data-video", "data-embed", "data-url", "data-src").forEach { attr ->
                val v = el.attr(attr).trim()
                if (v.isNotBlank() && (v.startsWith("http") || v.startsWith("//") || v.length > 10)) {
                    if (v.startsWith("http") || v.startsWith("//")) {
                        candidateUrls.add(toAbsoluteUrl(v))
                    }
                }
            }
        }

        // 4. Download / stream links
        doc.select("div.download a[href], div.linkdownload a[href], .gmr-download a[href]").forEach { a ->
            val href = a.attr("href").trim()
            if (href.startsWith("http") || href.startsWith("//")) {
                candidateUrls.add(toAbsoluteUrl(href))
            }
        }

        var foundAny = false

        for (candidate in candidateUrls) {
            val cleanCandidate = if (candidate.startsWith("//")) "https:$candidate" else candidate

            // Handle Byseqekaho / Byse API player embeds
            if (cleanCandidate.contains("byseqekaho.com") || cleanCandidate.contains("dismz4n3wp6xnr3.org")) {
                val code = Regex("""/(?:e|v|3lh|d|2tkhl|b0b)/([a-zA-Z0-9]+)""").find(cleanCandidate)?.groupValues?.getOrNull(1)
                if (!code.isNullOrBlank()) {
                    try {
                        val apiUrl = "https://byseqekaho.com/api/videos/$code/embed/details"
                        val apiRes = app.get(
                            apiUrl,
                            headers = mapOf(
                                "User-Agent" to USER_AGENT,
                                "Referer" to "https://animeplay.org/",
                                "Accept" to "application/json"
                            )
                        ).text

                        val json = org.json.JSONObject(apiRes)
                        val frameUrl = json.optString("embed_frame_url", "")
                        if (frameUrl.isNotBlank()) {
                            loadExtractor(frameUrl, pageUrl, subtitleCallback, callback)
                            foundAny = true
                        }
                    } catch (_: Exception) {}
                }
            }

            // Direct Video Streams (.mp4 / .m3u8)
            if (cleanCandidate.contains(".m3u8") || cleanCandidate.contains(".mp4")) {
                val isM3u8 = cleanCandidate.contains(".m3u8")
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = "${this.name} Direct",
                        url = cleanCandidate,
                        type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = pageUrl
                    }
                )
                foundAny = true
            } else {
                // Universal / Custom Extractors (StreamWish, FileLions, VidHide, YouTube, etc.)
                try {
                    loadExtractor(cleanCandidate, pageUrl, subtitleCallback, callback)
                    foundAny = true
                } catch (_: Exception) {}
            }
        }

        return foundAny
    }
}
