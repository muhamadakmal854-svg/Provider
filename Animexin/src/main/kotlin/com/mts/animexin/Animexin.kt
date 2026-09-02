package com.mts.animexin

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class Animexin : MainAPI() {
    override var mainUrl = "https://animexin.dev"
    override var name = "Animexin"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    override fun getVideoHeaders(url: String): Map<String, String> {
        return mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$mainUrl/"
        )
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

        for (attr in listOf("src", "data-src", "data-lazy-src", "data-original", "data-cfsrc", "srcset", "data-srcset", "content")) {
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
        "$mainUrl/" to "Terbaru",
        "$mainUrl/release-date/" to "Jadwal Rilis",
        "$mainUrl/az-lists-2/" to "Daftar A-Z",
        "$mainUrl/genres/action/" to "Action",
        "$mainUrl/genres/adventure/" to "Adventure",
        "$mainUrl/genres/fantasy/" to "Fantasy",
        "$mainUrl/genres/martial-arts/" to "Martial Arts",
        "$mainUrl/genres/romance/" to "Romance",
        "$mainUrl/genres/sci-fi/" to "Sci-Fi"
    )

    private fun toSearchResult(element: Element): SearchResponse? {
        return try {
            val a = element.selectFirst("a[href]") ?: return null
            val href = toAbsoluteUrl(a.attr("href"))
            if (href.isBlank() || href == "$mainUrl/" || href.contains("/genres/") || href.contains("/release-date/")) return null

            val img = element.selectFirst("img") ?: a.selectFirst("img")
            var rawTitle = element.selectFirst(".tt, .title, h2, h3, .entry-title")?.text()?.trim().orEmpty()
            if (rawTitle.isBlank()) {
                rawTitle = a.attr("title").trim()
            }
            if (rawTitle.isBlank()) {
                rawTitle = img?.attr("title")?.trim().orEmpty().ifBlank { img?.attr("alt")?.trim().orEmpty() }
            }
            if (rawTitle.isBlank()) {
                rawTitle = a.text().trim()
            }

            rawTitle = rawTitle.lines().firstOrNull()?.trim() ?: ""
            if (rawTitle.isBlank()) return null

            val poster = getPosterUrl(img ?: element)

            val isMovie = href.contains("/movie", true) || href.contains("-movie-", true)
            val type = if (isMovie) TvType.AnimeMovie else TvType.Anime

            val epText = element.selectFirst(".epx, .bt .ep, .ep, .eggep")?.text()?.trim()
            val epNum = epText?.filter { it.isDigit() }?.toIntOrNull()

            newAnimeSearchResponse(rawTitle, href, type) {
                this.posterUrl = poster
                this.posterHeaders = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/"
                )
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

        val res = app.get(targetUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/"))
        val doc = res.document

        val cards = doc.select(".listupd .bsx, .bsx, article.bs, .item, .animpost, .listupd article, .bs").mapNotNull {
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

        val res = app.get(searchUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/"))
        val doc = res.document

        return doc.select(".listupd .bsx, .bsx, article.bs, .item, .animpost, .listupd article, .bs").mapNotNull {
            toSearchResult(it)
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val fullUrl = toAbsoluteUrl(url)
        val res = app.get(fullUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/"))
        val doc = res.document

        val rawTitle = doc.selectFirst("h1.entry-title, h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property='og:title']")?.attr("content")?.trim()
            ?: return null

        val title = rawTitle.replace("- AnimeXin", "", ignoreCase = true)
            .replace("AnimeXin", "", ignoreCase = true)
            .replace("Subtitle Indonesia", "", ignoreCase = true)
            .replace("Sub Indo", "", ignoreCase = true)
            .replace("Indonesia, English Sub", "", ignoreCase = true)
            .trim()

        val poster = doc.selectFirst("meta[property='og:image']")?.attr("content")?.let { toAbsoluteUrl(it) }
            ?: getPosterUrl(doc.selectFirst(".thumb img, .poster img, img[itemprop='image'], .ts-post-image"))
            ?: ""

        val plot = doc.select(".entry-content p, .sinopsis, .desc, div[itemprop='description']").joinToString("\n") {
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
                this.posterUrl = poster
            }
        }.distinctBy { it.data }

        val isMovie = rawEpisodes.isEmpty() || fullUrl.contains("/movie", true) || fullUrl.contains("-movie-", true)

        return if (isMovie) {
            newMovieLoadResponse(title, fullUrl, TvType.AnimeMovie, fullUrl) {
                this.posterUrl = poster
                this.posterHeaders = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/"
                )
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
                this.posterHeaders = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/"
                )
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
        val doc = res.document

        var foundAny = false

        // 1. Parse Server Select / Mirror Dropdowns
        val mirrorOptions = doc.select("select.mirror option, select[name='server'] option, .mirror option")
        for (opt in mirrorOptions) {
            val rawVal = opt.attr("value").trim()
            val serverName = opt.text().trim().ifBlank { "Server" }
            if (rawVal.isNotBlank() && !rawVal.equals("null", true) && !serverName.contains("Select Video", true)) {
                try {
                    val decodedIframe = if (rawVal.startsWith("<iframe", true) || rawVal.startsWith("<div", true)) {
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

                    val fullSrc = toAbsoluteUrl(src)
                    if (fullSrc.isNotBlank() && fullSrc.startsWith("http")) {
                        if (fullSrc.contains("rumble.com")) {
                            extractRumbleDirect(fullSrc, pageUrl, serverName, callback)
                            foundAny = true
                        } else {
                            try {
                                loadExtractor(fullSrc, pageUrl, subtitleCallback, callback)
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
                if (src.contains("rumble.com")) {
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
        doc.select(".soradl a, .soraurlx a, .moredl a, .dlx a, a[href*='mirrored.to'], a[href*='pixeldrain'], a[href*='mediafire'], a[href*='mega.nz']").forEach { a ->
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
                        name = "${this.name} - $serverName ${if (isM3u8) "HLS" else "MP4"}",
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
