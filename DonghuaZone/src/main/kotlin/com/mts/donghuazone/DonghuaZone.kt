package com.mts.donghuazone

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class DonghuaZone : MainAPI() {
    override var mainUrl = "https://www.donghuazone.com"
    override var name = "DonghuaZone"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    // Netflix-Style Main Page Layout (Tanpa Baris Bintang Tersendiri)
    override val mainPage = mainPageOf(
        "$mainUrl/feeds/posts/default?alt=json#spotlight" to "✨ Pilihan Utama (Spotlight)",
        "$mainUrl/feeds/posts/default?alt=json#trending" to "🔥 Trending Hari Ini (Top 10)",
        "$mainUrl/feeds/posts/default?alt=json" to "⚡ Rilisan Terbaru (Update Harian)",
        "$mainUrl/feeds/posts/default/-/Ongoing?alt=json" to "🎬 Sedang Tayang (Ongoing)",
        "$mainUrl/feeds/posts/default/-/Movie?alt=json" to "🍿 Donghua Movie (Film Layar Lebar)",
        "$mainUrl/feeds/posts/default/-/Action?alt=json" to "⚔️ Aksi & Petualangan",
        "$mainUrl/feeds/posts/default/-/Fantasy?alt=json" to "🔮 Fantasi & Sihir",
        "$mainUrl/feeds/posts/default/-/Adventure?alt=json" to "🥋 Bela Diri (Wuxia / Cultivation)",
        "$mainUrl/feeds/posts/default/-/Romance?alt=json" to "🌸 Romantis"
    )

    private fun parseDateToEpoch(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            val clean = dateStr.replace("Z", "+00:00")
            val pattern = if (clean.length > 19 && clean[19] == '.') {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
            } else if (clean.contains("T")) {
                "yyyy-MM-dd'T'HH:mm:ssXXX"
            } else {
                "yyyy-MM-dd"
            }
            SimpleDateFormat(pattern, Locale.US).parse(clean)?.time
        } catch (_: Exception) {
            try {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr.take(10))?.time
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun formatDateDisplay(pubDate: String?, epoch: Long?): String {
        if (epoch != null) {
            return try {
                SimpleDateFormat("d MMM yyyy", Locale("id", "ID")).format(java.util.Date(epoch))
            } catch (_: Exception) {
                pubDate?.take(10).orEmpty()
            }
        }
        return pubDate?.take(10).orEmpty()
    }

    private fun getHighResThumbnail(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url
            .replace("/s72-c/", "/s640/")
            .replace("/s72-w/", "/s640/")
            .replace("/w72-h72-p-k-no-nu/", "/s640/")
            .replace("/s1600-w/", "/s1600/")
            .replace("/default.jpg", "/hqdefault.jpg")
    }

    private fun cleanTitle(raw: String): String {
        return raw
            .replace(Regex("""\[.*?\]"""), "")
            .replace(Regex("""[\u2013\-]\s*English.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[\u2013\-]\s*Indo.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""Subtitle\s*Indonesia|Sub\s*Indo|Indo\s*Sub|Multi\s*Sub|Donghua\s*Zone""", RegexOption.IGNORE_CASE), "")
            .trim()
            .trim('-', ':', '–')
            .trim()
    }

    private fun cleanSeriesTitle(raw: String): String {
        val base = cleanTitle(raw)
        return base
            .replace(Regex("""(?:Episode|Eps\.)\s*\d+.*""", RegexOption.IGNORE_CASE), "")
            .trim()
            .trim('-', ':', '–')
            .trim()
    }

    private fun extractEpisodeNumber(title: String, url: String): Int? {
        return Regex("""(?:Episode|Eps\.)\s*(\d+)""", RegexOption.IGNORE_CASE).find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("""-episode-(\d+)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("""_(\d+)\.html""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun parseFeedEntryToSearchResult(entryObj: JSONObject): SearchResponse? {
        val rawTitle = entryObj.optJSONObject("title")?.optString("\$t") ?: return null
        val links = entryObj.optJSONArray("link") ?: return null
        var altUrl = ""
        for (i in 0 until links.length()) {
            val l = links.optJSONObject(i)
            if (l?.optString("rel") == "alternate") {
                altUrl = l.optString("href")
                break
            }
        }
        if (altUrl.isBlank()) return null

        val thumb = getHighResThumbnail(entryObj.optJSONObject("media\$thumbnail")?.optString("url"))
        // Paparkan nama siri Anime, bukan nombor episod pada tajuk kad homepage
        val seriesTitle = cleanSeriesTitle(rawTitle).ifBlank { cleanTitle(rawTitle) }
        val epNum = extractEpisodeNumber(rawTitle, altUrl)

        val isMovie = rawTitle.contains("Movie", ignoreCase = true) || altUrl.contains("-movie", ignoreCase = true)
        val type = if (isMovie) TvType.AnimeMovie else TvType.Anime

        return newAnimeSearchResponse(seriesTitle, altUrl, type) {
            this.posterUrl = thumb
            if (epNum != null) {
                addDubStatus(false, epNum)
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val cleanData = request.data.substringBefore("#")
        val isSpotlight = request.data.endsWith("#spotlight")
        val isTrending = request.data.endsWith("#trending")

        if ((isSpotlight || isTrending) && page > 1) return null

        val pageSize = if (isSpotlight) 15 else if (isTrending) 15 else 30
        val startIndex = (page - 1) * pageSize + 1
        val sep = if (cleanData.contains("?")) "&" else "?"
        val targetUrl = "$cleanData${sep}start-index=$startIndex&max-results=$pageSize"

        val res = app.get(targetUrl, headers = mapOf("User-Agent" to USER_AGENT))
        val root = JSONObject(res.text)
        val feed = root.optJSONObject("feed") ?: return null
        val entries = feed.optJSONArray("entry") ?: return null

        val results = mutableListOf<SearchResponse>()
        for (i in 0 until entries.length()) {
            val e = entries.optJSONObject(i) ?: continue
            val item = parseFeedEntryToSearchResult(e) ?: continue
            results.add(item)
        }

        // De-duplicate mengikut nama siri supaya tiada anime berulang dalam baris yang sama
        val finalResults = results.distinctBy { it.name }
        return if (finalResults.isNotEmpty()) {
            newHomePageResponse(request.name, finalResults)
        } else null
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val targetUrl = "$mainUrl/feeds/posts/default?alt=json&q=$encoded&max-results=30"

        val res = try {
            app.get(targetUrl, headers = mapOf("User-Agent" to USER_AGENT))
        } catch (_: Exception) {
            return emptyList()
        }

        val root = JSONObject(res.text)
        val feed = root.optJSONObject("feed") ?: return emptyList()
        val entries = feed.optJSONArray("entry") ?: return emptyList()

        val results = mutableListOf<SearchResponse>()
        for (i in 0 until entries.length()) {
            val e = entries.optJSONObject(i) ?: continue
            val item = parseFeedEntryToSearchResult(e) ?: continue
            results.add(item)
        }

        return results.distinctBy { it.name }
    }

    override suspend fun load(url: String): LoadResponse? {
        val res = app.get(url, headers = mapOf("User-Agent" to USER_AGENT))
        val doc = res.document

        val rawTitle = doc.selectFirst("h1.entry-title, h1.post-title, h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property='og:title']")?.attr("content")?.trim()
            ?: "Donghua"

        val cleanSeries = cleanSeriesTitle(rawTitle)
        val poster = getHighResThumbnail(doc.selectFirst("meta[property='og:image']")?.attr("content"))
            ?: getHighResThumbnail(doc.selectFirst(".post-body img, .entry-content img")?.attr("src"))
            ?: ""

        val plot = doc.select(".post-body p, .entry-content p").joinToString("\n") { it.text().trim() }
            .ifBlank { doc.selectFirst("meta[property='og:description']")?.attr("content")?.trim().orEmpty() }

        // Discover series label from labels/tags
        val labelLinks = doc.select("a[href*='/search/label/']")
        val ignoredLabels = setOf(
            "episode", "eps", "eps.", "sub", "indo", "english", "hot", "danger",
            "4k", "1080p", "donghua", "series", "ongoing", "completed", "censored",
            "japan", "action", "adventure", "fantasy", "romance", "summer 2026", "movie"
        )
        var seriesLabel: String? = null
        for (a in labelLinks) {
            val rawLabel = try {
                URLDecoder.decode(a.attr("href").substringAfter("/search/label/").substringBefore("?").trim(), "UTF-8")
            } catch (_: Exception) {
                a.attr("href").substringAfter("/search/label/").substringBefore("?").trim()
            }
            if (rawLabel.isNotBlank() && !ignoredLabels.contains(rawLabel.lowercase()) && !rawLabel.startsWith("Eps.", true)) {
                seriesLabel = rawLabel
                break
            }
        }

        // Fallback series label to clean series title if none found
        val queryLabel = seriesLabel ?: cleanSeries

        // Fetch episode list from Blogger feed
        val episodes = mutableListOf<Episode>()
        try {
            val feedUrl = "$mainUrl/feeds/posts/default/-/${URLEncoder.encode(queryLabel, "UTF-8")}?alt=json&max-results=500"
            val feedRes = app.get(feedUrl, headers = mapOf("User-Agent" to USER_AGENT))
            val root = JSONObject(feedRes.text)
            val entries = root.optJSONObject("feed")?.optJSONArray("entry")
            if (entries != null) {
                for (i in 0 until entries.length()) {
                    val e = entries.optJSONObject(i) ?: continue
                    val epTitle = e.optJSONObject("title")?.optString("\$t") ?: continue
                    val links = e.optJSONArray("link") ?: continue
                    var altLink = ""
                    for (j in 0 until links.length()) {
                        val l = links.optJSONObject(j)
                        if (l?.optString("rel") == "alternate") {
                            altLink = l.optString("href")
                            break
                        }
                    }
                    if (altLink.isBlank()) continue

                    val epNum = extractEpisodeNumber(epTitle, altLink)
                    val pubDate = e.optJSONObject("published")?.optString("\$t")
                    val epochDate = parseDateToEpoch(pubDate)
                    val displayDate = formatDateDisplay(pubDate, epochDate)
                    val epThumb = getHighResThumbnail(e.optJSONObject("media\$thumbnail")?.optString("url")) ?: poster

                    episodes.add(
                        newEpisode(altLink) {
                            this.name = if (epNum != null) "Episode $epNum" else cleanTitle(epTitle)
                            this.episode = epNum
                            this.posterUrl = epThumb
                            if (epochDate != null) {
                                this.date = epochDate
                            }
                            this.description = if (displayDate.isNotBlank()) "Rilis: $displayDate • Sub Indo" else null
                        }
                    )
                }
            }
        } catch (_: Exception) {}

        val isMovie = rawTitle.contains("Movie", ignoreCase = true) || url.contains("-movie", ignoreCase = true) || queryLabel.equals("Movie", ignoreCase = true)

        return if (!isMovie && episodes.isNotEmpty()) {
            val sortedEpisodes = episodes.distinctBy { it.data }.sortedBy { it.episode ?: 0 }
            newTvSeriesLoadResponse(cleanSeries.ifBlank { rawTitle }, url, TvType.Anime, sortedEpisodes) {
                this.posterUrl = poster
                this.plot = plot
            }
        } else {
            newMovieLoadResponse(cleanSeries.ifBlank { rawTitle }, url, TvType.AnimeMovie, url) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = try {
            app.get(data, headers = mapOf("User-Agent" to USER_AGENT)).text
        } catch (_: Exception) {
            return false
        }

        var foundAny = false
        val candidateUrls = mutableSetOf<String>()

        // 1. changeServer(this, 'url') atau sebarang petikan
        Regex("""changeServer\s*\(\s*[^,]+,\s*['"]([^'"]+)['"]""").findAll(html).forEach { m ->
            val u = m.groupValues[1].trim()
            if (u.isNotBlank() && u.startsWith("http")) candidateUrls.add(u)
        }

        // 2. Iframe src / data-src
        Regex("""<iframe[^>]+(?:src|data-src)=['"]([^'"]+)['"]""").findAll(html).forEach { m ->
            val u = m.groupValues[1].trim()
            if (u.isNotBlank() && !u.contains("facebook.com") && !u.contains("disqus.com")) {
                candidateUrls.add(u)
            }
        }

        // 3. Sebarang Dailymotion / geo.dailymotion links dalam teks HTML
        Regex("""https?://(?:geo\.)?dailymotion\.com/[^\s"'<>]+""").findAll(html).forEach { m ->
            val u = m.value.trim().trimEnd('\\', '"', '\'')
            candidateUrls.add(u)
        }

        // 4. Sebarang pautan terus video (.mp4 / .m3u8)
        Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8)(?:\?[^\s"'<>]*)?""").findAll(html).forEach { m ->
            candidateUrls.add(m.value.trim())
        }

        for (u in candidateUrls) {
            // Resolusi Dailymotion dengan Header Lengkap (Anti-403)
            if (u.contains("dailymotion.com", true) || u.contains("dai.ly", true)) {
                val videoId = when {
                    u.contains("video=") -> u.substringAfter("video=").substringBefore("&").substringBefore("\"").substringBefore("'")
                    u.contains("/video/") -> u.substringAfter("/video/").substringBefore("?").substringBefore("/").substringBefore("\"")
                    u.contains("dai.ly/") -> u.substringAfter("dai.ly/").substringBefore("?").substringBefore("/")
                    else -> Regex("""(?:video[=/]|/embed/video/)([a-zA-Z0-9]+)""").find(u)?.groupValues?.getOrNull(1)
                }

                if (!videoId.isNullOrBlank()) {
                    try {
                        val dmHeaders = mapOf(
                            "User-Agent" to USER_AGENT,
                            "Referer" to "https://geo.dailymotion.com/"
                        )
                        val metaUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
                        val metaJson = app.get(metaUrl, headers = dmHeaders).text

                        // 1. Ekstrak Sarikata
                        try {
                            val metaObj = JSONObject(metaJson)
                            val subsData = metaObj.optJSONObject("subtitles")?.optJSONObject("data")
                            if (subsData != null) {
                                val keys = subsData.keys()
                                while (keys.hasNext()) {
                                    val k = keys.next()
                                    val subItem = subsData.optJSONObject(k)
                                    val label = subItem?.optString("label") ?: k
                                    val subUrl = subItem?.optJSONArray("urls")?.optString(0)
                                    if (!subUrl.isNullOrBlank()) {
                                        subtitleCallback(SubtitleFile(label, subUrl))
                                    }
                                }
                            }
                        } catch (_: Exception) {}

                        // 2. Ekstrak Master M3u8 URL
                        var autoM3u8Url = ""
                        try {
                            val metaObj = JSONObject(metaJson)
                            autoM3u8Url = metaObj.optJSONObject("qualities")?.optJSONArray("auto")?.optJSONObject(0)?.optString("url").orEmpty()
                        } catch (_: Exception) {}

                        if (autoM3u8Url.isBlank()) {
                            val m = Regex(""""url"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""").find(metaJson)
                            autoM3u8Url = m?.groupValues?.getOrNull(1)?.replace("\\/", "/")?.replace("\\u0026", "&").orEmpty()
                        }

                        if (autoM3u8Url.isNotBlank() && autoM3u8Url.contains(".m3u8", true)) {
                            // Muat turun manifest untuk ekstrak resolusi sebenar terus dari CDN
                            try {
                                val manifestText = app.get(autoM3u8Url, headers = dmHeaders).text
                                val streamRegex = Regex("""#EXT-X-STREAM-INF:[^\n]*?(?:NAME="(\d+)"|RESOLUTION=(\d+x\d+))[^\n]*\n([^\n]+)""")
                                val subStreams = streamRegex.findAll(manifestText).toList()

                                for (sub in subStreams) {
                                    val nameQ = sub.groupValues[1]
                                    val resQ = sub.groupValues[2]
                                    val streamUrl = sub.groupValues[3].trim()
                                    val qInt = when {
                                        nameQ == "1080" || resQ.contains("1080") -> Qualities.P1080.value
                                        nameQ == "720" || resQ.contains("720") -> Qualities.P720.value
                                        nameQ == "480" || resQ.contains("480") -> Qualities.P480.value
                                        nameQ == "360" || resQ.contains("360") -> Qualities.P360.value
                                        nameQ == "240" || resQ.contains("240") -> Qualities.P240.value
                                        else -> Qualities.Unknown.value
                                    }
                                    val qLabel = if (nameQ.isNotBlank()) "${nameQ}p" else if (resQ.isNotBlank()) resQ else "HLS"

                                    callback(
                                        newExtractorLink(
                                            source = name,
                                            name = "$name - Dailymotion $qLabel",
                                            url = streamUrl,
                                            type = ExtractorLinkType.M3U8
                                        ) {
                                            this.referer = "https://geo.dailymotion.com/"
                                            this.headers = dmHeaders
                                            this.quality = qInt
                                        }
                                    )
                                    foundAny = true
                                }
                            } catch (_: Exception) {}

                            // Aliran Master Auto
                            callback(
                                newExtractorLink(
                                    source = name,
                                    name = "$name - Dailymotion Auto",
                                    url = autoM3u8Url,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.referer = "https://geo.dailymotion.com/"
                                    this.headers = dmHeaders
                                    this.quality = Qualities.P1080.value
                                }
                            )
                            foundAny = true
                        }
                    } catch (_: Exception) {}
                }

                // Fallback kepada CloudStream built-in / registered Dailymotion extractor
                try {
                    loadExtractor(u, "https://geo.dailymotion.com/", subtitleCallback) { link ->
                        callback(link)
                        foundAny = true
                    }
                } catch (_: Exception) {}
                continue
            }

            // Direct M3u8
            if (u.contains(".m3u8", true)) {
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name - HLS Stream",
                        url = u,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = mainUrl
                        this.headers = mapOf("User-Agent" to USER_AGENT)
                        this.quality = Qualities.P1080.value
                    }
                )
                foundAny = true
                continue
            }

            // Direct MP4
            if (u.contains(".mp4", true)) {
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name - Direct MP4",
                        url = u,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = mainUrl
                        this.headers = mapOf("User-Agent" to USER_AGENT)
                        this.quality = Qualities.P1080.value
                    }
                )
                foundAny = true
                continue
            }

            // Pelayan standard lain (Blogger, Google Drive, OK.ru, Pixeldrain, Turbovid dll)
            try {
                loadExtractor(u, mainUrl, subtitleCallback) { link ->
                    callback(link)
                    foundAny = true
                }
            } catch (_: Exception) {}
        }

        return foundAny
    }
}
