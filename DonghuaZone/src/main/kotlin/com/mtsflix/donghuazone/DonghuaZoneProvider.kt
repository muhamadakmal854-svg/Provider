package com.mtsflix.donghuazone

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import org.jsoup.Jsoup
import java.util.regex.Pattern

class DonghuaZoneProvider : MainAPI() {
    override var mainUrl = "https://www.donghuazone.com"
    override var name = "DonghuaZone"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "search/label/Latest?&max-results=15" to "Latest Episodes",
        "search/label/Ongoing?&max-results=15" to "Ongoing Donghua",
        "search/label/Completed?&max-results=15" to "Completed Donghua",
        "search/label/Movie?&max-results=15" to "Donghua Movies",
        "search/label/Action?&max-results=15" to "Action",
        "search/label/Adventure?&max-results=15" to "Adventure",
        "search/label/Fantasy?&max-results=15" to "Fantasy",
        "search/label/Cultivation?&max-results=15" to "Cultivation",
        "search/label/Martial%20Arts?&max-results=15" to "Martial Arts",
        "search/label/Romance?&max-results=15" to "Romance"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) {
            "$mainUrl/${request.data}"
        } else {
            "$mainUrl/${request.data}&page=$page"
        }
        
        val document = app.get(url).document
        val items = document.select("article, .post-outer-container, .post-outer, div.thumbnail")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElem = this.selectFirst("h2, h3, .post-title, .entry-title") ?: return null
        val title = titleElem.text().trim()
        if (title.isBlank()) return null

        val a = this.selectFirst("a[href]") ?: return null
        val href = fixUrl(a.attr("href"))

        val imgElem = this.selectFirst("img[src], img[data-src]")
        val posterUrl = imgElem?.let { it.attr("data-src").ifEmpty { it.attr("src") } }?.let { fixUrlNull(it) }

        val type = if (title.contains("Movie", true)) TvType.AnimeMovie else TvType.Anime

        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search?q=$query").document
        return document.select("article, .post-outer-container, .post-outer, div.thumbnail")
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.post-title, h1.entry-title, h1")?.text()?.trim() ?: return null
        
        val posterUrl = document.selectFirst(".post-body img, .entry-content img")?.attr("src")?.let { fixUrlNull(it) }
        val plot = document.selectFirst(".sinoposis p, .post-body p")?.text()?.trim()

        val isMovie = title.contains("Movie", true) || url.contains("Movie", true)

        val scriptText = document.select("script").joinToString("\n") { it.data() }
        val labelMatch = Pattern.compile("label_episode\\s*=\\s*["']([^"']+)["']").matcher(scriptText)
        
        val episodes = mutableListOf<Episode>()

        if (labelMatch.find()) {
            val rawLabel = labelMatch.group(1).split("/")[0].replace("_", " ")
            val feedUrl = "$mainUrl/feeds/posts/default/-/$rawLabel?alt=json&max-results=100"
            try {
                val jsonText = app.get(feedUrl).text
                val json = parseJson<BloggerFeedResponse>(jsonText)
                json.feed?.entry?.forEachIndexed { index, entry ->
                    val epTitle = entry.title?.t ?: "Episode ${index + 1}"
                    val epHref = entry.link?.firstOrNull { it.rel == "alternate" }?.href
                    if (!epHref.isNullOrBlank()) {
                        episodes.add(newEpisode(epHref) {
                            this.name = epTitle
                            this.episode = index + 1
                        })
                    }
                }
            } catch (e: Exception) {
                // Fallback
            }
        }

        if (episodes.isEmpty()) {
            episodes.add(newEpisode(url) {
                this.name = title
            })
        }

        return if (isMovie) {
            newMovieLoadResponse(title, url, TvType.AnimeMovie, url) {
                this.posterUrl = posterUrl
                this.plot = plot
            }
        } else {
            newAnimeLoadResponse(title, url, TvType.Anime) {
                this.posterUrl = posterUrl
                this.plot = plot
                addEpisodes(DubStatus.Subbed, episodes)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val html = document.html()

        val foundServers = mutableListOf<String>()

        val changeServerRegex = Pattern.compile("changeServer\\([^,]+,\\s*['"]([^'"]+)['"]\\)")
        val matcher = changeServerRegex.matcher(html)
        while (matcher.find()) {
            val serverUrl = matcher.group(1).trim()
            if (serverUrl.isNotBlank() && !foundServers.contains(serverUrl)) {
                foundServers.add(serverUrl)
            }
        }

        document.select("iframe").forEach { iframe ->
            val src = iframe.attr("data-src").ifEmpty { iframe.attr("src") }.trim()
            if (src.isNotBlank() && !foundServers.contains(src)) {
                foundServers.add(src)
            }
        }

        for (serverUrl in foundServers) {
            val fixedUrl = fixUrl(serverUrl)
            loadExtractor(fixedUrl, data, subtitleCallback, callback)
        }

        return foundServers.isNotEmpty()
    }

    data class BloggerFeedResponse(val feed: BloggerFeed?)
    data class BloggerFeed(val entry: List<BloggerEntry>?)
    data class BloggerEntry(val title: BloggerText?, val link: List<BloggerLink>?)
    data class BloggerText(val t: String?)
    data class BloggerLink(val rel: String?, val href: String?)
}
