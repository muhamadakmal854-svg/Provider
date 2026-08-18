package com.myanimeku

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.net.URLEncoder

class Myanimeku : MainAPI() {
    override var mainUrl = "https://myanimeku.com"
    override var name = "Myanimeku"
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
                    val abs = toAbsoluteUrl(v)
                    if (!abs.endsWith("/storage/posters") && !abs.endsWith("/storage/posters/")) {
                        return abs
                    }
                }
            }
        }

        val style = img.attr("style").ifBlank { element.attr("style") }
        if (style.isNotBlank()) {
            val bgMatch = Regex("""url\(['"]?(.*?)['"]?\)""").find(style)
            if (bgMatch != null) {
                val bgUrl = bgMatch.groupValues[1].trim()
                if (bgUrl.isNotBlank() && !bgUrl.startsWith("data:", true)) {
                    val abs = toAbsoluteUrl(bgUrl)
                    if (!abs.endsWith("/storage/posters") && !abs.endsWith("/storage/posters/")) {
                        return abs
                    }
                }
            }
        }

        return null
    }

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Update Terbaru",
        "$mainUrl/search?type=TV" to "TV Series",
        "$mainUrl/search?type=MOVIE" to "Movie Anime",
        "$mainUrl/search?type=ONA" to "ONA / Donghua",
        "$mainUrl/search?type=OVA" to "OVA",
        "$mainUrl/search?type=SPECIAL" to "Special",
        "$mainUrl/search?status=Ongoing" to "Anime On-Going",
        "$mainUrl/search?status=Completed" to "Anime Completed",
        "$mainUrl/search?genre=action" to "Action",
        "$mainUrl/search?genre=adventure" to "Adventure",
        "$mainUrl/search?genre=comedy" to "Comedy",
        "$mainUrl/search?genre=drama" to "Drama",
        "$mainUrl/search?genre=fantasy" to "Fantasy",
        "$mainUrl/search?genre=isekai" to "Isekai",
        "$mainUrl/search?genre=romance" to "Romance",
        "$mainUrl/search?genre=sci-fi" to "Sci-Fi",
        "$mainUrl/search?genre=supernatural" to "Supernatural",
        "$mainUrl/search?genre=suspense" to "Suspense",
        "$mainUrl/search?genre=ecchi" to "Ecchi",
        "$mainUrl/search?genre=hentong" to "Hentong"
    )

    private fun toSearchResult(element: Element): SearchResponse? {
        return try {
            val a = if (element.tagName().equals("a", true)) element else element.selectFirst("a[href*='/anime/']") ?: return null
            val href = toAbsoluteUrl(a.attr("href"))
            if (href.isBlank() || href == "$mainUrl/" || !href.contains("/anime/")) return null

            val img = a.selectFirst("img") ?: element.selectFirst("img")
            var rawTitle = img?.attr("alt")?.trim().orEmpty()
            if (rawTitle.isBlank()) {
                rawTitle = a.attr("title").trim()
            }
            if (rawTitle.isBlank()) {
                rawTitle = element.selectFirst(".title, h2, h3, p, div")?.text()?.trim().orEmpty()
            }
            if (rawTitle.isBlank()) {
                rawTitle = a.text().trim()
            }

            rawTitle = rawTitle.lines().firstOrNull()?.trim() ?: ""
            if (rawTitle.isBlank() || rawTitle.equals("DETAILS", ignoreCase = true) || rawTitle.equals("Detail", ignoreCase = true)) {
                val slug = href.removeSuffix("/").substringAfterLast("/")
                rawTitle = slug.replace("-", " ").split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
            }

            val poster = getPosterUrl(img ?: element)

            val isMovie = href.contains("/movie", true) || href.contains("-movie-", true)
            val type = if (isMovie) TvType.AnimeMovie else TvType.Anime

            val epText = element.selectFirst(".ep, .badge, .status")?.text()?.trim()
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
        val targetUrl = if (request.data == "$mainUrl/") {
            if (page <= 1) "$mainUrl/" else "$mainUrl/search?page=$page"
        } else {
            if (page <= 1) request.data else "${request.data}&page=$page"
        }

        val doc = app.get(targetUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).document

        val cards = doc.select("a[href*='/anime/']").mapNotNull {
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
        val searchUrl = "$mainUrl/search?q=$encodedQuery"

        val doc = app.get(searchUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).document

        return doc.select("a[href*='/anime/']").mapNotNull {
            toSearchResult(it)
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val fullUrl = toAbsoluteUrl(url)
        val doc = app.get(fullUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).document

        val rawTitle = doc.selectFirst("h1.hero-title, h1.title, h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property='og:title']")?.attr("content")?.trim()
            ?: fullUrl.removeSuffix("/").substringAfterLast("/").replace("-", " ")

        val title = rawTitle.replace("Nonton", "", ignoreCase = true)
            .replace("Subtitle Indonesia", "", ignoreCase = true)
            .replace("- MyAnimeKu", "", ignoreCase = true)
            .replace("MyAnimeKu", "", ignoreCase = true)
            .trim()

        val poster = getPosterUrl(doc.selectFirst("img[src*='anilist.co'], img[src*='myanimelist'], .poster img, meta[property='og:image']"))
            ?: doc.selectFirst("meta[property='og:image']")?.attr("content")?.let { toAbsoluteUrl(it) }
            ?: ""

        val plot = doc.select(".synopsis, .description, div[class*='synopsis'], .entry-content p, p").firstOrNull {
            it.text().trim().length > 30
        }?.text()?.trim().orEmpty().ifBlank {
            doc.selectFirst("meta[property='og:description']")?.attr("content")?.trim() ?: ""
        }

        val tags = doc.select("a[href*='genre='], a[href*='/genre/'], span.badge").map { it.text().trim() }.distinct()
        val year = doc.selectFirst("a[href*='year='], .year, .meta")?.text()?.let {
            Regex("""\b(19\d\d|20\d\d)\b""").find(it)?.groupValues?.get(1)?.toIntOrNull()
        }

        // Extract Episode List from allEpisodes JavaScript JSON array
        val html = doc.html()
        val epJsonMatch = Regex("""const\s+allEpisodes\s*=\s*(\[.*?\]);\s*(?:const|let|var|\n)""", RegexOption.DOT_MATCHES_ALL)
            .find(html)
            ?: Regex("""allEpisodes\s*=\s*(\[\{.*?\}\]);""", RegexOption.DOT_MATCHES_ALL).find(html)

        val episodes = mutableListOf<Episode>()

        if (epJsonMatch != null) {
            try {
                val jsonArr = JSONArray(epJsonMatch.groupValues[1])
                for (i in 0 until jsonArr.length()) {
                    val epObj = jsonArr.optJSONObject(i) ?: continue
                    val epNum = epObj.optString("episode_number", "").toIntOrNull() ?: (i + 1)
                    val epTitle = epObj.optString("title", "").ifBlank { "Episode $epNum" }
                    val epThumb = epObj.optString("thumbnail", "")

                    val v1 = epObj.optString("video_url", "")
                    val v2 = epObj.optString("video_url2", "")
                    val v3 = epObj.optString("video_url3", "")
                    val s1 = epObj.optString("server1_name", "HD-1")
                    val s2 = epObj.optString("server2_name", "HD-2")
                    val s3 = epObj.optString("server3_name", "HD-3")
                    val dls = epObj.optString("download_links", "")

                    val payload = JSONObject().apply {
                        put("v1", v1)
                        put("s1", s1)
                        put("v2", v2)
                        put("s2", s2)
                        put("v3", v3)
                        put("s3", s3)
                        put("dls", dls)
                        put("page", fullUrl)
                    }.toString()

                    episodes.add(
                        newEpisode(payload) {
                            this.name = if (epTitle.contains("Episode", true)) epTitle else "Episode $epNum: $epTitle"
                            this.episode = epNum
                            if (epThumb.isNotBlank() && !epThumb.equals("null", true)) {
                                this.posterUrl = epThumb
                            }
                        }
                    )
                }
            } catch (_: Exception) {}
        }

        val isMovie = episodes.isEmpty() || fullUrl.contains("/movie", true) || fullUrl.contains("-movie-", true)

        return if (isMovie) {
            newMovieLoadResponse(title, fullUrl, TvType.AnimeMovie, fullUrl) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.year = year
            }
        } else {
            newTvSeriesLoadResponse(title, fullUrl, TvType.Anime, episodes) {
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
        var foundAny = false

        val videoUrls = mutableListOf<Pair<String, String>>() // Pair(URL, ServerName)

        if (data.startsWith("{") && data.endsWith("}")) {
            try {
                val json = JSONObject(data)
                val v1 = json.optString("v1", "")
                val s1 = json.optString("s1", "Server 1")
                val v2 = json.optString("v2", "")
                val s2 = json.optString("s2", "Server 2")
                val v3 = json.optString("v3", "")
                val s3 = json.optString("s3", "Server 3")
                val dls = json.optString("dls", "")

                if (v1.isNotBlank() && !v1.equals("null", true)) videoUrls.add(v1 to s1)
                if (v2.isNotBlank() && !v2.equals("null", true)) videoUrls.add(v2 to s2)
                if (v3.isNotBlank() && !v3.equals("null", true)) videoUrls.add(v3 to s3)

                if (dls.isNotBlank() && !dls.equals("null", true)) {
                    try {
                        if (dls.startsWith("[")) {
                            val dlArr = JSONArray(dls)
                            for (d in 0 until dlArr.length()) {
                                val dlObj = dlArr.optJSONObject(d) ?: continue
                                val dlUrl = dlObj.optString("url", "")
                                val dlName = dlObj.optString("name", "Download")
                                if (dlUrl.isNotBlank()) videoUrls.add(dlUrl to dlName)
                            }
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        } else {
            videoUrls.add(data to "Server")
        }

        for ((rawUrl, serverName) in videoUrls) {
            // Handle Yoredesu / Blogspot Base64 links
            if (rawUrl.contains("yoredesu.blogspot.com") || rawUrl.contains("link=")) {
                val linkParam = Regex("""[?&]link=([^&]+)""").find(rawUrl)?.groupValues?.getOrNull(1)
                if (!linkParam.isNullOrBlank()) {
                    try {
                        val decodedLink = String(Base64.decode(URLDecoder.decode(linkParam, "UTF-8"), Base64.DEFAULT), Charsets.UTF_8)
                        
                        // Check if multi-quality JSON
                        if (decodedLink.trim().startsWith("[")) {
                            val sourcesArr = JSONArray(decodedLink.trim())
                            for (s in 0 until sourcesArr.length()) {
                                val srcObj = sourcesArr.optJSONObject(s) ?: continue
                                val streamUrl = srcObj.optString("url", "")
                                val qualityLabel = srcObj.optString("html", "720p")
                                val quality = when {
                                    qualityLabel.contains("1080", true) -> Qualities.P1080.value
                                    qualityLabel.contains("720", true) -> Qualities.P720.value
                                    qualityLabel.contains("480", true) -> Qualities.P480.value
                                    qualityLabel.contains("360", true) -> Qualities.P360.value
                                    else -> Qualities.Unknown.value
                                }

                                if (streamUrl.isNotBlank()) {
                                    val isM3u8 = streamUrl.contains(".m3u8", true)
                                    callback(
                                        newExtractorLink(
                                            source = this.name,
                                            name = "${this.name} $qualityLabel",
                                            url = streamUrl,
                                            type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                        ) {
                                            this.referer = "https://myanimeku.com/"
                                            this.quality = quality
                                        }
                                    )
                                    foundAny = true
                                }
                            }
                        } else if (decodedLink.isNotBlank()) {
                            val isM3u8 = decodedLink.contains(".m3u8", true)
                            callback(
                                newExtractorLink(
                                    source = this.name,
                                    name = "${this.name} - $serverName",
                                    url = decodedLink,
                                    type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = "https://myanimeku.com/"
                                }
                            )
                            foundAny = true
                        }
                    } catch (_: Exception) {}
                }
            }

            // Handle Myanimeku stream v2 Base64 path
            if (rawUrl.contains("/stream/v2/") || rawUrl.contains("/stream/v1/")) {
                val b64Part = rawUrl.substringAfter("/stream/v2/").substringAfter("/stream/v1/").substringBefore("?").trim()
                if (b64Part.isNotBlank()) {
                    try {
                        val decodedUrl = String(Base64.decode(URLDecoder.decode(b64Part, "UTF-8"), Base64.DEFAULT), Charsets.UTF_8)
                        if (decodedUrl.isNotBlank() && (decodedUrl.startsWith("http://") || decodedUrl.startsWith("https://"))) {
                            val isM3u8 = decodedUrl.contains(".m3u8", true)
                            callback(
                                newExtractorLink(
                                    source = this.name,
                                    name = "${this.name} - $serverName",
                                    url = decodedUrl,
                                    type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = "https://myanimeku.com/"
                                }
                            )
                            foundAny = true
                        }
                    } catch (_: Exception) {}
                }
            }

            // Direct Video Streams (.mp4 / .m3u8)
            if (rawUrl.contains(".m3u8", true) || rawUrl.contains(".mp4", true)) {
                val isM3u8 = rawUrl.contains(".m3u8", true)
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = "${this.name} Direct - $serverName",
                        url = rawUrl,
                        type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://myanimeku.com/"
                    }
                )
                foundAny = true
            } else {
                // Universal Extractors (StreamWish, VidHide, FileLions, YouTube, etc.)
                try {
                    loadExtractor(rawUrl, "https://myanimeku.com/", subtitleCallback, callback)
                    foundAny = true
                } catch (_: Exception) {}
            }
        }

        return foundAny
    }
}
