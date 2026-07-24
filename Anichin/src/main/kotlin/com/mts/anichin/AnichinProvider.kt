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
            // Handle OK.ru
            if (href.contains("ok=") || href.contains("ok.ru")) {
                val okId = if (href.contains("ok=")) href.substringAfter("ok=").substringBefore("&")
                           else if (href.contains("/videoembed/")) href.substringAfter("/videoembed/").substringBefore("?").substringBefore("&")
                           else ""
                if (okId.isNotBlank()) {
                    loadExtractor("https://ok.ru/videoembed/$okId", data, subtitleCallback, callback)
                }
            }

            // Handle Dailymotion [ADS] / Anichin Player
            if (href.contains("anichin-player.web.id") || href.contains("dailymotion.com")) {
                val videoId = if (href.contains("url=")) href.substringAfter("url=").substringBefore("&")
                              else if (href.contains("video=")) href.substringAfter("video=").substringBefore("&")
                              else if (href.contains("/video/")) href.substringAfter("/video/").substringBefore("?")
                              else ""
                if (videoId.isNotBlank()) {
                    val dmEmbedUrl = "https://www.dailymotion.com/embed/video/$videoId"
                    loadExtractor(dmEmbedUrl, data, subtitleCallback, callback)

                    try {
                        val dmApiUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
                        val apiResp = app.get(dmApiUrl, headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")).text
                        if (!apiResp.contains("DM005") && !apiResp.contains("Content rejected")) {
                            val m3u8Match = Regex("""https?://[^\s'"<]+\.m3u8[^\s'"<]*""").find(apiResp)
                            if (m3u8Match != null) {
                                val rawM3u8 = m3u8Match.value
                                val cleanUrl = rawM3u8.replace("""\/""", "/")
                                callback.invoke(
                                    newExtractorLink(
                                        name = "Dailymotion [ADS]",
                                        source = "Dailymotion",
                                        url = cleanUrl,
                                        type = ExtractorLinkType.M3U8
                                    ) {
                                        this.headers = mapOf(
                                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                                            "Referer" to "https://www.dailymotion.com/"
                                        )
                                        this.referer = "https://www.dailymotion.com/"
                                        this.quality = Qualities.P1080.value
                                    }
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Check anichin.stream ID
            if (href.contains("anichin.stream") || href.contains("/hls/") || href.contains("?id=")) {
                val sidMatch = Regex("""(?:\?id=|/hls/)([\w\-]+)""").find(href)
                if (sidMatch != null) {
                    val sid = sidMatch.groupValues[1]
                    val m3u8Url = "https://anichin.stream/hls/$sid.m3u8"
                    val reqHeaders = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                        "Referer" to "https://anichin.stream/"
                    )

                    callback.invoke(
                        newExtractorLink(
                            name = "Anichin Stream (Master)",
                            source = "Anichin Stream",
                            url = m3u8Url,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.headers = reqHeaders
                            this.referer = "https://anichin.stream/"
                            this.quality = Qualities.P1080.value
                        }
                    )

                    try {
                        val m3u8Text = app.get(m3u8Url, headers = reqHeaders).text
                        val lines = m3u8Text.lines()
                        for (i in 0 until lines.size) {
                            val line = lines[i].trim()
                            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                                val label = if (line.contains("1080")) "1080p"
                                            else if (line.contains("720")) "720p"
                                            else if (line.contains("480")) "480p"
                                            else "360p"
                                val qual = if (label == "1080p") Qualities.P1080.value
                                           else if (label == "720p") Qualities.P720.value
                                           else if (label == "480p") Qualities.P480.value
                                           else Qualities.P360.value

                                val nextLine = if (i + 1 < lines.size) lines[i + 1].trim() else ""
                                if (nextLine.startsWith("http")) {
                                    callback.invoke(
                                        newExtractorLink(
                                            name = "Anichin Stream $label",
                                            source = "Anichin Stream",
                                            url = nextLine,
                                            type = ExtractorLinkType.M3U8
                                        ) {
                                            this.headers = reqHeaders
                                            this.referer = "https://anichin.stream/"
                                            this.quality = qual
                                        }
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Also load built-in extractors for Rumble, Vidhide/Smoothpre/Morencius, StreamRuby, AbyssPlayer, etc.
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
