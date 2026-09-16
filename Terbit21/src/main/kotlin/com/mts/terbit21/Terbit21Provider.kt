package com.mts.terbit21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import android.util.Log
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Terbit21Provider : MainAPI() {
    override var mainUrl = "https://162.244.95.227"
    override var name = "Terbit21"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "" to "🍿 Sedang Tayang (Terbaru)",
        "on-going/" to "🔥 Serial TV Sedang Tayang (Ongoing)",
        "drama-korea/" to "🌸 Drama Korea (K-Drama Pilihan)",
        "west-series/" to "🎬 Serial Barat (West Series Populer)",
        "film-action-terbaru/" to "💥 Film Aksi & Laga (Action Box Office)",
        "adventure/" to "🗺️ Petualangan Seru (Adventure)",
        "comedy/" to "😂 Komedi Mengocok Perut (Comedy)",
        "crime/" to "🕵️ Kriminal & Penyelidikan (Crime)",
        "fantasy/" to "🧙 Fantasi & Sihir (Fantasy)",
        "mystery/" to "🔍 Misteri & Teka-Teki (Mystery)",
        "romance/" to "💖 Romansa & Cinta (Romance)",
        "science-fiction/" to "🚀 Fiksi Ilmiah (Sci-Fi Movies)",
        "thriller/" to "⚡ Ketegangan Memuncak (Thriller)",
        "animation/" to "🎨 Animasi Terbaik (Animation)",
        "country/korea/" to "🇰🇷 Sinema Korea (Korean Movies)",
        "country/japan/" to "🇯🇵 Sinema Jepang (Japanese Movies)",
        "country/china/" to "🇨🇳 Sinema Mandarin (Chinese Movies)",
        "country/usa/" to "🇺🇸 Hollywood Box Office (USA)",
        "country/thailand/" to "🇹🇭 Sinema Thailand",
        "country/india/" to "🇮🇳 Sinema Bollywood (India)",
        "country/indonesia/" to "🇮🇩 Sinema Indonesia",
        "completed/" to "🏆 Serial TV Tamat (Binge-Watch)",
        "batch/" to "📦 Serial TV Lengkap (Batch)",
        "year/2026/" to "✨ Rilisan Terkini (2026)",
        "year/2025/" to "🌟 Film Terbaik (2025)",
        "year/2024/" to "⭐ Film Terpopuler (2024)",
        "year/2023/" to "💫 Koleksi Nostalgia (2023)"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val path = request.data
        var pageUrl = if (page == 1) {
            if (path.isEmpty()) "$mainUrl/" else "$mainUrl/$path"
        } else {
            val cleanPath = path.removeSuffix("/")
            if (cleanPath.isEmpty()) "$mainUrl/page/$page/" else "$mainUrl/$cleanPath/page/$page/"
        }
        if (pageUrl.startsWith("https://")) {
            pageUrl = "https://" + pageUrl.substring(8).replace("//", "/")
        } else if (pageUrl.startsWith("http://")) {
            pageUrl = "http://" + pageUrl.substring(7).replace("//", "/")
        }
        val document = app.get(pageUrl, timeout = 30).document
        val items = document.select("article.item-infinite, div.gmr-box-item, article.post, article.item")
        val homeItems = items.mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, homeItems, hasNext = homeItems.isNotEmpty())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = this.selectFirst("a") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val img = this.selectFirst("img")
        val posterUrl = img?.let { i ->
            listOf("data-src", "data-lazy-src", "data-original", "src").map { i.attr(it) }.firstOrNull { it.isNotBlank() && !it.startsWith("data:") }
        }?.let { fixUrlNull(it) }

        var rawTitle = this.selectFirst("h2.entry-title, h3.entry-title, h2, h3, .entry-title, .title")?.text()?.trim()
        if (rawTitle.isNullOrBlank()) {
            rawTitle = a.attr("title").ifEmpty { img?.attr("alt").orEmpty().ifEmpty { a.text() } }
        }
        val title = rawTitle
            .removePrefix("Permalink ke: ")
            .removePrefix("Permalink to: ")
            .removePrefix("Download ")
            .split("Sub Indo")[0]
            .split("Full Movie")[0]
            .split("Full Episode")[0]
            .trim()

        if (title.isBlank() || href.isBlank()) return null

        val isSeries = href.contains("/tv/") || href.contains("/series/") || href.contains("/serial-tv/") || href.contains("full-episode", true) || title.contains("Season", true)

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = app.get(searchUrl, timeout = 30).document
        return document.select("article.item-infinite, div.gmr-box-item, article.post, article.item").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, timeout = 30).document
        val rawTitle = document.selectFirst("h1.entry-title, .title-content")?.text()?.trim() ?: throw Exception("Title not found")
        val title = rawTitle
            .removePrefix("Permalink ke: ")
            .removePrefix("Permalink to: ")
            .removePrefix("Download ")
            .split("Sub Indo")[0]
            .split("Full Movie")[0]
            .split("Full Episode")[0]
            .trim()

        val poster = document.selectFirst(".gmr-poster-img img, .poster img")?.let { i ->
            listOf("data-src", "data-lazy-src", "src").map { i.attr(it) }.firstOrNull { it.isNotBlank() }
        }?.let { fixUrlNull(it) }

        val plot = document.selectFirst(".entry-content p, .synopsis p")?.text()?.trim()
        val year = document.selectFirst(".gmr-moviedata strong:contains(Year:) + a, .gmr-moviedata a[href*='/year/'], time[itemprop='dateCreated']")?.text()?.trim()?.toIntOrNull()
        val scoreText = document.selectFirst(".gmr-rating-item, span[itemprop='ratingValue'], .rating")?.text()?.trim()
        val scoreVal = scoreText?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()

        // Precise Series detection: only URLs with /tv/, /series/, /serial-tv/ or pages with .gmr-listseries containing episode links
        val isSeries = url.contains("/tv/") || url.contains("/series/") || url.contains("/serial-tv/") ||
            document.select(".gmr-listseries a[href*='/eps/'], .gmr-listseries a.button-shadow").isNotEmpty()

        if (isSeries) {
            val episodes = mutableListOf<Episode>()

            // 1. Fetch dates via WP REST API search (fast, reliable metadata)
            val dateMap = mutableMapOf<String, Long>()
            try {
                val cleanTitle = title.replace(Regex("(?i)season.*|\\(.*?\\)"), "").trim()
                val encTitle = URLEncoder.encode(cleanTitle, "UTF-8")
                val restUrl = "$mainUrl/wp-json/wp/v2/episode/?search=$encTitle&per_page=100"
                val restResp = app.get(restUrl, timeout = 15)
                if (restResp.isSuccessful && restResp.text.startsWith("[")) {
                    val arr = org.json.JSONArray(restResp.text)
                    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)
                    for (i in 0 until arr.length()) {
                        val epObj = arr.getJSONObject(i)
                        val slug = epObj.optString("slug", "").trim()
                        val link = epObj.optString("link", "").trim()
                        val dateStr = epObj.optString("date", "").trim()
                        if (dateStr.isNotBlank()) {
                            val timeMillis = try { isoFormat.parse(dateStr)?.time } catch (_: Exception) { null }
                            if (timeMillis != null) {
                                if (slug.isNotBlank()) dateMap[slug] = timeMillis
                                val normLink = fixUrl(link).trimEnd('/')
                                dateMap[normLink] = timeMillis
                                val slugFromLink = normLink.substringAfterLast("/")
                                dateMap[slugFromLink] = timeMillis
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // 2. Parse episode elements from .gmr-listseries
            val listSeriesElements = document.select(".gmr-listseries a").filter {
                !it.hasClass("gmr-all-serie") && !it.text().contains("View All", true)
            }

            if (listSeriesElements.isNotEmpty()) {
                listSeriesElements.forEachIndexed { index, el ->
                    val epHref = fixUrlNull(el.attr("href")) ?: return@forEachIndexed
                    val epText = el.text().trim()
                    val titleAttr = el.attr("title").removePrefix("Permalink to ").removePrefix("Permalink ke: ").trim()

                    val seasonNum = Regex("""(?i)S(\d+)""").find(epText)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val epNum = Regex("""(?i)Eps?(\d+)""").find(epText)?.groupValues?.get(1)?.toIntOrNull()
                        ?: Regex("""(?i)episode[- ]*(\d+)""").find(epHref)?.groupValues?.get(1)?.toIntOrNull()
                        ?: (index + 1)

                    val epSlug = epHref.trimEnd('/').substringAfterLast('/')
                    val epDateMillis = dateMap[epSlug] ?: dateMap[epHref.trimEnd('/')]
                    val dateFormatted = epDateMillis?.let {
                        SimpleDateFormat("dd MMM yyyy", Locale.ROOT).format(Date(it))
                    }

                    val epName = if (!dateFormatted.isNullOrBlank()) {
                        "Episode $epNum • $dateFormatted"
                    } else if (titleAttr.isNotBlank()) {
                        titleAttr
                    } else {
                        "Episode $epNum"
                    }

                    episodes.add(newEpisode(epHref) {
                        this.name = epName
                        this.episode = epNum
                        this.season = seasonNum
                        this.posterUrl = poster
                        if (epDateMillis != null) {
                            this.date = epDateMillis
                        }
                        if (!dateFormatted.isNullOrBlank()) {
                            this.description = "Tarikh Rilis: $dateFormatted"
                        }
                    })
                }
            }

            // Fallback: gmr-numpost or gmr-listepisode
            if (episodes.isEmpty()) {
                val fallbackEps = document.select("a.gmr-numpost, .gmr-listepisode a, .list-episode a")
                fallbackEps.forEachIndexed { index, el ->
                    val epHref = fixUrlNull(el.attr("href")) ?: return@forEachIndexed
                    val epText = el.text().trim()
                    val epNum = epText.replace(Regex("[^0-9]"), "").toIntOrNull() ?: (index + 1)
                    val epSlug = epHref.trimEnd('/').substringAfterLast('/')
                    val epDateMillis = dateMap[epSlug] ?: dateMap[epHref.trimEnd('/')]
                    val dateFormatted = epDateMillis?.let {
                        SimpleDateFormat("dd MMM yyyy", Locale.ROOT).format(Date(it))
                    }

                    val epName = if (!dateFormatted.isNullOrBlank()) {
                        "Episode $epNum • $dateFormatted"
                    } else {
                        "Episode $epNum"
                    }

                    episodes.add(newEpisode(epHref) {
                        this.name = epName
                        this.episode = epNum
                        this.season = 1
                        this.posterUrl = poster
                        if (epDateMillis != null) {
                            this.date = epDateMillis
                        }
                        if (!dateFormatted.isNullOrBlank()) {
                            this.description = "Tarikh Rilis: $dateFormatted"
                        }
                    })
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.sortedBy { it.episode ?: 0 }) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                if (scoreVal != null) {
                    this.score = Score.from10(scoreVal)
                }
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                if (scoreVal != null) {
                    this.score = Score.from10(scoreVal)
                }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCdn: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var found = false
        try {
            val doc = app.get(data, timeout = 30).document

            fun Element?.getIframeSrc(): String? {
                if (this == null) return null
                return listOf(
                    attr("data-litespeed-src"),
                    attr("data-lazy-src"),
                    attr("data-src"),
                    attr("data-video"),
                    attr("data-embed"),
                    attr("data-url"),
                    attr("data-iframe"),
                    attr("src")
                ).firstOrNull { it.isNotBlank() && !it.equals("about:blank", true) && !it.startsWith("javascript", true) }
            }

            val candidateUrls = mutableListOf<String>()

            // 1. Direct iframes on the page
            doc.select("iframe").forEach { iframe ->
                val src = iframe.getIframeSrc()
                if (!src.isNullOrBlank()) {
                    val fixed = fixUrl(src)
                    if (!fixed.contains("youtube.com") && !fixed.contains("youtu.be")) {
                        candidateUrls.add(fixed)
                    }
                }
            }

            // 2. Server sub-page / tab links (e.g. ?player=2, ?player=3, .gmr-server-wrap a)
            val serverTabLinks = mutableListOf<String>()
            doc.select(".gmr-server-wrap a, ul.muvipro-player-tabs a, ul.gmr-player-tabs a, .gmr-player-nav a, ul#gmr-tab a, a[href*='?player='], a[href*='&player=']").forEach { a ->
                val href = a.attr("href").trim()
                if (href.isNotBlank() && !href.startsWith("#") && !href.startsWith("javascript", true)
                    && !href.contains("youtube.com") && !href.contains("youtu.be")) {
                    val resolved = fixUrl(href)
                    if (resolved != data) {
                        serverTabLinks.add(resolved)
                    }
                }
            }

            serverTabLinks.distinct().take(8).forEach { tabUrl ->
                try {
                    val tabDoc = app.get(tabUrl, timeout = 20, headers = mapOf("Referer" to data)).document
                    tabDoc.select("iframe").forEach { iframe ->
                        val src = iframe.getIframeSrc()
                        if (!src.isNullOrBlank()) {
                            val fixed = fixUrl(src)
                            if (!fixed.contains("youtube.com") && !fixed.contains("youtu.be")) {
                                candidateUrls.add(fixed)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Terbit21Provider", "Server tab error [$tabUrl]: ${e.message}")
                }
            }

            // 3. Muvipro AJAX tab content (action=muvipro_player_content)
            val postId = doc.selectFirst("div.gmr-server-wrap[data-id], div[data-id]")?.attr("data-id")
                ?: doc.selectFirst("body")?.classNames()
                    ?.firstOrNull { it.startsWith("postid-") }?.removePrefix("postid-")

            if (!postId.isNullOrBlank()) {
                doc.select("ul.muvipro-player-tabs a[href^='#p'], ul.nav-tabs a[href^='#p']").forEach { a ->
                    val tabName = a.attr("href").removePrefix("#").trim()
                    if (tabName.isNotBlank()) {
                        try {
                            val resHtml = app.post(
                                "$mainUrl/wp-admin/admin-ajax.php",
                                data = mapOf("action" to "muvipro_player_content", "tab" to tabName, "post_id" to postId),
                                headers = mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to data),
                                timeout = 15
                            ).text
                            val tabSrc = Jsoup.parse(resHtml).selectFirst("iframe").getIframeSrc()
                            if (!tabSrc.isNullOrBlank()) {
                                val fixedSrc = fixUrl(tabSrc)
                                if (!fixedSrc.contains("youtube.com")) {
                                    candidateUrls.add(fixedSrc)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("Terbit21Provider", "AJAX tab error: ${e.message}")
                        }
                    }
                }
            }

            // 4. Process all candidate URLs through specialized extractors
            candidateUrls.distinct().forEach { embedUrl ->
                try {
                    when {
                        embedUrl.contains("sf21.rpmvid.com") -> {
                            Sf21RpmvidCom().getUrl(embedUrl, data, subtitleCallback) {
                                found = true
                                callback(it)
                            }
                        }
                        embedUrl.contains("sf21.vidplayer.live") -> {
                            Sf21VidplayerLive().getUrl(embedUrl, data, subtitleCallback) {
                                found = true
                                callback(it)
                            }
                        }
                        embedUrl.contains("abyssplayer.com") -> {
                            PlayerAbyssplayerCom().getUrl(embedUrl, data, subtitleCallback) {
                                found = true
                                callback(it)
                            }
                        }
                        embedUrl.contains("morencius.com") -> {
                            MorenciusCom().getUrl(embedUrl, data, subtitleCallback) {
                                found = true
                                callback(it)
                            }
                        }
                        embedUrl.contains("hgcloud.to") || embedUrl.contains("masukestin.com") -> {
                            HgcloudTo().getUrl(embedUrl, data, subtitleCallback) {
                                found = true
                                callback(it)
                            }
                        }
                        embedUrl.contains("embedpyrox.xyz") -> {
                            EmbedpyroxXyz().getUrl(embedUrl, data, subtitleCallback) {
                                found = true
                                callback(it)
                            }
                        }
                        else -> {
                            if (loadExtractor(embedUrl, data, subtitleCallback, callback)) {
                                found = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Terbit21Provider", "Error extracting candidate [$embedUrl]: ${e.message}")
                }
            }

            // 5. Fallback: check page HTML for unpacked JS or direct m3u8 if no links found yet
            if (!found) {
                val pageHtml = doc.html()
                val m3u8Regex = Regex("""https?://[^\s"']+\.m3u8[^\s"']*""", RegexOption.IGNORE_CASE)
                m3u8Regex.findAll(pageHtml).forEach { m ->
                    val videoUrl = m.value.trim()
                    generateM3u8(name, videoUrl, data).forEach { link ->
                        found = true
                        callback(link)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("Terbit21Provider", "loadLinks error: ${e.message}")
        }
        return found
    }
}
