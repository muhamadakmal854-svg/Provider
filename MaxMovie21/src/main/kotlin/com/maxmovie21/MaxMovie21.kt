package com.maxmovie21

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

class MaxMovie21 : MainAPI() {
    override var mainUrl = "https://94.26.35.96"
    override var name = "MaxMovie21"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

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
                SimpleDateFormat("d MMMM, yyyy", Locale.US),
                SimpleDateFormat("MMMM d, yyyy", Locale.US),
                SimpleDateFormat("d MMM yyyy", Locale.US),
                SimpleDateFormat("d MMM, yyyy", Locale.US),
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
        "$mainUrl/year/2026/" to "✨ Pilihan Utama (Spotlight 2026)",
        "$mainUrl/year/2025/" to "🔥 Filem Terhebat 2025",
        "$mainUrl/year/2024/" to "⭐ Filem Pilihan 2024",
        "$mainUrl/dub-melayu/" to "🇲🇾 Koleksi Dubbing Melayu",
        "$mainUrl/tv-movie/" to "📺 TV Movie Pilihan",
        "$mainUrl/action/" to "💥 Aksi & Petualangan (Action)",
        "$mainUrl/horror/" to "👻 Horor & Misteri (Horror)",
        "$mainUrl/comedy/" to "😂 Komedi Terlucu (Comedy)",
        "$mainUrl/romance/" to "💖 Percintaan & Romantik (Romance)",
        "$mainUrl/thriller/" to "🔍 Thriller & Jenayah (Thriller)",
        "$mainUrl/science-fiction/" to "🚀 Fiksi Ilmiah (Sci-Fi)",
        "$mainUrl/animation/" to "⛩️ Animasi & Kartun (Animation)",
        "$mainUrl/drama/" to "🎭 Drama Pilihan (Drama)",
        "$mainUrl/fantasy/" to "🔮 Fantasi & Sihir (Fantasy)"
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

