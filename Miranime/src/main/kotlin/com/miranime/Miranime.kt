package com.miranime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.net.URLEncoder

class Miranime : MainAPI() {
    override var mainUrl = "https://miranime.net"
    override var name = "Miranime"
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

        for (attr in listOf("src", "data-src", "data-lazy-src", "data-original", "srcset", "data-srcset", "content")) {
            var v = img.attr(attr).trim()
            if (v.isNotBlank() && !v.startsWith("data:image", true) && !v.startsWith("data:text", true)) {
                if (attr.contains("srcset")) {
                    v = v.substringBefore(" ").substringBefore(",").trim()
                }
                if (v.contains("/_next/image?url=")) {
                    val inner = Regex("""url=([^&]+)""").find(v)?.groupValues?.getOrNull(1)
                    if (!inner.isNullOrBlank()) {
                        try {
                            v = URLDecoder.decode(inner, "UTF-8")
                        } catch (_: Exception) {}
                    }
                }
                if (v.isNotBlank() && !v.startsWith("data:", true) && !v.contains("Logo", true)) {
                    return toAbsoluteUrl(v)
                }
            }
        }

        val style = img.attr("style").ifBlank { element.attr("style") }
        if (style.isNotBlank()) {
            val bgMatch = Regex("""url\(['"]?(.*?)['"]?\)""").find(style)
            if (bgMatch != null) {
                val bgUrl = bgMatch.groupValues[1].trim()
                if (bgUrl.isNotBlank() && !bgUrl.startsWith("data:", true) && !bgUrl.contains("Logo", true)) {
                    return toAbsoluteUrl(bgUrl)
                }
            }
        }

