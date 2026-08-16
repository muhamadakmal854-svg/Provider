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
        "quick/ongoing?order_by=updated" to "Sedang Tayang",
        "quick/finished?order_by=updated" to "Selesai Tayang",
        "quick/movie?order_by=updated" to "Film Layar Lebar",
        "DYNAMIC_SEASON_MOST_VIEWED" to "Dilihat Terbanyak Musim Ini"
    )

    private fun Element.toSearchResult(): SearchResponse? {
        val a = if (this.tagName() == "a") this else this.selectFirst("a") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val title = this.selectFirst(".entry-title, h2.entry-title, h2, h3, .title, .film-name, .movie-title, h5")?.text()?.trim()
            ?: a.attr("title").trim().ifEmpty { a.text().trim() }
        if (title.isBlank()) return null
        
        val posterUrl = (this.selectFirst(".set-bg")?.attr("data-setbg")
            ?: this.selectFirst("[data-setbg]")?.attr("data-setbg")
            ?: this.selectFirst("img")?.let { i ->
                listOf("data-src", "data-lazy-src", "src").map { i.attr(it) }.firstOrNull { it.isNotBlank() }
            })?.let { fixUrlNull(it) }

        val hrefLower = href.lowercase()
        val isMovie = hrefLower.contains("/movie/") || this.selectFirst(".type, .label")?.text()?.lowercase()?.contains("movie") == true

        return if (isMovie) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path = request.data
        val pageUrl = if (path == "DYNAMIC_SEASON_MOST_VIEWED") {
            val homeDoc = app.get(mainUrl, headers = mapOf("Referer" to mainUrl), timeout = 30).document
            val targetLink = homeDoc.select("div, section").firstOrNull { 
                it.text().contains("Dilihat Terbanyak Musim Ini", ignoreCase = true) 
            }?.selectFirst("a[href*=season]")?.attr("href")
            
            val resolvedLink = if (!targetLink.isNullOrBlank()) {
                fixUrl(targetLink)
            } else {
                "$mainUrl/properties/season/summer-2026?order_by=most_viewed" // fallback
            }
            val separator = if (resolvedLink.contains("?")) "&" else "?"
            resolvedLink + if (page > 1) "${separator}page=$page" else ""
        } else {
            val cleanPath = path.removePrefix("/").removeSuffix("/")
            if (path.startsWith("http")) {
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
        val targetUrl = if (url.contains("/episode/")) {
            url.substringBefore("/episode/")
        } else {
            url
        }
        val document = app.get(targetUrl, headers = mapOf("Referer" to mainUrl), timeout = 30).document
        val title = document.selectFirst(".sheader .data h1, h1.entry-title, .data h1, h1, .heading-name, .film-name, .anime__details__title h3")?.text()?.trim() ?: return null
        
        val poster = (document.selectFirst(".anime__details__pic, .set-bg")?.attr("data-setbg")
            ?: document.selectFirst(".poster img, img.wp-post-image, .thumb img")?.let { img ->
                listOf("data-src", "src").map { img.attr(it) }.firstOrNull { it.isNotBlank() }
            })?.let { fixUrl(it) }

        val plot = document.selectFirst(".description p, .entry-content p, .synops p, .anime__details__text p")?.text()?.trim() ?: ""
        
        val year = document.select(".anime__details__widget ul li").firstOrNull { 
            it.text().contains("Tayang:") || it.text().contains("Musim:") 
        }?.text()?.filter { it.isDigit() }?.let {
            val idx = it.indexOf("20")
            if (idx != -1 && idx + 4 <= it.length) {
                it.substring(idx, idx + 4).toIntOrNull()
            } else if (it.length >= 4) {
                it.substring(0, 4).toIntOrNull()
            } else {
                null
            }
        }

        val genres = document.select(".anime__details__widget ul li a[href*=/genre/], .genres a, .genre a").map { it.text().trim() }.filter { it.isNotBlank() }

        val typeText = document.select(".anime__details__widget ul li").firstOrNull {
            it.text().contains("Tipe:")
        }?.text()?.lowercase() ?: ""
        val isMovie = typeText.contains("movie") || url.contains("/movie/")

        return if (isMovie) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = genres
            }
        } else {
            val episodes = mutableListOf<Episode>()
            val epListsEl = document.selectFirst("#episodeLists")
            val dataContent = epListsEl?.attr("data-content") ?: ""
            if (dataContent.isNotEmpty()) {
                val popoverDoc = Jsoup.parse(dataContent)
                popoverDoc.select("a[href]").forEachIndexed { i, a ->
                    val epUrl = fixUrl(a.attr("href"))
                    val epText = a.text().trim()
                    episodes.add(newEpisode(epUrl) {
                        this.name = "Episode $epText"
                        this.episode = epText.toIntOrNull() ?: (i + 1)
                    })
                }
            }
            if (episodes.isEmpty()) {
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
                }
            }
            if (episodes.isEmpty()) {
                episodes.add(newEpisode(url) {
                    this.name = "Episode 1"
                    this.episode = 1
                })
            }
            episodes.sortBy { it.episode }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
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

        // Dynamically parse data-kk and load its JS file to extract auth values
        val dataKk = document.selectFirst("[data-kk]")?.attr("data-kk") ?: ""
        if (dataKk.isBlank()) return false

        val jsResponse = app.get("$mainUrl/assets/js/$dataKk.js", headers = mapOf("Referer" to data), timeout = 15)
        if (!jsResponse.isSuccessful) return false
        val jsText = jsResponse.text

        // Extract MIX_AUTH_ROUTE_PARAM, MIX_AUTH_KEY, MIX_AUTH_TOKEN, etc.
        val authRouteParam = Regex("MIX_AUTH_ROUTE_PARAM\\s*:\\s*['\"]([^'\"]+)['\"]").find(jsText)?.groupValues?.get(1) ?: "Ks6sqSgloPTlHMl.txt"
        val authKey = Regex("MIX_AUTH_KEY\\s*:\\s*['\"]([^'\"]+)['\"]").find(jsText)?.groupValues?.get(1) ?: "rFj8fp1nxMuNfKq"
        val authToken = Regex("MIX_AUTH_TOKEN\\s*:\\s*['\"]([^'\"]+)['\"]").find(jsText)?.groupValues?.get(1) ?: "ijjAwj6Jze0kscx"
        val pageTokenKey = Regex("MIX_PAGE_TOKEN_KEY\\s*:\\s*['\"]([^'\"]+)['\"]").find(jsText)?.groupValues?.get(1) ?: "Ub3BzhijicHXZdv"
        val streamServerKey = Regex("MIX_STREAM_SERVER_KEY\\s*:\\s*['\"]([^'\"]+)['\"]").find(jsText)?.groupValues?.get(1) ?: "C2XAPerzX1BM7V9"
        
        // Fetch token from custom endpoint using dynamically parsed values
        val tokenUrl = "$mainUrl/assets/$authRouteParam"
        val tokenHeaders = mapOf(
            "X-Fuck-ID" to "$authKey:$authToken",
            "X-Request-ID" to generateRandomString(6),
            "X-Request-Index" to "0",
            "X-Requested-With" to "XMLHttpRequest",
            "X-CSRF-TOKEN" to csrfToken,
            "Referer" to data
        )
        val tokenRes = app.get(tokenUrl, headers = tokenHeaders, timeout = 15).text.trim()
        if (tokenRes.isBlank()) return false
        
        // Generate UUID session cookie
        val uuid = java.util.UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val vsid = "$uuid-$timestamp"
        
        // Iterate through servers
        val servers = listOf("kuramadrive", "doodstream", "filemoon", "rpmshare", "streamp2p")
        var found = false
        
        for (s in servers) {
            val pageUrl = "$data?$pageTokenKey=$tokenRes&$streamServerKey=$s&page=$pageParam"
            val pageHeaders = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "X-CSRF-TOKEN" to csrfToken,
                "Referer" to data,
                "Accept" to "text/html, */*; q=0.01"
            )
            
            // Send the POST request to emulate jQuery load() call with authorization parameters & cookies
            val postData = mapOf("authorization" to "kJuHHkaqcBFXiGMHQf6bJw8YAyDcwGD8Ur")
            val cookies = mapOf("kuramanime_vsid" to vsid, "preferred_stserver" to s)
            
            val playerPageRes = app.post(pageUrl, headers = pageHeaders, data = postData, cookies = cookies, timeout = 30)
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
