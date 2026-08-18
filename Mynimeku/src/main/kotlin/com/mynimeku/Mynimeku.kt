package com.mynimeku

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder

class Mynimeku : MainAPI() {
    override var mainUrl = "https://www.mynimeku.com"
    override var name = "Mynimeku"
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

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Update Terbaru",
        "$mainUrl/latest-series/" to "Latest Series",
        "$mainUrl/full-list/mix/o:popular/" to "Anime Populer",
        "$mainUrl/full-list/mix/s:on-going~t:BD,LA,MOVIE,MUSIC,ONA,OVA,SPECIAL,TV/" to "Anime On-Going",
        "$mainUrl/full-list/mix/s:completed~t:BD,LA,MOVIE,MUSIC,ONA,OVA,SPECIAL,TV/" to "Anime Completed",
        "$mainUrl/full-list/mix/t:TV/" to "Anime TV",
        "$mainUrl/full-list/mix/t:MOVIE/" to "Anime Movie",
        "$mainUrl/full-list/mix/t:ONA/" to "ONA / Donghua",
        "$mainUrl/full-list/mix/t:OVA/" to "OVA",
        "$mainUrl/full-list/mix/t:SPECIAL/" to "Special",
        "$mainUrl/genre/action/" to "Action",
        "$mainUrl/genre/adventure/" to "Adventure",
        "$mainUrl/genre/comedy/" to "Comedy",
        "$mainUrl/genre/drama/" to "Drama",
        "$mainUrl/genre/fantasy/" to "Fantasy",
        "$mainUrl/genre/isekai/" to "Isekai",
        "$mainUrl/genre/romance/" to "Romance",
        "$mainUrl/genre/school/" to "School",
        "$mainUrl/genre/sci-fi/" to "Sci-Fi",
        "$mainUrl/genre/shounen/" to "Shounen",
        "$mainUrl/genre/supernatural/" to "Supernatural",
        "$mainUrl/genre/suspense/" to "Suspense"
    )

    private fun toSearchResult(element: Element): SearchResponse? {
        return try {
            val a = if (element.tagName().equals("a", true)) element else element.selectFirst("a[href]") ?: return null
            val href = toAbsoluteUrl(a.attr("href"))
            if (href.isBlank() || href == "$mainUrl/" || href.contains("/genre/") || href.contains("/years/") || href.contains("/season/")) return null

            val img = a.selectFirst("img") ?: element.selectFirst("img")
            val rawTitle = a.attr("title").ifBlank { img?.attr("alt") ?: element.selectFirst(".title, h2, h3, a")?.text()?.trim() ?: a.text().trim() }
            if (rawTitle.isBlank()) return null

            val poster = getPosterUrl(img ?: element)

            val isMovie = href.contains("/movie", true) || href.contains("-movie-", true)
            val type = if (isMovie) TvType.AnimeMovie else TvType.Anime

            val epText = element.selectFirst(".ep, .status, .type")?.text()?.trim()
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
        val targetUrl = if (page <= 1) {
            request.data
        } else {
            val base = request.data.removeSuffix("/")
            "$base/page/$page/"
        }

        val doc = app.get(targetUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).document

        val cards = doc.select("a[href*='/series/'], a[href*='/komik/'], article, .post-item, .item").mapNotNull {
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

        return doc.select("a[href*='/series/'], a[href*='/komik/'], article, .post-item, .item").mapNotNull {
            toSearchResult(it)
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val fullUrl = toAbsoluteUrl(url)
        val doc = app.get(fullUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).document

        val rawTitle = doc.selectFirst("h1.komik-series-hero__title, h1.title, h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property='og:title']")?.attr("content")?.trim()
            ?: return null

        val title = rawTitle.replace("- MyNimeku", "", ignoreCase = true)
            .replace("MyNimeku", "", ignoreCase = true)
            .trim()

        val poster = getPosterUrl(doc.selectFirst("meta[property='og:image'], .thumb img, img[data-lazy-src], .komik-series-hero__thumb img"))
            ?: doc.selectFirst("meta[property='og:image']")?.attr("content")?.let { toAbsoluteUrl(it) }
            ?: ""

        val plot = doc.select(".komik-series-hero__sinopsis, .synopsis, .description, div[itemprop='description'], .entry-content p").joinToString("\n") {
            it.text().trim()
        }.ifBlank {
            doc.selectFirst("meta[property='og:description']")?.attr("content")?.trim() ?: ""
        }

        val tags = doc.select("a[href*='/genre/'], a[href*='/category/'], a[href*='/tag/']").map { it.text().trim() }.distinct()
        val year = doc.selectFirst("a[href*='/years/'], .spe, .data")?.text()?.let {
            Regex("""\b(19\d\d|20\d\d)\b""").find(it)?.groupValues?.get(1)?.toIntOrNull()
        }

        // Extract Episode List
        val epElements = doc.select("ul.episodelist li a, .episodelist a, a[href*='/episode/'], a[href*='/eps/']")
        val episodes = epElements.mapNotNull { el ->
            val href = toAbsoluteUrl(el.attr("href"))
            if (href.isBlank() || href == fullUrl || href == "$mainUrl/") return@mapNotNull null

            val epName = el.text().trim()
            val epNum = Regex("""(?:Episode|Eps|Ep)\s*(\d+)""", RegexOption.IGNORE_CASE)
                .find(epName)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: Regex("""\b(\d+)\b""").find(epName)?.groupValues?.getOrNull(1)?.toIntOrNull()

            newEpisode(href) {
                this.name = if (epNum != null) "Episode $epNum" else epName.lines().firstOrNull()?.trim() ?: "Episode"
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

        var foundAny = false

        // 1. Parse server buttons
        val serverButtons = doc.select("button[data-player-url], button[data-player-host], .mynimeku-episode-server-btn")
        for (btn in serverButtons) {
            val playerUrl = btn.attr("data-player-url").trim()
            val host = btn.attr("data-player-host").trim().ifBlank { btn.text().trim() }
            val type = btn.attr("data-player-type").trim()

            if (playerUrl.isNotBlank() && !playerUrl.startsWith("javascript:")) {
                val fullPlayerUrl = toAbsoluteUrl(playerUrl)
                val quality = when {
                    host.contains("1080", true) -> Qualities.P1080.value
                    host.contains("720", true) -> Qualities.P720.value
                    host.contains("480", true) -> Qualities.P480.value
                    host.contains("360", true) -> Qualities.P360.value
                    else -> Qualities.Unknown.value
                }
                val label = if (type.isNotBlank()) "${type.uppercase()} $host" else host

                // Follow 301 / 302 redirect for myplayerku / workers / Google Drive
                val streamUrl = try {
                    val headRes = app.get(
                        fullPlayerUrl,
                        headers = mapOf("User-Agent" to USER_AGENT, "Referer" to pageUrl),
                        allowRedirects = false
                    )
                    val loc = headRes.headers["location"] ?: headRes.headers["Location"]
                    if (!loc.isNullOrBlank()) toAbsoluteUrl(loc) else fullPlayerUrl
                } catch (_: Exception) {
                    fullPlayerUrl
                }

                if (streamUrl.isNotBlank()) {
                    val isM3u8 = streamUrl.contains(".m3u8", true)
                    callback(
                        newExtractorLink(
                            source = this.name,
                            name = "${this.name} - $label",
                            url = streamUrl,
                            type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = "https://www.mynimeku.com/"
                            this.quality = quality
                        }
                    )
                    foundAny = true
                }
            }
        }

        // 2. Parse download links
        doc.select("div.mynimeku-episode-download a[href], div[class*='download'] a[href]").forEach { a ->
            val href = toAbsoluteUrl(a.attr("href"))
            val dlName = a.text().trim()
            if (href.startsWith("http") && !href.contains("javascript:")) {
                try {
                    val streamUrl = try {
                        val headRes = app.get(
                            href,
                            headers = mapOf("User-Agent" to USER_AGENT, "Referer" to pageUrl),
                            allowRedirects = false
                        )
                        val loc = headRes.headers["location"] ?: headRes.headers["Location"]
                        if (!loc.isNullOrBlank()) toAbsoluteUrl(loc) else href
                    } catch (_: Exception) {
                        href
                    }

                    if (streamUrl.contains(".mp4", true) || streamUrl.contains(".m3u8", true) || streamUrl.contains("googleapis.com") || streamUrl.contains("workers.dev")) {
                        val isM3u8 = streamUrl.contains(".m3u8", true)
                        callback(
                            newExtractorLink(
                                source = this.name,
                                name = "${this.name} Download - $dlName",
                                url = streamUrl,
                                type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                this.referer = "https://www.mynimeku.com/"
                            }
                        )
                        foundAny = true
                    } else {
                        loadExtractor(streamUrl, pageUrl, subtitleCallback, callback)
                        foundAny = true
                    }
                } catch (_: Exception) {}
            }
        }

        // 3. Fallback direct Iframes & universal extractors
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
