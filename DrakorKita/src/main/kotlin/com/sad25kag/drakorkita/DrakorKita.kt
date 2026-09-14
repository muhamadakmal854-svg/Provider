package com.sad25kag.drakorkita

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DrakorKita : MainAPI() {
    override var mainUrl = "https://drakor.kita.mobi"
    override var name = "DrakorKita"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.TvSeries, TvType.Movie)

    private val sourceHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        "Referer" to "$mainUrl/"
    )

    private val ajaxHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "Accept" to "text/plain, */*; q=0.01",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        "Origin" to mainUrl,
        "Referer" to "$mainUrl/"
    )

    override val mainPage = mainPageOf(
        "all?status=returning%20series"   to "🔥 Drama Ongoing Terhangat",
        ""                                to "⚡ Episod Terkini",
        "all?media_type=tv"               to "✨ K-Drama Terpopuler",
        "all?genre=Romance"               to "💖 Drakor Romantis",
        "all?genre=Action"                to "💥 Drama Aksi & Ketegangan",
        "all?genre=Comedy"                to "😂 Drama Komedi",
        "all?genre=Fantasy"               to "🔮 Fantasi & Supernatural",
        "all?status=ended"                to "🏁 Drama Lengkap (Tamat)",
        "all?media_type=movie"            to "🎬 Filem Korea & Asia",
        "all"                             to "📺 Semua Drama & Filem"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = getDocument(buildPagedUrl(request.data, page))
        val isSpotlight = request.name.contains("Ongoing", ignoreCase = true) ||
            request.name.contains("Terkini", ignoreCase = true)
        val list = document.toSearchResults(request.name)

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = list,
                isHorizontalImages = isSpotlight
            ),
            hasNext = list.isNotEmpty() && hasNextPage(document, page)
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchUrls = listOf(
            buildPagedUrl("all?q=$encoded", page)
        )

        val results = linkedMapOf<String, SearchResponse>()
        searchUrls.forEach { url ->
            runCatching {
                getDocument(url).toSearchResults("Search")
            }.getOrDefault(emptyList()).forEach { item ->
                results[item.url] = item
            }
        }

        return results.values.toList().toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val document = getDocument(url)
        val rawTitle = document.pickTitle()
            .ifBlank { url.substringBeforeLast('/').substringAfterLast('/').replace('-', ' ') }
        val cleanTitle = cleanDetailTitle(rawTitle)
        val poster = document.pickPoster()
        val plot = document.pickDescription()
        val tags = document.pickTags()
        val year = Regex("""\((\d{4})\)""")
            .find(rawTitle)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val baseYear = year ?: 2026

        val configs = parseApiConfigs(document, url, cleanTitle, poster)
        val apiEpisodes = configs
            .take(1)
            .flatMap { config -> fetchEpisodes(config, poster, baseYear) }
            .distinctBy { it.data }
            .sortedBy { it.episode ?: Int.MAX_VALUE }

        val infoText = document.select("ul.anf, .anf").text()
        val isSeries = apiEpisodes.size > 1 ||
            infoText.contains("Type : TV Series", ignoreCase = true) ||
            infoText.contains("Type: TV Series", ignoreCase = true) ||
            infoText.contains("Type:  TV Series", ignoreCase = true) ||
            rawTitle.contains("Season", ignoreCase = true) ||
            rawTitle.contains(Regex("""Episode\s+\d+\s*-\s*\d+""", RegexOption.IGNORE_CASE))

        return if (isSeries) {
            val episodes = if (apiEpisodes.isNotEmpty()) {
                apiEpisodes
            } else {
                configs.firstOrNull()?.let { config ->
                    listOf(
                        newEpisode(config.toPayloadJson()) {
                            this.name = "Episode 1"
                            this.episode = 1
                            this.posterUrl = poster
                            this.date = Calendar.getInstance().apply {
                                set(baseYear, Calendar.JANUARY, 1, 0, 0, 0)
                            }.timeInMillis
                            this.description = "Rilis: 1 Januari $baseYear • Sub Indo"
                        }
                    )
                } ?: emptyList()
            }

            newTvSeriesLoadResponse(cleanTitle, url, TvType.AsianDrama, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        } else {
            val movieData = apiEpisodes.firstOrNull()?.data
                ?: configs.firstOrNull()?.toPayloadJson()
                ?: url

            newMovieLoadResponse(cleanTitle, url, TvType.Movie, movieData) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
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
        val payload = parsePayload(data)

        if (payload != null) {
            val apiHandled = DrakorKitaResolver.resolveApiPlayback(
                providerName = name,
                mainUrl = mainUrl,
                payload = payload,
                ajaxHeaders = ajaxHeaders,
                subtitleCallback = subtitleCallback,
                callback = callback
            )
            if (apiHandled) foundAny = true
        }

        val pageUrl = payload?.detailUrl ?: fixUrl(data)
        runCatching {
            val document = getDocument(pageUrl)

            val subs = DrakorKitaResolver.extractSubtitles(document, mainUrl)
            subs.forEach(subtitleCallback)

            val embedCandidates = DrakorKitaResolver.extractEmbedCandidates(document, mainUrl)
            val embedHandled = DrakorKitaResolver.resolveCandidates(
                providerName = name,
                mainUrl = mainUrl,
                pageUrl = pageUrl,
                candidates = embedCandidates,
                subtitleCallback = subtitleCallback,
                callback = callback
            )
            if (embedHandled) foundAny = true
        }

        return foundAny
    }

    private suspend fun fetchEpisodes(
        config: ApiConfig,
        seriesPoster: String?,
        baseYear: Int
    ): List<com.lagradost.cloudstream3.Episode> {
        val candidateApiHosts = listOf(
            config.cApiHost.trimEnd('/'),
            "https://api.nonton.bid/c_api",
            "$mainUrl/c_api",
            "https://drakorindo18.kita.baby/c_api"
        ).filter { it.isNotBlank() }.distinct()

        val episodes = mutableListOf<com.lagradost.cloudstream3.Episode>()
        val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("id", "ID"))

        for (cApiHost in candidateApiHosts) {
            val url = "$cApiHost/episode_mob.php" +
                "?is_mob=${config.isMob}" +
                "&is_uc=${config.isUc}" +
                "&movie_id=${encode(config.movieId)}" +
                "&cat=${encode(config.tag)}" +
                "&tag=${encode(config.ver)}" +
                "&c=${encode(config.c)}" +
                "&t=${encode(config.t)}"

            val json = runCatching {
                val res = app.get(url, headers = ajaxHeaders, referer = config.detailUrl)
                val txt = res.text.trim()
                if (txt.startsWith("{") && txt.endsWith("}")) JSONObject(txt) else null
            }.getOrNull() ?: continue

            val episodeListsHtml = json.optString("episode_lists")
            val defaultServerXid = json.optString("server_xid", "f1")

            if (episodeListsHtml.isNotBlank()) {
                val doc = Jsoup.parse(episodeListsHtml)
                val buttons = doc.select("a.btn-svr, a[data-epid]")

                buttons.forEachIndexed { i, btn ->
                    val epId = btn.attr("data-epid").ifBlank { btn.attr("data-id") }
                    val epNameText = btn.text().trim()
                    val epNum = epNameText.toIntOrNull()
                        ?: Regex("""\d+""").find(epNameText)?.value?.toIntOrNull()
                        ?: (i + 1)
                    val epXid = btn.attr("data-server_xid").ifBlank { defaultServerXid }
                    val epCat = btn.attr("data-cat").ifBlank { config.tag }
                    val epVer = btn.attr("data-tag").ifBlank { config.ver }

                    val payload = DrakorKitaResolver.ApiPayload(
                        detailUrl = config.detailUrl,
                        title = config.cleanTitle,
                        movieId = config.movieId,
                        episodeId = epId,
                        serverXid = epXid,
                        tag = epCat,
                        c = config.c,
                        t = config.t,
                        ver = epVer,
                        cApiHost = config.cApiHost,
                        isMob = config.isMob,
                        isUc = config.isUc,
                        mediaType = "tv"
                    )

                    val calendar = Calendar.getInstance()
                    calendar.set(baseYear, Calendar.JANUARY, 1, 0, 0, 0)
                    calendar.add(Calendar.DAY_OF_YEAR, (epNum - 1) * 7)
                    val epDateEpoch = calendar.timeInMillis
                    val dateStr = dateFormat.format(Date(epDateEpoch))

                    episodes.add(
                        newEpisode(payload.toPayloadJson()) {
                            this.name = "Episode $epNum"
                            this.episode = epNum
                            this.posterUrl = config.poster ?: seriesPoster
                            this.date = epDateEpoch
                            this.description = "Rilis: $dateStr • Sub Indo"
                        }
                    )
                }
            }

            if (episodes.isNotEmpty()) break
        }

        return episodes
    }

    private fun parseApiConfigs(
        document: Document,
        detailUrl: String,
        cleanTitle: String,
        poster: String?
    ): List<ApiConfig> {
        val html = document.html()
        val script2Vars = decodeScript2Variables(html)

        val jsVars = extractJsVariables(html)
        val combinedVars = script2Vars + jsVars

        val c = combinedVars["c"].orEmpty()
        val t = combinedVars["t"].orEmpty()
        val isMob = combinedVars["is_mob"].orEmpty().ifBlank { "0" }
        val isUc = combinedVars["is_uc"].orEmpty().ifBlank { "0" }
        val cApiHost = combinedVars["c_api_host"].orEmpty()
            .ifBlank { combinedVars["api_host"].orEmpty() }
            .ifBlank { "https://api.nonton.bid/c_api" }

        val configs = mutableListOf<ApiConfig>()

        val epButtons = document.select("a.post-page-numbers, div.pagination a, button[onclick*='loadEpisode'], a[onclick*='loadEpisode']")
        epButtons.forEach { element ->
            val onclick = element.attr("onclick")
            val (movieId, tag, ver) = parseOnclickLoadEpisode(onclick)
            if (movieId.isNotBlank()) {
                configs.add(
                    ApiConfig(
                        detailUrl = detailUrl,
                        cleanTitle = cleanTitle,
                        poster = poster,
                        movieId = movieId,
                        tag = tag.ifBlank { "hs" },
                        c = c,
                        t = t,
                        ver = ver.ifBlank { "ind" },
                        cApiHost = cApiHost,
                        isMob = isMob,
                        isUc = isUc
                    )
                )
            }
        }

        if (configs.isEmpty()) {
            val initMatch = Regex("""initEpisodeList\s*\(\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"]""").find(html)
            if (initMatch != null) {
                val (movieId, tag, ver) = initMatch.destructured
                configs.add(
                    ApiConfig(
                        detailUrl = detailUrl,
                        cleanTitle = cleanTitle,
                        poster = poster,
                        movieId = movieId,
                        tag = tag.ifBlank { "hs" },
                        c = c,
                        t = t,
                        ver = ver.ifBlank { "ind" },
                        cApiHost = cApiHost,
                        isMob = isMob,
                        isUc = isUc
                    )
                )
            }
        }

        return configs.distinctBy { "${it.movieId}_${it.tag}" }
    }

    private fun parseOnclickLoadEpisode(onclick: String): Triple<String, String, String> {
        val match = Regex("""loadEpisode\s*\(\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"]""").find(onclick)
            ?: Regex("""initEpisodeList\s*\(\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"]""").find(onclick)
        if (match != null) {
            return Triple(match.groupValues[1], match.groupValues[2], match.groupValues[3])
        }
        return Triple("", "", "")
    }

    private fun decodeScript2Variables(html: String): Map<String, String> {
        val script2Regex = Regex("""([a-zA-Z0-9_$]+)\s*=\s*'([A-Za-z0-9+/=]{10,}(?:\.[A-Za-z0-9+/=]{10,})+)'""")
        val script2Match = script2Regex.find(html) ?: return emptyMap()

        val payload = script2Match.groupValues[2]
        val decodedChars = mutableListOf<Char>()

        payload.split('.').forEach { chunk ->
            if (chunk.isNotBlank()) {
                val padded = chunk + "=".repeat((4 - chunk.length % 4) % 4)
                runCatching {
                    val rawStr = String(Base64.getDecoder().decode(padded), Charsets.ISO_8859_1)
                    val digits = rawStr.replace(Regex("""\D"""), "")
                    if (digits.isNotBlank()) {
                        decodedChars.add(digits.toInt().toChar())
                    }
                }
            }
        }

        val decodedScript = decodedChars.joinToString("")
        val vars = extractJsVariables(decodedScript).toMutableMap()

        // Also extract initEpisodeList arguments
        val initEp = Regex("""initEpisodeList\s*\(\s*['"]([^'"]*)['"]\s*,\s*['"]([^'"]*)['"]\s*,\s*['"]([^'"]*)['"]\s*\)""").find(decodedScript)
        if (initEp != null) {
            vars["movie_id"] = initEp.groupValues[1]
            vars["cat"] = initEp.groupValues[2]
            vars["tag"] = initEp.groupValues[3]
        }

        return vars
    }

    private fun extractJsVariables(script: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val varRegex = Regex("""var\s+([a-zA-Z0-9_$]+)\s*=\s*['"]([^'"]*)['"]""")
        varRegex.findAll(script).forEach { match ->
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
    }

    private fun parsePayload(data: String): DrakorKitaResolver.ApiPayload? {
        val trimmed = data.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching {
            val json = JSONObject(trimmed)
            DrakorKitaResolver.ApiPayload(
                detailUrl = json.optString("detailUrl"),
                title = json.optString("title"),
                movieId = json.optString("movieId"),
                episodeId = json.optString("episodeId"),
                serverXid = json.optString("serverXid"),
                tag = json.optString("tag"),
                c = json.optString("c"),
                t = json.optString("t"),
                ver = json.optString("ver"),
                cApiHost = json.optString("cApiHost"),
                isMob = json.optString("isMob", "0"),
                isUc = json.optString("isUc", "0"),
                mediaType = json.optString("mediaType")
            )
        }.getOrNull()
    }

    private fun Document.toSearchResults(sectionName: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        val targetElements = select("a.poster, .content-item, .post-item, article, div.item, a[href*='/detail/']")

        targetElements.forEach { element ->
            val linkElement = if (element.tagName() == "a") element else element.selectFirst("a[href]") ?: return@forEach
            val href = linkElement.attr("href")
            val fullUrl = fixUrlNull(href) ?: return@forEach

            if (!isValidContentUrl(fullUrl)) return@forEach

            val rawTitle = element.selectFirst(".titit, .title, .entry-title, h2, h3, h4, .name")?.text()
                ?: linkElement.attr("title").ifBlank { linkElement.attr("alt") }
                ?: linkElement.text()

            val cleanTitle = cleanDetailTitle(rawTitle)
            if (cleanTitle.isBlank()) return@forEach

            val imageElement = element.selectFirst("img.poster, img[src*='tmdb'], img:not([src*='flagsapi']):not([src*='flag'])")
                ?: linkElement.selectFirst("img.poster, img[src*='tmdb'], img:not([src*='flagsapi']):not([src*='flag'])")

            var poster = imageElement?.attr("data-src")
                ?.ifBlank { imageElement.attr("src") }
                ?.ifBlank { imageElement.attr("data-lazy-src") }
                ?.let { fixUrlNull(it) }

            if (poster != null && (poster.contains("flagsapi") || poster.contains("flag"))) {
                poster = null
            }

            val isMovie = sectionName.equals("Filem Korea & Asia", ignoreCase = true) ||
                sectionName.equals("Movie", ignoreCase = true) ||
                fullUrl.contains("media_type=movie", ignoreCase = true) ||
                fullUrl.contains("/movie/", ignoreCase = true)

            val response = if (isMovie) {
                newMovieSearchResponse(cleanTitle, fullUrl, TvType.Movie) {
                    this.posterUrl = poster
                }
            } else {
                newAnimeSearchResponse(cleanTitle, fullUrl, TvType.AsianDrama) {
                    this.posterUrl = poster
                    val epNum = Regex("""(?i)\b(?:E|Ep|Episode)\s*(\d+)""").find(rawTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?: Regex("""\b(\d+)\s*$""").find(rawTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    if (epNum != null) {
                        addDubStatus(false, epNum)
                    }
                }
            }

            results.add(response)
        }

        return results.distinctBy { it.url }
    }

    private fun isValidContentUrl(url: String): Boolean {
        if (!url.startsWith("http")) return false
        val lower = url.lowercase()
        val blacklisted = listOf(
            "/category/", "/tag/", "/genre/", "/page/", "/dmca", "/about",
            "/contact", "/privacy", "/disclaimer", "/login", "/register", "/change_theme"
        )
        return blacklisted.none { lower.contains(it) }
    }

    private fun hasNextPage(doc: Document, currentPage: Int): Boolean {
        val nextLink = doc.selectFirst("a.next, a.next-page, a[rel=next], .pagination a:contains(Next), .pagination a:contains(>), li.next a")
        if (nextLink != null) return true
        val pageNumbers = doc.select(".pagination a, .nav-links a").mapNotNull {
            Regex("""\d+""").find(it.text())?.value?.toIntOrNull()
        }
        return pageNumbers.any { it > currentPage }
    }

    private fun buildPagedUrl(path: String, page: Int): String {
        val base = mainUrl.trimEnd('/')
        val cleanPath = path.trimStart('/')
        if (page <= 1) return if (cleanPath.isBlank()) base else "$base/$cleanPath"

        return when {
            cleanPath.isBlank() -> "$base/page/$page"
            cleanPath.contains("?") -> {
                val parts = cleanPath.split("?", limit = 2)
                "$base/${parts[0].trimEnd('/')}/page/$page?${parts[1]}"
            }
            else -> "$base/${cleanPath.trimEnd('/')}/page/$page"
        }
    }

    private fun Document.pickTitle(): String {
        return selectFirst("h1.entry-title, h1.title, h1.name, h1, .titit")?.text()
            ?: selectFirst("meta[property=og:title]")?.attr("content")
            ?: title()
    }

    private fun Document.pickPoster(): String? {
        val img = selectFirst("img.poster, img[src*='tmdb'], img:not([src*='flagsapi']):not([src*='flag'])")
        val src = img?.attr("data-src")
            ?.ifBlank { img.attr("src") }
            ?.ifBlank { img.attr("data-lazy-src") }

        val posterUrl = fixUrlNull(src) ?: fixUrlNull(selectFirst("meta[property=og:image]")?.attr("content"))
        if (posterUrl != null && (posterUrl.contains("flagsapi") || posterUrl.contains("flag"))) {
            return null
        }
        return posterUrl
    }

    private fun Document.pickDescription(): String? {
        return selectFirst(".entry-content p, .synopsis, .description, .desc, .mv-description")?.text()?.trim()
            ?.ifBlank { selectFirst("meta[property=og:description]")?.attr("content")?.trim() }
    }

    private fun Document.pickTags(): List<String> {
        return select(".genre a, .tag a, .category a, ul.anf a").map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun cleanDetailTitle(raw: String): String {
        return raw
            .replace(Regex("""(?i)\b(nonton\s+drama\s+korea|nonton\s+film|nonton|drama\s+korea|drakor|subtitle\s+indonesia|sub\s+indo|batch|complete|ongoing|tamat|download|stream|hd|4k|1080p|720p|480p)\b"""), " ")
            .replace(Regex("""(?i)\b(?:E|Ep|Episode)\s*\d+\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private suspend fun getDocument(url: String): Document {
        val res = app.get(url, headers = sourceHeaders)
        return Jsoup.parse(res.text, url)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    data class ApiConfig(
        val detailUrl: String,
        val cleanTitle: String,
        val poster: String?,
        val movieId: String,
        val tag: String,
        val c: String,
        val t: String,
        val ver: String,
        val cApiHost: String,
        val isMob: String,
        val isUc: String
    ) {
        fun toPayloadJson(): String {
            val json = JSONObject()
            json.put("detailUrl", detailUrl)
            json.put("title", cleanTitle)
            json.put("movieId", movieId)
            json.put("tag", tag)
            json.put("c", c)
            json.put("t", t)
            json.put("ver", ver)
            json.put("cApiHost", cApiHost)
            json.put("isMob", isMob)
            json.put("isUc", isUc)
            return json.toString()
        }
    }

    private fun DrakorKitaResolver.ApiPayload.toPayloadJson(): String {
        val json = JSONObject()
        json.put("detailUrl", detailUrl)
        json.put("title", title)
        json.put("movieId", movieId)
        json.put("episodeId", episodeId)
        json.put("serverXid", serverXid)
        json.put("tag", tag)
        json.put("c", c)
        json.put("t", t)
        json.put("ver", ver)
        json.put("cApiHost", cApiHost)
        json.put("isMob", isMob)
        json.put("isUc", isUc)
        json.put("mediaType", mediaType)
        return json.toString()
    }
}
