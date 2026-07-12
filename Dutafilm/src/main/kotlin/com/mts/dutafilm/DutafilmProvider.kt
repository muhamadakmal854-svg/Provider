package com.mts.dutafilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import android.util.Base64

class DutafilmProvider : MainAPI() {
    override var mainUrl = "https://dutafilm30.mantab.men"
    override var name = "Dutafilm"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "movies_list" to "Movies List",
        "series_list" to "Series List",
        "anime_list"  to "Anime",
        "explore?media_type=movie" to "Movie",
        "explore?media_type=tv"   to "Serial TV",
        "explore"                 to "Genre"
    )

    // ── Search result ────────────────────────────────────────────────────────
    private fun Element.toSearchResult(): SearchResponse? {
        val a         = this.parent() ?: return null
        val title     = a.attr("title")?.trim()
            ?: this.selectFirst(".mv-desc")?.text()?.trim()
            ?: return null
        val href      = fixUrlNull(a.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img.mv-poster")?.attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    // ── Main page ────────────────────────────────────────────────────────────
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path     = request.data
        val homeList = mutableListOf<SearchResponse>()
        if (path == "movies_list" || path == "series_list" || path == "anime_list") {
            val document    = app.get(mainUrl).document
            val headingText = when (path) {
                "movies_list" -> "Movies List"
                "series_list" -> "Series List"
                "anime_list"  -> "Anime"
                else          -> ""
            }
            val labelDiv = document.select("div.featured-label").firstOrNull {
                it.selectFirst("h3")?.text()?.trim()?.equals(headingText, ignoreCase = true) == true
            }
            if (labelDiv != null) {
                var sibling = labelDiv.nextElementSibling()
                while (sibling != null && !sibling.hasClass("featured-label") && sibling.tagName() != "div") {
                    if (sibling.tagName() == "a")
                        sibling.selectFirst("div.mv")?.toSearchResult()?.let { homeList.add(it) }
                    sibling = sibling.nextElementSibling()
                }
            }
        } else {
            val sep     = if (path.contains("?")) "&" else "?"
            val pageUrl = if (page > 1) "$mainUrl/$path${sep}page=$page" else "$mainUrl/$path"
            app.get(pageUrl).document.select("div.mv").forEach {
                it.toSearchResult()?.let { r -> homeList.add(r) }
            }
        }
        return newHomePageResponse(request.name, homeList, hasNext = homeList.isNotEmpty())
    }

    // ── Search ────────────────────────────────────────────────────────────────
    override suspend fun search(query: String): List<SearchResponse> =
        app.get("$mainUrl/explore?q=$query").document
            .select("div.mv").mapNotNull { it.toSearchResult() }

    // ── Data classes ──────────────────────────────────────────────────────────
    data class ServerInfo(
        @JsonProperty("qua") val qua: String = "",
        @JsonProperty("sid") val sid: String = "",
        @JsonProperty("cat") val cat: String = "",
        @JsonProperty("tag") val tag: String = "",
        @JsonProperty("nm")  val nm:  String = ""
    )
    data class EpData(
        @JsonProperty("id")  val id:  String           = "",
        @JsonProperty("c")   val c:   String           = "",
        @JsonProperty("t")   val t:   String           = "",
        @JsonProperty("api") val api: String           = "",
        @JsonProperty("ep")  val ep:  Int              = 1,
        @JsonProperty("sv")  val sv:  List<ServerInfo> = emptyList()
    )
    data class EpisodeListResponse(
        @JsonProperty("episode_lists") val episode_lists: String?,
        @JsonProperty("first_ep_id")   val first_ep_id:   String?,
        @JsonProperty("server_xid")    val server_xid:    String?
    )
    data class VideoPlayResponse(
        @JsonProperty("file")     val file:     String?,
        @JsonProperty("subtitle") val subtitle: String?
    )

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun decodeVar(encoded: String): String = try {
        String(Base64.decode(encoded.reversed(), Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: Exception) { "" }

    private fun Element.toServerInfo() = ServerInfo(
        qua = attr("data-epid"),
        sid = attr("data-server"),
        cat = attr("data-server_xid"),
        tag = attr("data-cat"),
        nm  = text().trim()
    )

    private fun buildEpJson(
        id: String, c: String, t: String, api: String,
        epNum: Int, servers: List<Element>
    ): String {
        val svJson = servers.joinToString(",") { link ->
            val s = link.toServerInfo()
            """{"qua":"${s.qua}","sid":"${s.sid}","cat":"${s.cat}","tag":"${s.tag}","nm":"${s.nm}"}"""
        }
        return """{"id":"$id","c":"$c","t":"$t","api":"$api","ep":$epNum,"sv":[$svJson]}"""
    }

    // ── Load ──────────────────────────────────────────────────────────────────
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title    = document.selectFirst("div.vid-details-right h3, h3")?.text()?.trim() ?: return null
        val poster   = document.selectFirst("div.vid-details-left img")?.attr("src")
        val plot     = document.selectFirst("p[itemprop='description']")?.text()?.trim() ?: ""
        val year     = document.selectFirst("a[href*='year=']")?.text()?.toIntOrNull()

        // Decode obfuscated script vars
        var cVal       = "c3c6"
        var tVal       = "1783566384"
        var movieIdVal: String? = null
        var apiHostVal = "https://api.drakor.bid/c_api"

        val obfReg = Regex("""[A-Za-z0-9_]+\s*=\s*'([A-Za-z0-9_.\-]{100,})'""")
        outer@ for (script in document.select("script")) {
            for (m in obfReg.findAll(script.data())) {
                val decoded = decodeVar(m.groupValues[1])
                if (!decoded.contains("c_api_host")) continue
                cVal       = Regex("""var\s+c\s*=\s*'([^']*)'""").find(decoded)?.groupValues?.getOrNull(1) ?: cVal
                tVal       = Regex("""var\s+t\s*=\s*'([^']*)'""").find(decoded)?.groupValues?.getOrNull(1) ?: tVal
                apiHostVal = Regex("""var\s+c_api_host\s*=\s*'([^']*)'""").find(decoded)?.groupValues?.getOrNull(1) ?: apiHostVal
                movieIdVal = Regex("""initEpisodeList\(\s*'([^']*)'""").find(decoded)?.groupValues?.getOrNull(1)
                    ?: Regex("""get_link\(\s*'([^']*)'""").find(decoded)?.groupValues?.getOrNull(1)
                if (movieIdVal != null) break@outer
            }
        }
        if (movieIdVal == null)
            movieIdVal = Regex("""loadEpisode\(\s*'([^']*)'""").find(document.html())?.groupValues?.getOrNull(1)
        if (movieIdVal.isNullOrBlank()) return null

        // Collect servers from BOTH softsub + hardsub tabs
        val allLinks = mutableListOf<Element>()
        for ((cat, tag) in listOf("ss" to "ind", "hs" to "ind", "ss" to "eng", "hs" to "eng")) {
            try {
                val epUrl  = "$apiHostVal/episode_mob.php?is_mob=0&is_uc=0&movie_id=$movieIdVal&cat=$cat&tag=$tag&c=$cVal&t=$tVal"
                val epResp = app.get(epUrl, referer = url).text
                val epJson = tryParseJson<EpisodeListResponse>(epResp) ?: continue
                Jsoup.parse(epJson.episode_lists ?: "").select("a.episode").forEach { allLinks.add(it) }
            } catch (e: Exception) { e.printStackTrace() }
        }
        if (allLinks.isEmpty()) return null

        // Deduplicate by (data-epid, data-server)
        val seen      = mutableSetOf<String>()
        val uniqLinks = allLinks.filter { seen.add("${it.attr("data-epid")}_${it.attr("data-server")}") }

        // TV series = multiple distinct data-ep values
        val epNums = uniqLinks.mapNotNull { it.attr("data-ep").toIntOrNull() }.distinct()
        val isTv   = epNums.size > 1

        return if (isTv) {
            val epMap = linkedMapOf<Int, MutableList<Element>>()
            for (link in uniqLinks) {
                val n = link.attr("data-ep").toIntOrNull() ?: 1
                epMap.getOrPut(n) { mutableListOf() }.add(link)
            }
            val episodes = epMap.entries.sortedBy { it.key }.map { (n, links) ->
                newEpisode(buildEpJson(movieIdVal, cVal, tVal, apiHostVal, n, links)) {
                    this.episode = n
                    this.name    = "Episode $n"
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster; this.plot = plot; this.year = year
            }
        } else {
            // Movie — ALL server links are different quality/server options
            newMovieLoadResponse(title, url, TvType.Movie,
                buildEpJson(movieIdVal, cVal, tVal, apiHostVal, 1, uniqLinks)
            ) {
                this.posterUrl = poster; this.plot = plot; this.year = year
            }
        }
    }

    // ── loadLinks ─────────────────────────────────────────────────────────────
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false
        val epData = tryParseJson<EpData>(data) ?: return false
        val id     = epData.id.ifBlank { return false }
        val c      = epData.c
        val t      = epData.t
        val api    = epData.api.ifBlank { "https://api.drakor.bid/c_api" }

        val headers = mapOf(
            "Referer"           to "$mainUrl/",
            "X-Requested-With" to "XMLHttpRequest",
            "User-Agent"        to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        )

        var found = false
        for (sv in epData.sv) {
            try {
                val apiUrl = "$api/video.php?is_mob=0&is_uc=0&id=$id&qua=${sv.qua}&server_id=${sv.sid}&cat=${sv.cat}&tag=${sv.tag}&c=$c&t=$t"
                val resp   = app.get(apiUrl, headers = headers).text
                val json   = tryParseJson<VideoPlayResponse>(resp) ?: continue
                val file   = json.file?.trim() ?: continue
                if (file.startsWith("http")) {
                    loadExtractor(file, referer = "$mainUrl/", subtitleCallback, callback)
                    found = true
                }
                // subtitle
                json.subtitle?.let { sub ->
                    if (sub.startsWith("http")) subtitleCallback(SubtitleFile("Indo", sub))
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        return found
    }
}
