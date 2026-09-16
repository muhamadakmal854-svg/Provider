package com.mts.rebahin

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

open class Rebahin : MainAPI() {
    override var mainUrl = "https://165.232.44.215"
    override var name = "Rebahin"
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    companion object {
        private const val UA_BROWSER =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private val FALLBACK_DOMAINS = listOf(
            "https://165.232.44.215",
            "https://rebahin.com",
            "https://165.227.239.221",
            "https://139.59.196.140",
            "https://139.59.197.199"
        )
    }

    // Tampilan Homepage Gaya Netflix dengan 22 Kategori Tematik Lengkap
    override val mainPage = mainPageOf(
        "$mainUrl/movies" to "✨ Pilihan Utama (Featured Movies)",
        "$mainUrl/movies?sort=trending" to "🔥 Sedang Hangat & Trending Hari Ini",
        "$mainUrl/tv" to "📺 Serial TV Terbaru (New Series)",
        "$mainUrl/movies?sort=popular" to "⭐ Film Box Office Terpopuler",
        "$mainUrl/country/kr" to "🇰🇷 Drama Korea Terhangat (K-Drama)",
        "$mainUrl/country/cn" to "🇨🇳 Drama Mandarin Populer (C-Drama)",
        "$mainUrl/country/jp" to "🇯🇵 Sinema & Drama Jepang (J-Drama)",
        "$mainUrl/genre/action" to "💥 Aksi & Laga Menegangkan (Action)",
        "$mainUrl/genre/horror" to "👻 Horor & Supranatural Seram (Horror)",
        "$mainUrl/genre/adventure" to "🗺️ Petualangan Epik (Adventure)",
        "$mainUrl/genre/comedy" to "😂 Komedi Lucu & Menghibur (Comedy)",
        "$mainUrl/genre/crime" to "🕵️ Kriminal & Siasatan (Crime)",
        "$mainUrl/genre/drama" to "🎭 Drama Kisah Kehidupan (Drama)",
        "$mainUrl/genre/fantasy" to "🔮 Fantasi & Dunia Sihir (Fantasy)",
        "$mainUrl/genre/mystery" to "🔍 Misteri & Teka-Teki (Mystery)",
        "$mainUrl/genre/romance" to "🌸 Romantis & Percintaan (Romance)",
        "$mainUrl/genre/science-fiction" to "🚀 Fiksyen Sains & Angkasa (Sci-Fi)",
        "$mainUrl/genre/thriller" to "🔪 Thriller Mendebarkan (Thriller)",
        "$mainUrl/genre/animation" to "🎨 Animasi & Film Kartun (Animation)",
        "$mainUrl/country/ph" to "🇵🇭 Sinema Filipina & Asia Tenggara",
        "$mainUrl/movies?sort=rating" to "💎 Rating Tertinggi (Top Rated)",
        "$mainUrl/tv?status=completed" to "🏆 Serial TV Tamat (Binge-Watch)"
    )

