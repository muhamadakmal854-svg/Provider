package com.mts.anichin

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class AnichinProvider : MainAPI() {
    companion object {
        var context: android.content.Context? = null
    }
    override var mainUrl = "https://anichin.cafe"
    override var name = "Anichin"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime)

    override val mainPage = mainPageOf(
        "anime/?order=update" to "Latest Update",
        "anime/?status=ongoing&order=update" to "Series Ongoing",
        "anime/?status=completed&order=update" to "Series Completed",
        "anime/?status=hiatus&order=update" to "Series Drop/Hiatus",
        "anime/?type=movie&order=update" to "Movie"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${mainUrl}/${request.data}&page=$page").document
        val home = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("div.bsx > a").attr("title").trim()
        val href = fixUrl(this.select("div.bsx > a").attr("href"))
        val posterUrl = fixUrlNull(this.select("div.bsx > a img").attr("src"))
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
        val document = app.get(fixUrl(url)).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim().toString()
        var poster = document.select("div.ime > img").attr("src")
        val description = document.selectFirst("div.entry-content")?.text()?.trim()
        val type = document.selectFirst(".spe")?.text().orEmpty()
        val tvType = if (type.contains("Movie", true)) TvType.Movie else TvType.TvSeries
        if (poster.isEmpty()) {
            poster = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select(".eplister li, .eplist li, ul.clstyle li").map { ep ->
                val link = fixUrl(ep.selectFirst("a")?.attr("href").orEmpty())
                val epTitle = ep.selectFirst(".epl-title")?.text()?.trim().orEmpty()
                val epSub = ep.selectFirst(".epl-sub span")?.text()?.trim().orEmpty()
                val epDate = ep.selectFirst(".epl-date")?.text()?.trim().orEmpty()

                val cleanTitle = epTitle
                    .replace(Regex("Episode\\s*\\d+\\s*Subtitle Indonesia", RegexOption.IGNORE_CASE), "")
                    .replace("Subtitle Indonesia", "")
                    .trim()

                val name = "-- $cleanTitle $epSub Indonesia".trim()
                val desc = if (epDate.isNotEmpty()) "Rilis: $epDate" else null

                newEpisode(link) {
                    this.name = name
                    this.posterUrl = fixUrlNull(poster)
                    this.description = desc
                }
            }.reversed()

            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = fixUrlNull(poster)
                this.plot = description
            }
        } else {
            val movieHref = document.selectFirst(".eplister li > a")?.attr("href")?.let { fixUrl(it) } ?: url
            newMovieLoadResponse(title, movieHref, TvType.Movie, movieHref) {
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
        val extractedEmbeds = mutableSetOf<String>()

        // 1. Load default embedded iframe
        val defaultIframeSrc = document.selectFirst("#pembed iframe, .player-embed iframe, #embed_holder iframe")?.attr("src")
        if (!defaultIframeSrc.isNullOrBlank()) {
            val defaultHref = if (defaultIframeSrc.startsWith("//")) "https:$defaultIframeSrc" else defaultIframeSrc
            if (defaultHref.startsWith("http")) {
                extractedEmbeds.add(defaultHref)
            }
        }

        // 2. Process select options (both raw URLs and base64 encoded IFrames)
        document.select(".mobius option, select.mirror option, select option[value], .mob-mirror option[value]").forEach { server ->
            val value = server.attr("value").trim()
            if (value.isBlank()) return@forEach
            if (value.startsWith("http") || value.startsWith("//")) {
                val href = if (value.startsWith("//")) "https:$value" else value
                extractedEmbeds.add(href)
                return@forEach
            }
            try {
                val decoded = base64Decode(value)
                val doc = Jsoup.parse(decoded)
                val iframeSrc = doc.selectFirst("iframe")?.attr("src")
                    ?: doc.selectFirst("[src]")?.attr("src")
                if (!iframeSrc.isNullOrBlank()) {
                    val href = if (iframeSrc.startsWith("//")) "https:$iframeSrc"
                    else if (iframeSrc.startsWith("http")) iframeSrc
                    else return@forEach
                    extractedEmbeds.add(href)
                }
            } catch (_: Exception) {}
        }

        // 3. Process extracted embeds
        extractedEmbeds.forEach { href ->
            // Check anichin.stream ID
            val sidMatch = Regex("""(?:\?id=|/hls/)([\w\-]+)""").find(href)
            if (sidMatch != null) {
                val sid = sidMatch.groupValues[1]
                val m3u8Url = "https://anichin.stream/hls/$sid.m3u8"
                callback.invoke(
                    newExtractorLink(
                        name = "Anichin Stream",
                        source = "Anichin Stream",
                        url = m3u8Url,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://anichin.stream/"
                        this.quality = Qualities.Unknown.value
                    }
                )
            }

            // Also load built-in extractors for StreamWish, Rumble, AbyssPlayer, etc.
            loadExtractor(href, data, subtitleCallback, callback)
        }

        return true
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
