package com.mtsflix.chineseanime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import android.util.Base64
import java.util.regex.Pattern

class ChineseAnimeProvider : MainAPI() {
    override var mainUrl = "https://chineseanime.in"
    override var name = "ChineseAnime"
    override var lang = "zh"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Anime, TvType.Donghua)

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Latest Donghua",
        "$mainUrl/anime/?order=popular" to "Popular Series",
        "$mainUrl/anime/" to "New Series",
        "$mainUrl/genres/action/" to "Action",
        "$mainUrl/genres/adventure/" to "Adventure",
        "$mainUrl/genres/fantasy/" to "Fantasy",
        "$mainUrl/genres/martial-arts/" to "Martial Arts",
        "$mainUrl/genres/romance/" to "Romance",
        "$mainUrl/genres/comedy/" to "Comedy",
        "$mainUrl/genres/sci-fi/" to "Sci-Fi",
        "$mainUrl/genres/supernatural/" to "Supernatural"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data.removeSuffix("/")}/page/$page/"
        val doc = app.get(url).document
        val home = parseAnimeList(doc)
        return newHomePageResponse(request.name, home, hasNext = home.isNotEmpty())
    }

    private fun parseAnimeList(doc: Document): List<SearchResponse> {
        val list = mutableListOf<SearchResponse>()
        val elements = doc.select("article, .post, .bsx, .item, a[href*='/anime/']")
        val seen = mutableSetOf<String>()

        for (el in elements) {
            val linkEl = if (el.tagName() == "a") el else el.selectFirst("a[href]") ?: continue
            val href = fixUrl(linkEl.attr("href"))
            if (!href.contains("/anime/") && !href.contains("/20")) continue
            if (seen.contains(href)) continue
            seen.add(href)

            val titleEl = el.selectFirst("h1, h2, h3, h4, .title, .tt") ?: linkEl
            val title = titleEl.text().trim()
            if (title.isEmpty() || title.contains("Episode", ignoreCase = true) && !title.contains("Season", ignoreCase = true)) {
                // Skip plain episode links in general listing if not series
            }

            val imgEl = el.selectFirst("img")
            val poster = fixUrl(imgEl?.attr("data-src") ?: imgEl?.attr("src") ?: "")

            list.add(newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = poster
            })
        }
        return list
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val doc = app.get(url).document
        val list = mutableListOf<SearchResponse>()
        val seen = mutableSetOf<String>()

        for (el in doc.select("article, .post, .bsx, .item")) {
            val linkEl = el.selectFirst("a[href]") ?: continue
            val href = fixUrl(linkEl.attr("href"))
            if (seen.contains(href)) continue
            seen.add(href)

            val titleEl = el.selectFirst("h1, h2, h3, h4, .title") ?: linkEl
            val title = titleEl.text().trim()
            if (title.isEmpty()) continue

            val imgEl = el.selectFirst("img")
            val poster = fixUrl(imgEl?.attr("data-src") ?: imgEl?.attr("src") ?: "")

            list.add(newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = poster
            })
        }
        return list
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1, .entry-title, .title")?.text()?.trim() ?: "Chinese Anime"
        val poster = fixUrl(doc.selectFirst(".thumb img, .poster img, img")?.let { it.attr("data-src").ifEmpty { it.attr("src") } } ?: "")
        val description = doc.selectFirst(".entry-content, .description, .synopsis, p")?.text()?.trim()
        val genres = doc.select("a[href*='/genres/'], .genre a").map { it.text().trim() }

        val episodes = mutableListOf<Episode>()
        val epElements = doc.select(".episodes a, .eplister a, .episodelist a, .bobi-list a")
        
        if (epElements.isNotEmpty()) {
            for (ep in epElements) {
                val epHref = fixUrl(ep.attr("href"))
                val epTitle = ep.text().trim()
                val epNum = Regex("(\\d+)").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()
                episodes.add(newEpisode(epHref) {
                    this.name = epTitle
                    this.episode = epNum
                })
            }
        } else {
            // Single episode or current episode page acts as main detail
            episodes.add(newEpisode(url) {
                this.name = title
                this.episode = 1
            })
        }

        return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes.reversed()) {
            this.posterUrl = poster
            this.plot = description
            this.tags = genres
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        // Extract servers from <option value="BASE64_ENCODED_IFRAME">
        val options = doc.select("option[value], select option")
        var serverCount = 0

        for (opt in options) {
            val value = opt.attr("value").trim()
            val serverName = opt.text().trim().ifEmpty { "Server ${++serverCount}" }

            if (value.isEmpty() || value.contains("Select", ignoreCase = true)) continue

            try {
                val decoded = String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
                val matcher = Pattern.compile("src=[\"']([^\"']+)[\"']").matcher(decoded)
                if (matcher.find()) {
                    val iframeSrc = fixUrl(matcher.group(1))
                    loadExtractor(iframeSrc, data, subtitleCallback, callback)
                }
            } catch (e: Exception) {
                // If not base64, check if direct URL
                if (value.startsWith("http")) {
                    loadExtractor(value, data, subtitleCallback, callback)
                }
            }
        }

        // Also fallback to direct iframe on page if no options parsed
        doc.select("iframe[src]").forEach { iframe ->
            val src = fixUrl(iframe.attr("src"))
            if (!src.isEmpty() && !src.contains("facebook") && !src.contains("twitter")) {
                loadExtractor(src, data, subtitleCallback, callback)
            }
        }

        return true
    }
}
