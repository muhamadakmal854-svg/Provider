package com.mts.dutafilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import android.util.Base64
import okhttp3.FormBody

class DutafilmProvider : MainAPI() {
    override var mainUrl = "https://dutafilm30.mantab.men"
    override var name = "Dutafilm"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "movies_list" to "Movies List",
        "series_list" to "Series List",
        "anime_list" to "Anime",
        "explore?media_type=movie" to "Movie",
        "explore?media_type=tv" to "Serial TV",
        "explore" to "Genre"
    )

    private fun Element.toSearchResult(): SearchResponse? {
        val a = this.parent() ?: return null
        val title = a.attr("title")?.trim() ?: this.selectFirst(".mv-desc")?.text()?.trim() ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img.mv-poster")?.attr("src"))
        
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val path = request.data
        val homeList = mutableListOf<SearchResponse>()
        
        if (path == "movies_list" || path == "series_list" || path == "anime_list") {
            val document = app.get(mainUrl).document
            val headingText = when (path) {
                "movies_list" -> "Movies List"
                "series_list" -> "Series List"
                "anime_list" -> "Anime"
                else -> ""
            }
            val labelDiv = document.select("div.featured-label").firstOrNull { 
                it.selectFirst("h3")?.text()?.trim()?.equals(headingText, ignoreCase = true) == true 
            }
            if (labelDiv != null) {
                var sibling = labelDiv.nextElementSibling()
                while (sibling != null && !sibling.hasClass("featured-label") && sibling.tagName() != "div") {
                    if (sibling.tagName() == "a") {
                        val mv = sibling.selectFirst("div.mv")
                        val searchResult = mv?.toSearchResult()
                        if (searchResult != null) {
                            homeList.add(searchResult)
                        }
                    }
                    sibling = sibling.nextElementSibling()
                }
            }
        } else {
            val pageUrl = if (page > 1) {
                val separator = if (path.contains("?")) "&" else "?"
                "$mainUrl/$path${separator}page=$page"
            } else {
                "$mainUrl/$path"
            }
            val document = app.get(pageUrl).document
            document.select("div.mv").forEach { mv ->
                val searchResult = mv.toSearchResult()
                if (searchResult != null) {
                    homeList.add(searchResult)
                }
            }
        }
        return newHomePageResponse(request.name, homeList, hasNext = homeList.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/explore?q=$query").document
        return document.select("div.mv").mapNotNull { it.toSearchResult() }
    }

    private fun decodeVar(encoded: String): String {
        return try {
            val reversed = encoded.reversed()
            val decodedBytes = Base64.decode(reversed, Base64.DEFAULT)
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("div.vid-details-right h3, h3")?.text()?.trim() ?: ""
        val poster = document.selectFirst("div.vid-details-left img")?.attr("src")
        val plot = document.selectFirst("div[itemtype='http://schema.org/Movie'] p, p[itemprop='description']")?.text()?.trim() ?: ""
        val year = document.selectFirst("a[href*='year=']")?.text()?.toIntOrNull()

        // 1. Extract dynamic c, t, movie_id
        var cVal: String? = null
        var tVal: String? = null
        var movieIdVal: String? = null
        var cApiHostVal: String? = null

        val regex = Regex("""([A-Za-z0-9_]+)\s*=\s*'([A-Za-z0-9_\.\-]{100,})'""")
        for (script in document.select("script")) {
            val txt = script.data()
            val matches = regex.findAll(txt)
            for (match in matches) {
                val encodedVal = match.groupValues[2]
                val decoded = decodeVar(encodedVal)
                if (decoded.contains("c_api_host")) {
                    cVal = Regex("""var\s+c\s*=\s*'([^']*)'""").find(decoded)?.groupValues?.getOrNull(1)
                    tVal = Regex("""var\s+t\s*=\s*'([^']*)'""").find(decoded)?.groupValues?.getOrNull(1)
                    cApiHostVal = Regex("""var\s+c_api_host\s*=\s*'([^']*)'""").find(decoded)?.groupValues?.getOrNull(1)
                    movieIdVal = Regex("""initEpisodeList\(\s*'([^']*)'""").find(decoded)?.groupValues?.getOrNull(1)
                    if (movieIdVal == null) {
                        movieIdVal = Regex("""get_link\(\s*'([^']*)'""").find(decoded)?.groupValues?.getOrNull(1)
                    }
                }
            }
        }

        if (cVal == null) cVal = "c3c6"
        if (tVal == null) tVal = "1783566384&ver=a78"
        if (cApiHostVal == null) cApiHostVal = "https://api.drakor.bid/c_api"
        if (movieIdVal == null) {
            movieIdVal = Regex("""loadEpisode\(\s*'([^']*)'""").find(document.html())?.groupValues?.getOrNull(1)
        }

        // 2. Fetch episode_mob.php
        val epUrl = "$cApiHostVal/episode_mob.php?is_mob=0&is_uc=0&movie_id=$movieIdVal&cat=ss&tag=ind&c=$cVal&t=$tVal"
        val epResponse = app.get(epUrl, referer = url).text
        val epJson = tryParseJson<EpisodeListResponse>(epResponse)
        
        val epListHtml = epJson?.episode_lists ?: ""
        val epDoc = Jsoup.parse(epListHtml)
        val serverLinks = epDoc.select("a.episode")

        val episodes = serverLinks.mapIndexed { index, linkElement ->
            val epid = linkElement.attr("data-epid")
            val server = linkElement.attr("data-server")
            val serverXid = linkElement.attr("data-server_xid")
            val cat = linkElement.attr("data-cat")
            val tag = linkElement.attr("data-tag")
            val epName = linkElement.text().trim()
            val epNum = epName.filter { it.isDigit() }.toIntOrNull() ?: 1

            // Serialize data for loadLinks
            val linkData = "id=$movieIdVal&qua=$epid&server_id=$server&cat=$serverXid&tag=$cat&c=$cVal&t=$tVal&api=$cApiHostVal"
            
            newEpisode(linkData) {
                this.name = epName
                this.episode = epNum
            }
        }

        val isTv = episodes.size > 1 || episodes.any { it.name?.contains("Eps", true) == true }

        return if (isTv) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, episodes.firstOrNull()?.data ?: "") {
                this.posterUrl = poster
                this.plot = plot
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
        if (data.isBlank()) return false
        
        // Parse serialized data
        val params = data.split("&").associate {
            val parts = it.split("=")
            parts[0] to (parts.getOrNull(1) ?: "")
        }

        val id = params["id"] ?: return false
        val qua = params["qua"] ?: ""
        val serverId = params["server_id"] ?: ""
        val cat = params["cat"] ?: ""
        val tag = params["tag"] ?: ""
        val c = params["c"] ?: ""
        val t = params["t"] ?: ""
        val api = params["api"] ?: "https://api.drakor.bid/c_api"

        val videoApiUrl = "$api/video.php?is_mob=0&is_uc=0&id=$id&qua=$qua&server_id=$serverId&cat=$cat&tag=$tag&c=$c&t=$t"
        
        val headers = mapOf(
            "Referer" to "$mainUrl/",
            "X-Requested-With" to "XMLHttpRequest",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        )
        
        val response = app.get(videoApiUrl, headers = headers).text
        val json = tryParseJson<VideoPlayResponse>(response)
        val fileUrl = json?.file ?: return false

        if (fileUrl.startsWith("http")) {
            loadExtractor(fileUrl, referer = "$mainUrl/", subtitleCallback, callback)
        }
        return true
    }

    data class EpisodeListResponse(
        val episode_lists: String?,
        val first_ep_id: String?,
        val server_xid: String?
    )

    data class VideoPlayResponse(
        val file: String?,
        val subtitle: String?
    )
}
