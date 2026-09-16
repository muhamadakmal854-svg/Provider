package com.mts.kiblatfilm21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import android.util.Log
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import org.json.JSONArray

class Kiblatfilm21 : MainAPI() {
    override var mainUrl = "https://kiblatfilm21.com"
    override var name = "Kiblatfilm21"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    companion object {
        private const val TMDB_API_KEY = "b030404650f279792a8d3287232358e3"

        fun parseIndoDate(dateStr: String?): Long? {
            if (dateStr.isNullOrBlank()) return null
            val months = mapOf(
                "jan" to 0, "feb" to 1, "mar" to 2, "apr" to 3,
                "mei" to 4, "may" to 4, "jun" to 5, "jul" to 6, "agu" to 7, "ags" to 7, "aug" to 7,
                "sep" to 8, "okt" to 9, "oct" to 9, "nov" to 10, "des" to 11, "dec" to 11
            )
            return try {
                val match = Regex("""(\d{1,2})\s+([a-zA-Z]+)\s+(\d{2,4})""").find(dateStr.trim())
                if (match != null) {
                    val day = match.groupValues[1].toInt()
                    val monKey = match.groupValues[2].lowercase(Locale.ROOT).take(3)
                    val month = months[monKey] ?: return null
                    var year = match.groupValues[3].toInt()
                    if (year < 100) year += 2000
                    val cal = java.util.Calendar.getInstance()
                    cal.set(year, month, day, 0, 0, 0)
                    cal.timeInMillis
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    override val mainPage = mainPageOf(
        "movie" to "🍿 Film Terbaru (Latest Movies)",
        "series" to "🔥 Serial TV Populer (Popular Series)",
        "country/kr" to "🌸 Drama Korea Pilihan (K-Drama)",
        "drama-china" to "🏮 Drama China Populer (C-Drama)",
        "anime" to "⚔️ Anime Populer (Anime Hub)",
        "marvel" to "🦸 Marvel Universe",
        "genre/action" to "💥 Film Aksi & Laga (Action)",
        "genre/adventure" to "🗺️ Petualangan Seru (Adventure)",
        "genre/comedy" to "😂 Komedi Lucu (Comedy)",
        "genre/crime" to "🕵️ Kriminal & Penyelidikan (Crime)",
        "genre/drama" to "🎭 Drama Penuh Emosi (Drama)",
        "genre/fantasy" to "🧙 Fantasi & Sihir (Fantasy)",
        "genre/horror" to "👻 Horor Mencekam (Horror)",
        "genre/mystery" to "🔍 Misteri & Teka-Teki (Mystery)",
        "genre/romance" to "💖 Romansa & Cinta (Romance)",
        "genre/sci-fi" to "🚀 Fiksi Ilmiah (Sci-Fi)",
        "genre/thriller" to "⚡ Ketegangan Memuncak (Thriller)",
        "genre/animation" to "🎨 Animasi Terbaik (Animation)",
        "country/jp" to "🇯🇵 Sinema Jepang (Japanese Cinema)",
        "country/cn" to "🇨🇳 Sinema Mandarin (Chinese Cinema)",
        "country/us" to "🇺🇸 Hollywood Box Office (USA)",
        "country/id" to "🇮🇩 Sinema Indonesia",
        "country/th" to "🇹🇭 Sinema Thailand",
        "country/in" to "🇮🇳 Sinema Bollywood (India)",
        "country/gb" to "🇬🇧 Sinema United Kingdom (British)",
        "year/2026" to "✨ Rilisan Terkini 2026"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val path = request.data.trim().removePrefix("/").removeSuffix("/")
        val pageUrl = if (path.isEmpty()) {
            if (page == 1) "$mainUrl/" else "$mainUrl/movie?page=$page"
        } else {
            if (page == 1) "$mainUrl/$path" else "$mainUrl/$path?page=$page"
        }

        val document = try {
            app.get(pageUrl, headers = mapOf("User-Agent" to USER_AGENT), timeout = 30).document
        } catch (e: Exception) {
            try {
                kotlinx.coroutines.delay(1000)
                app.get(pageUrl, headers = mapOf("User-Agent" to USER_AGENT), timeout = 30).document
            } catch (e2: Exception) {
                return newHomePageResponse(request.name, emptyList())
            }
        }
        val home = ArrayList<SearchResponse>()
        val seenUrls = HashSet<String>()

        val cardElements = document.select("a.content-card, a[href^=\"/movie/\"], a[href^=\"/series/\"], a[href^=\"/drama-china/\"]")
        for (card in cardElements) {
            val href = fixUrl(card.attr("href"))
            if (!href.contains("/movie/") && !href.contains("/series/") && !href.contains("/drama-china/")) continue
            if (href.endsWith("/watch") || href.contains("/episode/")) continue
            if (!seenUrls.add(href)) continue

            val title = card.selectFirst("h3")?.text()?.trim()
                ?: card.selectFirst("img")?.attr("alt")?.replace(Regex("(?i)\\s*[-–—~•|]?\\s*drama China.*"), "")?.trim()
                ?: card.text().trim()
            if (title.isEmpty() || title.equals("Tonton", ignoreCase = true)) continue

            val poster = card.selectFirst("img")?.let { img ->
                val src = img.attr("src").ifEmpty { img.attr("data-src") }
                if (src.isNotEmpty()) fixUrl(src) else null
            }

            val isMovie = href.contains("/movie/")
            val quality = card.selectFirst(".content-badge--cyan, .content-badge")?.text()?.trim()

            if (isMovie) {
                home.add(newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = poster
                    addQuality(quality ?: "HD")
                })
            } else {
                home.add(newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = poster
                    addQuality(quality ?: "HD")
                })
            }
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/search?q=$encodedQuery"
        val document = app.get(searchUrl, headers = mapOf("User-Agent" to USER_AGENT)).document
        val results = ArrayList<SearchResponse>()
        val seenUrls = HashSet<String>()

        val cardElements = document.select("a.content-card, a[href^=\"/movie/\"], a[href^=\"/series/\"], a[href^=\"/drama-china/\"]")
        for (card in cardElements) {
            val href = fixUrl(card.attr("href"))
            if (!href.contains("/movie/") && !href.contains("/series/") && !href.contains("/drama-china/")) continue
            if (href.endsWith("/watch") || href.contains("/episode/")) continue
            if (!seenUrls.add(href)) continue

            val title = card.selectFirst("h3")?.text()?.trim()
                ?: card.selectFirst("img")?.attr("alt")?.replace(Regex("(?i)\\s*[-–—~•|]?\\s*drama China.*"), "")?.trim()
                ?: card.text().trim()
            if (title.isEmpty() || title.equals("Tonton", ignoreCase = true)) continue

            val poster = card.selectFirst("img")?.let { img ->
                val src = img.attr("src").ifEmpty { img.attr("data-src") }
                if (src.isNotEmpty()) fixUrl(src) else null
            }

            val isMovie = href.contains("/movie/")
            val quality = card.selectFirst(".content-badge--cyan, .content-badge")?.text()?.trim()

            if (isMovie) {
                results.add(newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = poster
                    addQuality(quality ?: "HD")
                })
            } else {
                results.add(newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = poster
                    addQuality(quality ?: "HD")
                })
            }
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val cleanUrl = url.removeSuffix("/")
        val isMovie = cleanUrl.contains("/movie/")
        val response = app.get(cleanUrl, headers = mapOf("User-Agent" to USER_AGENT))
        val rawHtml = response.text
        val document = response.document

        val title = document.selectFirst("h1")?.text()?.replace(Regex("""\(\s*\d{4}\s*\)"""), "")?.trim()
            ?: document.title().substringBefore("Sub Indo").substringBefore("").trim()

        val yearMatch = Regex("""\b(19\d\d|20\d\d)\b""").find(document.title())
            ?: Regex("""\b(19\d\d|20\d\d)\b""").find(cleanUrl)
        val year = yearMatch?.value?.toIntOrNull()

        val poster = document.selectFirst("meta[property=\"og:image\"]")?.attr("content")
            ?: document.selectFirst("img[src*=\"/gambar/\"], img[src*=\"tmdb.org\"]")?.attr("src")?.let { fixUrl(it) }

        // Synopsis from JSON-LD or meta description
        var synopsis: String? = null
        for (script in document.select("script[type=\"application/ld+json\"]")) {
            try {
                val json = JSONObject(script.data())
                val desc = json.optString("description")
                if (desc.isNotEmpty() && !desc.startsWith("KIBLATFILM21") && !desc.startsWith("Katalog")) {
                    synopsis = desc
                    break
                }
            } catch (e: Exception) {}
        }
        if (synopsis.isNullOrBlank()) {
            synopsis = document.selectFirst("meta[name=\"description\"]")?.attr("content")
        }

        // Genres & Tags
        val tags = document.select("a[href^=\"/genre/\"]")
            .map { it.text().removePrefix("Film").trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        // Actors
        val actors = document.select("a[href^=\"/person/\"]")
            .map { ActorData(Actor(it.text().trim())) }
            .distinctBy { it.actor.name }

        // TMDB ID and Trailer Extraction
        var tmdbId = Regex("""(?:vidlink\.pro|2embed\.cc|videasy\.net|vidfast\.pro)/(?:movie|tv|embed)/(\d+)""")
            .find(rawHtml)?.groupValues?.get(1)

        var trailerUrl: String? = null
        try {
            if (tmdbId == null && title.isNotEmpty()) {
                val searchType = if (isMovie) "movie" else "tv"
                val searchApi = "https://api.themoviedb.org/3/search/$searchType?query=${URLEncoder.encode(title, "UTF-8")}&api_key=$TMDB_API_KEY"
                val searchRes = app.get(searchApi).text
                val searchJson = JSONObject(searchRes)
                val resultsArr = searchJson.optJSONArray("results")
                if (resultsArr != null && resultsArr.length() > 0) {
                    tmdbId = resultsArr.getJSONObject(0).opt("id")?.toString()
                }
            }

            if (tmdbId != null) {
                val vidType = if (isMovie) "movie" else "tv"
                val vidApi = "https://api.themoviedb.org/3/$vidType/$tmdbId/videos?api_key=$TMDB_API_KEY"
                val vidRes = app.get(vidApi).text
                val vidJson = JSONObject(vidRes)
                val vidsArr = vidJson.optJSONArray("results")
                if (vidsArr != null) {
                    for (v in 0 until vidsArr.length()) {
                        val vObj = vidsArr.getJSONObject(v)
                        val site = vObj.optString("site")
                        val key = vObj.optString("key")
                        val vType = vObj.optString("type")
                        if (site.equals("YouTube", ignoreCase = true) && key.isNotEmpty()) {
                            trailerUrl = "https://www.youtube.com/watch?v=$key"
                            if (vType.equals("Trailer", ignoreCase = true)) {
                                break
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Kiblatfilm21", "Trailer lookup error: ${e.message}")
        }

        if (isMovie) {
            return newMovieLoadResponse(title, cleanUrl, TvType.Movie, data = "$cleanUrl/watch") {
                this.posterUrl = poster
                this.year = year
                this.plot = synopsis
                this.tags = tags
                this.actors = actors
                if (!trailerUrl.isNullOrBlank()) {
                    addTrailer(trailerUrl)
                }
            }
        } else {
            val episodes = ArrayList<Episode>()

            if (cleanUrl.contains("/drama-china/")) {
                // Drama China episode extraction: /drama-china/{slug}/{number}
                val epNumMatches = Regex("""/drama-china/[^"'\s]+/(\d+)""").findAll(rawHtml).mapNotNull {
                    it.groupValues[1].toIntOrNull()
                }.toList()
                val maxEp = epNumMatches.maxOrNull() ?: 1
                for (epNum in 1..maxEp) {
                    val epHref = "$cleanUrl/$epNum"
                    episodes.add(newEpisode(epHref) {
                        this.name = "Episode $epNum"
                        this.season = 1
                        this.episode = epNum
                        this.posterUrl = poster
                    })
                }
            } else {
                val epLinks = document.select("a.clickable.group.block[href*=\"/episode/\"], a[href*=\"/season/\"][href*=\"/episode/\"]")
                    .filter { it.text().trim() != "Tonton" }

                for (card in epLinks) {
                    val epHref = fixUrl(card.attr("href"))
                    val epTitle = card.selectFirst("h3")?.text()?.trim() ?: "Episode"
                    val epSynopsis = card.selectFirst("p")?.text()?.trim()
                    val epImg = card.selectFirst("img")?.attr("src")?.let { fixUrl(it) } ?: poster

                    // Parse season & episode numbers from URL: /season/1/episode/5
                    val seMatch = Regex("""season/(\d+)/episode/(\d+)""").find(epHref)
                    val seasonNum = seMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val epNum = seMatch?.groupValues?.get(2)?.toIntOrNull() ?: 1

                    // Parse release date from badge, e.g. "25 Mei 26"
                    var epDateMillis: Long? = null
                    val spans = card.select("span")
                    for (s in spans) {
                        val txt = s.text().trim()
                        if (Regex("""\d{1,2}\s+[a-zA-Z]+\s+\d{2,4}""").containsMatchIn(txt)) {
                            epDateMillis = parseIndoDate(txt)
                            break
                        }
                    }

                    episodes.add(newEpisode(epHref) {
                        this.name = epTitle
                        this.season = seasonNum
                        this.episode = epNum
                        this.posterUrl = epImg
                        if (epDateMillis != null) {
                            this.date = epDateMillis
                        }
                        if (!epSynopsis.isNullOrBlank()) {
                            this.description = epSynopsis
                        }
                    })
                }

                // Fallback: if no episode cards found in DOM, check Next.js stream links
                if (episodes.isEmpty()) {
                    val streamEpRegex = Regex("""(/series/[^"'\s]+/season/(\d+)/episode/(\d+))""")
                    val seen = HashSet<String>()
                    streamEpRegex.findAll(rawHtml).forEach { m ->
                        val epPath = m.groupValues[1]
                        val sNum = m.groupValues[2].toIntOrNull() ?: 1
                        val eNum = m.groupValues[3].toIntOrNull() ?: 1
                        val fullEpHref = fixUrl(epPath)
                        if (seen.add(fullEpHref)) {
                            episodes.add(newEpisode(fullEpHref) {
                                this.name = "Episode $eNum"
                                this.season = sNum
                                this.episode = eNum
                                this.posterUrl = poster
                            })
                        }
                    }
                }
            }

            return newTvSeriesLoadResponse(title, cleanUrl, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = synopsis
                this.tags = tags
                this.actors = actors
                if (!trailerUrl.isNullOrBlank()) {
                    addTrailer(trailerUrl)
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
        var found = false
        try {
            val targetUrl = if (data.contains("/movie/") && !data.endsWith("/watch")) {
                "${data.removeSuffix("/")}/watch"
            } else {
                data
            }

            val pageHtml = app.get(targetUrl, headers = mapOf("User-Agent" to USER_AGENT)).text

            // Check direct HTML5 video in page (e.g. drama-china or direct stream)
            val videoRegex = Regex("""<video[^>]+src=["']([^"']+)["']""")
            val videoSrc = videoRegex.find(pageHtml)?.groupValues?.get(1)
            if (!videoSrc.isNullOrBlank()) {
                callback(
                    newExtractorLink(
                        name = name,
                        source = "$name Direct",
                        url = fixUrl(videoSrc),
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = targetUrl
                        this.headers = mapOf("Referer" to targetUrl, "User-Agent" to USER_AGENT)
                    }
                )
                found = true
            }

            // 1. Extract all servers from Next.js embeds JSON
            val serverRegex = Regex("""(?:\\"|")server(?:\\"|")\s*:\s*(?:\\"|")([^\\"]+)(?:\\"|")\s*,\s*(?:\\"|")url(?:\\"|")\s*:\s*(?:\\"|")([^\\"]+)(?:\\"|")""")
            val servers = serverRegex.findAll(pageHtml).map {
                it.groupValues[1].trim() to it.groupValues[2].trim()
            }.toList()

            for ((serverName, embedUrl) in servers) {
                try {
                    when {
                        embedUrl.contains("abyssplayer.com") || embedUrl.contains("abyss.to") -> {
                            PlayerAbyssplayerCom().getUrl(embedUrl, targetUrl, subtitleCallback) {
                                found = true
                                callback(it)
                            }
                        }
                        embedUrl.contains("morencius.com") || embedUrl.contains("streamwish") -> {
                            MorenciusCom().getUrl(embedUrl, targetUrl, subtitleCallback) {
                                found = true
                                callback(it)
                            }
                        }
                        embedUrl.contains("embed4me.vip") || embedUrl.contains("4meplayer.com") -> {
                            Embed4Me().getUrl(embedUrl, targetUrl, subtitleCallback) {
                                found = true
                                callback(it)
                            }
                        }
                        embedUrl.contains("playerp2p.online") || embedUrl.contains("p2pplay.pro") -> {
                            PlayerP2P().getUrl(embedUrl, targetUrl, subtitleCallback) {
                                found = true
                                callback(it)
                            }
                        }
                        embedUrl.contains("upns.live") || embedUrl.contains("luluvdo.com") -> {
                            UpnsLive().getUrl(embedUrl, targetUrl, subtitleCallback) {
                                found = true
                                callback(it)
                            }
                        }
                        embedUrl.contains("2embed.cc") -> {
                            try {
                                val em2Doc = app.get(embedUrl, headers = mapOf("Referer" to targetUrl, "User-Agent" to USER_AGENT)).text
                                val swishRegex = Regex("""https?://streamsrcs\.2embed\.cc/swish\?id=([a-zA-Z0-9_-]+)""")
                                val swishMatch = swishRegex.find(em2Doc)
                                if (swishMatch != null) {
                                    val swishId = swishMatch.groupValues[1]
                                    val morenciusUrl = "https://morencius.com/embed/$swishId"
                                    MorenciusCom().getUrl(morenciusUrl, targetUrl, subtitleCallback) {
                                        found = true
                                        callback(it)
                                    }
                                }
                            } catch (e: Exception) {}
                        }
                        else -> {
                            if (loadExtractor(embedUrl, targetUrl, subtitleCallback, callback)) {
                                found = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Kiblatfilm21", "Error extracting candidate [$embedUrl]: ${e.message}")
                }
            }

            // 2. Direct Fallback: search HTML for m3u8
            if (!found) {
                val m3u8Regex = Regex("""https?://[^\s"']+\.m3u8[^\s"']*""", RegexOption.IGNORE_CASE)
                m3u8Regex.findAll(pageHtml).forEach { m ->
                    val videoUrl = m.value.trim()
                    generateM3u8(name, videoUrl, targetUrl).forEach { link ->
                        found = true
                        callback(link)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("Kiblatfilm21", "loadLinks error: ${e.message}")
        }
        return found
    }
}
