package com.mtsflix.nontondrama

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import android.util.Log

class NontonDramaProvider : MainAPI() {
    override var mainUrl = "https://tv4.nontondrama.my"
    override var name = "NontonDrama"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "release/" to "Latest Update",
        "populer/" to "Terpopuler",
        "series/ongoing/" to "Ongoing Series",
        "series/complete/" to "Complete Series"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val pageUrl = if (page == 1) {
            "$mainUrl/${request.data}"
        } else {
            "$mainUrl/${request.data}page/$page/"
        }
        val document = app.get(pageUrl).document
        val items = document.select("article")
        val homeItems = items.mapNotNull { element ->
            val a = element.selectFirst("figure a") ?: element.selectFirst("a") ?: return@mapNotNull null
            val title = a.selectFirst(".poster-title")?.text() ?: a.selectFirst("h3")?.text() ?: a.attr("title") ?: ""
            val href = a.attr("href") ?: return@mapNotNull null
            val img = a.selectFirst("img")
            val poster = img?.attr("src") ?: img?.attr("data-src") ?: ""
            newMovieSearchResponse(title, fixUrl(href), TvType.TvSeries) {
                this.posterUrl = poster
            }
        }
        return newHomePageResponse(request.name, homeItems, hasNext = homeItems.isNotEmpty())
    }

    data class SearchItem(
        val title: String?,
        val slug: String?,
        val type: String?
    )
    
    data class GudangVapeSearchResponse(
        val total: Int?,
        val results: List<SearchItem>?
    )

    override suspend fun search(query: String): List<SearchResponse> {
        try {
            val response = app.get(
                "https://gudangvape.com/",
                params = mapOf("s" to query),
                headers = mapOf(
                    "Referer" to "$mainUrl/",
                    "Origin" to mainUrl
                )
            ).text
            val searchResults = tryParseJson<GudangVapeSearchResponse>(response)
            val results = searchResults?.results ?: emptyList()
            
            return results.amap { item: SearchItem ->
                val pageUrl = "$mainUrl/${item.slug}"
                val poster = try {
                    val doc = app.get(pageUrl).document
                    doc.selectFirst("meta[property=og:image]")?.attr("content")
                        ?: doc.selectFirst(".poster img")?.attr("src") ?: ""
                } catch(e: Exception) { "" }
                
                newMovieSearchResponse(item.title ?: "", pageUrl, TvType.TvSeries) {
                    this.posterUrl = poster
                }
            }
        } catch (e: Exception) {
            Log.e("NontonDrama", "Error in search: ${e.message}")
            return emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst(".entry-title, h1.entry-title")?.text()?.trim() ?: ""
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst(".poster img")?.attr("src") ?: ""
        val plot = document.selectFirst("meta[property=og:description]")?.attr("content")
            ?: document.selectFirst(".entry-content p, .synopsis p")?.text()?.trim() ?: ""

        val hasEpisodeNoScript = document.select("script").any { it.html().contains("episode_no") }
        
        val mainPageDoc = if (!hasEpisodeNoScript) {
            val mainPageUrl = document.select("a").firstOrNull { 
                val href = it.attr("href")
                href.isNotBlank() && !href.startsWith("http") && href.matches(Regex("/[^/]+-[0-9]{4}")) 
            }?.attr("href")
            if (mainPageUrl != null) app.get(fixUrl(mainPageUrl)).document else document
        } else {
            document
        }
        
        val episodes = mutableListOf<Episode>()
        val script = mainPageDoc.select("script").firstOrNull { it.html().contains("episode_no") }?.html()
        if (script != null) {
            try {
                val cleanScript = script.substringAfter("=").trim().substringBeforeLast(";")
                val jsonObject = org.json.JSONObject(cleanScript)
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val arr = jsonObject.optJSONArray(key) ?: continue
                    for (i in 0 until arr.length()) {
                        val epObj = arr.optJSONObject(i) ?: continue
                        val epNum = epObj.optInt("episode_no", 0)
                        val epSlug = epObj.optString("slug", "")
                        if (epSlug.isNotBlank()) {
                            episodes.add(
                                newEpisode("$mainUrl/$epSlug") {
                                    this.episode = epNum
                                    this.name = "Episode $epNum"
                                }
                            )
                        }
                    }
                }
            } catch(e: Exception) {
                Log.e("NontonDrama", "Error parsing episodes script: ${e.message}")
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.reversed()) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val document = app.get(data).document
            val links = document.select("#player-list li a, #player-select option")
            
            links.forEach { element ->
                val embedUrl = element.attr("data-url").ifBlank { element.attr("href") }.ifBlank { element.attr("value") }
                if (embedUrl.isNotBlank()) {
                    val cleanUrl = if (embedUrl.startsWith("//")) "https:$embedUrl" else embedUrl
                    val id = cleanUrl.substringAfterLast("/")
                    val resolvedUrl = when {
                        cleanUrl.contains("/p2p/") -> "https://cloud.hownetwork.xyz/video.php?id=$id"
                        cleanUrl.contains("/turbovip/") -> "https://emturbovid.com/t/$id"
                        cleanUrl.contains("/cast/") -> "https://gn1r5n.org/e/$id"
                        cleanUrl.contains("/hydrax/") -> "https://abyssplayer.com/$id"
                        else -> cleanUrl
                    }
                    loadExtractor(resolvedUrl, subtitleCallback, callback)
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("NontonDrama", "Error in loadLinks: ${e.message}")
            return false
        }
    }
}