    private suspend fun safeGet(url: String, referer: String = "$mainUrl/"): String {
        val firstAttempt = runCatching {
            app.get(
                url,
                headers = mapOf(
                    "User-Agent" to UA_BROWSER,
                    "Referer" to referer,
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                ),
                timeout = 10
            ).text
        }.getOrNull()

        if (!firstAttempt.isNullOrBlank()) return firstAttempt

        for (domain in FALLBACK_DOMAINS) {
            val fallbackUrl = try {
                val origUri = java.net.URI(url)
                val targetUri = java.net.URI(domain)
                val newPort = if (targetUri.port != -1) ":${targetUri.port}" else ""
                val pathAndQuery = (origUri.rawPath ?: "") + if (origUri.rawQuery != null) "?${origUri.rawQuery}" else ""
                "${targetUri.scheme}://${targetUri.host}$newPort$pathAndQuery"
            } catch (_: Exception) {
                null
            } ?: continue

            val res = runCatching {
                app.get(
                    fallbackUrl,
                    headers = mapOf(
                        "User-Agent" to UA_BROWSER,
                        "Referer" to "$domain/",
                        "Accept" to "*/*"
                    ),
                    timeout = 8
                ).text
            }.getOrNull()

            if (!res.isNullOrBlank()) return res
        }

        return ""
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val sep = if (request.data.contains("?")) "&" else "?"
        val url = if (page <= 1) request.data else "${request.data}${sep}page=$page"
        val res = safeGet(url, referer = "$mainUrl/")
        val items = parseRebahinItems(res)
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    private fun parseRebahinItems(html: String): List<SearchResponse> {
        val items = mutableListOf<SearchResponse>()
        if (html.isBlank()) return items

        val cleanHtml = html
            .replace("\\\"", "\"")
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\n", " ")

        // 1. Next.js JSON RSC Payload Parser
        val itemRegex = Regex("""\{[^{}]*?"id"\s*:\s*"([^"]+)"[^{}]*?"title"\s*:\s*"([^"]+)"[^{}]*\}""")
        itemRegex.findAll(cleanHtml).forEach { match ->
            val block = match.value
            val id = match.groupValues[1]
            val title = match.groupValues[2]

            if (id.isNotBlank() && title.isNotBlank() && !title.startsWith("REBAHIN", true)) {
                val type = Regex(""""type"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1) ?: "movie"
                val isTv = type.equals("tv", ignoreCase = true)
                val itemUrl = if (isTv) "$mainUrl/tv/$id" else "$mainUrl/movies/$id"

                var poster = Regex(""""posterPath"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1)
                    ?: Regex(""""posterUrl"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1)
                    ?: Regex(""""image"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1)

                if (poster != null) {
                    if (poster.contains("/_next/image?url=")) {
                        val extracted = poster.substringAfter("url=").substringBefore("&")
                        poster = runCatching { java.net.URLDecoder.decode(extracted, "UTF-8") }.getOrDefault(extracted)
                    }
                    if (poster.startsWith("/")) {
                        poster = "https://image.tmdb.org/t/p/w500$poster"
                    }
                }

                val item = if (isTv) {
                    newTvSeriesSearchResponse(title, itemUrl, TvType.TvSeries) {
                        this.posterUrl = poster
                    }
                } else {
                    newMovieSearchResponse(title, itemUrl, TvType.Movie) {
                        this.posterUrl = poster
                    }
                }
                items.add(item)
            }
        }

        // 2. DOM HTML Jsoup Fallback
        if (items.isEmpty()) {
            val doc = Jsoup.parse(html)
            doc.select("a[href^='/movies/'], a[href^='/tv/'], a[href*='/movies/'], a[href*='/tv/']").forEach { a ->
                val href = a.attr("href")
                val img = a.select("img").firstOrNull()
                var rawPoster = img?.attr("src")?.ifBlank { img.attr("data-src") }
                if (!rawPoster.isNullOrBlank() && rawPoster.contains("/_next/image?url=")) {
                    val extracted = rawPoster.substringAfter("url=").substringBefore("&")
                    rawPoster = runCatching { java.net.URLDecoder.decode(extracted, "UTF-8") }.getOrDefault(extracted)
                }
                if (!rawPoster.isNullOrBlank() && rawPoster.startsWith("/")) {
                    rawPoster = "https://image.tmdb.org/t/p/w500$rawPoster"
                }

                val title = img?.attr("alt")?.ifBlank { null }
                    ?: a.select(".font-medium, span, h2, h3").text().ifBlank { null }
                    ?: a.attr("title").ifBlank { null }
                    ?: a.text().trim()

                if (href.isNotBlank() && title.isNotBlank() && !title.contains("REBAHIN", true) && title.length > 1) {
                    val isTv = href.contains("/tv/")
                    val fullUrl = if (href.startsWith("http")) href else "$mainUrl$href"

                    val item = if (isTv) {
                        newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries) {
                            this.posterUrl = rawPoster
                        }
                    } else {
                        newMovieSearchResponse(title, fullUrl, TvType.Movie) {
                            this.posterUrl = rawPoster
                        }
                    }
                    items.add(item)
                }
            }
        }

        return items.distinctBy { it.url }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchApiUrl = "$mainUrl/api/search?q=$encodedQuery"
        val res = safeGet(searchApiUrl, referer = "$mainUrl/")
        val apiItems = parseRebahinItems(res)
        if (apiItems.isNotEmpty()) return apiItems

        val webSearchUrl = "$mainUrl/search?q=$encodedQuery"
        val webRes = safeGet(webSearchUrl, referer = "$mainUrl/")
        return parseRebahinItems(webRes)
    }

