package com.mts.indoxxi

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

class Indoxxi : MainAPI() {
    override var mainUrl = "https://indoxx1.one"
    override var name = "Indoxxi"
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

        fun decodeBase64Safe(input: String): String? {
            val clean = input.trim()
            if (clean.isBlank()) return null
            return try {
                val bytes = Base64.decode(clean, Base64.DEFAULT)
                val decoded = String(bytes, Charsets.UTF_8).trim()
                if (decoded.startsWith("http://") || decoded.startsWith("https://") || decoded.startsWith("//")) {
                    decoded
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    // ─── TAMPILAN HOMEPAGE GAYA NETFLIX ──────────────────────────────────────────
    override val mainPage = mainPageOf(
        "$mainUrl/top-imdb/" to "✨ Pilihan Utama (Spotlight / Trending 2026)",
        "$mainUrl/movies/" to "🔥 Film Terbaru (Update Harian)",
        "$mainUrl/series/" to "📺 TV Series & Drama Terkini",
        "$mainUrl/country/indonesia/" to "🎬 Sinema Indonesia Pilihan",
        "$mainUrl/country/south-korea/" to "🇰🇷 Drama & Film Korea (K-Drama)",
        "$mainUrl/country/china/" to "🐉 Drama Mandarin & Film China",
        "$mainUrl/country/japan/" to "🎎 J-Drama & Film Jepun",
        "$mainUrl/country/hong-kong/" to "🥋 Hong Kong Cinema & Action",
        "$mainUrl/country/united-states/" to "🇺🇸 Hollywood & Western Hits",
        "$mainUrl/genre/action/" to "💥 Aksi & Petualangan (Action)",
        "$mainUrl/genre/horror/" to "👻 Horor & Misteri (Horror)",
        "$mainUrl/genre/comedy/" to "😂 Komedi Pilihan (Comedy)",
        "$mainUrl/genre/romance/" to "💖 Percintaan & Romantik (Romance)",
        "$mainUrl/genre/science-fiction/" to "🚀 Fiksi Ilmiah (Sci-Fi & Fantasy)"
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

        val items = doc.select(".ml-item, article, div.item, .movie-item, .post-item")
        val searchResponses = items.mapNotNull { toSearchResult(it) }.distinctBy { it.url }
        return newHomePageResponse(request.name, searchResponses, hasNext = searchResponses.isNotEmpty())
    }

    private fun getPosterUrl(element: Element?): String? {
        if (element == null) return null
        val img = if (element.tagName().equals("img", true)) element else (element.selectFirst("img") ?: element)
        for (attr in listOf("data-src", "data-original", "data-lazy-src", "src")) {
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
            if (href.isBlank() || href == "$mainUrl/" || href.contains("/tag/") || href.contains("/genre/") || href.contains("/category/") || href.contains("/country/") || href.contains("/year/") || href.contains("/page/")) return null

            val img = element.selectFirst("img") ?: a.selectFirst("img")
            var rawTitle = element.selectFirst(".mli-info h2, .mli-info, h2, h3, .entry-title, .title, .movie-title")?.text()?.trim().orEmpty()
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
                .removePrefix("Permalink to ")
                .removePrefix("Download ")
                .removePrefix("Nonton Film ")
                .removePrefix("Nonton ")
                .split("Subtitle Indonesia")[0]
                .split("Sub Indo")[0]
                .split("Full Movie")[0]
                .split("Full Episode")[0]
                .trim()

            val poster = getPosterUrl(img ?: element)

            val isSeries = href.contains("/series/", ignoreCase = true) ||
                href.contains("/tv/", ignoreCase = true) ||
                href.contains("/eps/", ignoreCase = true) ||
                href.contains("-season-", ignoreCase = true) ||
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

        return doc.select(".ml-item, article, div.item, .movie-item, .post-item")
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

        val rawTitle = doc.selectFirst("h1[itemprop='name'], h1.entry-title, .entry-title, .title-content, h1")?.text()?.trim() ?: "Indoxxi"
        val cleanTitle = rawTitle
            .removePrefix("Permalink to ")
            .removePrefix("Nonton Film ")
            .removePrefix("Nonton ")
            .split("Subtitle Indonesia")[0]
            .split("Sub Indo")[0]
            .split("Full Movie")[0]
            .trim()

        val poster = getPosterUrl(doc.selectFirst("figure img, .poster img, .cover img, img[itemprop='image'], .mvic-thumb img, img[src*='poster'], img[src*='wp-content/uploads']"))

        val plot = doc.selectFirst(".desc, [itemprop='description'], .entry-content p, .synopsis p, .description p")?.text()?.trim()

        // Extract metadata fields
        var year: Int? = null
        var releaseDateStr: String? = null
        var releaseEpoch: Long? = null
        val genres = mutableListOf<String>()
        val actors = mutableListOf<String>()

        doc.select(".mvic-info p, .mvici-left p, .mvici-right p, .mvic-desc p, .list-inline li, .metadata div, .details div, .info div").forEach { row ->
            val text = row.text().trim()
            when {
                text.startsWith("Release Date:", ignoreCase = true) || text.startsWith("Rilis:", ignoreCase = true) || text.startsWith("Release:", ignoreCase = true) || text.startsWith("Tanggal Rilis:", ignoreCase = true) -> {
                    val dateVal = text.substringAfter(":").trim()
                    if (dateVal.isNotBlank()) {
                        releaseDateStr = dateVal
                        releaseEpoch = parseDateToEpoch(dateVal)
                    }
                }
                text.startsWith("Tahun:", ignoreCase = true) || text.startsWith("Year:", ignoreCase = true) -> {
                    if (year == null) {
                        year = Regex("""(\d{4})""").find(text)?.groupValues?.get(1)?.toIntOrNull()
                    }
                }
                text.startsWith("Genre:", ignoreCase = true) -> {
                    row.select("a").forEach { g -> genres.add(g.text().trim()) }
                }
                text.startsWith("Actors:", ignoreCase = true) || text.startsWith("Cast:", ignoreCase = true) || text.startsWith("Pemain:", ignoreCase = true) || text.startsWith("Bintang:", ignoreCase = true) -> {
                    row.select("a").forEach { a -> actors.add(a.text().trim()) }
                }
            }
        }

        if (year == null) {
            year = Regex("""(19\d{2}|20\d{2})""").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()
        }

        // Series Check & Watch Page Parsing
        val isTvSeries = url.contains("/series/", ignoreCase = true) ||
            url.contains("/tv/", ignoreCase = true) ||
            url.contains("-season-", ignoreCase = true) ||
            cleanTitle.contains("Season", ignoreCase = true)

        if (isTvSeries) {
            val watchUrl = if (url.endsWith("/watch") || url.endsWith("/watch/")) url else "${url.removeSuffix("/")}/watch"
            val watchDoc = try {
                app.get(
                    watchUrl,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to url
                    ),
                    timeout = 15
                ).document
            } catch (_: Exception) {
                null
            }

            val episodeElements = watchDoc?.select("a.btn-eps, [id*='episode-'], .server")
                ?: doc.select("a.btn-eps, [id*='episode-'], .server")

            if (episodeElements.isNotEmpty()) {
                val episodes = mutableListOf<Episode>()
                val seasonMatch = Regex("""(?i)(?:Season|S)\s*(\d+)""").find("$rawTitle $url")
                val seasonNum = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

                episodeElements.forEachIndexed { index, el ->
                    val epText = el.text().trim()
                    val epNum = epText.toIntOrNull() ?: (index + 1)
                    val rawIframe = el.attr("data-iframe").trim()
                    val decodedIframe = decodeBase64Safe(rawIframe)

                    val epDataUrl = if (!decodedIframe.isNullOrBlank()) {
                        decodedIframe
                    } else {
                        "$watchUrl#ep=$epNum"
                    }

                    episodes.add(
                        newEpisode(epDataUrl) {
                            this.name = "Episode $epNum"
                            this.season = seasonNum
                            this.episode = epNum
                            this.posterUrl = poster
                            this.date = releaseEpoch
                            this.description = if (!releaseDateStr.isNullOrBlank()) "Rilis: $releaseDateStr • Sub Indo" else "Season $seasonNum Episode $epNum • Sub Indo"
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
        }

        // Movie Load Response
        val playUrl = if (url.endsWith("/play") || url.endsWith("/play/")) url else "${url.removeSuffix("/")}/play"
        return newMovieLoadResponse(cleanTitle, url, TvType.Movie, playUrl) {
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
        val candidates = linkedSetOf<String>()

        // 1. If data itself is already an embed URL (e.g. Putarin, VidHide, etc.)
        if (cleanData.startsWith("http://") || cleanData.startsWith("https://") || cleanData.startsWith("//")) {
            val fixed = fixUrl(cleanData)
            if (fixed.contains("/e/") || fixed.contains("puterin") || fixed.contains("putarin") || fixed.contains("vidhide") || fixed.contains("streamwish")) {
                candidates.add(fixed)
            }
        }

        // 2. Visit target URL & /play / /watch endpoints
        val urlsToVisit = mutableListOf<String>()
        urlsToVisit.add(cleanData)
        if (!cleanData.endsWith("/play") && !cleanData.endsWith("/watch") && !cleanData.contains("/e/")) {
            urlsToVisit.add("${cleanData.removeSuffix("/")}/play")
            urlsToVisit.add("${cleanData.removeSuffix("/")}/watch")
        }

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

            // Direct Iframes & Embeds
            doc.select("iframe[src], embed[src], video[src], source[src]").forEach { el ->
                val src = el.attr("src").ifBlank { el.attr("data-src") }.trim()
                if (src.isNotBlank() && !src.contains("google.com") && !src.contains("youtube.com") && !src.contains("a-ads.com")) {
                    candidates.add(fixUrl(src))
                }
            }

            // Server Data & Base64 encoded buttons
            doc.select("[data-iframe], [data-drive], [data-mp4], [data-openload], [data-strgo], [data-url], a.btn-eps, .server").forEach { el ->
                for (attr in listOf("data-iframe", "data-drive", "data-mp4", "data-openload", "data-strgo", "data-url")) {
                    val rawVal = el.attr(attr).trim()
                    if (rawVal.isNotBlank()) {
                        val decoded = decodeBase64Safe(rawVal)
                        if (!decoded.isNullOrBlank()) {
                            candidates.add(fixUrl(decoded))
                        } else if (rawVal.startsWith("http") || rawVal.startsWith("//")) {
                            candidates.add(fixUrl(rawVal))
                        }
                    }
                }
            }

            // Download buttons
            doc.select(".download-btn, a[href*='download'], .dl-links a").forEach { btn ->
                val href = btn.attr("href").trim()
                if (href.isNotBlank() && !href.startsWith("#") && !href.startsWith("javascript:") && !href.contains("/tag/")) {
                    candidates.add(fixUrl(href))
                }
            }
        }

        // 3. Resolve all video server candidates
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

        // 1. Putarin / Puterin AES-256-GCM Player
        if (lower.contains("putarin.") || lower.contains("puterin.") || lower.contains("/e/") || lower.contains("/iembed/")) {
            runCatching {
                PutarinExtractor().getUrl(url, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 2. VidHide / Morencius / Callistanise
        if (lower.contains("vidhide") || lower.contains("morencius.com") || lower.contains("callistanise.com")) {
            runCatching {
                VidHideExtractor().getUrl(url, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 3. EfekStream (VIP Server)
        if (lower.contains("efek.stream")) {
            runCatching {
                EfekStream().getUrl(url, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 4. AbyssCDN / AbyssPlayer / Hydrax / Sora
        if (lower.contains("abysscdn.com") || lower.contains("abyssplayer.com") || lower.contains("abyss.to")) {
            runCatching {
                AbyssPlayer().getUrl(url, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 5. Byseq / Filemoon
        if (lower.contains("byseqekaho.com") || lower.contains("filemoon.to") || lower.contains("filemoon.sx") || lower.contains("filemoon.in")) {
            runCatching {
                ByseqExtractor().getUrl(url, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 6. StreamWish
        if (lower.contains("streamwish") || lower.contains("wishembed") || lower.contains("dwish")) {
            runCatching {
                StreamWishExtractor().getUrl(url, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 7. StreamP2P
        if (lower.contains("strp2p.site") || lower.contains("rpmvid.com")) {
            runCatching {
                StreamP2PExtractor().getUrl(url, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 8. Direct M3U8
        if (lower.contains(".m3u8")) {
            runCatching {
                generateM3u8(name, url, referer).forEach { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 9. Direct MP4
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

        // 10. Universal fallback to CloudStream's loadExtractor
        runCatching {
            loadExtractor(url, referer, subtitleCallback) { l ->
                callback.invoke(l)
                handled = true
            }
        }

        return handled
    }
}
