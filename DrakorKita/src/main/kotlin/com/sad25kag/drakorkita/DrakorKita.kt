package com.sad25kag.drakorkita

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.Base64

class DrakorKita : MainAPI() {
    override var mainUrl = "https://drakor.kita.mobi"
    override var name = "DrakorKita"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.TvSeries, TvType.Movie)

    private val sourceHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Mobile Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    private val ajaxHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Mobile Safari/537.36",
        "Accept" to "text/plain, */*; q=0.01",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        "Origin" to mainUrl
    )

    override val mainPage = mainPageOf(
        "/" to "Eps Terbaru",
        "/" to "Complete / Ended",
        "/" to "Movie Terbaru",
        "/" to "Serie Terbaru",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = getDocument(buildPagedUrl(request.data, page))
        val list = document.toSearchResults(request.name)

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = list,
                isHorizontalImages = false
            ),
            hasNext = list.isNotEmpty() && hasNextPage(document, page)
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
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
        val title = document.pickTitle()
            .ifBlank { url.substringBeforeLast('/').substringAfterLast('/').replace('-', ' ') }
        val cleanTitle = cleanDetailTitle(title)
        val poster = document.pickPoster()
        val plot = document.pickDescription()
        val tags = document.pickTags()
        val year = Regex("""\((\d{4})\)""")
            .find(title)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        val configs = parseApiConfigs(document, url, cleanTitle, poster)
        val apiEpisodes = configs
            .take(1)
            .flatMap { config -> fetchEpisodes(config) }
            .distinctBy { it.data }
            .sortedBy { it.episode ?: Int.MAX_VALUE }

        val infoText = document.select("ul.anf").text()
        val isSeries = apiEpisodes.size > 1 ||
            infoText.contains("Type : TV Series", ignoreCase = true) ||
            title.contains("Season", ignoreCase = true) ||
            title.contains(Regex("""Episode\s+\d+\s*-\s*\d+""", RegexOption.IGNORE_CASE))

        return if (isSeries) {
            val episodes = if (apiEpisodes.isNotEmpty()) {
                apiEpisodes
            } else {
                configs.firstOrNull()?.let { config ->
                    listOf(
                        newEpisode(config.toPayloadJson()) {
                            name = "Episode"
                            posterUrl = poster
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
        val payload = ApiConfig.fromPayloadJson(data)
        if (payload != null) {
            val resolvedPayload = DrakorKitaResolver.resolvePayloadLinks(
                payload = payload.toResolverPayload(),
                mainUrl = mainUrl,
                headers = sourceHeaders,
                subtitleCallback = subtitleCallback,
                callback = callback
            )
            if (resolvedPayload) return true
        }

        val pageUrl = payload?.detailUrl ?: data
        if (!pageUrl.startsWith("http")) return false

        val doc = getDocument(pageUrl)
        val candidates = DrakorKitaResolver.extractEmbedCandidates(doc, mainUrl)

        var foundAny = false
        candidates.forEach { candidate ->
            val clean = DrakorKitaResolver.normalizeUrl(candidate, mainUrl)
            if (clean.isNotBlank()) {
                val lower = clean.lowercase()
                if (lower.contains(".m3u8") || lower.contains(".mp4")) {
                    foundAny = true
                    callback(
                        com.lagradost.cloudstream3.utils.newExtractorLink(
                            source = name,
                            name = "$name Direct",
                            url = clean
                        ) {
                            this.referer = pageUrl
                        }
                    )
                } else {
                    val loaded = com.lagradost.cloudstream3.utils.loadExtractor(
                        clean,
                        pageUrl,
                        subtitleCallback,
                        callback
                    )
                    if (loaded) foundAny = true
                }
            }
        }

        return foundAny
    }

    private fun parseApiConfigs(
        doc: Document,
        detailUrl: String,
        fallbackTitle: String,
        fallbackPoster: String?
    ): List<ApiConfig> {
        val list = mutableListOf<ApiConfig>()
        val jsonBlocks = extractInlineJsonObjects(doc.html())

        jsonBlocks.forEach { raw ->
            runCatching {
                val obj = JSONObject(raw)
                val isTarget = obj.has("movie_id") || obj.has("c_api_host") || obj.has("server_xid") || obj.has("episode_id")
                if (isTarget) {
                    val config = ApiConfig(
                        detailUrl = detailUrl,
                        title = obj.optString("title", fallbackTitle).ifBlank { fallbackTitle },
                        movieId = obj.optString("movie_id").ifBlank { "0" },
                        episodeId = obj.optString("episode_id").ifBlank { "0" },
                        serverXid = obj.optString("server_xid").ifBlank { "0" },
                        tag = obj.optString("tag").ifBlank { "" },
                        c = obj.optString("c").ifBlank { "" },
                        t = obj.optString("t").ifBlank { "" },
                        ver = obj.optString("ver").ifBlank { "1" },
                        cApiHost = obj.optString("c_api_host", mainUrl).ifBlank { mainUrl },
                        isMob = obj.optString("is_mob").ifBlank { "1" },
                        isUc = obj.optString("is_uc").ifBlank { "0" },
                        mediaType = obj.optString("media_type").ifBlank { "drama" }
                    )
                    list.add(config)
                }
            }
        }

        return list.distinctBy { "${it.movieId}_${it.episodeId}_${it.serverXid}_${it.tag}" }
    }

    private suspend fun fetchEpisodes(config: ApiConfig): List<com.lagradost.cloudstream3.Episode> {
        val episodes = mutableListOf<com.lagradost.cloudstream3.Episode>()

        val rawEpisodeText = runCatching {
            val endpoint = "${config.cApiHost.trimEnd('/')}/api/v1/episodes"
            val query = listOf(
                "movie_id" to config.movieId,
                "detail_url" to config.detailUrl,
                "tag" to config.tag,
                "c" to config.c,
                "t" to config.t,
                "ver" to config.ver,
                "is_mob" to config.isMob,
                "is_uc" to config.isUc
            ).joinToString("&") { "${it.first}=${URLEncoder.encode(it.second, "UTF-8")}" }

            app.get("$endpoint?$query", headers = ajaxHeaders).text
        }.getOrDefault("")

        if (rawEpisodeText.isNotBlank()) {
            runCatching {
                val root = JSONObject(rawEpisodeText)
                val array = root.optJSONArray("data") ?: root.optJSONArray("episodes")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val epNumber = item.optInt("episode", item.optInt("ep", i + 1))
                        val epName = item.optString("name", item.optString("title", "Episode $epNumber"))
                        val epDataConfig = config.copy(
                            episodeId = item.optString("episode_id", item.optString("id", config.episodeId)),
                            serverXid = item.optString("server_xid", config.serverXid),
                            tag = item.optString("tag", config.tag)
                        )

                        episodes.add(
                            newEpisode(epDataConfig.toPayloadJson()) {
                                this.name = epName
                                this.episode = epNumber
                            }
                        )
                    }
                }
            }
        }

        if (episodes.isEmpty()) {
            val formEpisodes = runCatching {
                val ajaxUrl = "${config.cApiHost.trimEnd('/')}/wp-admin/admin-ajax.php"
                val body = mapOf(
                    "action" to "get_episodes",
                    "movie_id" to config.movieId,
                    "tag" to config.tag,
                    "c" to config.c,
                    "t" to config.t,
                    "ver" to config.ver
                )
                app.post(ajaxUrl, data = body, headers = ajaxHeaders).text
            }.getOrDefault("")

            if (formEpisodes.isNotBlank()) {
                val parsedDoc = Jsoup.parse(formEpisodes)
                parsedDoc.select("a, button, li").forEachIndexed { index, element ->
                    val epName = element.text().ifBlank { "Episode ${index + 1}" }
                    val epNum = Regex("""\d+""").find(epName)?.value?.toIntOrNull() ?: (index + 1)
                    val epId = element.attr("data-episode_id").ifBlank { element.attr("data-id") }
                    val epConfig = config.copy(
                        episodeId = if (epId.isNotBlank()) epId else config.episodeId
                    )

                    episodes.add(
                        newEpisode(epConfig.toPayloadJson()) {
                            this.name = epName
                            this.episode = epNum
                        }
                    )
                }
            }
        }

        return episodes.distinctBy { "${it.episode}_${it.data}" }
    }

    private fun extractInlineJsonObjects(html: String): List<String> {
        val results = mutableListOf<String>()
        val regex = Regex("""\{[^{}]*"movie_id"\s*:\s*[^}]*\}""", RegexOption.IGNORE_CASE)
        regex.findAll(html).forEach { match ->
            results.add(match.value)
        }

        if (results.isEmpty()) {
            var depth = 0
            var start = -1
            for (i in html.indices) {
                when (html[i]) {
                    '{' -> {
                        if (depth == 0) start = i
                        depth++
                    }
                    '}' -> {
                        if (depth > 0) {
                            depth--
                            if (depth == 0 && start != -1) {
                                val block = html.substring(start, i + 1)
                                if (block.contains("movie_id", ignoreCase = true) || block.contains("c_api_host", ignoreCase = true)) {
                                    results.add(block)
                                }
                                start = -1
                            }
                        }
                    }
                }
            }
        }

        return results
    }

    private fun Document.toSearchResults(sectionName: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        val targetElements = select(".content-item, .post-item, article, div.item, div.post-thumbnail, div.box, a.poster, a[href*='/drama/'], a[href*='/movie/'], a[href*='/series/']")

        targetElements.forEach { element ->
            val linkElement = if (element.tagName() == "a") element else element.selectFirst("a[href]") ?: return@forEach
            val href = linkElement.attr("href")
            val fullUrl = fixUrlNull(href) ?: return@forEach

            if (!isValidContentUrl(fullUrl)) return@forEach

            val rawTitle = element.selectFirst(".title, .entry-title, h2, h3, h4, .name")?.text()
                ?: linkElement.attr("title").ifBlank { linkElement.attr("alt") }
                ?: linkElement.text()
            val cleanTitle = cleanDetailTitle(rawTitle)
            if (cleanTitle.isBlank()) return@forEach

            val imageElement = element.selectFirst("img") ?: linkElement.selectFirst("img")
            val poster = imageElement?.attr("data-src")
                ?.ifBlank { imageElement.attr("src") }
                ?.ifBlank { imageElement.attr("data-lazy-src") }
                ?.let { fixUrlNull(it) }

            val isMovie = sectionName.contains("Movie", ignoreCase = true) ||
                fullUrl.contains("/movie/", ignoreCase = true) ||
                rawTitle.contains("Movie", ignoreCase = true)

            val isEnd = sectionName.contains("Ended", ignoreCase = true) ||
                sectionName.contains("Complete", ignoreCase = true)

            val response = if (isMovie) {
                newMovieSearchResponse(cleanTitle, fullUrl, TvType.Movie) {
                    this.posterUrl = poster
                }
            } else {
                newTvSeriesSearchResponse(cleanTitle, fullUrl, TvType.AsianDrama) {
                    this.posterUrl = poster
                }
            }

            if (sectionName == "Complete / Ended" && !isEnd) {
                return@forEach
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
            "/contact", "/privacy", "/disclaimer", "/login", "/register"
        )
        return blacklisted.none { lower.contains(it) }
    }

    private fun hasNextPage(doc: Document, currentPage: Int): Boolean {
        val nextLink = doc.selectFirst("a.next, a.next-page, a[rel=next], .pagination a:contains(Next), .pagination a:contains(>)")
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
        return selectFirst("h1.entry-title, h1.title, h1.name, h1")?.text()
            ?: selectFirst("meta[property=og:title]")?.attr("content")
            ?: title()
    }

    private fun Document.pickPoster(): String? {
        val img = selectFirst(".poster img, .thumb img, article img, div.entry-content img")
        val src = img?.attr("data-src")
            ?.ifBlank { img.attr("src") }
            ?.ifBlank { img.attr("data-lazy-src") }
        return fixUrlNull(src) ?: fixUrlNull(selectFirst("meta[property=og:image]")?.attr("content"))
    }

    private fun Document.pickDescription(): String? {
        return selectFirst(".entry-content p, .synopsis, .description, .desc")?.text()?.trim()
            ?.ifBlank { selectFirst("meta[property=og:description]")?.attr("content")?.trim() }
    }

    private fun Document.pickTags(): List<String> {
        return select(".genre a, .tag a, .category a, ul.anf a").map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun cleanDetailTitle(raw: String): String {
        return raw.replace(Regex("""(?i)\s*(nonton|subtitle|indo|sub|download|stream|hd|4k)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private suspend fun getDocument(url: String): Document {
        val res = app.get(url, headers = sourceHeaders)
        return Jsoup.parse(res.text, url)
    }

    data class ApiConfig(
        val detailUrl: String,
        val title: String,
        val movieId: String,
        val episodeId: String,
        val serverXid: String,
        val tag: String,
        val c: String,
        val t: String,
        val ver: String,
        val cApiHost: String,
        val isMob: String,
        val isUc: String,
        val mediaType: String
    ) {
        fun toPayloadJson(): String {
            val json = JSONObject()
            json.put("detail_url", detailUrl)
            json.put("title", title)
            json.put("movie_id", movieId)
            json.put("episode_id", episodeId)
            json.put("server_xid", serverXid)
            json.put("tag", tag)
            json.put("c", c)
            json.put("t", t)
            json.put("ver", ver)
            json.put("c_api_host", cApiHost)
            json.put("is_mob", isMob)
            json.put("is_uc", isUc)
            json.put("media_type", mediaType)
            return json.toString()
        }

        fun toResolverPayload(): DrakorKitaResolver.ApiPayload {
            return DrakorKitaResolver.ApiPayload(
                detailUrl = detailUrl,
                title = title,
                movieId = movieId,
                episodeId = episodeId,
                serverXid = serverXid,
                tag = tag,
                c = c,
                t = t,
                ver = ver,
                cApiHost = cApiHost,
                isMob = isMob,
                isUc = isUc,
                mediaType = mediaType
            )
        }

        companion object {
            fun fromPayloadJson(jsonString: String): ApiConfig? {
                return runCatching {
                    val obj = JSONObject(jsonString)
                    ApiConfig(
                        detailUrl = obj.optString("detail_url"),
                        title = obj.optString("title"),
                        movieId = obj.optString("movie_id"),
                        episodeId = obj.optString("episode_id"),
                        serverXid = obj.optString("server_xid"),
                        tag = obj.optString("tag"),
                        c = obj.optString("c"),
                        t = obj.optString("t"),
                        ver = obj.optString("ver"),
                        cApiHost = obj.optString("c_api_host"),
                        isMob = obj.optString("is_mob"),
                        isUc = obj.optString("is_uc"),
                        mediaType = obj.optString("media_type")
                    )
                }.getOrNull()
            }
        }
    }
}