    override suspend fun load(url: String): LoadResponse {
        val res = safeGet(url, referer = "$mainUrl/")
        val cleanHtml = res
            .replace("\\\"", "\"")
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\n", " ")

        val title = Regex("""(?i)"title"\s*:\s*"([^"]+)"""").find(cleanHtml)?.groupValues?.get(1)?.trim()
            ?: Regex("""<h1[^>]*>([^<]+)</h1>""").find(res)?.groupValues?.get(1)?.trim()
            ?: Jsoup.parse(res).select("h1, meta[property='og:title']").attr("content").ifBlank {
                Jsoup.parse(res).select("h1").text()
            }.replace(Regex("""(?i)\s*-\s*REBAHIN.*$"""), "").trim()
            ?: "Rebahin"

        var poster = Regex("""(?i)"posterPath"\s*:\s*"([^"]+)"""").find(cleanHtml)?.groupValues?.get(1)
            ?: Regex("""(?i)"posterUrl"\s*:\s*"([^"]+)"""").find(cleanHtml)?.groupValues?.get(1)
            ?: Jsoup.parse(res).select("meta[property='og:image']").attr("content").ifBlank { null }

        if (poster != null) {
            if (poster.contains("/_next/image?url=")) {
                val extracted = poster.substringAfter("url=").substringBefore("&")
                poster = runCatching { java.net.URLDecoder.decode(extracted, "UTF-8") }.getOrDefault(extracted)
            }
            if (poster.startsWith("/")) {
                poster = "https://image.tmdb.org/t/p/w500$poster"
            }
        }

        val plot = Regex("""(?i)"overview"\s*:\s*"([^"]+)"""").find(cleanHtml)?.groupValues?.get(1)
            ?: Jsoup.parse(res).select("meta[property='og:description'], meta[name='description']").attr("content").ifBlank { null }

        val year = Regex("""(?i)"releaseYear"\s*:\s*(\d+)""").find(cleanHtml)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""(?i)"year"\s*:\s*(\d+)""").find(cleanHtml)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""(?i)\b(19\d\d|20\d\d)\b""").find(url)?.groupValues?.get(1)?.toIntOrNull()

        val rating = Regex("""(?i)"voteAverage"\s*:\s*([\d.]+)""").find(cleanHtml)?.groupValues?.get(1)

        val isTv = url.contains("/tv/") || cleanHtml.contains("\"episodes\"") || res.contains("/season-")

        if (isTv) {
            val episodes = mutableListOf<Episode>()

            // 1. Next.js RSC Episodes parser
            val epBlockRegex = Regex("""\{[^{}]*?"episodeNumber"\s*:\s*(\d+)[^{}]*\}""")
            epBlockRegex.findAll(cleanHtml).forEach { match ->
                val block = match.value
                val epNum = Regex(""""episodeNumber"\s*:\s*(\d+)""").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val seasonNum = Regex(""""seasonNumber"\s*:\s*(\d+)""").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val epName = Regex(""""name"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1)?.trim()
                val airDate = Regex(""""airDate"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1)?.trim()
                val stillPath = Regex(""""stillPath"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1)?.trim()
                val epOverview = Regex(""""overview"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1)?.trim()

                val baseCleanUrl = url.substringBefore("?").removeSuffix("/")
                val epUrl = "$baseCleanUrl/season-$seasonNum/episode-$epNum"

                val dateEpoch = if (!airDate.isNullOrBlank()) {
                    runCatching {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                        sdf.parse(airDate)?.time
                    }.getOrNull()
                } else null

                val stillUrl = when {
                    stillPath.isNullOrBlank() -> poster
                    stillPath.startsWith("http") -> stillPath
                    stillPath.startsWith("/") -> "https://image.tmdb.org/t/p/w500$stillPath"
                    else -> "https://image.tmdb.org/t/p/w500/$stillPath"
                }

                val formattedEpName = when {
                    !epName.isNullOrBlank() && !epName.startsWith("Episode", ignoreCase = true) -> "Eps $epNum - $epName"
                    !epName.isNullOrBlank() -> epName
                    else -> "Episode $epNum"
                }

                val releaseStr = if (!airDate.isNullOrBlank()) "Rilis: $airDate" else "Rilis: Terkini"
                val desc = if (!epOverview.isNullOrBlank()) "$releaseStr • $epOverview" else "$releaseStr • Subtitle Indonesia"

                episodes.add(
                    newEpisode(epUrl) {
                        this.name = formattedEpName
                        this.season = seasonNum
                        this.episode = epNum
                        this.posterUrl = stillUrl
                        this.date = dateEpoch
                        this.description = desc
                    }
                )
            }

            // 2. DOM HTML Jsoup Fallback for Episodes
            if (episodes.isEmpty()) {
                val doc = Jsoup.parse(res)
                doc.select("a[href*='/season-'][href*='/episode-'], a[href*='episode']").forEach { a ->
                    val epHref = a.attr("href")
                    if (epHref.isNotBlank()) {
                        val fullEpUrl = if (epHref.startsWith("http")) epHref else "$mainUrl$epHref"
                        val sMatch = Regex("""/season-(\d+)""").find(epHref)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                        val eMatch = Regex("""/episode-(\d+)""").find(epHref)?.groupValues?.get(1)?.toIntOrNull()
                            ?: Regex("""\b(\d+)\b""").find(a.text())?.groupValues?.get(1)?.toIntOrNull() ?: 1

                        val aImg = a.select("img").attr("src").let {
                            if (it.startsWith("/")) "https://image.tmdb.org/t/p/w500$it" else it.ifBlank { poster }
                        }
                        val aName = a.text().trim().ifBlank { "Episode $eMatch" }

                        episodes.add(
                            newEpisode(fullEpUrl) {
                                this.name = if (!aName.startsWith("Episode", ignoreCase = true)) "Eps $eMatch - $aName" else aName
                                this.season = sMatch
                                this.episode = eMatch
                                this.posterUrl = aImg
                                this.description = "Rilis: Terkini • Sub Indo"
                            }
                        )
                    }
                }
            }

            val distinctEpisodes = episodes.distinctBy { "${it.season}_${it.episode}_${it.data}" }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, distinctEpisodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                if (!rating.isNullOrBlank()) {
                    this.score = Score.from10(rating)
                }
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                if (!rating.isNullOrBlank()) {
                    this.score = Score.from10(rating)
                }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = safeGet(data, referer = "$mainUrl/")
        if (res.isBlank()) return false

        val cleanHtml = res
            .replace("\\\"", "\"")
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\n", " ")

        val loadedUrls = mutableSetOf<String>()

        // -------------------------------------------------------------
        // 1. Parse "sources": [ ... ] array
        // -------------------------------------------------------------
        val sourcesStart = cleanHtml.indexOf("\"sources\":")
        if (sourcesStart != -1) {
            val bracketStart = cleanHtml.indexOf('[', sourcesStart)
            if (bracketStart != -1) {
                val arrayStr = findMatchingBracket(cleanHtml, bracketStart, '[', ']')
                if (!arrayStr.isNullOrBlank()) {
                    runCatching {
                        val arr = JSONArray(arrayStr)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val streamUrl = obj.optString("playbackUrl").ifBlank {
                                obj.optString("url").ifBlank {
                                    obj.optString("src").ifBlank { obj.optString("file") }
                                }
                            }
                            if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                                val quality = obj.optString("quality", "HD")
                                val server = obj.optString("server").ifBlank {
                                    obj.optString("name", "Server VIP ${i + 1}")
                                }
                                processStreamUrl(streamUrl, server, quality, data, loadedUrls, subtitleCallback, callback)
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 2. Parse "playerSources": [ ... ] array
        // -------------------------------------------------------------
        val playerStart = cleanHtml.indexOf("\"playerSources\":")
        if (playerStart != -1) {
            val bracketStart = cleanHtml.indexOf('[', playerStart)
            if (bracketStart != -1) {
                val arrayStr = findMatchingBracket(cleanHtml, bracketStart, '[', ']')
                if (!arrayStr.isNullOrBlank()) {
                    runCatching {
                        val arr = JSONArray(arrayStr)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val playerUrl = obj.optString("url").ifBlank {
                                obj.optString("src").ifBlank { obj.optString("playbackUrl") }
                            }
                            if (playerUrl.isNotBlank() && playerUrl.startsWith("http")) {
                                val name = obj.optString("name", "External Player ${i + 1}")
                                val quality = obj.optString("quality", "HD")
                                processStreamUrl(playerUrl, name, quality, data, loadedUrls, subtitleCallback, callback)
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 3. Regex scan for individual source objects with playbackUrl
        // -------------------------------------------------------------
        val objRegex = Regex("""\{[^{}]*?"(?:playbackUrl|url|src|file)"\s*:\s*"([^"]+)"[^{}]*\}""")
        objRegex.findAll(cleanHtml).forEach { m ->
            val block = m.value
            val url = m.groupValues[1]
            if (url.startsWith("http") && !url.contains("tmdb.org") && !url.contains("schema.org") && !url.contains("w3.org")) {
                val q = Regex(""""quality"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1) ?: "HD"
                val s = Regex(""""(?:server|name)"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1) ?: "VIP Server"
                processStreamUrl(url, s, q, data, loadedUrls, subtitleCallback, callback)
            }
        }

        // -------------------------------------------------------------
        // 4. Extract direct iframe embed tags in DOM
        // -------------------------------------------------------------
        val doc = Jsoup.parse(res)
        doc.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("src").trim()
            val fixedSrc = if (src.startsWith("//")) "https:$src" else src
            if (fixedSrc.startsWith("http") &&
                !fixedSrc.contains("google") &&
                !fixedSrc.contains("facebook") &&
                !fixedSrc.contains("recaptcha") &&
                !fixedSrc.contains("disqus")
            ) {
                if (loadedUrls.add(fixedSrc)) {
                    runCatching {
                        loadExtractor(fixedSrc, data, subtitleCallback, callback)
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 5. Direct Regex scan for embedded .m3u8 and .mp4 stream links
        // -------------------------------------------------------------
        val mediaRegex = Regex("""https?://[^\s"'<>\\]+\.(?:m3u8|mp4)[^\s"'<>\\]*""")
        mediaRegex.findAll(cleanHtml).forEach { m ->
            val raw = m.value.replace("\\u0026", "&")
            processStreamUrl(raw, "Rebahin Stream", "HD", data, loadedUrls, subtitleCallback, callback)
        }

        return loadedUrls.isNotEmpty()
    }

    private suspend fun processStreamUrl(
        streamUrl: String,
        serverName: String,
        qualityStr: String,
        refererUrl: String,
        loadedUrls: MutableSet<String>,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = streamUrl.trim()
        if (!loadedUrls.add(cleanUrl)) return

        val qualValue = getQualityFromName(qualityStr)

        try {
            when {
                cleanUrl.contains(".m3u8") -> {
                    generateM3u8(serverName, cleanUrl, refererUrl).forEach(callback)
                }
                cleanUrl.contains(".mp4") -> {
                    callback.invoke(
                        newExtractorLink(
                            source = serverName,
                            name = "$serverName - $qualityStr",
                            url = cleanUrl,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = "$mainUrl/"
                            this.quality = qualValue
                        }
                    )
                }
                cleanUrl.contains("kotakajaib.me") -> {
                    KotakajaibMe().getUrl(cleanUrl, refererUrl, subtitleCallback, callback)
                }
                cleanUrl.contains("playhydrax.com") -> {
                    Playhydrax().getUrl(cleanUrl, refererUrl, subtitleCallback, callback)
                }
                cleanUrl.contains("emturbovid.com") || cleanUrl.contains("turboviplay.com") -> {
                    Emturbovid().getUrl(cleanUrl, refererUrl, subtitleCallback, callback)
                }
                cleanUrl.contains("gdriveplayer.to") -> {
                    Gdriveplayer().getUrl(cleanUrl, refererUrl, subtitleCallback, callback)
                }
                cleanUrl.contains("abyss") -> {
                    AbyssPlayer().getUrl(cleanUrl, refererUrl, subtitleCallback, callback)
                }
                cleanUrl.contains("vidhide") -> {
                    VidHideExtractor().getUrl(cleanUrl, refererUrl, subtitleCallback, callback)
                }
                cleanUrl.contains("streamp2p") -> {
                    StreamP2PExtractor().getUrl(cleanUrl, refererUrl, subtitleCallback, callback)
                }
                else -> {
                    loadExtractor(cleanUrl, refererUrl, subtitleCallback, callback)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun findMatchingBracket(text: String, startIndex: Int, openChar: Char = '[', closeChar: Char = ']'): String? {
        var depth = 0
        var inString = false
        var escape = false

        for (i in startIndex until text.length) {
            val c = text[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == openChar) depth++
                else if (c == closeChar) {
                    depth--
                    if (depth == 0) {
                        return text.substring(startIndex, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun getQualityFromName(q: String): Int {
        val clean = q.lowercase()
        return when {
            clean.contains("4k") || clean.contains("2160") -> Qualities.P2160.value
            clean.contains("1080") -> Qualities.P1080.value
            clean.contains("720") -> Qualities.P720.value
            clean.contains("480") -> Qualities.P480.value
            clean.contains("360") -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }
}

class RebahinProvider : Rebahin()
