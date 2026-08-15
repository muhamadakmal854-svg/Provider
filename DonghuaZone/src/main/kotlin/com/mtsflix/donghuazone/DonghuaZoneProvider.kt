package com.mtsflix.donghuazone

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
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

    // Only categories with actual content - empty ones removed
    override val mainPage = mainPageOf(
        "" to "Latest Episode",
        "search/label/Ongoing" to "Ongoing",
        "search/label/Movie" to "Movie",
        "search/label/Donghua" to "Donghua"
    )

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

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search?q=$query").document
        return document.select(".post-outer-container, article.post-outer-container, article.post")
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.title-stream, h1.post-title, h1.entry-title, h1")
            ?.text()?.trim() ?: return null

        val posterUrl = document.selectFirst(".post-thumbnail, .post-body img, .entry-content img")?.let {
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

        // Genre labels to ignore - only detect series title label
        val ignoreGenres = setOf(
            "movie", "ongoing", "completed", "action", "adventure",
            "fantasy", "romance", "cultivation", "martial arts", "donghua", "episode", "3d"
        )

        var seriesLabel: String? = null
        document.select("a[href*='/search/label/']").forEach { a ->
            val href = a.attr("href")
            if (href.contains("/search/label/")) {
                val raw = href.substringAfter("/search/label/").substringBefore("?")
                    .replace("%20", " ").trim()
                if (raw.isNotEmpty() && !ignoreGenres.contains(raw.lowercase())) {
                    seriesLabel = raw
                }
            }
        }

        val episodes = mutableListOf<Episode>()
        if (!seriesLabel.isNullOrBlank()) {
            try {
                val feedUrl = "$mainUrl/feeds/posts/default/-/$seriesLabel?alt=json&max-results=100"
                val jsonText = app.get(feedUrl).text
                val feedJson = JSONObject(jsonText)
                val feedObj = feedJson.optJSONObject("feed")
                val entryArr = feedObj?.optJSONArray("entry") ?: JSONArray()
                val epList = mutableListOf<Pair<String, String>>()
                for (i in 0 until entryArr.length()) {
                    val entry = entryArr.getJSONObject(i)
                    val titleObj = entry.optJSONObject("title")
                    val epTitle = titleObj?.optString("\$t")?.takeIf { it.isNotBlank() }
                        ?: "Episode ${i + 1}"
                    val linkArr = entry.optJSONArray("link") ?: continue
                    var epHref: String? = null
                    for (j in 0 until linkArr.length()) {
                        val linkObj = linkArr.getJSONObject(j)
                        if (linkObj.optString("rel") == "alternate") {
                            epHref = linkObj.optString("href")
                            break
                        }
                    }
                    if (!epHref.isNullOrBlank()) {
                        epList.add(Pair(epTitle, epHref))
                    }
                }
                // Reverse to get ep 1 first
                epList.reversed().forEachIndexed { index, (epTitle, epHref) ->
                    episodes.add(newEpisode(epHref) {
                        this.name = epTitle
                        this.episode = index + 1
                    })
                }
            } catch (e: Exception) {
                // Fallback: single episode
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val foundServers = mutableListOf<Pair<String, String>>()

        // Extract all 3 server buttons: Multi Sub, Indonesia Sub, English Sub
        document.select("button[onclick*='changeServer'], a[onclick*='changeServer'], .serverBtn").forEach { btn ->
            val serverName = btn.text().trim()
            val onclick = btn.attr("onclick")
            val match = Regex("""changeServer\([^,]+,\s*['"]([^'"]+)['"]\)""").find(onclick)
            val serverUrl = match?.groupValues?.getOrNull(1)?.trim() ?: ""
            if (serverUrl.isNotBlank() && serverUrl.startsWith("http")) {
                if (foundServers.none { it.second == serverUrl }) {
                    foundServers.add(Pair(serverName, serverUrl))
                }
            }
        }

        // Fallback: regex scan whole HTML if no buttons found
        if (foundServers.isEmpty()) {
            val html = document.html()
            val regex = Regex("""changeServer\([^,]+,\s*['"]([^'"]+)['"]\)""")
            regex.findAll(html).forEach { m ->
                val serverUrl = m.groupValues.getOrNull(1)?.trim() ?: ""
                if (serverUrl.isNotBlank() && serverUrl.startsWith("http")) {
                    if (foundServers.none { it.second == serverUrl }) {
                        foundServers.add(Pair("Server", serverUrl))
                    }
                }
            }
        }

        // Fallback: iframes
        if (foundServers.isEmpty()) {
            document.select("iframe").forEach { iframe ->
                val src = iframe.attr("data-src").ifEmpty { iframe.attr("src") }.trim()
                if (src.isNotBlank() && src.startsWith("http")) {
                    if (foundServers.none { it.second == src }) {
                        foundServers.add(Pair("Iframe", src))
                    }
                }
            }
        }

        var count = 0
        for ((_, serverUrl) in foundServers) {
            val fixedUrl = fixUrl(serverUrl)
            // Convert geo.dailymotion.com/player/...?video=ID to standard embed URL
            if (fixedUrl.contains("dailymotion")) {
                val videoId = when {
                    fixedUrl.contains("video=") ->
                        fixedUrl.substringAfter("video=").substringBefore("&").substringBefore("#")
                    fixedUrl.contains("/embed/video/") ->
                        fixedUrl.substringAfter("/embed/video/").substringBefore("?").substringBefore("/")
                    fixedUrl.contains("/video/") ->
                        fixedUrl.substringAfter("/video/").substringBefore("?").substringBefore("/")
                    else -> null
                }
                if (!videoId.isNullOrBlank()) {
                    val stdEmbedUrl = "https://www.dailymotion.com/embed/video/$videoId"
                    loadExtractor(stdEmbedUrl, data, subtitleCallback, callback)
                    count++
                    continue
                }
            }
            loadExtractor(fixedUrl, data, subtitleCallback, callback)
            count++
        }

        return count > 0
    }
}
