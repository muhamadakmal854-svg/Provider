package com.mts.pusatfilm21

import android.util.Base64
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TrailerData
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addQuality
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
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Pusatfilm21 : MainAPI() {
    override var mainUrl = "https://v4.pusatfilm21info.net"
    override var name = "Pusatfilm21"
    override val hasMainPage = true
    override var lang = "id"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    companion object {
        private const val UA_BROWSER =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    // Tampilan Homepage Gaya Netflix dengan 23 Kategori Lengkap
    override val mainPage = mainPageOf(
        "" to "✨ Pilihan Utama (Spotlight & Rekomendasi)",
        "trending/" to "🔥 Sedang Hangat & Trending",
        "best-rating/" to "⭐ Rating Tertinggi (Top Rated)",
        "tv/" to "📺 Siri TV & Drama Terkini (All Series)",
        "country/korea/" to "🇰🇷 Drama Korea Terhangat (K-Drama)",
        "country/china/" to "🇨🇳 Drama Mandarin Populer (C-Drama)",
        "country/japan/" to "🇯🇵 Siri & Drama Jepun (J-Drama)",
        "country/usa/" to "🇺🇸 Filem & Siri Barat (Western)",
        "country/indonesia/" to "🇮🇩 Sinema & Siri Indonesia",
        "country/thailand/" to "🇹🇭 Drama & Sinema Thailand",
        "country/india/" to "🇮🇳 Sinema Bollywood & India",
        "genre/action/" to "💥 Aksi & Suspen (Action)",
        "genre/horror/" to "👻 Horror & Misteri (Horror)",
        "genre/comedy/" to "😂 Komedi Santai (Comedy)",
        "genre/romance/" to "💖 Romantik & Cinta (Romance)",
        "genre/science-fiction/" to "🚀 Fiksyen Sains (Sci-Fi)",
        "genre/thriller/" to "🔪 Thriller & Ketegangan (Thriller)",
        "genre/crime/" to "🔍 Jenayah & Siasatan (Crime)",
        "genre/animation/" to "🎨 Animasi & Anime (Animation)",
        "genre/fantasy/" to "🔮 Fantasi Duniawi (Fantasy)",
        "genre/adventure/" to "🗺️ Kembara & Pengembaraan (Adventure)",
        "genre/drama/" to "🎭 Kisah Hidup & Emosi (Drama)",
        "year/2026/" to "📅 Rilisan Terkini 2026"
    )

    private fun cleanTitle(rawTitle: String?): String {
        if (rawTitle.isNullOrBlank()) return ""
        return rawTitle
            .replace(Regex("""(?i)^\s*(?:Nonton\s+(?:film|series)?|Streaming|Pusatfilm21)\s*"""), "")
            .replace(Regex("""(?i)\s+(?:streaming|sub\s*indo|subtitle\s*indonesia|gratis).*$"""), "")
            .trim()
    }

    private fun Element.getImageAttr(): String? {
        val srcset = attr("srcset").ifBlank { attr("data-srcset") }
        if (srcset.isNotBlank()) {
            val highest = srcset.split(",")
                .map { it.trim().substringBefore(" ") }
                .filter { it.isNotBlank() && !it.endsWith(".svg", true) && !it.endsWith(".gif", true) }
                .lastOrNull()
            if (!highest.isNullOrBlank()) return fixUrlNull(highest)
        }
        val src = attr("src").ifBlank {
            attr("data-src").ifBlank {
                attr("data-original").ifBlank {
                    attr("data-lazy-src")
                }
            }
        }
        return fixUrlNull(src)
    }

    private fun parseDateToEpoch(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        val clean = dateStr.trim()
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ssZZZZZ",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "dd MMM yyyy",
            "dd MMMM yyyy",
            "MMMM dd, yyyy",
            "dd/MM/yyyy",
            "dd-MM-yyyy"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.ENGLISH)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val d = sdf.parse(clean)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
        return null
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val pageUrl = if (request.data.isBlank()) {
            if (page <= 1) "$mainUrl/" else "$mainUrl/page/$page/"
        } else {
            val cleanData = request.data.trim().removePrefix("/").removeSuffix("/")
            if (page <= 1) "$mainUrl/$cleanData/" else "$mainUrl/$cleanData/page/$page/"
        }

        val document = app.get(pageUrl, headers = mapOf("User-Agent" to UA_BROWSER)).document
        val items = mutableListOf<SearchResponse>()

        // Spotlight & featured slider pada halaman utama
        if (request.data.isBlank() && page <= 1) {
            document.select("ul#featured-slider li.slider article, .slides article, .gmr-featured-slider article").forEach { el ->
                val a = el.selectFirst("figure a") ?: el.selectFirst("a") ?: return@forEach
                val href = a.attr("href") ?: return@forEach
                if (href.isBlank() || href == "#" || href.startsWith("javascript:")) return@forEach
                val rawTitle = el.selectFirst(".poster-title")?.text()
                    ?: a.attr("title")
                    ?: el.selectFirst("h3")?.text()
                    ?: el.selectFirst("img")?.attr("alt")
                val title = cleanTitle(rawTitle)
                val poster = el.selectFirst("img")?.getImageAttr()
                val isTv = href.contains("/tv/") || href.contains("/series/")

                if (title.isNotBlank()) {
                    if (isTv) {
                        items.add(newTvSeriesSearchResponse(title, fixUrl(href), TvType.TvSeries) {
                            this.posterUrl = poster
                        })
                    } else {
                        items.add(newMovieSearchResponse(title, fixUrl(href), TvType.Movie) {
                            this.posterUrl = poster
                        })
                    }
                }
            }
        }

        // Grid artikel catalog utama
        document.select("article.item-infinite, article.item, article").forEach { el ->
            val res = el.toSearchResult()
            if (res != null) items.add(res)
        }

        val distinctItems = items.distinctBy { it.url }
        return newHomePageResponse(
            HomePageList(
                name = request.name,
                list = distinctItems,
                isHorizontalImages = false
            ),
            hasNext = distinctItems.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleLink = selectFirst("h2.entry-title a, .entry-title a")
            ?: selectFirst(".content-thumbnail a, a[title]")
            ?: selectFirst("a")
            ?: return null

        val rawHref = titleLink.attr("href")
        if (rawHref.isBlank() || rawHref == "#" || rawHref.startsWith("javascript:")) return null
        val fullUrl = fixUrl(rawHref)

        val rawTitle = titleLink.attr("title").ifBlank { titleLink.text() }
        val title = cleanTitle(rawTitle).ifBlank {
            cleanTitle(selectFirst("img")?.attr("title") ?: selectFirst("img")?.attr("alt"))
        }
        if (title.isBlank()) return null

        val posterUrl = selectFirst(".content-thumbnail img, figure img, img")?.getImageAttr()
        val isTv = fullUrl.contains("/tv/") || fullUrl.contains("/series/") || fullUrl.contains("/eps/")

        val qualityText = selectFirst(".gmr-quality-item a, .gmr-post-quality, .quality")?.text()?.trim()

        return if (isTv) {
            newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries) {
                this.posterUrl = posterUrl
                if (!qualityText.isNullOrBlank()) addQuality(qualityText)
            }
        } else {
            newMovieSearchResponse(title, fullUrl, TvType.Movie) {
                this.posterUrl = posterUrl
                if (!qualityText.isNullOrBlank()) addQuality(qualityText)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val searchUrl = "$mainUrl/?s=${query.trim().replace(" ", "+")}"
            val doc = app.get(searchUrl, headers = mapOf("User-Agent" to UA_BROWSER)).document
            doc.select("article.item-infinite, article.item, article").mapNotNull { it.toSearchResult() }.distinctBy { it.url }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = mapOf("User-Agent" to UA_BROWSER)).document

        // Jika membuka laman episod (/eps/), cari pautan siri induk
        var parentDoc = document
        var parentUrl = url
        if (url.contains("/eps/")) {
            val seriesLink = document.selectFirst(".gmr-listseries a[href*='/tv/']")?.attr("href")
                ?: document.selectFirst(".entry-content a[href*='/tv/']")?.attr("href")
                ?: document.selectFirst("meta[property='og:url']")?.attr("content")
            if (!seriesLink.isNullOrBlank() && seriesLink.contains("/tv/")) {
                runCatching {
                    parentDoc = app.get(fixUrl(seriesLink), headers = mapOf("User-Agent" to UA_BROWSER)).document
                    parentUrl = fixUrl(seriesLink)
                }
            }
        }

        val rawTitle = parentDoc.selectFirst("h1.entry-title")?.text()?.trim()
            ?: document.selectFirst("h1.entry-title")?.text()?.trim()
            ?: ""
        val title = cleanTitle(rawTitle)

        val poster = parentDoc.selectFirst(".gmr-movie-data figure img, .content-thumbnail img, figure img")?.getImageAttr()
            ?: document.selectFirst(".gmr-movie-data figure img, .content-thumbnail img, figure img")?.getImageAttr()
            ?: parentDoc.selectFirst("meta[property='og:image']")?.attr("content")

        val plotText = parentDoc.selectFirst("div.entry-content[itemprop='description'] p")?.text()?.trim()
            ?: parentDoc.selectFirst("div.entry-content p")?.text()?.trim()
            ?: document.selectFirst("meta[property='og:description']")?.attr("content")

        val yearText = parentDoc.selectFirst(".gmr-moviedata a[href*='year']")?.text()?.toIntOrNull()
            ?: parentDoc.selectFirst("span:contains(Tahun:)")?.text()?.replace("Tahun:", "")?.trim()?.toIntOrNull()
            ?: Regex("""\b(19\d\d|20\d\d)\b""").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()

        val ratingText = parentDoc.selectFirst("[itemprop='ratingValue']")?.text()?.trim()
            ?: parentDoc.selectFirst(".gmr-rating-item")?.text()?.trim()
        val tagsList = parentDoc.select(".gmr-moviedata a[href*='genre']").map { it.text().trim() }
        val actorsList = parentDoc.select("[itemprop='actors'] a, .gmr-moviedata a[href*='actor']").map { it.text().trim() }
        val trailerUrl = parentDoc.selectFirst("a.gmr-trailer-popup")?.attr("href")
            ?: document.selectFirst("a.gmr-trailer-popup")?.attr("href")

        // Ekstrak tarikh rilis induk
        val releaseText = parentDoc.selectFirst("span:contains(Rilis:)")?.text()?.replace("Rilis:", "")?.trim()
            ?: parentDoc.selectFirst(".gmr-moviedata:contains(Rilis:)")?.text()?.replace("Rilis:", "")?.trim()
            ?: parentDoc.selectFirst("time[itemprop='dateCreated']")?.text()?.trim()
            ?: parentDoc.selectFirst("time.entry-date")?.text()?.trim()
        val defaultEpoch = parseDateToEpoch(
            parentDoc.selectFirst("time[itemprop='dateCreated']")?.attr("datetime")
                ?: parentDoc.selectFirst("time[itemprop='datePublished']")?.attr("datetime")
                ?: releaseText
        )

        // Cari senarai episod siri
        val seasonWraps = parentDoc.select(".season-accordion-wrap").ifEmpty {
            document.select(".season-accordion-wrap")
        }

        val allEpLinks = mutableListOf<Element>()
        if (seasonWraps.isNotEmpty()) {
            seasonWraps.forEach { wrap ->
                wrap.select("a.button.s-eps[href*='/eps/'], a[href*='/eps/']").forEach { epEl ->
                    if (!epEl.text().equals("Info", true) && !epEl.attr("href").contains("/tv/")) {
                        allEpLinks.add(epEl)
                    }
                }
            }
        } else {
            parentDoc.select(".gmr-listseries a[href*='/eps/'], a.button.s-eps[href*='/eps/'], a[href*='/eps/']").forEach { epEl ->
                if (!epEl.text().equals("Info", true) && !epEl.attr("href").contains("/tv/")) {
                    allEpLinks.add(epEl)
                }
            }
        }

        val isTvPage = url.contains("/tv/") || url.contains("/eps/") || url.contains("/series/") || parentDoc.select("body.single-tv, article[itemtype*='TVSeries']").isNotEmpty()
        val isSeries = allEpLinks.isNotEmpty() && isTvPage
        val type = if (isSeries) TvType.TvSeries else TvType.Movie

        if (isSeries) {
            val episodes = allEpLinks.distinctBy { it.attr("href") }.mapNotNull { el ->
                val epHref = el.attr("href")
                if (epHref.isBlank()) return@mapNotNull null
                val epTitleRaw = el.attr("title").removePrefix("Permalink ke ").trim()
                val cleanEpTitle = cleanTitle(epTitleRaw).ifBlank { el.text().trim() }

                val epNum = Regex("""(?:Episode|Eps|Ep)\s*(\d+)""", RegexOption.IGNORE_CASE).find(epTitleRaw)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""(\d+)""").find(el.text())?.groupValues?.get(1)?.toIntOrNull()
                    ?: 1

                val seasonNum = Regex("""(?:Season|S)\s*(\d+)""", RegexOption.IGNORE_CASE).find(epTitleRaw)?.groupValues?.get(1)?.toIntOrNull()
                    ?: 1

                val fullEpHref = fixUrl(epHref)

                newEpisode(fullEpHref) {
                    this.name = if (cleanEpTitle.startsWith("Episode", true)) cleanEpTitle else "Episode $epNum"
                    this.episode = epNum
                    this.season = seasonNum
                    this.posterUrl = poster
                    this.date = defaultEpoch
                    this.description = if (!releaseText.isNullOrBlank()) "Rilis: $releaseText • Sub Indo" else "Sub Indo"
                }
            }

            return newTvSeriesLoadResponse(title, url, type, episodes) {
                this.posterUrl = poster
                this.plot = plotText
                this.year = yearText
                this.score = Score.from10(ratingText)
                this.tags = tagsList
                this.actors = actorsList.map { ActorData(Actor(it)) }
                if (!trailerUrl.isNullOrBlank()) this.trailers.add(TrailerData(trailerUrl, null, false))
            }
        } else {
            return newMovieLoadResponse(title, url, type, url) {
                this.posterUrl = poster
                this.plot = plotText
                this.year = yearText
                this.score = Score.from10(ratingText)
                this.tags = tagsList
                this.actors = actorsList.map { ActorData(Actor(it)) }
                if (!trailerUrl.isNullOrBlank()) this.trailers.add(TrailerData(trailerUrl, null, false))
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

        if (data.contains("kotakajaib.me") || data.contains("playhydrax.com") || data.contains("emturbovid.com")) {
            iframes.add(data)
        } else {
            val document = app.get(data, headers = mapOf("User-Agent" to UA_BROWSER)).document
            val pageHtml = document.html()

            // 1. Kumpulkan iframe player dari halaman
            document.select(".gmr-embed-responsive iframe, #pembed iframe, iframe").forEach { ifr ->
                val src = ifr.attr("src").ifBlank { ifr.attr("data-src") }
                if (src.isNotBlank() && !src.contains("googletagmanager") && !src.contains("googleads")) {
                    iframes.add(src)
                }
            }

            // Regex iframe sekiranya tersembunyi
            Regex("""https?://kotakajaib\.me/(?:embed|file)/[a-zA-Z0-9]+""").findAll(pageHtml).forEach {
                iframes.add(it.value)
            }
            Regex("""https?://playhydrax\.com/\?v=[a-zA-Z0-9_-]+""").findAll(pageHtml).forEach {
                iframes.add(it.value)
            }
            Regex("""https?://emturbovid\.com/t/[a-zA-Z0-9_-]+""").findAll(pageHtml).forEach {
                iframes.add(it.value)
            }
        }

        coroutineScope {
            iframes.distinct().map { rawIframe ->
                async {
                    try {
                        val iframeUrl = fixUrl(rawIframe)
                        when {
                            iframeUrl.contains("kotakajaib.me") -> {
                                KotakajaibMe().getUrl(iframeUrl, data, subtitleCallback, callback)
                            }
                            iframeUrl.contains("playhydrax.com") -> {
                                Playhydrax().getUrl(iframeUrl, data, subtitleCallback, callback)
                            }
                            iframeUrl.contains("emturbovid.com") || iframeUrl.contains("turboviplay.com") -> {
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
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }.awaitAll()
        }

        return true
    }
}
