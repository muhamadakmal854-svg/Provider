package com.miranime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Miranime : MainAPI() {
    override var mainUrl = "https://miranime.net"
    override var name = "Miranime"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        fun parseDateToEpoch(dateStr: String?): Long? {
            if (dateStr.isNullOrBlank()) return null
            val clean = dateStr.trim()
            var normalized = clean
                .replace("pukul", "", ignoreCase = true)
                .replace("Senin,", "", ignoreCase = true)
                .replace("Selasa,", "", ignoreCase = true)
                .replace("Rabu,", "", ignoreCase = true)
                .replace("Kamis,", "", ignoreCase = true)
                .replace("Jumat,", "", ignoreCase = true)
                .replace("Jum'at,", "", ignoreCase = true)
                .replace("Sabtu,", "", ignoreCase = true)
                .replace("Minggu,", "", ignoreCase = true)
                .replace("Januari", "January", ignoreCase = true)
                .replace("Februari", "February", ignoreCase = true)
                .replace("Maret", "March", ignoreCase = true)
                .replace("April", "April", ignoreCase = true)
                .replace("Mei", "May", ignoreCase = true)
                .replace("Juni", "June", ignoreCase = true)
                .replace("Juli", "July", ignoreCase = true)
                .replace("Agustus", "August", ignoreCase = true)
                .replace("September", "September", ignoreCase = true)
                .replace("Oktober", "October", ignoreCase = true)
                .replace("November", "November", ignoreCase = true)
                .replace("Desember", "December", ignoreCase = true)
                .replace(Regex("""\s+"""), " ")
                .trim()

            val formats = listOf(
                SimpleDateFormat("d MMMM yyyy HH:mm", Locale.US),
                SimpleDateFormat("d MMMM yyyy", Locale.US),
                SimpleDateFormat("d/M/yyyy", Locale.US),
                SimpleDateFormat("d-M-yyyy", Locale.US),
                SimpleDateFormat("yyyy-MM-dd", Locale.US)
            )

            for (fmt in formats) {
                try {
                    fmt.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
                    val d = fmt.parse(normalized)
                    if (d != null) return d.time
                } catch (_: Exception) {}
            }
            return null
        }
    }

    private fun toAbsoluteUrl(url: String): String {
        val clean = url.trim()
        if (clean.isBlank()) return ""
        return when {
            clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true) -> clean
            clean.startsWith("//") -> "https:$clean"
            clean.startsWith("/") -> "$mainUrl$clean"
            else -> "$mainUrl/$clean"
        }
    }

    private fun getPosterUrl(element: Element?): String? {
        if (element == null) return null
        val img = if (element.tagName().equals("img", true) || element.tagName().equals("source", true)) {
            element
        } else {
            element.selectFirst("img, picture source, [style*='background'], [style*='url']") ?: element
        }

        for (attr in listOf("src", "data-src", "data-lazy-src", "data-original", "srcset", "data-srcset", "content")) {
            var v = img.attr(attr).trim()
            if (v.isNotBlank() && !v.startsWith("data:image", true) && !v.startsWith("data:text", true)) {
                if (attr.contains("srcset")) {
                    v = v.split(",").lastOrNull()?.trim()?.substringBefore(" ")?.trim() ?: v
                }
                if (v.contains("/_next/image?url=")) {
                    val inner = Regex("""url=([^&]+)""").find(v)?.groupValues?.getOrNull(1)
                    if (!inner.isNullOrBlank()) {
                        try {
                            v = URLDecoder.decode(inner, "UTF-8")
                        } catch (_: Exception) {}
                    }
                }
                if (v.isNotBlank() && !v.startsWith("data:", true) && !v.contains("Logo", true)) {
                    return toAbsoluteUrl(v)
                }
            }
        }

        val style = img.attr("style").ifBlank { element.attr("style") }
        if (style.isNotBlank()) {
            val bgMatch = Regex("""url\(['"]?(.*?)['"]?\)""").find(style)
            if (bgMatch != null) {
                val bgUrl = bgMatch.groupValues[1].trim()
                if (bgUrl.isNotBlank() && !bgUrl.startsWith("data:", true) && !bgUrl.contains("Logo", true)) {
                    return toAbsoluteUrl(bgUrl)
                }
            }
        }

        return null
    }

    // ─── TAMPILAN HOMEPAGE GAYA NETFLIX ──────────────────────────────────────────
    override val mainPage = mainPageOf(
        "$mainUrl/" to "✨ Pilihan Utama (Spotlight)",
        "$mainUrl/ongoing-anime" to "🔥 Sedang Hangat (Trending Hari Ini)",
        "$mainUrl/ongoing-anime" to "⚡ Sedang Tayang (Anime Ongoing)",
        "$mainUrl/completed-anime" to "🏁 Siri Tamat (Completed Anime)",
        "$mainUrl/genre/action" to "💥 Aksi & Pengembaraan (Action)",
        "$mainUrl/genre/fantasy" to "🔮 Fantasi & Magis (Fantasy)",
        "$mainUrl/genre/isekai" to "⛩️ Isekai & Dunia Lain (Isekai)",
        "$mainUrl/genre/comedy" to "😂 Komedi & Santai (Comedy)",
        "$mainUrl/genre/romance" to "💖 Romantik & Percintaan (Romance)",
        "$mainUrl/genre/adventure" to "🌟 Pengembaraan Ajaib (Adventure)",
        "$mainUrl/genre/shounen" to "⚔️ Pertarungan Hebat (Shounen)",
        "$mainUrl/genre/sci-fi" to "🚀 Sains Fiksyen & Mecha (Sci-Fi)",
        "$mainUrl/genre/supernatural" to "👻 Misteri & Supernatural",
        "$mainUrl/genre/drama" to "🎭 Drama Pilihan (Drama)",
        "$mainUrl/genre/mystery" to "🔍 Penyiasatan & Misteri (Mystery)"
    )

    private fun isExcludedLink(href: String): Boolean {
        val path = href.removePrefix(mainUrl).trim()
        if (path.isBlank() || path == "/" || path.startsWith("#") || path.startsWith("javascript:")) return true
        val excludedPrefixes = listOf(
            "/genre", "/genres", "/daftar", "/ongoing", "/completed",
            "/contact", "/privacy", "/dmca", "/jadwal", "/masuk",
            "/search", "/admin", "/profil", "/lupa-password", "/reset-password", "/d/"
        )
        return excludedPrefixes.any { path.startsWith(it, ignoreCase = true) }
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        return try {
            val a = if (element.tagName().equals("a", true)) element else element.selectFirst("a[href]") ?: return null
            val href = toAbsoluteUrl(a.attr("href"))
            if (href.isBlank() || isExcludedLink(href)) return null

            val img = a.selectFirst("img") ?: element.selectFirst("img")
            var rawTitle = img?.attr("alt")?.trim().orEmpty()
            if (rawTitle.isBlank()) {
                rawTitle = a.attr("title").trim()
            }
            if (rawTitle.isBlank()) {
                rawTitle = element.selectFirst("h2, h3, h4, .title, p")?.text()?.trim().orEmpty()
            }
            if (rawTitle.isBlank()) {
                rawTitle = a.text().trim()
            }

            rawTitle = rawTitle.lines().firstOrNull()?.trim() ?: ""
            if (rawTitle.isBlank() || rawTitle.equals("Detail", true) || rawTitle.equals("Tonton Sekarang", true)) {
                val slug = href.removeSuffix("/").substringAfterLast("/")
                rawTitle = slug.replace("-sub-indo", "", ignoreCase = true)
                    .replace("-subtitle-indonesia", "", ignoreCase = true)
                    .replace("-", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
            }

            val poster = getPosterUrl(img ?: element)

            val isMovie = href.contains("/movie", true) || href.contains("-movie-", true)
            val type = if (isMovie) TvType.AnimeMovie else TvType.Anime

            val epText = element.selectFirst(".badge, .ep, [class*='badge'], span, div")?.text()?.trim()
            val epNum = Regex("""\b(\d+)\b""").find(epText.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()

            newAnimeSearchResponse(rawTitle, href, type) {
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
            request.data == "$mainUrl/" -> {
                if (page <= 1) "$mainUrl/" else "$mainUrl/ongoing-anime?page=$page"
            }
            page <= 1 -> request.data
            request.data.contains("?") -> "${request.data}&page=$page"
            else -> "${request.data}?page=$page"
        }

        val doc = try {
            app.get(targetUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).document
        } catch (_: Exception) {
            return null
        }

        val cards = doc.select("a[href]").mapNotNull {
            toSearchResult(it)
        }.distinctBy { it.url }

        return if (cards.isNotEmpty()) {
            newHomePageResponse(request.name, cards, hasNext = cards.size >= 10)
        } else {
            null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/search?keyword=$encodedQuery"

        val doc = try {
            app.get(searchUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).document
        } catch (_: Exception) {
            return emptyList()
        }

        return doc.select("a[href]").mapNotNull {
            toSearchResult(it)
        }.distinctBy { it.url }
    }

    // ─── DETAIL & CARD EPISOD BESERTA TARIKH RILIS ───────────────────────────────
    override suspend fun load(url: String): LoadResponse? {
        val fullUrl = toAbsoluteUrl(url)
        val res = app.get(fullUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/"))
        val html = res.text
        val doc = res.document

        val rawTitle = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property='og:title']")?.attr("content")?.trim()
            ?: fullUrl.removeSuffix("/").substringAfterLast("/").replace("-", " ")

        val title = rawTitle.replace("Nonton dan Download", "", ignoreCase = true)
            .replace("Nonton", "", ignoreCase = true)
            .replace("Sub Indo", "", ignoreCase = true)
            .replace("Subtitle Indonesia", "", ignoreCase = true)
            .replace("— Miranime", "", ignoreCase = true)
            .replace("- Miranime", "", ignoreCase = true)
            .replace("Miranime", "", ignoreCase = true)
            .trim()

        val poster = getPosterUrl(doc.selectFirst("meta[property='og:image'], img[src*='/covers/'], img[src*='image?url=']"))
            ?: doc.selectFirst("meta[property='og:image']")?.attr("content")?.let { toAbsoluteUrl(it) }
            ?: ""

        val plot = doc.select("p.text-muted-foreground, p").firstOrNull {
            it.text().trim().length > 30
        }?.text()?.trim().orEmpty().ifBlank {
            doc.selectFirst("meta[property='og:description']")?.attr("content")?.trim() ?: ""
        }

        val tags = doc.select("a[href*='/genre/']").map { it.text().trim() }.distinct()
        val year = doc.selectFirst(".year, .meta, span, div")?.text()?.let {
            Regex("""\b(19\d\d|20\d\d)\b""").find(it)?.groupValues?.get(1)?.toIntOrNull()
        }

        // Ekstrak Senarai Episod dengan Card dan Tarikh Rilis
        val epElements = doc.select("a[href*='/nonton/']")
        val episodes = mutableListOf<Episode>()

        for (el in epElements) {
            val href = toAbsoluteUrl(el.attr("href"))
            if (href.isBlank() || href == fullUrl || href == "$mainUrl/") continue

            val rawEpText = el.text().trim()
            // Abaikan butang hero 'Tonton Episode X' di atas halaman jika sudah ada episod sama
            if (rawEpText.startsWith("Tonton", ignoreCase = true) && episodes.any { it.data == href }) {
                continue
            }

            val epNum = Regex("""Episode\s*(\d+)""", RegexOption.IGNORE_CASE).find(rawEpText)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("""-episode-(\d+)""", RegexOption.IGNORE_CASE).find(href)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("""\b(\d+)\b""").find(rawEpText)?.groupValues?.getOrNull(1)?.toIntOrNull()

            // Tarikh Rilis dari tag <p> atau regex tarikh Indonesia
            val dateStr = el.selectFirst("p")?.text()?.trim()
                ?: Regex("""([A-Za-z]+,\s*\d+\s+[A-Za-z]+\s+\d{4}(?:\s+pukul\s+\d+:\d+)?)""").find(el.text())?.groupValues?.getOrNull(1)?.trim()
                ?: Regex("""\b(\d{1,2}/\d{1,2}/\d{4})\b""").find(el.text())?.groupValues?.getOrNull(1)?.trim()

            val releaseEpoch = parseDateToEpoch(dateStr)

            val epName = if (epNum != null) "Episode $epNum" else rawEpText.lines().firstOrNull()?.trim() ?: "Episode"

            episodes.add(
                newEpisode(href) {
                    this.name = epName
                    this.episode = epNum
                    this.season = 1
                    this.posterUrl = poster
                    this.date = releaseEpoch
                    this.description = if (!dateStr.isNullOrBlank()) "Rilis: $dateStr • Sub Indo" else "Episode ${epNum ?: 1} • Sub Indo"
                }
            )
        }

        val distinctEpisodes = episodes.distinctBy { it.data }
        val isMovie = distinctEpisodes.isEmpty() || fullUrl.contains("/movie", true) || fullUrl.contains("-movie-", true)

        return if (isMovie) {
            newMovieLoadResponse(title, fullUrl, TvType.AnimeMovie, fullUrl) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.year = year
            }
        } else {
            // Susun episod mengikut turutan menaik (Episode 1, 2, 3...)
            val sortedEpisodes = distinctEpisodes.sortedWith(
                compareBy({ it.season ?: 1 }, { it.episode ?: 0 })
            )

            newTvSeriesLoadResponse(title, fullUrl, TvType.Anime, sortedEpisodes) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.year = year
            }
        }
    }

    // ─── SCRAPING SEMUA SERVER VIDEO SECARA TEPAT ────────────────────────────────
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val pageUrl = toAbsoluteUrl(data)
        val res = try {
            app.get(pageUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/"))
        } catch (_: Exception) {
            return false
        }
        val html = res.text
        val doc = res.document

        var foundAny = false

        // 1. Ekstrak data 'sources' JSON dari Next.js RSC payload
        val sourcesMatches = Regex("""\\?"sources\\?"\s*:\s*(\[[\s\S]*?\])(?:,\s*\\?"[a-zA-Z0-9_-]+\\?"|\})""").findAll(html)
        for (m in sourcesMatches) {
            try {
                val rawSources = m.groupValues[1]
                    .replace("\\\"", "\"")
                    .replace("\\/", "/")
                    .replace("\\u0026", "&")
                    .replace("\\\\", "\\")
                val jsonArr = JSONArray(rawSources)
                for (i in 0 until jsonArr.length()) {
                    val srcObj = jsonArr.optJSONObject(i) ?: continue
                    val link = srcObj.optString("link", "").trim()
                    val reso = srcObj.optString("reso", "").trim()
                    val provider = srcObj.optString("provider", "Server").trim()

                    if (link.isNotBlank() && !link.startsWith("javascript:", true)) {
                        val quality = when {
                            reso.contains("1080", true) -> Qualities.P1080.value
                            reso.contains("720", true) -> Qualities.P720.value
                            reso.contains("480", true) -> Qualities.P480.value
                            reso.contains("360", true) -> Qualities.P360.value
                            else -> Qualities.Unknown.value
                        }

                        // A. GoogleDrive / Kuro-CDN Direct MP4
                        if (link.contains("kuro-cdn") || link.contains("workers.dev") || link.contains("kuroplay") || link.contains(".mp4", true) || link.contains(".m3u8", true)) {
                            val isM3u8 = link.contains(".m3u8", true)
                            callback(
                                newExtractorLink(
                                    source = this.name,
                                    name = "${this.name} - $provider $reso",
                                    url = link,
                                    type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = "$mainUrl/"
                                    this.quality = quality
                                }
                            )
                            foundAny = true
                        }
                        // B. AbyssPlayer (AES-CTR Encrypted Sora Stream)
                        else if (link.contains("abyssplayer.com", true) || link.contains("abyss.to", true)) {
                            try {
                                AbyssPlayer().getUrl(link, "$mainUrl/", subtitleCallback, callback)
                                foundAny = true
                            } catch (_: Exception) {}
                        }
                        // C. Luluvid / LuluStream
                        else if (link.contains("luluvid.com", true) || link.contains("luluvdo", true) || link.contains("lulustream", true)) {
                            try {
                                val normalizedLulu = link.replace("luluvid.com", "luluvdo.com")
                                loadExtractor(normalizedLulu, "$mainUrl/", subtitleCallback, callback)
                                foundAny = true
                            } catch (_: Exception) {
                                try {
                                    Luluvid().getUrl(link, "$mainUrl/", subtitleCallback, callback)
                                    foundAny = true
                                } catch (_: Exception) {}
                            }
                        }
                        // D. Krakenfiles
                        else if (link.contains("krakenfiles.com", true)) {
                            try {
                                KrakenfilesExtractor().getUrl(link, "$mainUrl/", subtitleCallback, callback)
                                foundAny = true
                            } catch (_: Exception) {}
                        }
                        // E. Telegram API Stream
                        else if (link.contains("api.miranime.net", true)) {
                            callback(
                                newExtractorLink(
                                    source = this.name,
                                    name = "${this.name} - $provider $reso",
                                    url = link,
                                    type = ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = "$mainUrl/"
                                    this.quality = quality
                                }
                            )
                            foundAny = true
                        }
                        // F. Fallback pelbagai extractor Cloudstream
                        else {
                            try {
                                loadExtractor(link, pageUrl, subtitleCallback, callback)
                                foundAny = true
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Ekstrak pautan muat turun & pautan terus dalam halaman
        doc.select("a[href*='mirrored.to'], a[href*='gofile.io'], a[href*='lulustream'], a[href*='luluvid'], a[href*='luluvdo'], a[href*='abyss'], a[href*='streamwish'], a[href*='filelions'], a[href*='krakenfiles.com']").forEach { a ->
            val href = a.attr("href").trim()
            if (href.startsWith("http", ignoreCase = true)) {
                if (href.contains("abyss", true)) {
                    try {
                        AbyssPlayer().getUrl(href, "$mainUrl/", subtitleCallback, callback)
                        foundAny = true
                    } catch (_: Exception) {}
                } else if (href.contains("krakenfiles.com", true)) {
                    try {
                        KrakenfilesExtractor().getUrl(href, "$mainUrl/", subtitleCallback, callback)
                        foundAny = true
                    } catch (_: Exception) {}
                } else if (href.contains("luluvid.com", true)) {
                    try {
                        val norm = href.replace("luluvid.com", "luluvdo.com")
                        loadExtractor(norm, "$mainUrl/", subtitleCallback, callback)
                        foundAny = true
                    } catch (_: Exception) {}
                } else {
                    try {
                        loadExtractor(href, pageUrl, subtitleCallback, callback)
                        foundAny = true
                    } catch (_: Exception) {}
                }
            }
        }

        // 3. Fallback iframe langsung
        doc.select("iframe[src], iframe[data-src]").forEach { ifr ->
            val src = toAbsoluteUrl(ifr.attr("src").ifBlank { ifr.attr("data-src") })
            if (src.isNotBlank() && !src.startsWith("about:") && !src.startsWith("javascript:")) {
                if (src.contains("abyss", true)) {
                    try {
                        AbyssPlayer().getUrl(src, "$mainUrl/", subtitleCallback, callback)
                        foundAny = true
                    } catch (_: Exception) {}
                } else {
                    try {
                        loadExtractor(src, pageUrl, subtitleCallback, callback)
                        foundAny = true
                    } catch (_: Exception) {}
                }
            }
        }

        return foundAny
    }
}
