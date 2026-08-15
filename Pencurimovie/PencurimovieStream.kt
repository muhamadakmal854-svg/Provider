package com.mts.pencurimovie

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import android.util.Log

class PencurimovieProvider : MainAPI() {

    override var mainUrl        = "https://ww11.pencurimovie.sbs"
    override var name           = "Pencurimovie"
    override var lang           = "ms"
    override val hasMainPage    = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama, TvType.Anime, TvType.Cartoon)

    override val mainPage = mainPageOf(
        "genre/action/" to "Action",
        "genre/adventure/" to "Adventure",
        "genre/animation/" to "Animation",
        "genre/drama/" to "Drama",
        "genre/comedy/" to "Comedy",
        "genre/crime/" to "Crime",
        "genre/fantasy/" to "Fantasy",
        "genre/horror/" to "Horror",
        "genre/romance/" to "Romance",
        "genre/science-fiction/" to "Science Fiction",
        "country/malaysia/" to "Country Malaysia",
        "country/indonesia/" to "Country Indonesia",
        "country/indonesian/" to "Country Indonesian",
        "country/india/" to "Country India",
        "country/japan/" to "Country Japan",
        "country/thailand/" to "Country Thailand",
        "country/china/" to "Country China",
        "most-viewed/" to "Most Viewed",
        "most-rating/" to "Most Rating",
        "top-imdb/" to "Top IMDB",
        "genre/subbed/malay-subbed/" to "Subbed Malaysia",
        "genre/subbed/english/" to "Subbed English",
        "genre/subbed/indonesian/" to "Subbed Indonesian",
        "genre/dubbed/malay/" to "Dubbed Malay"
    )

    private fun Element.toSearchResult(): SearchResponse? {
        val a = (if (this.tagName() == "a") this else this.selectFirst("a[href*='.sbs/'], a")) ?: return null
        val href = a.attr("href").let { h -> if (h.startsWith("http")) h else "$mainUrl$h" }
        val img = this.selectFirst("img") ?: this.selectFirst("[data-original], [data-src], [data-lazy-src]")
        var title = this.selectFirst(".entry-title, h2, h3, .title, .mli-info, h2.entry-title")?.text()?.trim()
        if (title.isNullOrBlank()) {
            title = a.attr("title").trim().ifEmpty { img?.attr("alt")?.trim() ?: "" }
        }
        if (title.isBlank()) return null
        
        val poster = img?.let { getPosterUrl(it) }
        val isSeries = href.contains("/tv/") || href.contains("/tvshows/") || href.contains("/series/")
        
        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path = request.data
        val pageUrl = if (page == 1) {
            if (path.isEmpty()) "$mainUrl/" else "$mainUrl/$path"
        } else {
            val cleanPath = path.removeSuffix("/")
            if (cleanPath.isEmpty()) "$mainUrl/page/$page/" else "$mainUrl/$cleanPath/page/$page/"
        }
        val doc = app.get(pageUrl, headers = mapOf("Referer" to mainUrl, "User-Agent" to USER_AGENT), timeout = 30).document
        val items = doc.select(".ml-item, div.ml-item, .gmr-item-modulepost, .gmr-item-archivepost, article.item, article.post, .item, article").mapNotNull {
            it.toSearchResult()
        }.distinctBy { it.url }
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=${query.replace(" ", "+")}"
        val doc = app.get(searchUrl, headers = mapOf("Referer" to mainUrl, "User-Agent" to USER_AGENT), timeout = 30).document
        return doc.select(".ml-item, div.ml-item, .gmr-item-modulepost, .gmr-item-archivepost, article.item, article.post, .item, article").mapNotNull {
            it.toSearchResult()
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("Referer" to mainUrl, "User-Agent" to USER_AGENT), timeout = 30).document
        
        var title = doc.selectFirst("h1.entry-title, .entry-title, h1, .post-title, .title")?.text()?.trim()
        if (title.isNullOrBlank()) {
            title = doc.selectFirst("title")?.text()?.substringBefore("-")?.substringBefore("|")?.substringBefore("–")?.trim()
        }
        if (title.isNullOrBlank()) return null

        val poster = doc.selectFirst(".thumb img, .film-poster img, .entry-thumb img, .poster img, img[src*='uploads'], img[data-original], img[src*='tmdb']")?.let { getPosterUrl(it) }
        val plot = doc.selectFirst(".entry-content p, .synopsis p, .description p, div.post-content, p")?.text()?.trim()
        val genres = doc.select(".genxed a, .genre-info a, .film-genres a, .sgenres a, .categories a").map { it.text().trim() }
        
        val isSeries = url.contains("/tv/") || url.contains("/tvshows/") || url.contains("/series/") || doc.select(".eplister ul li a, #episode_by_temp li a").isNotEmpty()
        
        return if (isSeries) {
            val episodes = doc.select(".eplister ul li a, .episodelist ul li a, #episode_by_temp li a, ul.episodios li a").mapNotNull { a ->
                val epUrl = a.attr("href")
                val epName = a.selectFirst(".epl-num, .num-epi, span")?.text()?.trim() ?: a.text().trim()
                if (epUrl.isNotBlank()) newEpisode(epUrl) { this.name = epName } else null
            }.reversed()
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = genres
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = genres
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCdn: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var found = false
        val extractedUrls = mutableSetOf<String>()

        suspend fun processExtractorUrl(rawUrl: String) {
            val cleanUrl = fixUrl(rawUrl, data, mainUrl)
            if (cleanUrl.isBlank() || extractedUrls.contains(cleanUrl) || cleanUrl.startsWith("about:blank", true)) return
            extractedUrls.add(cleanUrl)

            val isAbyss = listOf("abyssplayer.com", "abyss.to", "abysscdn.com", "iamcdn.net", "sssrr").any { cleanUrl.contains(it, true) }
            val isStreamWish = listOf("streamwish", "mwish", "wishembed", "morencius", "filelinks", "strwish", "embedpyrox", "dsvplay", "hgcloud").any { cleanUrl.contains(it, true) }

            when {
                isAbyss -> {
                    try {
                        AbyssExtractor().getUrl(cleanUrl, data, subtitleCallback, callback)
                        found = true
                    } catch (e: Exception) {
                        Log.e("PencurimovieProvider", "AbyssExtractor error: ${e.message}")
                    }
                }
                isStreamWish -> {
                    try {
                        PencuriStreamWishExtractor().getUrl(cleanUrl, data, subtitleCallback, callback)
                        found = true
                    } catch (_: Exception) {
                        try {
                            loadExtractor(cleanUrl, data, subtitleCallback, callback)
                            found = true
                        } catch (_: Exception) {}
                    }
                }
                else -> {
                    try {
                        loadExtractor(cleanUrl, data, subtitleCallback, callback)
                        found = true
                    } catch (_: Exception) {}
                }
            }
        }

        try {
            val doc = app.get(data, headers = mapOf("Referer" to mainUrl, "User-Agent" to USER_AGENT), timeout = 30).document

            // 1. All direct iframes on page (data-src, src, data-lazy-src)
            doc.select("iframe").forEach { iframe ->
                val src = iframe.getIframeSrc()
                if (!src.isNullOrBlank()) {
                    processExtractorUrl(src)
                }
            }

            // 2. Server player tabs / links
            doc.select("ul.player-nav a, ul.muvipro-player-tabs a, div.playex a, a[data-src], div.gmr-embed-responsive iframe").forEach { el ->
                val src = el.getIframeSrc() ?: el.attr("href")
                if (!src.isNullOrBlank() && !src.startsWith("#")) {
                    processExtractorUrl(src)
                }
            }
        } catch (e: Exception) {
            Log.e("PencurimovieProvider", "Error in loadLinks: ${e.message}")
        }
        return found
    }
}
