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
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

open class Rebahin : MainAPI() {
    override var mainUrl = "http://143.198.83.188"
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
    }

    // Tampilan Homepage Gaya Netflix dengan 23 Kategori Tematik Lengkap
    override val mainPage = mainPageOf(
        "" to "✨ Pilihan Utama (Spotlight & Rekomendasi)",
        "tv/" to "📺 Serial TV Terbaru (New TV Series)",
        "country/korea/" to "🇰🇷 Drama Korea Terhangat (K-Drama)",
        "country/china/" to "🇨🇳 Drama Mandarin Populer (C-Drama)",
        "country/japan/" to "🇯🇵 Sinema & Anime Jepang (J-Drama)",
        "country/thailand/" to "🇹🇭 Drama & Sinema Thailand",
        "country/india/" to "🇮🇳 Sinema Bollywood & India",
        "country/usa/" to "🇺🇸 Film & Serial Barat (Western)",
        "genre/action/" to "💥 Aksi & Laga Menegangkan (Action)",
        "genre/horror/" to "👻 Horor & Supranatural Seram (Horror)",
        "genre/adventure/" to "🗺️ Petualangan Epik (Adventure)",
        "genre/comedy/" to "😂 Komedi Lucu & Menghibur (Comedy)",
        "genre/crime/" to "🕵️ Kriminal & Siasatan (Crime)",
        "genre/drama/" to "🎭 Drama Kisah Kehidupan (Drama)",
        "genre/fantasy/" to "🔮 Fantasi & Dunia Sihir (Fantasy)",
        "genre/mystery/" to "🔍 Misteri & Teka-Teki (Mystery)",
        "genre/romance/" to "🌸 Romantis & Percintaan (Romance)",
        "genre/science-fiction/" to "🚀 Fiksyen Sains & Angkasa (Sci-Fi)",
        "genre/thriller/" to "🔪 Thriller Mendebarkan (Thriller)",
        "genre/animation/" to "🎨 Animasi & Film Kartun (Animation)",
        "genre/family/" to "👨‍👩‍👧‍👦 Tontonan Keluarga (Family)",
        "year/2026/" to "📅 Rilisan Terkini 2026",
        "year/2025/" to "📅 Rilisan Populer 2025"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) {
            if (request.data.isBlank()) "$mainUrl/" else "$mainUrl/${request.data}"
        } else {
            val cleanBase = if (request.data.isBlank()) "$mainUrl" else "$mainUrl/${request.data.removeSuffix("/")}"
            "$cleanBase/page/$page/"
        }

        val doc = app.get(
            url,
            headers = mapOf(
                "User-Agent" to UA_BROWSER,
                "Referer" to "$mainUrl/"
            )
        ).document

        val items = doc.select("article.item, article[class*='item']").mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val a = selectFirst("a[href]") ?: return null
        val href = a.attr("href")
        if (href.isBlank() || href == "#") return null

        val fullUrl = if (href.startsWith("http")) href else "$mainUrl$href"
        val isTv = href.contains("/tv/") || hasClass("category-tv") || hasClass("type-tv")

        val title = a.attr("title").ifBlank {
            selectFirst(".entry-title, h2, h3")?.text() ?: a.text()
        }.replace("Permalink to: ", "").replace("Permalink to ", "").replace("Nonton Film ", "").trim()

        if (title.isBlank() || title.equals("Rebahin", ignoreCase = true)) return null

        val img = selectFirst("img")
        var rawPoster = img?.attr("src")?.ifBlank { img.attr("data-src") }
        if (!rawPoster.isNullOrBlank()) {
            if (rawPoster.startsWith("//")) rawPoster = "http:$rawPoster"
            val srcset = img?.attr("srcset") ?: ""
            if (srcset.isNotBlank()) {
                val maxSrc = srcset.split(",").map { it.trim().substringBefore(" ") }.lastOrNull { it.isNotBlank() }
                if (!maxSrc.isNullOrBlank()) rawPoster = maxSrc
            }
            rawPoster = rawPoster.replace(Regex("""-\d+x\d+(\.[a-zA-Z]+)$"""), "$1")
        }

        val rating = selectFirst(".gmr-rating-item, .rating")?.text()?.replace(Regex("""[^\d.]"""), "")?.trim()
        val quality = selectFirst(".gmr-quality-item, .quality")?.text()?.trim()

        return if (isTv) {
            newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries) {
                this.posterUrl = rawPoster
                if (!rating.isNullOrBlank()) this.score = Score.from10(rating)
                if (!quality.isNullOrBlank()) addQuality(quality)
            }
        } else {
            newMovieSearchResponse(title, fullUrl, TvType.Movie) {
                this.posterUrl = rawPoster
                if (!rating.isNullOrBlank()) this.score = Score.from10(rating)
                if (!quality.isNullOrBlank()) addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/?s=$encoded"
        val doc = app.get(url, headers = mapOf("User-Agent" to UA_BROWSER)).document
        return doc.select("article.item, article[class*='item']").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = mapOf("User-Agent" to UA_BROWSER)).document

        val title = doc.selectFirst("h1.entry-title, h1")?.text()
            ?.replace(Regex("""(?i)^\s*Nonton\s+Film\s+"""), "")
            ?.replace(Regex("""(?i)\s+Streaming\s+Subtitle\s+Indonesia.*$"""), "")
            ?.replace(Regex("""(?i)\s*-\s*Rebahin.*$"""), "")
            ?.trim()
            ?: doc.title().replace(" - Rebahin", "").trim()

        val img = doc.selectFirst(".entry-content img, .attachment-post-thumbnail, .poster img")
        var rawPoster = img?.attr("src")?.ifBlank { img.attr("data-src") }
        if (!rawPoster.isNullOrBlank()) {
            if (rawPoster.startsWith("//")) rawPoster = "http:$rawPoster"
            val srcset = img?.attr("srcset") ?: ""
            if (srcset.isNotBlank()) {
                val maxSrc = srcset.split(",").map { it.trim().substringBefore(" ") }.lastOrNull { it.isNotBlank() }
                if (!maxSrc.isNullOrBlank()) rawPoster = maxSrc
            }
            rawPoster = rawPoster.replace(Regex("""-\d+x\d+(\.[a-zA-Z]+)$"""), "$1")
        }

        val plot = doc.selectFirst(".entry-content p, [itemprop='description']")?.text()
            ?: doc.selectFirst("meta[property='og:description']")?.attr("content")

        val yearText = doc.select(".gmr-moviedata").firstOrNull { it.text().contains("Year", true) }?.text()
            ?: doc.selectFirst("time[datetime], .year")?.text()
        val year = Regex("""\b(19\d\d|20\d\d)\b""").find(yearText ?: "")?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""\b(19\d\d|20\d\d)\b""").find(title)?.groupValues?.get(1)?.toIntOrNull()

        val ratingText = doc.selectFirst(".gmr-rating-item, [itemprop='ratingValue']")?.text()
            ?.replace(Regex("""[^\d.]"""), "")?.trim()

        val tags = doc.select(".gmr-movie-on a, a[rel='category tag']").map { it.text().trim() }.filter { it.isNotBlank() }

        val actors = doc.select(".gmr-moviedata").firstOrNull { it.text().contains("Cast", true) }
            ?.select("a")?.map { ActorData(Actor(it.text().trim())) } ?: emptyList()

        val epLinks = doc.select(".gmr-listseries a, a[href*='/eps/']").filter { !it.hasClass("gmr-all-serie") }
        val isTv = url.contains("/tv/") || epLinks.isNotEmpty()

        if (isTv && epLinks.isNotEmpty()) {
            val episodes = coroutineScope {
                epLinks.map { a ->
                    async {
                        val href = a.attr("href")
                        val rawTitle = a.attr("title").ifBlank { a.text() }
                        val cleanTitle = rawTitle.replace("Permalink to: ", "").replace("Permalink to ", "").replace("Nonton Film ", "").trim()

                        val seasonNum = Regex("""(?i)season-(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                            ?: Regex("""(?i)S(\d+)""").find(a.text())?.groupValues?.get(1)?.toIntOrNull() ?: 1
                        val epNum = Regex("""(?i)episode-(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                            ?: Regex("""(?i)Eps(\d+)""").find(a.text())?.groupValues?.get(1)?.toIntOrNull() ?: 1

                        var epDate: Long? = null
                        var epDesc: String? = null
                        var epPoster: String? = rawPoster

                        val epDoc = runCatching {
                            app.get(href, headers = mapOf("User-Agent" to UA_BROWSER), timeout = 8).document
                        }.getOrNull()

                        if (epDoc != null) {
                            val timeEl = epDoc.selectFirst("time[itemprop='dateCreated'], time[datetime], time.published")
                            val rawDate = timeEl?.attr("datetime")?.ifBlank { timeEl.text().trim() }
                            if (!rawDate.isNullOrBlank()) {
                                val dateIso = rawDate.substringBefore("T")
                                epDate = runCatching {
                                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                                    sdf.parse(dateIso)?.time
                                }.getOrNull() ?: runCatching {
                                    val sdf = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)
                                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                                    sdf.parse(rawDate)?.time
                                }.getOrNull()

                                epDesc = "Rilis: ${dateIso.ifBlank { rawDate }} • Sub Indo"
                            }

                            val descMeta = epDoc.selectFirst("meta[property='og:description'], meta[name='description']")?.attr("content")
                            if (!descMeta.isNullOrBlank()) {
                                epDesc = if (epDesc != null) "$epDesc\n$descMeta" else descMeta
                            }

                            val epImg = epDoc.selectFirst(".entry-content img, .attachment-post-thumbnail, .poster img")
                            val src = epImg?.attr("src")?.ifBlank { epImg.attr("data-src") }
                            if (!src.isNullOrBlank()) {
                                val fullSrc = if (src.startsWith("//")) "http:$src" else src
                                epPoster = fullSrc.replace(Regex("""-\d+x\d+(\.[a-zA-Z]+)$"""), "$1")
                            }
                        }

                        val epName = if (cleanTitle.isNotBlank() && !cleanTitle.startsWith("S1 Eps", ignoreCase = true)) {
                            cleanTitle
                        } else {
                            "Episode $epNum"
                        }

                        newEpisode(href) {
                            this.name = epName
                            this.season = seasonNum
                            this.episode = epNum
                            this.posterUrl = epPoster
                            this.date = epDate
                            this.description = epDesc ?: "Rilis: Terkini • Sub Indo"
                        }
                    }
                }.awaitAll()
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = rawPoster
                this.plot = plot
                this.year = year
                this.tags = tags
                this.actors = actors
                if (!ratingText.isNullOrBlank()) this.score = Score.from10(ratingText)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = rawPoster
                this.plot = plot
                this.year = year
                this.tags = tags
                this.actors = actors
                if (!ratingText.isNullOrBlank()) this.score = Score.from10(ratingText)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val iframes = mutableListOf<String>()
        val loadedUrls = mutableSetOf<String>()

        val doc = app.get(data, headers = mapOf("User-Agent" to UA_BROWSER)).document
        val pageHtml = doc.html()

        // 1. Direct iframe tags on page
        doc.select("iframe").forEach { ifr ->
            val src = ifr.attr("src").ifBlank { ifr.attr("data-src") }.trim()
            if (src.isNotBlank() && !src.contains("googletagmanager") && !src.contains("googleads") && !src.contains("histats")) {
                val fullSrc = if (src.startsWith("//")) "https:$src" else src
                iframes.add(fullSrc)
            }
        }

        // 2. Tab buttons for server 2, server 3, etc. (.muvipro-player-tabs a)
        val tabLinks = doc.select(".muvipro-player-tabs a, ul.tabs a, .server-wrapper a")
            .mapNotNull { a ->
                val href = a.attr("href").trim()
                if (href.isNotBlank() && (href.contains("?player=") || href.contains("&player="))) {
                    if (href.startsWith("http")) href else "$mainUrl$href"
                } else null
            }.distinct()

        if (tabLinks.isNotEmpty()) {
            coroutineScope {
                tabLinks.map { tabUrl ->
                    async {
                        runCatching {
                            val tabDoc = app.get(tabUrl, headers = mapOf("User-Agent" to UA_BROWSER), timeout = 8).document
                            tabDoc.select("iframe").forEach { ifr ->
                                val src = ifr.attr("src").ifBlank { ifr.attr("data-src") }.trim()
                                if (src.isNotBlank() && !src.contains("googletagmanager") && !src.contains("googleads") && !src.contains("histats")) {
                                    val fullSrc = if (src.startsWith("//")) "https:$src" else src
                                    synchronized(iframes) {
                                        iframes.add(fullSrc)
                                    }
                                }
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        // 3. Regex scan for embed links
        Regex("""https?://(?:morencius\.com|minochinos\.com|vidhidehub\.com|vidhide\.com|vidhidepro\.com|asnwish\.com|streamwish\.to|embedwish\.com|bestx\.stream)/[^\s"'<>\\]+""").findAll(pageHtml).forEach {
            iframes.add(it.value)
        }

        // 4. Process all collected iframes with extractors
        val allIframeTargets = mutableListOf<String>()
        iframes.distinct().forEach { rawIframe ->
            allIframeTargets.add(rawIframe)
            if (rawIframe.contains("vidhidehub.com") || rawIframe.contains("vidhide.com")) {
                allIframeTargets.add(rawIframe.replace("vidhidehub.com", "vidhidepro.com").replace("vidhide.com", "vidhidepro.com"))
            }
        }

        coroutineScope {
            allIframeTargets.distinct().map { rawIframe ->
                async {
                    val iframeUrl = fixUrl(rawIframe)
                    if (loadedUrls.add(iframeUrl)) {
                        runCatching {
                            when {
                                iframeUrl.contains("morencius.com") -> {
                                    MorenciusExtractor().getUrl(iframeUrl, data, subtitleCallback, callback)
                                }
                                iframeUrl.contains("minochinos.com") -> {
                                    MinochinosExtractor().getUrl(iframeUrl, data, subtitleCallback, callback)
                                }
                                iframeUrl.contains("asnwish.com") || iframeUrl.contains("streamwish") -> {
                                    AsnwishExtractor().getUrl(iframeUrl, data, subtitleCallback, callback)
                                }
                                iframeUrl.contains("vidhide") -> {
                                    VidHideExtractor().getUrl(iframeUrl, data, subtitleCallback, callback)
                                }
                                iframeUrl.contains("kotakajaib.me") -> {
                                    KotakajaibMe().getUrl(iframeUrl, data, subtitleCallback, callback)
                                }
                                iframeUrl.contains("playhydrax.com") -> {
                                    Playhydrax().getUrl(iframeUrl, data, subtitleCallback, callback)
                                }
                                iframeUrl.contains("emturbovid.com") -> {
                                    Emturbovid().getUrl(iframeUrl, data, subtitleCallback, callback)
                                }
                                iframeUrl.contains("gdriveplayer.to") -> {
                                    Gdriveplayer().getUrl(iframeUrl, data, subtitleCallback, callback)
                                }
                                iframeUrl.contains("abyss") -> {
                                    AbyssPlayer().getUrl(iframeUrl, data, subtitleCallback, callback)
                                }
                                else -> {
                                    loadExtractor(iframeUrl, data, subtitleCallback, callback)
                                }
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        // 5. Fallback loose m3u8 scan
        Regex("""https?://[^\s"'<>\\]+\.m3u8[^\s"'<>\\]*""").findAll(pageHtml).forEach { m ->
            val m3u8 = m.value.replace("\\u0026", "&")
            if (loadedUrls.add(m3u8)) {
                generateM3u8(name, m3u8, data).forEach(callback)
                callback.invoke(
                    newExtractorLink(name, "$name - Direct Stream", m3u8, ExtractorLinkType.M3U8) {
                        this.referer = data
                    }
                )
            }
        }

        return loadedUrls.isNotEmpty()
    }
}
