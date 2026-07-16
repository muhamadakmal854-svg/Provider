package com.mts.juraganfilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import org.json.JSONObject
import android.util.Log

class JuraganfilmProvider : MainAPI() {
    override var mainUrl = "https://tv47.juragan.film"
    override var name = "Juraganfilm"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "movies/" to "Film Terbaru",
        "tvshows/" to "TV Series Terbaru",
        "genre/action/" to "Aksi",
        "genre/horror/" to "Seram",
        "genre/comedy/" to "Komedi"
    )

    private fun Element.toSearchResult(): SearchResponse? {
        val a = if (this.tagName() == "a") this else this.selectFirst("a") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val title = this.selectFirst(".entry-title, h2.entry-title, h2, h3, .title, .film-name, .movie-title")?.text()?.trim()
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
                   hrefLower.contains("/drama-serial/") ||
                   hrefLower.contains("/ongoing/") ||
                   hrefLower.contains("/drakor/")

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
        val pageUrl = if (page > 1) "$mainUrl/$path/page/$page/" else "$mainUrl/$path"
        val document = app.get(pageUrl).document
        val homeList = document.select(".listupd .bsx, .listupd .bs, .card, article, div.movie-item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, homeList, hasNext = homeList.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select(".listupd .bsx, .listupd .bs, .card, article, div.movie-item").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title, h1")?.text()?.trim() ?: return null
        val poster = document.selectFirst(".poster img, img.wp-post-image, .thumb img")?.let { img ->
            listOf("data-src", "src").map { img.attr(it) }.firstOrNull { it.isNotBlank() }
        }
        val plot = document.selectFirst(".description p, .entry-content p, .synops p")?.text()?.trim() ?: ""
        val year = document.selectFirst(".date, .year, a[href*='/tahun/'], a[href*='/year/']")?.text()?.filter { it.isDigit() }?.toIntOrNull()
        val genres = document.select(".genres a, .genre a, a[href*='/kategori-film/']").map { it.text().trim() }.filter { it.isNotBlank() }

        val isTv = url.contains("/film-seri/") ||
                   url.contains("/tvshows/") ||
                   url.contains("/series/") ||
                   url.contains("/tv/") ||
                   url.contains("/drama-serial/") ||
                   url.contains("/ongoing/") ||
                   url.contains("/drakor/") ||
                   document.select(".post-page-numbers").isNotEmpty()

        return if (isTv) {
            val episodes = mutableListOf<Episode>()
            episodes.add(newEpisode(url) {
                this.name = "Episode 1"
                this.episode = 1
            })

            document.select("a.post-page-numbers").forEach { a ->
                val href = a.attr("href")
                val num = a.text().trim().toIntOrNull()
                if (num != null && num > 1 && href.isNotBlank()) {
                    episodes.add(newEpisode(fixUrl(href)) {
                        this.name = "Episode $num"
                        this.episode = num
                    })
                }
            }
            episodes.sortBy { it.episode }
            
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

    data class SourceItem(
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("link") val link: String? = null
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false
        val document = app.get(data).document
        
        val iframeSrc = document.select("iframe[src*=/file/?id=]").firstOrNull()?.attr("src")
                     ?: document.select("iframe[id^=jf-frame-]").firstOrNull()?.attr("src")
                     ?: document.select(".gmr-embed-responsive iframe").firstOrNull()?.attr("src")
                     ?: return false

        val iframeUrl = fixUrl(iframeSrc)
        val headers = mapOf("Referer" to data, "User-Agent" to "Mozilla/5.0")
        val iframeResponse = app.get(iframeUrl, headers = headers).text
        
        val regex = Regex("""const\s+SOURCES\s*=\s*(\[.*?\])\s*;""")
        val match = regex.find(iframeResponse) ?: return false
        val jsonStr = match.groupValues[1]
        
        val sources = tryParseJson<List<SourceItem>>(jsonStr) ?: return false
        var found = false
        
        for (src in sources) {
            val link = src.link ?: continue
            if (link.contains("hotfile.my.id") || link.contains(".m3u8") || link.contains(".mp4")) {
                val isM3u8 = link.contains(".m3u8") || src.type == "hls"
                callback(
                    newExtractorLink(
                        source = name,
                        name = src.label ?: "Server",
                        url = link,
                        type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = iframeUrl
                    }
                )
                found = true
            } else {
                found = loadExtractor(link, iframeUrl, subtitleCallback, callback) || found
            }
        }
        return found
    }
}
