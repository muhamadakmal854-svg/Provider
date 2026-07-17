package com.mts.kuramanime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element
import org.jsoup.Jsoup
import org.json.JSONObject
import org.json.JSONArray
import android.util.Log

class KuramanimeProvider : MainAPI() {
    override var mainUrl = "https://m2.kuramanime.ing"
    override var name = "Kuramanime"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override val mainPage = mainPageOf(
        "quick/upcoming?order_by=popular" to "Segera Tayang",
        "quick/movie?order_by=text" to "Film Layar Lebar",
        "playing" to "Filem Terbaru",
        "airing" to "TV Series Terbaru",
        "popular" to "Popular"
    )

    private fun Element.toSearchResult(): SearchResponse? {
        val a = if (this.tagName() == "a") this else this.selectFirst("a") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val title = this.selectFirst(".entry-title, h2.entry-title, h2, h3, .title, .film-name, .movie-title, h5")?.text()?.trim()
            ?: a.attr("title").trim().ifEmpty { a.text().trim() }
        if (title.isBlank()) return null
        val img = this.selectFirst("img")
        val posterUrl = img?.let { i ->
            listOf("data-src", "data-lazy-src", "src").map { i.attr(it) }.firstOrNull { it.isNotBlank() }
        }?.let { fixUrlNull(it) }

        val hrefLower = href.lowercase()
        val isTv = hrefLower.contains("/film-seri/") ||
                   hrefLower.contains("/tvshows/") ||
                   hrefLower.contains("/series/") ||
                   hrefLower.contains("/tv/") ||
                   hrefLower.contains("/ongoing/") ||
                   hrefLower.contains("/drakor/") ||
                   hrefLower.contains("/anime/")

        return if (isTv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path = request.data
        val cleanPath = path.removePrefix("/").removeSuffix("/")
        val pageUrl = if (path.startsWith("http")) {
            val separator = if (path.contains("?")) "&" else "?"
            path + if (page > 1) "${separator}page=$page" else ""
        } else {
            if (cleanPath.isEmpty()) {
                mainUrl + if (page > 1) "?page=$page" else ""
            } else {
                val separator = if (cleanPath.contains("?")) "&" else "?"
                val pagedPath = if (page > 1) "$cleanPath${separator}page=$page" else cleanPath
                "$mainUrl/$pagedPath"
            }
        }
        val document = app.get(pageUrl, headers = mapOf("Referer" to mainUrl), timeout = 30).document
        val homeList = document.select(".product__item, .listupd .bsx, .card, article, div.movie-item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, homeList, hasNext = homeList.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/anime?search=$query", headers = mapOf("Referer" to mainUrl), timeout = 30).document
        return document.select(".product__item, .listupd .bsx, .card, article, div.movie-item").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = mapOf("Referer" to mainUrl), timeout = 30).document
        val title = document.selectFirst(".sheader .data h1, h1.entry-title, .data h1, h1, .heading-name, .film-name, .anime__details__title h3")?.text()?.trim() ?: return null
        val poster = document.selectFirst(".poster img, img.wp-post-image, .thumb img, .anime__details__pic img")?.let { img ->
            listOf("data-src", "src").map { img.attr(it) }.firstOrNull { it.isNotBlank() }
        }?.let { fixUrl(it) }
        val plot = document.selectFirst(".description p, .entry-content p, .synops p, .anime__details__text p")?.text()?.trim() ?: ""
        val year = document.selectFirst(".date, .year, a[href*='/tahun/'], a[href*='/year/'], .anime__details__widget ul li:contains(Tipe) a")?.text()?.filter { it.isDigit() }?.toIntOrNull()
        val genres = document.select(".genres a, .genre a, a[href*='/kategori-film/'], .anime__details__widget ul li:contains(Genre) a").map { it.text().trim() }.filter { it.isNotBlank() }

        val isTv = url.contains("/film-seri/") ||
                   url.contains("/tvshows/") ||
                   url.contains("/series/") ||
                   url.contains("/tv/") ||
                   url.contains("/ongoing/") ||
                   url.contains("/drakor/") ||
                   url.contains("/anime/") ||
                   document.select(".post-page-numbers").isNotEmpty() ||
                   document.select(".anime__details__episodes").isNotEmpty()

        return if (isTv) {
            val episodes = mutableListOf<Episode>()
            val epElements = document.select(".anime__details__episodes a, .eplister ul li a, .episodelist ul li a, .ep-list li a")
            if (epElements.isNotEmpty()) {
                epElements.forEachIndexed { i, a ->
                    val epUrl = fixUrl(a.attr("href"))
                    val epText = a.text().trim()
                    episodes.add(newEpisode(epUrl) {
                        this.name = epText
                        this.episode = i + 1
                    })
                }
            } else {
                episodes.add(newEpisode(url) {
                    this.name = "Episode 1"
                    this.episode = 1
                })
            }
            
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = genres
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = genres
            }
        }
    }

    private fun generateRandomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false
        
        // 1. Fetch the main episode page
        val response = app.get(data, headers = mapOf("Referer" to mainUrl), timeout = 30)
        val document = response.document
        
        // Extract CSRF token
        val csrfToken = document.selectFirst("meta[name=csrf-token]")?.attr("content") ?: ""
        
        // Extract checkEp endpoint
        val checkEpUrl = document.selectFirst("input#checkEp")?.attr("value") ?: ""
        if (checkEpUrl.isBlank()) return false
        
        // Fetch page param
        val checkRes = app.get(checkEpUrl, headers = mapOf("Referer" to data, "X-Requested-With" to "XMLHttpRequest"), timeout = 15).text
        val pageParam = checkRes.replace("\"", "").trim()
        
        // Fetch token from custom endpoint using authorized headers
        val tokenUrl = "$mainUrl/assets/Ks6sqSgloPTlHMl.txt"
        val tokenHeaders = mapOf(
            "X-Fuck-ID" to "rFj8fp1nxMuNfKq:ijjAwj6Jze0kscx",
            "X-Request-ID" to generateRandomString(6),
            "X-Request-Index" to "0",
            "X-Requested-With" to "XMLHttpRequest",
            "X-CSRF-TOKEN" to csrfToken,
            "Referer" to data
        )
        val tokenRes = app.get(tokenUrl, headers = tokenHeaders, timeout = 15).text.trim()
        if (tokenRes.isBlank()) return false
        
        // Iterate through servers
        val servers = listOf("kuramadrive", "doodstream", "filemoon", "rpmshare", "streamp2p")
        var found = false
        
        for (s in servers) {
            val pageUrl = "$data?Ub3BzhijicHXZdv=$tokenRes&C2XAPerzX1BM7V9=$s&page=$pageParam"
            val pageHeaders = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "X-CSRF-TOKEN" to csrfToken,
                "Referer" to data,
                "Accept" to "text/html, */*; q=0.01"
            )
            
            // Send the POST request to emulate jQuery load() call with authorization parameters
            val postData = mapOf("authorization" to "kJuHHkaqcBFXiGMHQf6bJw8YAyDcwGD8Ur")
            val playerPageRes = app.post(pageUrl, headers = pageHeaders, data = postData, timeout = 30)
            if (!playerPageRes.isSuccessful) continue
            
            val playerDoc = Jsoup.parse(playerPageRes.text)
            
            // Check for direct video sources (e.g. kuramadrive)
            val videoSources = playerDoc.select("video source")
            if (videoSources.isNotEmpty()) {
                videoSources.forEach { source ->
                    val src = source.attr("src")
                    val label = source.attr("size").ifBlank { "720" }
                    if (src.isNotBlank()) {
                        callback(
                            newExtractorLink(
                                source = "Kuramadrive",
                                name = "Kuramadrive - ${label}p",
                                url = src,
                                type = ExtractorLinkType.VIDEO
                            ) {
                                this.referer = data
                                this.quality = when (label) {
                                    "360" -> Qualities.P360.value
                                    "480" -> Qualities.P480.value
                                    "720" -> Qualities.P720.value
                                    "1080" -> Qualities.P1080.value
                                    else -> Qualities.Unknown.value
                                }
                            }
                        )
                        found = true
                    }
                }
            } else {
                // Check for iframe sources (doodstream, filemoon, rpmshare, streamp2p)
                val iframe = playerDoc.selectFirst("iframe")
                val iframeSrc = iframe?.attr("src")
                if (!iframeSrc.isNullOrBlank()) {
                    found = loadExtractor(iframeSrc, data, subtitleCallback, callback) || found
                }
            }
        }
        return found
    }
}
