package com.mts.film21

import android.util.Base64
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
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
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Film21 : MainAPI() {
    override var mainUrl = "https://tv14.filem21.net"
    override var name = "Film21"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

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
        "$mainUrl/genre/box-office/" to "✨ Pilihan Utama (Spotlight / Trending)",
        "$mainUrl/best-rating/" to "🔥 Trending Hari Ini (Top 10)",
        "$mainUrl/" to "⚡ Film Terbaru (Update Harian)",
        "$mainUrl/tv/" to "🎬 Serial TV & Drama Series",
        "$mainUrl/genre/drama-korea/" to "⭐ Drama Korea Terpopuler",
        "$mainUrl/genre/drama-china/" to "🐉 Drama China Terkini",
        "$mainUrl/genre/action/" to "💥 Aksi & Petualangan (Action)",
        "$mainUrl/genre/horror/" to "👻 Horor & Misteri (Horror)",
        "$mainUrl/genre/comedy/" to "🍿 Komedi Terlucu (Comedy)",
        "$mainUrl/genre/drama/" to "🎭 Drama Terbaik (Best Drama)",
        "$mainUrl/genre/science-fiction/" to "🚀 Fiksi Ilmiah (Sci-Fi & Fantasy)",
        "$mainUrl/genre/romance/" to "❤️ Romantis (Romance)",
        "$mainUrl/genre/film-semi/" to "🔞 Film Semi & Dewasa (18+)",
        "$mainUrl/genre/vivamax/" to "🌟 VivaMax Eksklusif"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val targetUrl = if (page == 1) {
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

        val items = doc.select("article, div.gmr-box-item, div.item, .item-infinite, .post-item")
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
            if (href.isBlank() || href == "$mainUrl/" || href.contains("/genre/") || href.contains("/dmca/")) return null

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
                .split("Sub Indo")[0]
                .split("Full Movie")[0]
                .split("Full Episode")[0]
                .trim()

            val poster = getPosterUrl(img ?: element)

            val isSeries = href.contains("/tv/", ignoreCase = true) ||
                href.contains("/series/", ignoreCase = true) ||
                href.contains("/eps/", ignoreCase = true) ||
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
        val searchUrl = "$mainUrl/?s=$encoded&post_type[]=post&post_type[]=tv"
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

        return doc.select("article, div.gmr-box-item, div.item, .item-infinite, .post-item")
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

        val rawTitle = doc.selectFirst("h1.entry-title, .entry-title, .title-content, h1")?.text()?.trim() ?: "Film21"
        val cleanTitle = rawTitle
            .removePrefix("Nonton Film ")
            .removePrefix("Nonton ")
            .split("LK21")[0]
            .split("Sub Indo")[0]
            .split("Full Movie")[0]
            .trim()

        val poster = getPosterUrl(doc.selectFirst(".gmr-movie-data figure img, .gmr-poster-img img, .poster img, .entry-content img"))

        val plot = doc.selectFirst(".entry-content-single p, .entry-content p, .synopsis p, .description p")?.text()?.trim()

        // Extract metadata fields from .gmr-moviedata blocks
        var year: Int? = null
        var releaseDateStr: String? = null
        var releaseEpoch: Long? = null
        var duration: String? = null
        val genres = mutableListOf<String>()
        val actors = mutableListOf<String>()

        doc.select(".gmr-moviedata").forEach { row ->
            val text = row.text().trim()
            when {
                text.startsWith("Tahun:", ignoreCase = true) -> {
                    year = Regex("""(\d{4})""").find(text)?.groupValues?.get(1)?.toIntOrNull()
                }
                text.startsWith("Rilis:", ignoreCase = true) || text.startsWith("Diposting pada:", ignoreCase = true) -> {
                    val dateVal = row.selectFirst("time")?.attr("datetime")
                        ?: row.selectFirst("time")?.text()
                        ?: text.substringAfter(":").trim()
                    if (releaseDateStr == null) {
                        releaseDateStr = row.selectFirst("time")?.text()?.ifBlank { dateVal } ?: dateVal
                        releaseEpoch = parseDateToEpoch(dateVal)
                    }
                }
                text.startsWith("Durasi:", ignoreCase = true) -> {
                    duration = text.substringAfter(":").trim()
                }
                text.startsWith("Genre:", ignoreCase = true) -> {
                    row.select("a").forEach { g -> genres.add(g.text().trim()) }
                }
                text.startsWith("Pemain:", ignoreCase = true) || text.startsWith("Cast:", ignoreCase = true) || text.startsWith("Bintang:", ignoreCase = true) -> {
                    row.select("a").forEach { a -> actors.add(a.text().trim()) }
                }
            }
        }

        if (year == null) {
            year = Regex("""(19\d{2}|20\d{2})""").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()
        }

        // Check if page contains episode list (Series / Drama)
        val episodeElements = doc.select("div.gmr-listseries a, .list-eps a, .gmr-episodes a, ul.episodios a")
            .filter {
                val h = it.attr("href").trim()
                h.isNotBlank() && h != url && !h.endsWith("/tv/") && !h.endsWith("/tv") && !it.text().contains("Lihat Semua Episode", ignoreCase = true)
            }

        val isTvSeries = episodeElements.isNotEmpty() || url.contains("/tv/", ignoreCase = true) || url.contains("/eps/", ignoreCase = true)

        if (isTvSeries && episodeElements.isNotEmpty()) {
            val episodes = mutableListOf<com.lagradost.cloudstream3.Episode>()

            episodeElements.forEachIndexed { index, el ->
                val epHref = fixUrlNull(el.attr("href")) ?: return@forEachIndexed
                val epText = el.text().trim()
                val epTooltip = el.attr("title").trim()

                // Extract season & episode numbers
                val seasonMatch = Regex("""(?i)(?:Season|S)\s*(\d+)""").find("$epText $epTooltip $epHref")
                val seasonNum = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

                val epMatch = Regex("""(?i)(?:Episode|Eps|Ep|E)\s*(\d+)""").find("$epText $epTooltip $epHref")
                val episodeNum = epMatch?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)

                val epName = if (epText.isNotBlank() && !epText.startsWith("S", ignoreCase = true)) {
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
        val doc = try {
            app.get(
                data,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/"
                ),
                timeout = 15
            ).document
        } catch (_: Exception) {
            return false
        }

        val candidates = linkedSetOf<String>()

        // 1. Ambil direct embed dalam page
        doc.select("iframe[src], embed[src], video[src], source[src]").forEach { el ->
            val src = el.attr("src").ifBlank { el.attr("data-src") }.trim()
            if (src.isNotBlank() && !src.contains("google.com") && !src.contains("youtube.com/embed/NA")) {
                candidates.add(fixUrl(src))
            }
        }

        // 2. Ambil post_id daripada .muvipro_player_content container
        val playerContainer = doc.selectFirst(".muvipro_player_content, #muvipro_player_content_id, .gmr-server-wrap")
        val postId = playerContainer?.attr("data-id")?.trim()

        // 3. Ambil semua tabs server (#p1, #p2, #p3, #p4)
        val tabElements = doc.select(".muvipro-player-tabs a, ul.gmr-player-nav a, #player-tabs a")
        val tabNames = mutableListOf<String>()
        tabElements.forEach { a ->
            val href = a.attr("href").trim()
            if (href.startsWith("#p")) {
                tabNames.add(href.removePrefix("#"))
            }
        }

        // Jika tiada tab ditemui tetapi ada post_id, cuba default p1, p2, p3, p4
        val tabsToQuery = if (tabNames.isNotEmpty()) tabNames.distinct() else listOf("p1", "p2", "p3", "p4")

        if (!postId.isNullOrBlank()) {
            val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
            for (tab in tabsToQuery) {
                runCatching {
                    val resp = app.post(
                        ajaxUrl,
                        data = mapOf(
                            "action" to "muvipro_player_content",
                            "tab" to tab,
                            "post_id" to postId
                        ),
                        headers = mapOf(
                            "User-Agent" to USER_AGENT,
                            "Referer" to data,
                            "X-Requested-With" to "XMLHttpRequest"
                        ),
                        timeout = 10
                    )
                    val respHtml = resp.text
                    val tabDoc = Jsoup.parse(respHtml, data)
                    tabDoc.select("iframe[src], embed[src], video[src], source[src], a[href]").forEach { el ->
                        val src = el.attr("src").ifBlank { el.attr("data-src") }.ifBlank { el.attr("href") }.trim()
                        if (src.isNotBlank() && !src.startsWith("#") && !src.startsWith("javascript:")) {
                            candidates.add(fixUrl(src))
                        }
                    }
                }
            }
        }

        // 4. Ekstrak pautan muat turun & safelinks daripada .gmr-download-list
        doc.select(".gmr-download-list a, #download a, a.button-shadow").forEach { a ->
            val href = a.attr("href").trim()
            if (href.isNotBlank()) {
                val clean = decodeSafelink(href)
                if (clean.isNotBlank()) {
                    candidates.add(clean)
                } else {
                    candidates.add(href)
                }
            }
        }

        // 5. Resolusi setiap calon server video
        for (candidate in candidates) {
            val resolved = resolveCandidate(candidate, data, subtitleCallback, callback)
            if (resolved) foundAny = true
        }

        return foundAny
    }

    private fun decodeSafelink(url: String): String {
        return runCatching {
            var current = url
            // Loop up to 3 times to unwrap nested safelinks
            for (i in 0 until 3) {
                if (current.contains("safelink=") || current.contains("wpsafelink=")) {
                    val b64 = if (current.contains("wpsafelink=")) {
                        current.substringAfter("wpsafelink=").substringBefore("&")
                    } else {
                        current.substringAfter("safelink=").substringBefore("&")
                    }
                    val decoded = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8).trim()
                    if (decoded.startsWith("http")) {
                        current = decoded
                    } else {
                        break
                    }
                } else {
                    break
                }
            }
            current
        }.getOrDefault(url)
    }

    private suspend fun resolveCandidate(
        url: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var handled = false
        val lower = url.lowercase()

        // 1. AbyssCDN / AbyssPlayer / Sora
        if (lower.contains("abysscdn.com") || lower.contains("abyssplayer.com") || lower.contains("abyss.to")) {
            runCatching {
                AbyssPlayer().getUrl(url, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            return handled
        }

        // 2. TurboVid
        if (lower.contains("turbovidhls.com") || lower.contains("emturbovid.com") || lower.contains("turbovid.eu")) {
            runCatching {
                TurboVidExtractor().getUrl(url, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            return handled
        }

        // 3. Morencius / StreamWish / Minochinos
        if (lower.contains("morencius.com") || lower.contains("minochinos.com") || lower.contains("streamwish.to") || lower.contains("embedwish.com")) {
            runCatching {
                StreamWishExtractor().getUrl(url, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            return handled
        }

        // 4. VidHide
        if (lower.contains("vidhidehub.com") || lower.contains("vidhide.com") || lower.contains("vidhidepro.com") || lower.contains("filelions.to")) {
            runCatching {
                VidHideExtractor().getUrl(url, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            return handled
        }

        // 5. Filemoon
        if (lower.contains("filemoon.to") || lower.contains("filemoon.sx") || lower.contains("filemoon.in")) {
            runCatching {
                FilemoonExtractor().getUrl(url, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            return handled
        }

        // 6. RPMVid P2P
        if (lower.contains("rpmvid.com")) {
            runCatching {
                RpmVidExtractor().getUrl(url, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            return handled
        }

        // 7. Direct MP4 / M3U8
        if (lower.contains(".m3u8")) {
            runCatching {
                generateM3u8(name, url, referer).forEach { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            return handled
        }

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

        // 8. Universal fallback to CloudStream's loadExtractor
        runCatching {
            loadExtractor(url, referer, subtitleCallback) { l ->
                callback.invoke(l)
                handled = true
            }
        }

        return handled
    }
}
