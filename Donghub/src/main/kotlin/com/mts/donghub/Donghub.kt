package com.mts.donghub

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import android.util.Base64

class Donghub : MainAPI() {
    override var mainUrl = "https://donghive.vip"
    override var name = "Donghub"
    override var lang = "id"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    override val hasQuickSearch = false

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        fun cleanSeriesTitle(title: String): String {
            return title
                .replace(Regex("""(?i)Episode\s+\d+.*"""), "")
                .replace(Regex("""(?i)Ep\s+\d+.*"""), "")
                .replace(Regex("""(?i)Subtitle\s+Indonesia.*"""), "")
                .replace(Regex("""(?i)Sub\s+Indo.*"""), "")
                .replace(Regex("""(?i)–\s*Donghub.*"""), "")
                .replace(Regex("""(?i)-\s*Donghub.*"""), "")
                .replace(Regex("""(?i)–\s*Donghive.*"""), "")
                .replace(Regex("""(?i)-\s*Donghive.*"""), "")
                .replace(Regex("""(?i)Donghub.*"""), "")
                .replace(Regex("""(?i)Donghive.*"""), "")
                .trim()
                .trimEnd('-', '–', ':', '|', ' ')
        }

        fun parseDateToEpoch(dateStr: String): Long? {
            if (dateStr.isBlank()) return null
            val normalized = dateStr.trim()
                .replace("Januari", "January", true)
                .replace("Februari", "February", true)
                .replace("Maret", "March", true)
                .replace("Mei", "May", true)
                .replace("Juni", "June", true)
                .replace("Juli", "July", true)
                .replace("Agustus", "August", true)
                .replace("Oktober", "October", true)
                .replace("Desember", "December", true)

            val formats = listOf(
                SimpleDateFormat("MMMM d, yyyy", Locale.US),
                SimpleDateFormat("d MMMM yyyy", Locale.US),
                SimpleDateFormat("yyyy-MM-dd", Locale.US),
                SimpleDateFormat("MMM d, yyyy", Locale.US)
            )

            for (fmt in formats) {
                try {
                    fmt.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
                    val parsed = fmt.parse(normalized)
                    if (parsed != null) return parsed.time
                } catch (_: Exception) {}
            }
            return null
        }
    }

    // 10 Kategori Netflix
    override val mainPage = mainPageOf(
        "$mainUrl/#spotlight" to "✨ Pilihan Utama (Spotlight)",
        "$mainUrl/#trending" to "🔥 Trending Hari Ini (Top 10)",
        "$mainUrl/anime/?order=update" to "⚡ Rilisan Terbaru (Update Harian)",
        "$mainUrl/anime/?status=ongoing&order=update" to "🎬 Sedang Tayang (Ongoing)",
        "$mainUrl/anime/?status=completed&order=update" to "🏆 Tamat (Completed)",
        "$mainUrl/anime/?type=movie&order=update" to "🍿 Donghua Movie (Film Layar Lebar)",
        "$mainUrl/genres/action/" to "⚔️ Aksi & Petualangan",
        "$mainUrl/genres/fantasy/" to "🔮 Fantasi & Sihir",
        "$mainUrl/genres/cultivation/" to "🥋 Kultivasi & Bela Diri (Wuxia)",
        "$mainUrl/genres/romance/" to "🌸 Romantis"
    )

    private fun getPosterUrl(element: Element?): String? {
        if (element == null) return null
        for (attr in listOf("data-src", "data-lazy-src", "data-cfsrc", "data-original", "data-image", "data-bg", "src")) {
            val v = element.attr(attr).trim()
            if (v.isNotBlank() && !v.contains("data:image") && (v.startsWith("http") || v.startsWith("//"))) {
                return if (v.startsWith("//")) "https:$v" else v
            }
        }
        return null
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        return try {
            val a = if (element.tagName().equals("a", true)) element else element.selectFirst("a[href]") ?: return null
            val href = fixUrl(a.attr("href"))
            if (href.isBlank() || href == "$mainUrl/" || href.contains("/genres/") || href.contains("/schedule/")) return null

            val img = a.selectFirst("img") ?: element.selectFirst("img")

            var rawTitle = element.selectFirst(".tt, .entry-title, h2, h3, .title")?.text()?.trim().orEmpty()
            if (rawTitle.isBlank()) {
                rawTitle = a.attr("title").trim()
            }
            if (rawTitle.isBlank()) {
                rawTitle = img?.attr("alt")?.trim().orEmpty()
            }
            if (rawTitle.isBlank()) {
                rawTitle = a.text().trim()
            }

            rawTitle = rawTitle.lines().firstOrNull()?.trim() ?: ""
            if (rawTitle.isBlank()) return null

            val seriesTitle = cleanSeriesTitle(rawTitle)
            val displayTitle = if (seriesTitle.isNotBlank()) seriesTitle else rawTitle

            val poster = getPosterUrl(img)

            val isMovie = href.contains("/movie", true) || href.contains("-movie-", true)
            val type = if (isMovie) TvType.AnimeMovie else TvType.Anime

            val epText = element.selectFirst(".ep, .bt .ep, .epx, .egg, .typez")?.text()?.trim()
            val epNum = epText?.filter { it.isDigit() }?.toIntOrNull()

            newAnimeSearchResponse(displayTitle, href, type) {
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
        val targetUrl = when {
            request.data.endsWith("#spotlight") || request.data.endsWith("#trending") -> {
                if (page > 1) return null
                "$mainUrl/"
            }
            page <= 1 -> request.data
            request.data.contains("?") -> {
                val base = request.data.substringBefore("?").trimEnd('/')
                val query = request.data.substringAfter("?")
                "$base/page/$page/?$query"
            }
            request.data.endsWith("/") -> "${request.data}page/$page/"
            else -> "${request.data}/page/$page/"
        }

        val doc = try {
            app.get(targetUrl, headers = mapOf("User-Agent" to USER_AGENT)).document
        } catch (_: Exception) {
            return null
        }

        val cards = when {
            request.data.endsWith("#spotlight") -> {
                val items = doc.select(".popularslider .bsx, .swiper-slide .bsx, .slider .slide, .bigslider .slide")
                    .mapNotNull { toSearchResult(it) }
                if (items.isNotEmpty()) items else doc.select(".listupd article, .listupd .bsx").take(15).mapNotNull { toSearchResult(it) }
            }
            request.data.endsWith("#trending") -> {
                val trendBox = doc.select(".bixbox").firstOrNull {
                    val h = it.selectFirst(".releases h2, .releases h3, h2, h3")?.text()?.lowercase() ?: ""
                    h.contains("popular") || h.contains("terpopuler") || h.contains("trending")
                }
                val items = (trendBox?.select(".bsx, article, .item") ?: doc.select(".popularslider .bsx")).mapNotNull {
                    toSearchResult(it)
                }
                items
            }
            else -> {
                doc.select(".listupd article, .listupd .bsx, article.bs, .bsx").mapNotNull { toSearchResult(it) }
            }
        }

        val uniqueCards = cards.distinctBy { it.name }
        return newHomePageResponse(request.name, uniqueCards)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (_: Exception) {
            query.replace(" ", "+")
        }

        val url = "$mainUrl/?s=$encoded"
        val doc = try {
            app.get(url, headers = mapOf("User-Agent" to USER_AGENT)).document
        } catch (_: Exception) {
            return emptyList()
        }

        return doc.select(".listupd article, .listupd .bsx, article.bs, .bsx")
            .mapNotNull { toSearchResult(it) }
            .distinctBy { it.name }
    }

    override suspend fun load(url: String): LoadResponse? {
        val initialDoc = try {
            app.get(url, headers = mapOf("User-Agent" to USER_AGENT)).document
        } catch (_: Exception) {
            return null
        }

        // Tentukan sama ada URL yang dibuka adalah episod atau siri anime
        var seriesDoc = initialDoc
        var isEpisodeUrl = url.contains("-episode-", ignoreCase = true)

        if (isEpisodeUrl) {
            val seriesHref = initialDoc.selectFirst(".naveps.bignav .nvsc a, .naveps a[aria-label='All Episodes'], .ts-breadcrumb li:nth-child(2) a")?.attr("href")
            if (!seriesHref.isNullOrBlank()) {
                val fullSeriesHref = fixUrl(seriesHref)
                try {
                    val fetchedDoc = app.get(fullSeriesHref, headers = mapOf("User-Agent" to USER_AGENT)).document
                    if (fetchedDoc.select(".eplister li").isNotEmpty()) {
                        seriesDoc = fetchedDoc
                    }
                } catch (_: Exception) {}
            }
        }

        val rawTitle = seriesDoc.selectFirst(".entry-title, h1.entry-title, .infox h1")?.text()?.trim()
            ?: initialDoc.selectFirst(".entry-title, h1")?.text()?.trim()
            ?: "Donghua"

        val title = cleanSeriesTitle(rawTitle)
        val poster = getPosterUrl(seriesDoc.selectFirst(".thumb img, .poster img, .infox img") ?: initialDoc.selectFirst(".thumb img, .poster img"))

        val description = seriesDoc.selectFirst(".entry-content, .desc, .synopsis")?.text()?.trim()
            ?: initialDoc.selectFirst(".entry-content, .desc")?.text()?.trim()

        val year = seriesDoc.selectFirst(".spe span:contains(Released), .infox span:contains(Released)")?.text()?.filter { it.isDigit() }?.toIntOrNull()

        val statusText = seriesDoc.selectFirst(".spe span:contains(Status), .infox span:contains(Status)")?.text()?.lowercase().orEmpty()
        val showStatus = when {
            statusText.contains("completed") -> ShowStatus.Completed
            statusText.contains("ongoing") -> ShowStatus.Ongoing
            else -> null
        }

        val tags = seriesDoc.select(".genxed a, .genre a, .spe a[href*='/genres/']").map { it.text().trim() }.distinct()

        // Ekstrak senarai episod daripada .eplister li
        val epElements = seriesDoc.select(".eplister li, .episodelist li, ul.episodes li")
        val episodes = mutableListOf<Episode>()

        if (epElements.isNotEmpty()) {
            for (li in epElements) {
                val epHref = li.selectFirst("a[href]")?.attr("href")?.trim() ?: continue
                val fullEpHref = fixUrl(epHref)

                val numStr = li.selectFirst(".epl-num")?.text()?.trim().orEmpty()
                val epNum = numStr.filter { it.isDigit() }.toIntOrNull()

                val epRawTitle = li.selectFirst(".epl-title")?.text()?.trim().orEmpty()
                val epTitle = when {
                    epNum != null -> "Episode $epNum"
                    epRawTitle.isNotBlank() -> epRawTitle
                    else -> "Episode"
                }

                val dateStr = li.selectFirst(".epl-date")?.text()?.trim().orEmpty()
                val epochDate = parseDateToEpoch(dateStr)

                episodes.add(
                    newEpisode(fullEpHref) {
                        this.name = epTitle
                        this.episode = epNum
                        this.posterUrl = poster // Mengaktifkan Episode Card View di CloudStream!
                        this.date = epochDate   // Menetapkan tarikh rilis episod
                        this.description = if (dateStr.isNotBlank()) "Rilis: $dateStr • Sub Indo" else "Sub Indo"
                    }
                )
            }
        } else if (isEpisodeUrl) {
            // Fallback sekiranya tiada siri dijumpai (episod tunggal)
            val epNum = Regex("""(?i)episode\s+(\d+)""").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()
            episodes.add(
                newEpisode(url) {
                    this.name = if (epNum != null) "Episode $epNum" else rawTitle
                    this.episode = epNum
                    this.posterUrl = poster
                    this.description = "Sub Indo"
                }
            )
        }

        // Susun episod mengikut nombor episod dari kecil ke besar (Episode 1, 2, 3...)
        val sortedEpisodes = episodes.sortedBy { it.episode ?: 0 }

        return newTvSeriesLoadResponse(title, url, TvType.Anime, sortedEpisodes) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.showStatus = showStatus
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = try {
            app.get(data, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).document
        } catch (_: Exception) {
            return false
        }

        var foundAny = false
        val embedUrls = mutableListOf<Pair<String, String>>() // Pair(serverName, embedUrl)

        // 1. Ekstrak dari <select class="mirror">
        val options = doc.select(".mirror option, select.mirror option, select[name='server'] option, .itemvideo option")
        for (opt in options) {
            val serverName = opt.text().trim()
            val rawVal = opt.attr("value").trim()
            if (rawVal.isBlank() || serverName.contains("Select", true) || serverName.contains("Not Available", true)) {
                continue
            }

            val decoded = try {
                String(Base64.decode(rawVal, Base64.DEFAULT), Charsets.UTF_8)
            } catch (_: Exception) {
                rawVal
            }

            if (decoded.contains("Not Available", true)) continue

            val srcMatch = Regex("""src=["']([^"']+)["']""").find(decoded)?.groupValues?.get(1)
            val embedUrl = srcMatch ?: if (decoded.startsWith("http")) decoded else ""

            if (embedUrl.isNotBlank()) {
                embedUrls.add(serverName to embedUrl)
            }
        }

        // 2. Ekstrak iframe utama sekiranya ada
        for (ifr in doc.select(".player-embed iframe, #embed_holder iframe, .video-content iframe")) {
            val src = ifr.attr("src").trim()
            if (src.isNotBlank() && src.startsWith("http")) {
                embedUrls.add("Default" to src)
            }
        }

        // 3. Proses setiap pelayan video
        for ((serverLabel, rawEmbed) in embedUrls.distinctBy { it.second }) {
            val embedUrl = rawEmbed.replace("&amp;", "&").trim()

            when {
                // A. Dailymotion
                embedUrl.contains("dailymotion.com", ignoreCase = true) -> {
                    try {
                        val videoId = Regex("""(?:video/|video=)([a-zA-Z0-9]+)""").find(embedUrl)?.groupValues?.get(1).orEmpty()
                        if (videoId.isNotBlank()) {
                            val dmHeaders = mapOf(
                                "User-Agent" to USER_AGENT,
                                "Referer" to "https://geo.dailymotion.com/"
                            )
                            val metaUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
                            val metaJson = app.get(metaUrl, headers = dmHeaders).text

                            // Subtitles
                            try {
                                val metaObj = JSONObject(metaJson)
                                val subArray = metaObj.optJSONObject("subtitles")?.optJSONArray("data")
                                if (subArray != null) {
                                    for (i in 0 until subArray.length()) {
                                        val subObj = subArray.optJSONObject(i) ?: continue
                                        val subUrl = subObj.optJSONArray("urls")?.optString(0).orEmpty()
                                        val label = subObj.optString("label", "Indonesian")
                                        if (subUrl.isNotBlank()) {
                                            subtitleCallback(SubtitleFile(label, subUrl))
                                        }
                                    }
                                }
                            } catch (_: Exception) {}

                            // Manifest URL
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

                                // Master Auto stream
                                callback(
                                    newExtractorLink(
                                        source = name,
                                        name = "$name - Dailymotion Auto",
                                        url = autoM3u8Url,
                                        type = ExtractorLinkType.M3U8
                                    ) {
                                        this.referer = "https://geo.dailymotion.com/"
                                        this.headers = dmHeaders
                                        this.quality = Qualities.P720.value
                                    }
                                )
                                foundAny = true
                            }
                        }
                    } catch (_: Exception) {}
                }

                // B. D.tube
                embedUrl.contains("d.tube", ignoreCase = true) -> {
                    try {
                        val vId = Regex("""[?&]v=([a-zA-Z0-9-]+)""").find(embedUrl)?.groupValues?.get(1).orEmpty()
                        if (vId.isNotBlank()) {
                            val dtubeHeaders = mapOf(
                                "User-Agent" to USER_AGENT,
                                "Referer" to "https://play.d.tube/"
                            )

                            val masterUrls = listOf(
                                "https://nas1.d.tube/videos/$vId/master.m3u8",
                                "https://nas2.d.tube/videos/$vId/master.m3u8"
                            )

                            var activeMaster = ""
                            var manifestText = ""

                            for (mUrl in masterUrls) {
                                try {
                                    val resp = app.get(mUrl, headers = dtubeHeaders)
                                    if (resp.code == 200 && resp.text.contains("#EXTM3U")) {
                                        activeMaster = mUrl
                                        manifestText = resp.text
                                        break
                                    }
                                } catch (_: Exception) {}
                            }

                            if (activeMaster.isNotBlank()) {
                                val baseUrl = activeMaster.substringBeforeLast("/") + "/"

                                // Sub-resolutions (e.g. 720p/playlist.m3u8, 360p/playlist.m3u8)
                                val subRegex = Regex("""#EXT-X-STREAM-INF:[^\n]*?RESOLUTION=(\d+x\d+)[^\n]*\n([^\n]+)""")
                                val subs = subRegex.findAll(manifestText).toList()

                                for (sub in subs) {
                                    val res = sub.groupValues[1]
                                    val path = sub.groupValues[2].trim()
                                    val streamUrl = if (path.startsWith("http")) path else "$baseUrl$path"
                                    val qInt = when {
                                        res.contains("1080") -> Qualities.P1080.value
                                        res.contains("720") -> Qualities.P720.value
                                        res.contains("480") -> Qualities.P480.value
                                        res.contains("360") -> Qualities.P360.value
                                        else -> Qualities.Unknown.value
                                    }
                                    val qLabel = if (res.contains("720")) "720p" else if (res.contains("360")) "360p" else res

                                    callback(
                                        newExtractorLink(
                                            source = name,
                                            name = "$name - DTube $qLabel",
                                            url = streamUrl,
                                            type = ExtractorLinkType.M3U8
                                        ) {
                                            this.referer = "https://play.d.tube/"
                                            this.headers = dtubeHeaders
                                            this.quality = qInt
                                        }
                                    )
                                    foundAny = true
                                }

                                // Auto Master link
                                callback(
                                    newExtractorLink(
                                        source = name,
                                        name = "$name - DTube Auto",
                                        url = activeMaster,
                                        type = ExtractorLinkType.M3U8
                                    ) {
                                        this.referer = "https://play.d.tube/"
                                        this.headers = dtubeHeaders
                                        this.quality = Qualities.P720.value
                                    }
                                )
                                foundAny = true
                            }

                            // Subtitle transcript
                            val vttUrl = "https://nas1.d.tube/transcripts/$vId.vtt"
                            try {
                                val vttCheck = app.get(vttUrl, headers = dtubeHeaders)
                                if (vttCheck.code == 200 && vttCheck.text.isNotBlank()) {
                                    subtitleCallback(SubtitleFile("Indonesian", vttUrl))
                                }
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                }

                // C. Morencius / StreamWish
                embedUrl.contains("morencius.com", ignoreCase = true) ||
                embedUrl.contains("rooserlyxose", ignoreCase = true) ||
                embedUrl.contains("streamwish", ignoreCase = true) -> {
                    try {
                        val resp = app.get(embedUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).text
                        val unpacked = getPacked(resp) ?: resp
                        val m3u8Match = Regex("""https?://[^"'\s<>]+\.m3u8[^"'\s<>]*""").find(unpacked)?.value
                        if (!m3u8Match.isNullOrBlank()) {
                            callback(
                                newExtractorLink(
                                    source = name,
                                    name = "$name - StreamWish 1080p",
                                    url = m3u8Match,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.referer = embedUrl
                                    this.quality = Qualities.P1080.value
                                }
                            )
                            foundAny = true
                        }
                        loadExtractor(embedUrl, "$mainUrl/", subtitleCallback, callback)
                    } catch (_: Exception) {}
                }

                // D. Turbovid
                embedUrl.contains("turbovid", ignoreCase = true) -> {
                    try {
                        val resp = app.get(embedUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).text
                        val m3u8Match = Regex("""https?://[^"'\s<>]+\.m3u8[^"'\s<>]*""").find(resp)?.value
                        if (!m3u8Match.isNullOrBlank()) {
                            callback(
                                newExtractorLink(
                                    source = name,
                                    name = "$name - Turbovid HLS",
                                    url = m3u8Match,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.referer = embedUrl
                                    this.quality = Qualities.P720.value
                                }
                            )
                            foundAny = true
                        }
                        loadExtractor(embedUrl, "$mainUrl/", subtitleCallback, callback)
                    } catch (_: Exception) {}
                }

                // E. OK.ru, Mega, dan Generic Extractors
                else -> {
                    try {
                        loadExtractor(embedUrl, "$mainUrl/", subtitleCallback, callback)
                        foundAny = true
                    } catch (_: Exception) {}
                }
            }
        }

        return foundAny
    }
}