        return null
    }

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Update Terbaru",
        "$mainUrl/ongoing-anime" to "Anime Ongoing",
        "$mainUrl/completed-anime" to "Anime Completed",
        "$mainUrl/genre/action" to "Action",
        "$mainUrl/genre/adult-cast" to "Adult Cast",
        "$mainUrl/genre/adventure" to "Adventure",
        "$mainUrl/genre/anthropomorphic" to "Anthropomorphic",
        "$mainUrl/genre/childcare" to "Childcare",
        "$mainUrl/genre/comedy" to "Comedy",
        "$mainUrl/genre/crossdressing" to "Crossdressing",
        "$mainUrl/genre/demons" to "Demons",
        "$mainUrl/genre/drama" to "Drama",
        "$mainUrl/genre/ecchi" to "Ecchi",
        "$mainUrl/genre/fantasy" to "Fantasy",
        "$mainUrl/genre/romance" to "Romance"
    )

    private fun isExcludedLink(href: String): Boolean {
        val path = href.removePrefix(mainUrl).trim()
        if (path.isBlank() || path == "/" || path.startsWith("#") || path.startsWith("javascript:")) return true
        val excludedPrefixes = listOf(
            "/genre", "/genres", "/daftar", "/ongoing", "/completed",
            "/contact", "/privacy", "/dmca", "/jadwal", "/masuk",
            "/search", "/admin", "/profil", "/lupa-password", "/reset-password"
        )
        return excludedPrefixes.any { path.startsWith(it, ignoreCase = true) }
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        return try {
            val a = if (element.tagName().equals("a", true)) element else element.selectFirst("a[href]") ?: return null
            val href = toAbsoluteUrl(a.attr("href"))
            if (href.isBlank() || isExcludedLink(href)) return null

            val img = a.selectFirst("img") ?: element.selectFirst("img")
            var rawTitle = img?.attr("alt")?.trim().orEmpty()
            if (rawTitle.isBlank()) {
                rawTitle = a.attr("title").trim()
            }
            if (rawTitle.isBlank()) {
                rawTitle = element.selectFirst("h2, h3, h4, .title, p")?.text()?.trim().orEmpty()
            }
            if (rawTitle.isBlank()) {
                rawTitle = a.text().trim()
            }

            rawTitle = rawTitle.lines().firstOrNull()?.trim() ?: ""
            if (rawTitle.isBlank() || rawTitle.equals("Detail", true) || rawTitle.equals("Tonton Sekarang", true)) {
                val slug = href.removeSuffix("/").substringAfterLast("/")
                rawTitle = slug.replace("-sub-indo", "", ignoreCase = true)
                    .replace("-subtitle-indonesia", "", ignoreCase = true)
                    .replace("-", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
            }

            val poster = getPosterUrl(img ?: element)

            val isMovie = href.contains("/movie", true) || href.contains("-movie-", true)
            val type = if (isMovie) TvType.AnimeMovie else TvType.Anime

            val epText = element.selectFirst(".badge, .ep, [class*='badge']")?.text()?.trim()
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
                if (page <= 1) "$mainUrl/" else "$mainUrl/ongoing-anime?page=$page"
            }
            page <= 1 -> request.data
            request.data.contains("?") -> "${request.data}&page=$page"
            else -> "${request.data}?page=$page"
        }

        val doc = app.get(targetUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).document

        val cards = doc.select("a[href]").mapNotNull {
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
        val searchUrl = "$mainUrl/search?keyword=$encodedQuery"

        val doc = app.get(searchUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).document

        return doc.select("a[href]").mapNotNull {
            toSearchResult(it)
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val fullUrl = toAbsoluteUrl(url)
        val doc = app.get(fullUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).document

        val rawTitle = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property='og:title']")?.attr("content")?.trim()
            ?: fullUrl.removeSuffix("/").substringAfterLast("/").replace("-", " ")

        val title = rawTitle.replace("Nonton dan Download", "", ignoreCase = true)
            .replace("Nonton", "", ignoreCase = true)
            .replace("Sub Indo", "", ignoreCase = true)
            .replace("Subtitle Indonesia", "", ignoreCase = true)
            .replace("— Miranime", "", ignoreCase = true)
            .replace("- Miranime", "", ignoreCase = true)
            .replace("Miranime", "", ignoreCase = true)
            .trim()

        val poster = getPosterUrl(doc.selectFirst("meta[property='og:image'], img[src*='/covers/'], img[src*='image?url=']"))
            ?: doc.selectFirst("meta[property='og:image']")?.attr("content")?.let { toAbsoluteUrl(it) }
            ?: ""

        val plot = doc.select("p.text-muted-foreground, p").firstOrNull {
            it.text().trim().length > 30
        }?.text()?.trim().orEmpty().ifBlank {
            doc.selectFirst("meta[property='og:description']")?.attr("content")?.trim() ?: ""
        }

        val tags = doc.select("a[href*='/genre/']").map { it.text().trim() }.distinct()
        val year = doc.selectFirst(".year, .meta, span, div")?.text()?.let {
            Regex("""\b(19\d\d|20\d\d)\b""").find(it)?.groupValues?.get(1)?.toIntOrNull()
        }

        // Extract Episode List
        val epElements = doc.select("a[href*='/nonton/']")
        val episodes = epElements.mapNotNull { el ->
            val href = toAbsoluteUrl(el.attr("href"))
            if (href.isBlank() || href == fullUrl || href == "$mainUrl/") return@mapNotNull null

            val epText = el.text().trim()
            val epNum = Regex("""Episode\s*(\d+)""", RegexOption.IGNORE_CASE).find(epText)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("""-episode-(\d+)""", RegexOption.IGNORE_CASE).find(href)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("""\b(\d+)\b""").find(epText)?.groupValues?.getOrNull(1)?.toIntOrNull()

            newEpisode(href) {
                this.name = if (epNum != null) "Episode $epNum" else epText.lines().firstOrNull()?.trim() ?: "Episode"
                this.episode = epNum
            }
        }.distinctBy { it.data }

        val isMovie = episodes.isEmpty() || fullUrl.contains("/movie", true) || fullUrl.contains("-movie-", true)

        return if (isMovie) {
            newMovieLoadResponse(title, fullUrl, TvType.AnimeMovie, fullUrl) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.year = year
            }
        } else {
            // Sort ascending (Episode 1, 2, 3...)
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
        val res = app.get(pageUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/"))
        val html = res.text
        val doc = res.document

        var foundAny = false

        // 1. Extract sources JSON array from Next.js RSC payload
        val sourcesMatches = Regex("""\"sources\":\s*(\[.*?\])(?:,\s*\"[a-zA-Z0-9_-]+\"|\})""").findAll(html)
        for (m in sourcesMatches) {
            try {
                val rawSources = m.groupValues[1].replace("\\\"", "\"")
                val jsonArr = JSONArray(rawSources)
                for (i in 0 until jsonArr.length()) {
                    val srcObj = jsonArr.optJSONObject(i) ?: continue
                    val link = srcObj.optString("link", "").trim()
                    val reso = srcObj.optString("reso", "").trim()
                    val provider = srcObj.optString("provider", "Server").trim()

                    if (link.isNotBlank() && !link.startsWith("javascript:")) {
                        val quality = when {
                            reso.contains("1080", true) -> Qualities.P1080.value
                            reso.contains("720", true) -> Qualities.P720.value
                            reso.contains("480", true) -> Qualities.P480.value
                            reso.contains("360", true) -> Qualities.P360.value
                            else -> Qualities.Unknown.value
                        }

                        if (link.contains("api.miranime.net") || link.contains(".mp4", true) || link.contains(".m3u8", true)) {
                            val isM3u8 = link.contains(".m3u8", true)
                            callback(
                                newExtractorLink(
                                    source = this.name,
                                    name = "${this.name} - $provider $reso",
                                    url = link,
                                    type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = "https://miranime.net/"
                                    this.quality = quality
                                }
                            )
                            foundAny = true
                        } else {
                            try {
                                loadExtractor(link, pageUrl, subtitleCallback, callback)
                                foundAny = true
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Extract embedded URLs from page links and buttons
        doc.select("a[href*='mirrored.to'], a[href*='gofile.io'], a[href*='lulustream'], a[href*='luluvid'], a[href*='abyss'], a[href*='streamwish'], a[href*='filelions']").forEach { a ->
            val href = a.attr("href").trim()
            if (href.startsWith("http")) {
                try {
                    loadExtractor(href, pageUrl, subtitleCallback, callback)
                    foundAny = true
                } catch (_: Exception) {}
            }
        }

        // 3. Fallback direct iframes
        doc.select("iframe[src], iframe[data-src]").forEach { ifr ->
            val src = toAbsoluteUrl(ifr.attr("src").ifBlank { ifr.attr("data-src") })
            if (src.isNotBlank() && !src.startsWith("about:") && !src.startsWith("javascript:")) {
                try {
                    loadExtractor(src, pageUrl, subtitleCallback, callback)
                    foundAny = true
                } catch (_: Exception) {}
            }
        }

        return foundAny
    }
}
