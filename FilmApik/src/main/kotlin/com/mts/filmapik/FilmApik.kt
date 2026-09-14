package com.mts.filmapik

import android.util.Base64
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class FilmApik : MainAPI() {
    override var mainUrl = "https://filmapik.college"
    override var name = "FilmApik"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama, TvType.Anime)

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

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
                .replace("Agu", "Aug", true)
                .replace("Okt", "Oct", true)
                .replace("Des", "Dec", true)

            val formats = listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
                SimpleDateFormat("yyyy-MM-dd", Locale.US),
                SimpleDateFormat("d MMMM yyyy", Locale.US),
                SimpleDateFormat("MMMM d, yyyy", Locale.US),
                SimpleDateFormat("d MMM yyyy", Locale.US),
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

    // ─── TAMPILAN HOMEPAGE GAYA NETFLIX ──────────────────────────────────────────
    override val mainPage = mainPageOf(
        "$mainUrl/release-year/2026" to "✨ Pilihan Utama (Spotlight / Trending)",
        "$mainUrl/" to "🔥 Film Terbaru (Update Harian)",
        "$mainUrl/tvshows-genre/k-drama" to "🎬 Drama Korea Terpopuler (K-Drama)",
        "$mainUrl/tvshows-genre/china-drama" to "🐉 Drama Mandarin & China Series",
        "$mainUrl/tvshows-genre/west-series" to "🍿 West TV Series & Serial Barat",
        "$mainUrl/tvshows-genre/anime" to "⛩️ Anime & Animasi Terkini",
        "$mainUrl/category/action" to "💥 Aksi & Petualangan (Action)",
        "$mainUrl/category/horror" to "👻 Horor & Misteri (Horror)",
        "$mainUrl/category/comedy" to "😂 Komedi Terlucu (Comedy)",
        "$mainUrl/category/drama" to "🎭 Drama Terbaik (Best Drama)",
        "$mainUrl/category/science-fiction" to "🚀 Fiksi Ilmiah (Sci-Fi & Fantasy)",
        "$mainUrl/category/thriller" to "🔍 Thriller & Kejahatan (Thriller)",
        "$mainUrl/tvshows-genre/japan-drama" to "🎎 Drama Jepang (J-Drama)",
        "$mainUrl/tvshows-genre/thailand-drama" to "🥊 Drama Thailand & Asia Lainnya"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val targetUrl = if (page <= 1) {
            request.data
        } else {
            val base = request.data.removeSuffix("/")
            if (base.contains("?")) {
                val parts = base.split("?", limit = 2)
                "${parts[0]}/page/$page/?${parts[1]}"
            } else {
                "$base/page/$page/"
            }
        }

        val doc = try {
            app.get(
                targetUrl,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/"
                ),
                timeout = 15
            ).document
        } catch (_: Exception) {
            return newHomePageResponse(request.name, emptyList())
        }

        val items = doc.select("article, div.gmr-box-item, div.item, .item-infinite, .post-item, div.movie-item, a[href*='/nonton-'], a[href*='/tvshows/']")
        val searchResponses = items.mapNotNull { toSearchResult(it) }.distinctBy { it.url }
        return newHomePageResponse(request.name, searchResponses, hasNext = searchResponses.isNotEmpty())
    }

    private fun getPosterUrl(element: Element?): String? {
        if (element == null) return null
        val img = if (element.tagName().equals("img", true)) element else (element.selectFirst("img") ?: element)
        for (attr in listOf("data-src", "data-lazy-src", "data-original", "src")) {
            val v = img.attr(attr).trim()
            if (v.isNotBlank() && !v.startsWith("data:") && (v.startsWith("http") || v.startsWith("//"))) {
                return fixUrlNull(v)
            }
        }
        return null
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        return runCatching {
            val a = if (element.tagName().equals("a", true)) element else element.selectFirst("a[href]") ?: return null
            val rawHref = a.attr("href").trim()
            val href = fixUrlNull(rawHref) ?: return null
            if (href.isBlank() || href == "$mainUrl/" || href.contains("/category/") || href.contains("/tvshows-genre/") || href.contains("/release-year/") || href.contains("/negara/") || href.contains("/dmca") || href.contains("/page/")) return null

            val img = element.selectFirst("img") ?: a.selectFirst("img")
            var rawTitle = element.selectFirst("h2.entry-title, h3.entry-title, h2, h3, .entry-title, .title, .gmr-movie-title")?.text()?.trim().orEmpty()
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

            val cleanTitle = rawTitle
                .removePrefix("Permalink ke: ")
                .removePrefix("Permalink to: ")
                .removePrefix("Permalink ke ")
                .removePrefix("Download ")
                .removePrefix("Nonton Film ")
                .removePrefix("Nonton ")
                .split("Subtitle Indonesia")[0]
                .split("Sub Indo")[0]
                .split("Full Movie")[0]
                .split("Full Episode")[0]
                .trim()

            val poster = getPosterUrl(img ?: element)

            val isSeries = href.contains("/tvshows/", ignoreCase = true) ||
                href.contains("/tv/", ignoreCase = true) ||
                href.contains("/series/", ignoreCase = true) ||
                href.contains("/episodes/", ignoreCase = true) ||
                href.contains("season-", ignoreCase = true) ||
                cleanTitle.contains("Season", ignoreCase = true)

            if (isSeries) {
                newTvSeriesSearchResponse(cleanTitle, href, TvType.TvSeries) {
                    this.posterUrl = poster
                }
            } else {
                newMovieSearchResponse(cleanTitle, href, TvType.Movie) {
                    this.posterUrl = poster
                }
            }
        }.getOrNull()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/?s=$encoded"
        val doc = try {
            app.get(
                searchUrl,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/"
                ),
                timeout = 15
            ).document
        } catch (_: Exception) {
            return emptyList()
        }

        return doc.select("article, div.gmr-box-item, div.item, .item-infinite, .post-item, div.movie-item, a[href*='/nonton-'], a[href*='/tvshows/']")
            .mapNotNull { toSearchResult(it) }
            .distinctBy { it.url }
    }

    // ─── LOAD DETAIL DENGAN CARD UNTUK SETIAP EPISODE & TARIKH RILIS ──────────────
    override suspend fun load(url: String): LoadResponse? {
        val doc = try {
            app.get(
                url,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/"
                ),
                timeout = 15
            ).document
        } catch (_: Exception) {
            return null
        }

        val rawTitle = doc.selectFirst("h1.entry-title, .entry-title, .title-content, h1")?.text()?.trim() ?: "FilmApik"
        val cleanTitle = rawTitle
            .removePrefix("Nonton Film ")
            .removePrefix("Nonton ")
            .split("Subtitle Indonesia")[0]
            .split("Sub Indo")[0]
            .split("Full Movie")[0]
            .trim()

        val poster = getPosterUrl(doc.selectFirst("figure img, .poster img, .cover img, .gmr-poster-img img, img[src*='poster'], img[src*='backdrop']"))

        val plot = doc.selectFirst(".entry-content-single p, .entry-content p, .synopsis p, .description p, p.story")?.text()?.trim()

        // Extract metadata fields
        var year: Int? = null
        var releaseDateStr: String? = null
        var releaseEpoch: Long? = null
        val genres = mutableListOf<String>()
        val actors = mutableListOf<String>()

        doc.select(".metadata div, .details div, .info div, .item-meta div, .spe span, div, p").forEach { row ->
            val text = row.text().trim()
            when {
                text.startsWith("Tahun:", ignoreCase = true) || text.startsWith("Year:", ignoreCase = true) -> {
                    if (year == null) {
                        year = Regex("""(\d{4})""").find(text)?.groupValues?.get(1)?.toIntOrNull()
                    }
                }
                text.startsWith("Rilis:", ignoreCase = true) || text.startsWith("Release:", ignoreCase = true) || text.startsWith("Diposting pada:", ignoreCase = true) -> {
                    val dateVal = row.selectFirst("time")?.attr("datetime")
                        ?: row.selectFirst("time")?.text()
                        ?: text.substringAfter(":").trim()
                    if (releaseDateStr == null) {
                        releaseDateStr = row.selectFirst("time")?.text()?.ifBlank { dateVal } ?: dateVal
                        releaseEpoch = parseDateToEpoch(dateVal)
                    }
                }
                text.startsWith("Genre:", ignoreCase = true) -> {
                    row.select("a").forEach { g -> genres.add(g.text().trim()) }
                }
                text.startsWith("Cast:", ignoreCase = true) || text.startsWith("Pemain:", ignoreCase = true) || text.startsWith("Bintang:", ignoreCase = true) -> {
                    row.select("a").forEach { a -> actors.add(a.text().trim()) }
                }
            }
        }

        if (year == null) {
            year = Regex("""(19\d{2}|20\d{2})""").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()
        }

        // Check if page contains episode list (Series / Drama)
        val episodeElements = doc.select("a[href*='/episodes/'], .famv-episode-btn, .famv-season-list a, div.gmr-listseries a")
            .filter {
                val h = it.attr("href").trim()
                h.isNotBlank() && h != url && !h.endsWith("/tv/") && !h.endsWith("/tvshows/")
            }

        val isTvSeries = episodeElements.isNotEmpty() || url.contains("/tvshows/", ignoreCase = true) || url.contains("/episodes/", ignoreCase = true)

        if (isTvSeries && episodeElements.isNotEmpty()) {
            val episodes = mutableListOf<Episode>()

            episodeElements.forEachIndexed { index, el ->
                val epHref = fixUrlNull(el.attr("href")) ?: return@forEachIndexed
                val epText = el.text().trim()
                val epTooltip = el.attr("title").trim()

                // Extract season & episode numbers
                val seasonParent = el.closest("[data-season]")?.attr("data-season")?.toIntOrNull()
                val seasonMatch = Regex("""(?i)(?:Season|S)\s*(\d+)""").find("$epTooltip $epHref")
                val seasonNum = seasonParent ?: seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

                val epMatch = Regex("""(?i)(?:Episode|Eps|Ep|EP|E)\s*(\d+)""").find("$epText $epTooltip $epHref")
                val episodeNum = epMatch?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)

                val epName = if (epTooltip.isNotBlank()) {
                    epTooltip
                } else if (epText.isNotBlank() && !epText.startsWith("EP", ignoreCase = true)) {
                    epText
                } else {
                    "Episode $episodeNum"
                }

                episodes.add(
                    newEpisode(epHref) {
                        this.name = epName
                        this.season = seasonNum
                        this.episode = episodeNum
                        this.posterUrl = poster
                        this.date = releaseEpoch
                        this.description = if (!releaseDateStr.isNullOrBlank()) "Rilis: $releaseDateStr • Sub Indo" else "Episode $episodeNum • Sub Indo"
                    }
                )
            }

            val sortedEpisodes = episodes.sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 0 }))

            return newTvSeriesLoadResponse(cleanTitle, url, TvType.TvSeries, sortedEpisodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = genres.distinct()
                this.actors = actors.distinct().map { ActorData(Actor(it)) }
            }
        }

        // Movie Load Response
        return newMovieLoadResponse(cleanTitle, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.tags = genres.distinct()
            this.actors = actors.distinct().map { ActorData(Actor(it)) }
        }
    }

    // ─── LOAD LINKS DENGAN SCRAPING SEMUA SERVER VIDEO & DOWNLOAD ───────────────
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundAny = false
        val cleanData = data.trim()
        val urlsToVisit = mutableListOf<String>()
        urlsToVisit.add(cleanData)

        // For movie URLs without /play/, add the /play/ URL as well
        if (!cleanData.contains("/episodes/") && !cleanData.endsWith("/play/") && !cleanData.endsWith("/play")) {
            urlsToVisit.add(cleanData.removeSuffix("/") + "/play/")
        }

        val candidates = linkedSetOf<String>()

        for (targetUrl in urlsToVisit) {
            val doc = try {
                app.get(
                    targetUrl,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to "$mainUrl/"
                    ),
                    timeout = 15
                ).document
            } catch (_: Exception) {
                continue
            }

            // 1. Ambil direct iframes & embeds
            doc.select("iframe[src], embed[src], video[src], source[src]").forEach { el ->
                val src = el.attr("src").ifBlank { el.attr("data-src") }.trim()
                if (src.isNotBlank() && !src.contains("google.com") && !src.contains("youtube.com/embed/NA")) {
                    candidates.add(fixUrl(src))
                }
            }

            // 2. Ambil semua server buttons (.famv-server-btn, .player-option, [data-server], a[data-url])
            doc.select(".famv-server-btn, .player-option, [data-server], a[data-url]").forEach { btn ->
                val dataUrl = btn.attr("data-url").ifBlank { btn.attr("href") }.trim()
                if (dataUrl.isNotBlank() && !dataUrl.startsWith("#") && !dataUrl.startsWith("javascript:")) {
                    candidates.add(fixUrl(dataUrl))
                }
            }

            // 3. Ambil pautan muat turun (.famv-download-btn, a[href*="download"], a[href*="drive"])
            doc.select("a.famv-download-btn, a[href*='download'], a[href*='drive'], [data-source-type='download']").forEach { a ->
                val href = a.attr("data-url").ifBlank { a.attr("href") }.trim()
                if (href.isNotBlank() && !href.startsWith("#") && !href.startsWith("javascript:")) {
                    candidates.add(fixUrl(href))
                }
            }
        }

        // 4. Resolusi setiap calon server video
        for (candidate in candidates) {
            val resolved = resolveCandidate(candidate, cleanData, subtitleCallback, callback)
            if (resolved) foundAny = true
        }

        return foundAny
    }

    private suspend fun resolveCandidate(
        url: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var handled = false
        val lower = url.lowercase()

        // 1. EfekStream (VIP Server)
        if (lower.contains("efek.stream")) {
            runCatching {
                EfekStream().getUrl(url, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 2. AbyssCDN / AbyssPlayer / Hydrax / Sora
        if (lower.contains("abysscdn.com") || lower.contains("abyssplayer.com") || lower.contains("abyss.to")) {
            runCatching {
                AbyssPlayer().getUrl(url, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 3. Byseq / Filemoon
        if (lower.contains("byseqekaho.com") || lower.contains("filemoon.to") || lower.contains("filemoon.sx") || lower.contains("filemoon.in")) {
            runCatching {
                ByseqExtractor().getUrl(url, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 4. StreamP2P
        if (lower.contains("strp2p.site") || lower.contains("rpmvid.com")) {
            runCatching {
                StreamP2PExtractor().getUrl(url, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 5. Direct M3U8
        if (lower.contains(".m3u8")) {
            runCatching {
                generateM3u8(name, url, referer).forEach { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 6. Direct MP4
        if (lower.contains(".mp4")) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "$name MP4",
                    url = url,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = referer
                    this.quality = Qualities.P1080.value
                }
            )
            return true
        }

        // 7. Universal fallback to CloudStream's loadExtractor
        runCatching {
            loadExtractor(url, referer, subtitleCallback) { l ->
                callback.invoke(l)
                handled = true
            }
        }

        return handled
    }
}