        val items = doc.select("article.item, article.has-post-thumbnail, div.gmr-box-item, article.item-infinite, .post-item, div.item, article")
        val searchResponses = items.mapNotNull { toSearchResult(it) }.distinctBy { it.url }
        return newHomePageResponse(request.name, searchResponses, hasNext = searchResponses.isNotEmpty())
    }

    private fun getPosterUrl(element: Element?): String? {
        if (element == null) return null
        val img = if (element.tagName().equals("img", true)) element else (element.selectFirst("img") ?: element)

        // Prioritas 1: srcset
        val srcset = img.attr("srcset").trim()
        if (srcset.isNotBlank()) {
            val best = srcset.split(",")
                .map { it.trim().split(" ")[0] }
                .lastOrNull { it.isNotBlank() }
            if (!best.isNullOrBlank()) {
                val clean = best.replace(Regex("""-\d+x\d+(?=\.(webp|jpg|jpeg|png))""", RegexOption.IGNORE_CASE), "")
                return fixUrlNull(clean)
            }
        }

        // Prioritas 2: data attributes
        for (attr in listOf("data-src", "data-lazy-src", "data-original", "src")) {
            val v = img.attr(attr).trim()
            if (v.isNotBlank() && !v.startsWith("data:") && (v.startsWith("http") || v.startsWith("//"))) {
                val clean = v.replace(Regex("""-\d+x\d+(?=\.(webp|jpg|jpeg|png))""", RegexOption.IGNORE_CASE), "")
                return fixUrlNull(clean)
            }
        }
        return null
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        return runCatching {
            val a = if (element.tagName().equals("a", true)) element else element.selectFirst("a[href]") ?: return null
            val rawHref = a.attr("href").trim()
            val href = fixUrlNull(rawHref) ?: return null
            if (href.isBlank() || href == "$mainUrl/" || href.contains("/tag/") || href.contains("/category/") || href.contains("/country/") || href.contains("/year/") || href.contains("/page/") || href.contains("/quality/") || href.contains("/director/") || href.contains("/cast/")) return null

            val img = element.selectFirst("img") ?: a.selectFirst("img")
            var rawTitle = element.selectFirst("h2.entry-title, h3.entry-title, h2, h3, .entry-title, .title, .movie-title")?.text()?.trim().orEmpty()
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
                .removePrefix("Permalink to: ")
                .removePrefix("Permalink to ")
                .removePrefix("Permalink ke ")
                .removePrefix("Permalink ke: ")
                .removePrefix("Download ")
                .removePrefix("Tonton Film ")
                .removePrefix("Tonton ")
                .removePrefix("Nonton Film ")
                .removePrefix("Nonton ")
                .split("Subtitle Indonesia")[0]
                .split("Sub Indo")[0]
                .split("Full Movie")[0]
                .split("Full Episode")[0]
                .trim()

            val poster = getPosterUrl(img ?: element)

            val typeText = element.selectFirst(".gmr-posttype-item")?.text()?.trim()
            val isSeries = typeText.equals("TV Show", ignoreCase = true) ||
                href.contains("/tv/", ignoreCase = true) ||
                href.contains("/series/", ignoreCase = true) ||
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

        return doc.select("article.item, article.has-post-thumbnail, div.gmr-box-item, article.item-infinite, .post-item, div.item, article")
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

        val rawTitle = doc.selectFirst("h1.entry-title, .entry-title, .title-content, h1")?.text()?.trim() ?: "MaxMovie21"
        val cleanTitle = rawTitle
            .removePrefix("Permalink ke ")
            .removePrefix("Permalink to ")
            .removePrefix("Tonton Film ")
            .removePrefix("Tonton ")
            .removePrefix("Nonton Film ")
            .removePrefix("Nonton ")
            .split("Subtitle Indonesia")[0]
            .split("Sub Indo")[0]
            .split("Full Movie")[0]
            .trim()

        val poster = getPosterUrl(doc.selectFirst("figure img, .gmr-poster-img img, .poster img, .cover img, img.wp-post-image, img.attachment-large, .thumb img"))

        val rawPlot = doc.selectFirst(".entry-content p, .description p, [itemprop='description'], .desc p, .synopsis, .gmr-movie-content")?.text()?.trim()
        val plot = rawPlot?.substringBefore("TONTON JUGA")?.substringBefore("Tonton juga")?.substringBefore("Tonton Juga")?.trim()

        // Extract metadata fields
        var year: Int? = null
        var releaseDateStr: String? = null
        var releaseEpoch: Long? = null
        val genres = mutableListOf<String>()
        val actors = mutableListOf<String>()

        doc.select(".gmr-moviedata, .list-inline li, .metadata div, .details div, .info div, .entry-content-meta span, .meta").forEach { row ->
            val text = row.text().trim()
            when {
                text.startsWith("Rilis:", ignoreCase = true) || text.startsWith("Release:", ignoreCase = true) || text.startsWith("Diposting pada:", ignoreCase = true) || text.startsWith("Tanggal Rilis:", ignoreCase = true) -> {
                    val dateVal = row.selectFirst("time")?.attr("datetime")
                        ?: row.selectFirst("time")?.text()
                        ?: text.substringAfter(":").trim()
                    if (releaseDateStr == null && dateVal.isNotBlank()) {
                        releaseDateStr = row.selectFirst("time")?.text()?.ifBlank { dateVal } ?: dateVal
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
                text.startsWith("Pemain:", ignoreCase = true) || text.startsWith("Cast:", ignoreCase = true) || text.startsWith("Actors:", ignoreCase = true) || text.startsWith("Bintang:", ignoreCase = true) -> {
                    row.select("a").forEach { a -> actors.add(a.text().trim()) }
                }
            }
        }

        if (year == null) {
            year = Regex("""(19\d{2}|20\d{2})""").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()
        }

        // Check if page contains season / episode blocks (Series / TV show)
        val seasonBlocks = doc.select("div.gmr-season-block, .season-list, .episodelst, .gmr-season-episodes, .episode-list")
        val allEpisodes = mutableListOf<Episode>()

        if (seasonBlocks.isNotEmpty()) {
            seasonBlocks.forEach { block ->
                val seasonTitle = block.selectFirst("h3.season-title, .season-title, h3")?.text()?.trim()
                val seasonNum = Regex("""(\d+)""").find(seasonTitle ?: "")?.groupValues?.get(1)?.toIntOrNull() ?: 1

                block.select("div.gmr-season-episodes a, a[href*='/eps/'], a[href*='/episode/']").forEachIndexed { index, epLink ->
                    val epHref = fixUrlNull(epLink.attr("href")) ?: return@forEachIndexed
                    val epText = epLink.text().trim()
                    val epTooltip = epLink.attr("title").trim()

                    if (epText.contains("Lihat Semua", ignoreCase = true) || epText.contains("View All", ignoreCase = true) || epText.contains("Batch", ignoreCase = true)) {
                        return@forEachIndexed
                    }

                    val epNumMatch = Regex("""(?i)(?:Episode|Eps|Ep|E)\s*(\d+)""").find("$epTooltip $epText $epHref")
                    val episodeNum = epNumMatch?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)

                    val epName = if (epTooltip.isNotBlank()) {
                        epTooltip.removePrefix("Permalink ke ").removePrefix("Permalink to ")
                    } else if (epText.isNotBlank() && !epText.startsWith("S", ignoreCase = true)) {
                        epText
                    } else {
                        "Episode $episodeNum"
                    }

                    allEpisodes.add(
                        newEpisode(epHref) {
                            this.name = epName
                            this.season = seasonNum
                            this.episode = episodeNum
                            this.posterUrl = poster
                            this.date = releaseEpoch
                            this.description = if (!releaseDateStr.isNullOrBlank()) "Rilis: $releaseDateStr • Sub Indo" else "Season $seasonNum Episode $episodeNum • Sub Indo"
                        }
                    )
                }
            }
        }

        val isTvSeries = allEpisodes.isNotEmpty() || url.contains("/tv/", ignoreCase = true) || url.contains("/series/", ignoreCase = true) || url.contains("/eps/", ignoreCase = true)

        if (isTvSeries && allEpisodes.isNotEmpty()) {
            val sortedEpisodes = allEpisodes.sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 0 }))
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
        val candidates = linkedSetOf<String>()
        val pagesToScrape = linkedSetOf(cleanData)

        val doc = try {
            app.get(
                cleanData,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/"
                ),
                timeout = 15
            ).document
        } catch (_: Exception) {
            return false
        }

        // 1. Ambil semua link server alternatif (?player=2, ?player=3, dsb)
        doc.select("a[href*='?player='], a[href*='&player='], a[href*='?server='], a[href*='&server='], .gmr-player-nav a").forEach { btn ->
            val href = btn.attr("href").trim()
            val fixedHref = fixUrlNull(href)
            if (fixedHref != null && fixedHref.isNotBlank() && !fixedHref.contains("javascript:") && !fixedHref.contains("#") && !fixedHref.contains("youtube.com") && !fixedHref.contains("youtu.be")) {
                pagesToScrape.add(fixedHref)
            }
        }

        // 2. Scrape iframe / embed dari setiap halaman server
        for (pageUrl in pagesToScrape) {
            val pageDoc = if (pageUrl == cleanData) doc else {
                try {
                    app.get(
                        pageUrl,
                        headers = mapOf(
                            "User-Agent" to USER_AGENT,
                            "Referer" to cleanData
                        ),
                        timeout = 10
                    ).document
                } catch (_: Exception) {
                    null
                }
            } ?: continue

            pageDoc.select("iframe[src], embed[src], video[src], source[src]").forEach { el ->
                val src = el.attr("src").ifBlank { el.attr("data-src") }.ifBlank { el.attr("data-litespeed-src") }.trim()
                if (src.isNotBlank() && !src.contains("google.com") && !src.contains("youtube.com") && !src.contains("youtu.be") && !src.contains("a-ads.com") && !src.contains("twitter.com") && !src.contains("whatsapp.com") && !src.contains("t.me")) {
                    candidates.add(fixUrl(src))
                }
            }

            // Regex scan pada seluruh HTML halaman untuk URL player
            val htmlText = pageDoc.html()
            val urlRegex = Regex("""(https?://[^\s"'<>]*(?:asiastream|playerp2p|strp2p|rpmvid|vidhide|morencius|callistanise|efek\.stream|filemoon|byseq|streamwish|embedpyrox|wishembed|hgcloud|upns|playmogo|fembed)[^\s"'<>]*)""", RegexOption.IGNORE_CASE)
            urlRegex.findAll(htmlText).forEach { match ->
                val u = match.groupValues[1].replace("\\/", "/").trim()
                if (!u.contains("wp-json") && !u.contains("twitter.com") && !u.contains("whatsapp.com") && !u.contains("t.me") && !u.contains("youtube.com")) {
                    candidates.add(u)
                }
            }

            // Muvipro Player Content Ajax
            val postId = pageDoc.selectFirst("div#muvipro_player_content_id, [data-id]")?.attr("data-id")
                ?: Regex("""postid-(\d+)""").find(pageDoc.body()?.className() ?: "")?.groupValues?.get(1)
            val tabs = pageDoc.select("div.tab-content-ajax, ul.muvipro-player-tabs li a, ul.muviprop-player-tabs li a, .gmr-player-nav li a")

            val tabIds = tabs.map { it.attr("id").ifBlank { it.attr("href").replace("#", "") }.ifBlank { it.attr("data-tab") }.trim() }
                .filter { it.isNotBlank() && !it.startsWith("http") && !it.startsWith("javascript:") && !it.contains("light") && !it.contains("comment") }
                .ifEmpty { listOf("p1", "p2", "player1") }

            if (!postId.isNullOrBlank()) {
                tabIds.forEach { tabId ->
                    try {
                        val responseDoc = app.post(
                            "$mainUrl/wp-admin/admin-ajax.php",
                            data = mapOf(
                                "action" to "muvipro_player_content",
                                "tab" to tabId,
                                "post_id" to postId
                            ),
                            headers = mapOf(
                                "User-Agent" to USER_AGENT,
                                "Referer" to pageUrl,
                                "X-Requested-With" to "XMLHttpRequest"
                            ),
                            timeout = 10
                        ).document

                        responseDoc.select("iframe[src], iframe[data-litespeed-src], iframe[data-src], embed[src]").forEach { ifr ->
                            val src = ifr.attr("src").ifBlank { ifr.attr("data-litespeed-src") }.ifBlank { ifr.attr("data-src") }.trim()
                            if (src.isNotBlank() && !src.contains("youtube.com")) {
                                candidates.add(fixUrl(src))
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Download buttons & other external video server links (exclude internal pages)
            pageDoc.select(".gmr-download-list a, .download-btn, a[href*='download'], a[href*='drive'], a[href*='stream']").forEach { btn ->
                val href = btn.attr("href").trim()
                val fixed = fixUrlNull(href)
                if (fixed != null && fixed.isNotBlank() && !fixed.startsWith("#") && !fixed.startsWith("javascript:") && !fixed.contains("/tag/") && !fixed.contains("/category/")) {
                    if (!fixed.startsWith(mainUrl) || fixed.contains(".mp4") || fixed.contains(".m3u8")) {
                        candidates.add(fixed)
                    }
                }
            }
        }

        // 3. Resolve every video server candidate
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
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank() || cleanUrl == "$mainUrl/" || cleanUrl.contains("wp-json") || cleanUrl.contains("google.com/analytics")) return false
        if (cleanUrl.startsWith(mainUrl) && !cleanUrl.contains(".m3u8") && !cleanUrl.contains(".mp4")) return false

        var handled = false
        val lower = cleanUrl.lowercase()

        // 1. AsiaStream (watch.asiastream.cc)
        if (lower.contains("asiastream")) {
            runCatching {
                AsiaStream().getUrl(cleanUrl, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 2. StreamP2P / PlayerP2P / LivePlayerP2P / Ewa
        if (lower.contains("playerp2p") || lower.contains("strp2p") || lower.contains("rpmvid") || lower.contains("p2pstream")) {
            runCatching {
                StreamP2PExtractor().getUrl(cleanUrl, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 3. AbyssCDN / AbyssPlayer / Hydrax / Sora
        if (lower.contains("abysscdn.com") || lower.contains("abyssplayer.com") || lower.contains("abyss.to")) {
            runCatching {
                AbyssPlayer().getUrl(cleanUrl, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 4. VidHide / Morencius / Callistanise
        if (lower.contains("vidhide") || lower.contains("morencius.com") || lower.contains("callistanise.com")) {
            runCatching {
                VidHideExtractor().getUrl(cleanUrl, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 5. EfekStream (VIP Server)
        if (lower.contains("efek.stream")) {
            runCatching {
                EfekStream().getUrl(cleanUrl, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 6. Byseq / Filemoon
        if (lower.contains("byseqekaho.com") || lower.contains("filemoon.to") || lower.contains("filemoon.sx") || lower.contains("filemoon.in")) {
            runCatching {
                ByseqExtractor().getUrl(cleanUrl, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 7. StreamWish / Hgcloud / Dm21 Upns
        if (lower.contains("streamwish") || lower.contains("wishembed") || lower.contains("hgcloud") || lower.contains("upns.live") || lower.contains("dwish")) {
            runCatching {
                StreamWishExtractor().getUrl(cleanUrl, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 8. EmbedPyrox
        if (lower.contains("embedpyrox.xyz")) {
            runCatching {
                EmbedPyroxExtractor().getUrl(cleanUrl, referer, subtitleCallback) { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            if (handled) return true
        }

        // 9. Direct M3U8
        if (lower.contains(".m3u8") || lower.contains("master.txt")) {
            val fixedM3u8 = cleanUrl.replace("master.txt", "master.m3u8")
            val defaultHeaders = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to referer
            )
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "$name (Auto)",
                    url = fixedM3u8,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = referer
                    this.quality = Qualities.Unknown.value
                    this.headers = defaultHeaders
                }
            )
            runCatching {
                generateM3u8(
                    source = name,
                    streamUrl = fixedM3u8,
                    referer = referer,
                    headers = defaultHeaders
                ).forEach { l ->
                    callback.invoke(l)
                    handled = true
                }
            }
            return true
        }

        // 10. Direct MP4
        if (lower.contains(".mp4")) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "$name MP4",
                    url = cleanUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = referer
                    this.quality = Qualities.P1080.value
                }
            )
            return true
        }

        // 11. Universal fallback to CloudStream's loadExtractor
        runCatching {
            loadExtractor(cleanUrl, referer, subtitleCallback) { l ->
                callback.invoke(l)
                handled = true
            }
        }

        return handled
    }
}
