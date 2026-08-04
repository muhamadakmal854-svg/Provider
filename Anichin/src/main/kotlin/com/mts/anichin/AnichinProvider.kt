package com.mts.anichin

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class AnichinProvider : MainAPI() {
    companion object {
        var context: android.content.Context? = null
    }
    override var mainUrl = "https://anichin.moe"
    override var name = "Anichin"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime)

    override val mainPage = mainPageOf(
        "page/%d/" to "Terbaru",
        "ongoing/page/%d/" to "Ongoing",
        "completed/page/%d/" to "Completed",
        "schedule/" to "Jadwal Update"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.contains("%d")) {
            "${mainUrl}/${request.data.format(page)}"
        } else {
            "${mainUrl}/${request.data}"
        }

        val document = app.get(url).document
        val home = document.select("div.listupd article, div.bsx, article.bs, div.bs, .listupd .bsx")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = home.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = this.selectFirst("a") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        var title = a.attr("title").trim()
        if (title.isBlank()) {
            title = this.selectFirst("div.title, div.tt, h2, h3, .entry-title")?.text()?.trim().orEmpty()
        }
        if (title.isBlank()) {
            title = a.text().trim()
        }
        if (title.isBlank()) return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 1..3) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").document
            val results = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }
            if (results.isEmpty()) break
            searchResponse.addAll(results)
        }
        return searchResponse.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val cleanUrl = fixUrl(url)
        val document = app.get(cleanUrl).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim().toString()
        val poster = document.selectFirst("img.wp-post-image, div.ime > img, .thumb img")
            ?.attr("src")?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
        val description = document.selectFirst("div.entry-content, .synopse p")?.text()?.trim()
        val episodeList = document.select(".eplister li, .eplist li, ul.clstyle li")
        val hasPlayer = document.selectFirst("#pembed, .player-embed") != null

        // Direct episode page: has player but no episode list -> play directly as Movie
        if (episodeList.isEmpty() && hasPlayer) {
            return newMovieLoadResponse(title, cleanUrl, TvType.Anime, cleanUrl) {
                this.posterUrl = fixUrlNull(poster)
                this.plot = description
            }
        }

        val isMovie = document.selectFirst(".spe")?.text().orEmpty().contains("Movie", true)
        return if (isMovie) {
            val movieHref = document.selectFirst(".eplister li > a")?.attr("href")?.let { fixUrl(it) } ?: cleanUrl
            newMovieLoadResponse(title, movieHref, TvType.Movie, movieHref) {
                this.posterUrl = fixUrlNull(poster)
                this.plot = description
            }
        } else {
            val episodes = episodeList.map { ep ->
                val link = fixUrl(ep.selectFirst("a")?.attr("href").orEmpty())
                val epTitle = ep.selectFirst(".epl-title")?.text()?.trim().orEmpty()
                val epSub = ep.selectFirst(".epl-sub span")?.text()?.trim().orEmpty()
                val epDate = ep.selectFirst(".epl-date")?.text()?.trim().orEmpty()
                val cleanTitle = epTitle
                    .replace(Regex("""Subtitle\s*Indonesia""", RegexOption.IGNORE_CASE), "")
                    .trim()
                val epName = if (cleanTitle.isNotBlank()) "$cleanTitle $epSub".trim() else "Episode"
                val desc = if (epDate.isNotEmpty()) "Rilis: $epDate" else null
                newEpisode(link) {
                    this.name = epName
                    this.posterUrl = fixUrlNull(poster)
                    this.description = desc
                }
            }.reversed()

            newTvSeriesLoadResponse(title, cleanUrl, TvType.Anime, episodes) {
                this.posterUrl = fixUrlNull(poster)
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(fixUrl(data)).document
        val streamUrls = mutableSetOf<String>()

        // 1. Default iframe — may be relative /stream/... path
        val defaultIframeSrc = document.selectFirst("#pembed iframe, .player-embed iframe, #embed_holder iframe")?.attr("src")
        if (!defaultIframeSrc.isNullOrBlank()) {
            val abs = when {
                defaultIframeSrc.startsWith("http") -> defaultIframeSrc
                defaultIframeSrc.startsWith("//")   -> "https:$defaultIframeSrc"
                defaultIframeSrc.startsWith("/")    -> "$mainUrl$defaultIframeSrc"
                else -> null
            }
            if (abs != null) streamUrls.add(abs)
        }

        // 2. Mirror select options (base64 encoded or direct URLs)
        document.select(".mobius option, select.mirror option, select option[value], .mob-mirror option[value]").forEach { opt ->
            val value = opt.attr("value").trim()
            if (value.isBlank()) return@forEach
            when {
                value.startsWith("http") -> streamUrls.add(value)
                value.startsWith("//")   -> streamUrls.add("https:$value")
                else -> try {
                    val decoded = base64Decode(value)
                    val sub = Jsoup.parse(decoded)
                    val src = sub.selectFirst("iframe[src], [src]")?.attr("src") ?: return@forEach
                    val abs = when {
                        src.startsWith("http") -> src
                        src.startsWith("//")   -> "https:$src"
                        src.startsWith("/")    -> "$mainUrl$src"
                        else -> return@forEach
                    }
                    streamUrls.add(abs)
                } catch (_: Exception) {}
            }
        }

        // 3. Resolve each URL — /stream/ URLs need Referer to avoid 403
        streamUrls.forEach { streamUrl ->
            if (streamUrl.contains("anichin.moe/stream/")) {
                // Fetch the stream wrapper page with the episode page as Referer
                try {
                    val streamDoc = app.get(
                        streamUrl,
                        referer = data,
                        headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
                        )
                    ).document
                    val embedSrc = streamDoc.selectFirst("iframe[src]")?.attr("src") ?: return@forEach
                    val embedUrl = when {
                        embedSrc.startsWith("http") -> embedSrc
                        embedSrc.startsWith("//")   -> "https:$embedSrc"
                        else -> return@forEach
                    }
                    loadExtractor(embedUrl, streamUrl, subtitleCallback, callback)
                } catch (_: Exception) {}
            } else {
                loadExtractor(streamUrl, data, subtitleCallback, callback)
            }
        }

        return streamUrls.isNotEmpty()
    }

    private fun base64Decode(encoded: String): String {
        return try {
            String(java.util.Base64.getDecoder().decode(encoded.trim()), Charsets.UTF_8)
        } catch (_: Exception) {
            try {
                String(android.util.Base64.decode(encoded.trim(), android.util.Base64.DEFAULT), Charsets.UTF_8)
            } catch (_: Exception) {
                encoded
            }
        }
    }
}
