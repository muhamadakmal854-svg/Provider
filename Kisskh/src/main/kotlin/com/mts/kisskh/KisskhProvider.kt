package com.mts.kisskh

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import org.json.JSONObject
import android.util.Log

class KisskhProvider : MainAPI() {
    override var mainUrl = "https://kisskh.buzz"
    override var name = "Kisskh"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "" to "Latest Update",
        "category/south-korean/" to "Top K-Drama",
        "category/china/" to "Top C-Drama",
        "category/ongoing/" to "Airing Now",
        "category/hollywood/" to "Hollywood",
        "category/upcoming/" to "Upcoming"
    )

    private fun Element.toSearchResult(): SearchResponse? {
        val a = if (this.tagName() == "a") this else this.selectFirst("a") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val img = this.selectFirst("img") ?: return null
        val posterUrl = img.let { i ->
            listOf("data-src", "data-lazy-src", "src").map { i.attr(it) }.firstOrNull { it.isNotBlank() }
        }?.let { fixUrlNull(it) }
        val title = this.selectFirst("h2, h3, .entry-title, .title")?.text()?.trim()
            ?: img.attr("alt").trim().ifEmpty { a.text().trim() }
        if (title.isBlank()) return null
        
        val isTv = href.contains("/series/") || href.contains("-episode-") || this.selectFirst(".episode") != null
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
        val pageUrl = if (path.isEmpty()) {
            if (page > 1) "$mainUrl/page/$page/" else "$mainUrl/"
        } else {
            if (page > 1) "$mainUrl/$path/page/$page/" else "$mainUrl/$path"
        }
        val document = app.get(pageUrl).document
        val homeList = document.select(".wp-block-post, .embla__slide, .embla__slide-card, article").mapNotNull {
            it.toSearchResult()
        }.distinctBy { it.url }
        return newHomePageResponse(request.name, homeList, hasNext = homeList.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select(".wp-block-post, .embla__slide, .embla__slide-card, article").mapNotNull {
            it.toSearchResult()
        }.distinctBy { it.url }
    }

    // Helper: fetch Blogger post content using org.json for reliable $t parsing
    private suspend fun fetchBloggerContent(blogId: String, postId: String): String? {
        return try {
            val apiUrl = "https://www.blogger.com/feeds/$blogId/posts/default/$postId?alt=json"
            val jsonText = app.get(apiUrl, headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36",
                "Accept" to "application/json, text/plain, */*",
                "Referer" to "$mainUrl/"
            )).text
            val obj = JSONObject(jsonText)
            val entryObj = obj.optJSONObject("entry") ?: return null
            val contentObj = entryObj.optJSONObject("content") ?: return null
            // Use the literal string "$t" - JSONObject handles $ in keys just fine
            val content = contentObj.optString("\$t", "")
            content.ifEmpty { null }
        } catch (e: Exception) {
            Log.e("Kisskh", "fetchBloggerContent error: ${e.message}")
            null
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.wp-block-post-title, h1.entry-title, h1")?.text()?.trim() ?: return null
        val poster = document.selectFirst(".wp-block-post-featured-image img, .poster img, img.wp-post-image")?.let { img ->
            listOf("data-src", "data-lazy-src", "src").map { img.attr(it) }.firstOrNull { it.isNotBlank() }
        }?.let { fixUrlNull(it) }
        val plot = document.selectFirst(".wp-block-post-content p, .entry-content p, .synops p, .description p")?.text()?.trim() ?: ""
        val genres = document.select("a[href*='/category/'], a[href*='/genre/']").map { it.text().trim() }.filter { it.isNotBlank() }
        
        val postIdRegex = Regex("""data-post-id=["'](\d+)["']""")
        val postId = postIdRegex.find(document.html())?.groupValues?.get(1)
        val blogIdRegex = Regex("""blogId":\s*\[\s*"(\d+)"\s*\]""")
        val blogId = blogIdRegex.find(document.html())?.groupValues?.get(1) ?: "1422331367239821646"
        
        if (postId.isNullOrBlank()) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = genres
            }
        }
        
        val content = fetchBloggerContent(blogId, postId) ?: ""
        
        val servers = content.split("{nextServer}")
        val server1Data = servers.firstOrNull() ?: ""
        val episodesList = server1Data.split(";").map { it.trim() }.filter { it.isNotBlank() && !it.contains("<img") }
        val numEpisodes = episodesList.size
        
        val urlLower = url.lowercase()
        val isTv = numEpisodes > 1
            || urlLower.contains("/series/")
            || urlLower.contains("episode=")
            || urlLower.contains("-episode-")
            || genres.any { g -> g.contains("drama", ignoreCase = true) || g.contains("series", ignoreCase = true) }
        
        return if (isTv) {
            val episodes = (1..numEpisodes).map { epNum ->
                newEpisode("$blogId|$postId|$epNum") {
                    this.episode = epNum
                    this.name = "Episode $epNum"
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = genres
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, "$blogId|$postId|1") {
                this.posterUrl = poster
                this.plot = plot
                this.tags = genres
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false
        if (data.startsWith("http")) {
            return loadExtractor(data, "$mainUrl/", subtitleCallback, callback)
        }
        
        val parts = data.split("|")
        if (parts.size < 3) return false
        val blogId = parts[0]
        val postId = parts[1]
        val episode = parts[2].toIntOrNull() ?: 1
        
        val content = fetchBloggerContent(blogId, postId) ?: return false
        
        val servers = content.split("{nextServer}")
        var found = false
        
        for ((serverIdx, serverData) in servers.withIndex()) {
            val lines = serverData.split(";").map { it.trim() }.filter { it.isNotBlank() && !it.contains("<img") }
            val epIdx = episode - 1
            if (epIdx < 0 || epIdx >= lines.size) continue
            
            val line = lines[epIdx]
            // Split by pipe - but only first 3 segments (videoUrl|labels|srtUrls)
            val firstPipe = line.indexOf('|')
            val secondPipe = if (firstPipe >= 0) line.indexOf('|', firstPipe + 1) else -1
            val videoUrl = (if (firstPipe >= 0) line.substring(0, firstPipe) else line).trim()
            if (videoUrl.isBlank()) continue
            
            val serverName = "Server ${serverIdx + 1}"
            
            // Parse subtitles - filter only valid HTTPS URLs
            val subtitleFiles = mutableListOf<SubtitleFile>()
            if (firstPipe >= 0 && secondPipe >= 0) {
                val labelsRaw = line.substring(firstPipe + 1, secondPipe)
                val urlsRaw = line.substring(secondPipe + 1)
                val labels = labelsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
                // Split srt URLs by comma, keep only valid http URLs
                val urls = urlsRaw.split(",").map { it.trim() }.filter { it.startsWith("http") }
                for (i in 0 until minOf(labels.size, urls.size)) {
                    val l = labels[i]
                    val u = urls[i]
                    if (l.isNotBlank() && u.isNotBlank()) {
                        subtitleFiles.add(SubtitleFile(l, u))
                    }
                }
            }
            
            if (videoUrl.contains(".m3u8") || videoUrl.contains(".mp4")) {
                val isM3u8 = videoUrl.contains(".m3u8")
                callback(
                    newExtractorLink(
                        source = name,
                        name = serverName,
                        url = videoUrl,
                        type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "$mainUrl/"
                        this.headers = mapOf("Origin" to mainUrl)
                    }
                )
                subtitleFiles.forEach { subtitleCallback(it) }
                found = true
            } else {
                found = loadExtractor(videoUrl, "$mainUrl/", subtitleCallback, callback) || found
            }
        }
        return found
    }
}
