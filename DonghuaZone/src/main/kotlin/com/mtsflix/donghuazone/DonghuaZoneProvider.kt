package com.mtsflix.donghuazone

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.json.JSONObject
import org.json.JSONArray
import org.jsoup.nodes.Element

class DonghuaZoneProvider : MainAPI() {
    override var mainUrl = "https://www.donghuazone.com"
    override var name = "DonghuaZone"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    // Only categories with real content
    override val mainPage = mainPageOf(
        "" to "Latest Episode",
        "search/label/Ongoing" to "Ongoing",
        "search/label/Movie" to "Movie",
        "search/label/Donghua" to "Donghua"
    )

    // -------------------------------------------------------
    // MAIN PAGE
    // -------------------------------------------------------
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path = request.data
        val url = if (path.isEmpty()) {
            if (page <= 1) "$mainUrl/" else "$mainUrl/page/$page/"
        } else {
            if (page <= 1) "$mainUrl/$path" else "$mainUrl/$path?page=$page"
        }
        val document = app.get(url).document
        val items = document.select(".post-outer-container, article.post-outer-container, article.post")
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3.post-title, .home-title, .grid2-tt, h2, h3")?.text()?.trim()
            ?: this.selectFirst("a[title]")?.attr("title")?.trim()
            ?: return null
        if (title.isBlank()) return null
        val a = this.selectFirst("a[href]") ?: return null
        val href = fixUrl(a.attr("href"))
        val imgElem = this.selectFirst("img[data-src], img[src]")
        val posterUrl = imgElem?.let {
            val src = it.attr("data-src").ifEmpty { it.attr("src") }
            if (src.startsWith("data:")) null else fixUrlNull(src)
        }
        val type = if (title.contains("Movie", true)) TvType.AnimeMovie else TvType.Anime
        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }

    // -------------------------------------------------------
    // SEARCH
    // -------------------------------------------------------
    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search?q=$query").document
        return document.select(".post-outer-container, article.post-outer-container, article.post")
            .mapNotNull { it.toSearchResult() }
    }

    // -------------------------------------------------------
    // LOAD - Series detail + episode list
    // -------------------------------------------------------
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.title-stream, h1.post-title, h1.entry-title, h1")
            ?.text()?.trim() ?: return null

        val posterUrl = document.selectFirst(".post-thumbnail img, .post-body img, .entry-content img")?.let {
            val src = it.attr("data-src").ifEmpty { it.attr("src") }
            if (src.startsWith("data:")) null else fixUrlNull(src)
        }
        val plot = document.selectFirst(".sinoposis p, .post-body p")?.text()?.trim()
        val isMovie = title.contains("Movie", true)

        if (isMovie) {
            return newMovieLoadResponse(title, url, TvType.AnimeMovie, url) {
                this.posterUrl = posterUrl
                this.plot = plot
            }
        }

        // Genre labels to skip when searching for series label
        val ignoreGenres = setOf(
            "movie", "ongoing", "completed", "action", "adventure",
            "fantasy", "romance", "cultivation", "martial arts", "donghua", "episode", "3d"
        )

        var seriesLabel: String? = null
        document.select("a[href*='/search/label/']").forEach { a ->
            val href = a.attr("href")
            val raw = href.substringAfter("/search/label/").substringBefore("?")
                .replace("+", " ").replace("%20", " ").trim()
            if (raw.isNotEmpty() && !ignoreGenres.contains(raw.lowercase()) && seriesLabel == null) {
                seriesLabel = raw
            }
        }

        val episodes = mutableListOf<Episode>()
        if (!seriesLabel.isNullOrBlank()) {
            try {
                val encoded = seriesLabel!!.replace(" ", "%20")
                val feedUrl = "$mainUrl/feeds/posts/default/-/$encoded?alt=json&max-results=200"
                val jsonText = app.get(feedUrl).text
                val feedObj = JSONObject(jsonText).optJSONObject("feed") ?: JSONObject()
                val entryArr = feedObj.optJSONArray("entry") ?: JSONArray()
                val epList = mutableListOf<Pair<String, String>>()
                for (i in 0 until entryArr.length()) {
                    val entry = entryArr.getJSONObject(i)
                    val titleObj = entry.optJSONObject("title")
                    // Use getString on key "$t" directly
                    val epTitle = if (titleObj != null && titleObj.has("\$t")) {
                        titleObj.getString("\$t").trim()
                    } else "Episode ${i + 1}"
                    val linkArr = entry.optJSONArray("link") ?: continue
                    var epHref: String? = null
                    for (j in 0 until linkArr.length()) {
                        val lObj = linkArr.getJSONObject(j)
                        if (lObj.optString("rel") == "alternate") {
                            epHref = lObj.optString("href")
                            break
                        }
                    }
                    if (!epHref.isNullOrBlank()) epList.add(Pair(epTitle, epHref))
                }
                // Reverse so episode 1 comes first
                epList.reversed().forEachIndexed { index, (epTitle, epHref) ->
                    episodes.add(newEpisode(epHref) {
                        this.name = epTitle
                        this.episode = index + 1
                    })
                }
            } catch (e: Exception) {
                // fallback handled below
            }
        }

        if (episodes.isEmpty()) {
            episodes.add(newEpisode(url) {
                this.name = title
                this.episode = 1
            })
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = posterUrl
            this.plot = plot
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    // -------------------------------------------------------
    // LOAD LINKS - Extract video from Dailymotion Metadata API
    // -------------------------------------------------------
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val html = document.html()

        // Regex: extract changeServer URLs (geo.dailymotion.com player URLs)
        val serverRegex = Regex("""changeServer\s*\(\s*(?:this|[^,]+)\s*,\s*['"]([^'"]+)['"]""")
        val btnTextRegex = Regex("""<button[^>]*onclick="changeServer\([^)]+\)"[^>]*>\s*([^<]+)\s*</button>""", RegexOption.IGNORE_CASE)

        // Map URL -> button label
        val urlToName = mutableMapOf<String, String>()
        btnTextRegex.findAll(html).forEach { m ->
            val fullBtn = m.value
            val btnLabel = m.groupValues[1].trim()
            val urlMatch = serverRegex.find(fullBtn)
            val playerUrl = urlMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
            if (playerUrl.isNotBlank()) urlToName[playerUrl] = btnLabel
        }

        // Fallback: just collect all URLs
        serverRegex.findAll(html).forEach { m ->
            val playerUrl = m.groupValues[1].trim()
            if (playerUrl.isNotBlank() && !urlToName.containsKey(playerUrl)) {
                urlToName[playerUrl] = "DonghuaZone"
            }
        }

        // Also check window.onload default server
        val onloadRegex = Regex("""changeServer\s*\([^,]+,\s*['"]([^'"]+)['"]\)\s*;?\s*\}""")
        onloadRegex.find(html)?.groupValues?.getOrNull(1)?.let { defUrl ->
            if (defUrl.isNotBlank() && !urlToName.containsKey(defUrl)) {
                urlToName[defUrl] = "Default"
            }
        }

        if (urlToName.isEmpty()) {
            // Final fallback: iframes
            document.select("iframe[src], iframe[data-src]").forEach { iframe ->
                val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }.trim()
                if (src.isNotBlank() && src.startsWith("http")) {
                    urlToName[src] = "Iframe Server"
                }
            }
        }

        var count = 0
        for ((playerUrl, serverName) in urlToName) {
            try {
                // Extract Dailymotion video ID from geo.dailymotion.com player URL
                val videoId = when {
                    playerUrl.contains("video=") ->
                        playerUrl.substringAfter("video=").substringBefore("&").substringBefore("#").trim()
                    playerUrl.contains("/embed/video/") ->
                        playerUrl.substringAfter("/embed/video/").substringBefore("?").substringBefore("/").trim()
                    playerUrl.contains("dailymotion.com/video/") ->
                        playerUrl.substringAfter("/video/").substringBefore("?").substringBefore("/").trim()
                    else -> null
                }

                if (!videoId.isNullOrBlank()) {
                    // Use Dailymotion metadata API to get M3U8 stream directly
                    val metaUrl = "https://www.dailymotion.com/player/metadata/video/$videoId?locale=en_US"
                    val metaResponse = app.get(
                        metaUrl,
                        headers = mapOf(
                            "Origin" to "https://www.dailymotion.com",
                            "Referer" to "https://www.dailymotion.com/",
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                        )
                    ).text

                    val metaJson = JSONObject(metaResponse)
                    val qualities = metaJson.optJSONObject("qualities") ?: JSONObject()
                    val qualityKeys = qualities.keys()
                    while (qualityKeys.hasNext()) {
                        val quality = qualityKeys.next()
                        val arr = qualities.optJSONArray(quality) ?: continue
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            val streamUrl = item.optString("url").trim()
                            val mimeType = item.optString("type")
                            if (streamUrl.isNotBlank() && (
                                    streamUrl.contains(".m3u8") ||
                                    mimeType.contains("mpegURL", true) ||
                                    mimeType.contains("x-mpegURL", true)
                                )) {
                                callback.invoke(
                                    newExtractorLink(
                                        source = "DonghuaZone",
                                        name = "$serverName ($quality)",
                                        url = streamUrl,
                                        type = ExtractorLinkType.M3U8
                                    ) {
                                        referer = "https://www.dailymotion.com/"
                                        this.quality = when (quality.lowercase()) {
                                            "1080" -> Qualities.P1080.value
                                            "720" -> Qualities.P720.value
                                            "480" -> Qualities.P480.value
                                            "360" -> Qualities.P360.value
                                            "240" -> Qualities.P240.value
                                            else -> Qualities.Unknown.value
                                        }
                                    }
                                )
                                count++
                            }
                        }
                    }
                    continue
                }

                // Non-Dailymotion: try generic extractor
                loadExtractor(fixUrl(playerUrl), data, subtitleCallback, callback)
                count++
            } catch (e: Exception) {
                // Skip failed server
            }
        }

        return count > 0
    }
}
