package com.sad25kag.drakorkita

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
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder

class DrakorKita : MainAPI() {
    override var mainUrl = "https://drakorindo18.kita.baby"
    override var name = "DrakorKita"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.TvSeries, TvType.Movie)

    private val sourceHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        "Referer" to "$mainUrl/"
    )

    override val mainPage = mainPageOf(
        "all?media_type=movie"            to "Movie",
        "all?media_type=tv"               to "Series",
        "all?status=returning%20series"   to "Ongoing",
        "all?status=ended"                to "Complete",
        "all"                             to "All"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path = request.data
        val url = buildPagedUrl(path, page)
        val document = getDocument(url)
        val list = document.toSearchResults(request.name)

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = list,
                isHorizontalImages = false
            ),
            hasNext = list.isNotEmpty() && hasNextPage(document, page)
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = buildPagedUrl("all?q=$encoded", page)

        val results = runCatching {
            getDocument(url).toSearchResults("Search")
        }.getOrDefault(emptyList())

        return results.toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val document = getDocument(url)
        val title = document.pickTitle()
            .ifBlank { url.substringBeforeLast('/').substringAfterLast('/').replace('-', ' ') }
        val cleanTitle = cleanDetailTitle(title)
        val poster = document.pickPoster()
        val plot = document.pickDescription()
        val tags = document.pickTags()
        val year = Regex("""\((\d{4})\)""")
            .find(title)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        val infoText = document.select(".anf").text()

        // Strict Movie vs Series Classification
        val isMovie = url.contains("media_type=movie") ||
            url.contains("/movie/") ||
            infoText.contains("Type : Movie", ignoreCase = true) ||
            infoText.contains("Type: Movie", ignoreCase = true) ||
            infoText.contains("Type:  Movie", ignoreCase = true)

        val isSeries = url.contains("media_type=tv") ||
            url.contains("/tv/") ||
            infoText.contains("Type : TV Series", ignoreCase = true) ||
            infoText.contains("Type: TV Series", ignoreCase = true) ||
            infoText.contains("Type:  TV Series", ignoreCase = true) ||
            infoText.contains("Episode Count", ignoreCase = true) ||
            infoText.contains("Ongoing", ignoreCase = true) ||
            title.contains("Season", ignoreCase = true)

        // Extract total episode count
        val epCountMatch = Regex("""\[Episode\s*\d+\s*-\s*(\d+)\]""", RegexOption.IGNORE_CASE).find(title)
            ?: Regex("""Episode Count:\s*(\d+)""", RegexOption.IGNORE_CASE).find(infoText)
        val totalEpisodes = epCountMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1

        // Extract loadEpisode call parameters
        val loadEpMatch = Regex("""loadEpisode\s*\(\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"]""").find(document.html())
        val serverTag = if (loadEpMatch != null) {
            val (_, tag, ver) = loadEpMatch.destructured
            "${tag}_$ver"
        } else {
            "hs_ind"
        }

        val episodes = mutableListOf<com.lagradost.cloudstream3.Episode>()

        val isFinalTv = isSeries || (!isMovie && totalEpisodes > 1)

        if (isFinalTv) {
            val baseTrimmed = url.trimEnd('/')
            for (i in 1..totalEpisodes) {
                val epUrl = "$baseTrimmed/$serverTag/$i/"
                episodes.add(
                    newEpisode(epUrl) {
                        this.name = "Episode $i"
                        this.episode = i
                        this.posterUrl = poster
                    }
                )
            }
        } else {
            val movieEpUrl = "${url.trimEnd('/')}/$serverTag/"
            episodes.add(
                newEpisode(movieEpUrl) {
                    this.name = "Play Movie"
                    this.episode = 1
                    this.posterUrl = poster
                }
            )
        }

        return if (isFinalTv) {
            newTvSeriesLoadResponse(cleanTitle, url, TvType.AsianDrama, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        } else {
            val movieEpUrl = episodes.firstOrNull()?.data ?: url
            newMovieLoadResponse(cleanTitle, url, TvType.Movie, movieEpUrl) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val pageUrl = fixUrl(data)

        val urlCandidates = linkedSetOf(pageUrl)
        val baseTrimmed = pageUrl.trimEnd('/')

        // Generate candidate server URLs for TV and Movies
        if (!pageUrl.contains("/hs_") && !pageUrl.contains("/ss_")) {
            urlCandidates.add("$baseTrimmed/hs_ind/1/")
            urlCandidates.add("$baseTrimmed/ss_ind/1/")
            urlCandidates.add("$baseTrimmed/hs_ind/")
            urlCandidates.add("$baseTrimmed/ss_ind/")
        }

        var foundAny = false

        for (candidateUrl in urlCandidates) {
            runCatching {
                val doc = getDocument(candidateUrl)

                val resolved = DrakorKitaResolver.resolvePageLinks(
                    document = doc,
                    pageUrl = candidateUrl,
                    mainUrl = mainUrl,
                    headers = sourceHeaders,
                    subtitleCallback = subtitleCallback,
                    callback = callback
                )
                if (resolved) foundAny = true

                val embeds = DrakorKitaResolver.extractEmbedCandidates(doc, mainUrl)
                embeds.forEach { candidateEmbed ->
                    val cleanEmbed = DrakorKitaResolver.normalizeUrl(candidateEmbed, mainUrl)
                    if (cleanEmbed.isNotBlank()) {
                        val loaded = loadExtractor(cleanEmbed, candidateUrl, subtitleCallback, callback)
                        if (loaded) foundAny = true
                    }
                }
            }
            if (foundAny) break
        }

        return foundAny
    }

    private fun Document.toSearchResults(sectionName: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        val targetElements = select("a.poster, .content-item, .post-item, article, div.item, a[href*='/detail/']")

        targetElements.forEach { element ->
            val linkElement = if (element.tagName() == "a") element else element.selectFirst("a[href]") ?: return@forEach
            val href = linkElement.attr("href")
            val fullUrl = fixUrlNull(href) ?: return@forEach

            if (!isValidContentUrl(fullUrl)) return@forEach

            val rawTitle = element.selectFirst(".titit, .title, .entry-title, h2, h3, h4, .name")?.text()
                ?: linkElement.attr("title").ifBlank { linkElement.attr("alt") }
                ?: linkElement.text()

            val cleanTitle = cleanDetailTitle(rawTitle)
            if (cleanTitle.isBlank()) return@forEach

            // Filter out flagsapi.com or flag images!
            val imageElement = element.selectFirst("img.poster, img[src*='tmdb'], img:not([src*='flagsapi']):not([src*='flag'])")
                ?: linkElement.selectFirst("img.poster, img[src*='tmdb'], img:not([src*='flagsapi']):not([src*='flag'])")

            var poster = imageElement?.attr("data-src")
                ?.ifBlank { imageElement.attr("src") }
                ?.ifBlank { imageElement.attr("data-lazy-src") }
                ?.let { fixUrlNull(it) }

            if (poster != null && (poster.contains("flagsapi") || poster.contains("flag"))) {
                poster = null
            }

            // Strict isMovie check
            val isMovie = sectionName.equals("Movie", ignoreCase = true) ||
                fullUrl.contains("media_type=movie", ignoreCase = true) ||
                fullUrl.contains("/movie/", ignoreCase = true)

            val response = if (isMovie) {
                newMovieSearchResponse(cleanTitle, fullUrl, TvType.Movie) {
                    this.posterUrl = poster
                }
            } else {
                newTvSeriesSearchResponse(cleanTitle, fullUrl, TvType.AsianDrama) {
                    this.posterUrl = poster
                }
            }

            results.add(response)
        }

        return results.distinctBy { it.url }
    }

    private fun isValidContentUrl(url: String): Boolean {
        if (!url.startsWith("http")) return false
        val lower = url.lowercase()
        val blacklisted = listOf(
            "/category/", "/tag/", "/genre/", "/page/", "/dmca", "/about",
            "/contact", "/privacy", "/disclaimer", "/login", "/register", "/change_theme"
        )
        return blacklisted.none { lower.contains(it) }
    }

    private fun hasNextPage(doc: Document, currentPage: Int): Boolean {
        val nextLink = doc.selectFirst("a.next, a.next-page, a[rel=next], .pagination a:contains(Next), .pagination a:contains(>), li.next a")
        if (nextLink != null) return true
        val pageNumbers = doc.select(".pagination a, .nav-links a").mapNotNull {
            Regex("""\d+""").find(it.text())?.value?.toIntOrNull()
        }
        return pageNumbers.any { it > currentPage }
    }

    private fun buildPagedUrl(path: String, page: Int): String {
        val base = mainUrl.trimEnd('/')
        val cleanPath = path.trimStart('/')
        if (page <= 1) return if (cleanPath.isBlank()) base else "$base/$cleanPath"

        return when {
            cleanPath.isBlank() -> "$base/page/$page"
            cleanPath.contains("?") -> {
                val parts = cleanPath.split("?", limit = 2)
                "$base/${parts[0].trimEnd('/')}/page/$page?${parts[1]}"
            }
            else -> "$base/${cleanPath.trimEnd('/')}/page/$page"
        }
    }

    private fun Document.pickTitle(): String {
        return selectFirst("h1.entry-title, h1.title, h1.name, h1, .titit")?.text()
            ?: selectFirst("meta[property=og:title]")?.attr("content")
            ?: title()
    }

    private fun Document.pickPoster(): String? {
        val img = selectFirst("img.poster, img[src*='tmdb'], img:not([src*='flagsapi']):not([src*='flag'])")
        val src = img?.attr("data-src")
            ?.ifBlank { img.attr("src") }
            ?.ifBlank { img.attr("data-lazy-src") }

        val posterUrl = fixUrlNull(src) ?: fixUrlNull(selectFirst("meta[property=og:image]")?.attr("content"))
        if (posterUrl != null && (posterUrl.contains("flagsapi") || posterUrl.contains("flag"))) {
            return null
        }
        return posterUrl
    }

    private fun Document.pickDescription(): String? {
        return selectFirst(".entry-content p, .synopsis, .description, .desc, .mv-description")?.text()?.trim()
            ?.ifBlank { selectFirst("meta[property=og:description]")?.attr("content")?.trim() }
    }

    private fun Document.pickTags(): List<String> {
        return select(".genre a, .tag a, .category a, ul.anf a").map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun cleanDetailTitle(raw: String): String {
        return raw.replace(Regex("""(?i)\s*(nonton|subtitle|indo|sub|download|stream|hd|4k)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private suspend fun getDocument(url: String): Document {
        val res = app.get(url, headers = sourceHeaders)
        return Jsoup.parse(res.text, url)
    }
}
