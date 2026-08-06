package com.mts.manseendrama

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.databind.DeserializationFeature

class ManseenDramaProvider : MainAPI() {
    override var mainUrl = "https://www.tiktok.com/@manseenddrama"
    override var name = "ManseenDrama"
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.AsianDrama,
        TvType.Movie
    )

    companion object {
        val mapper = jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    override val mainPage = mainPageOf(
        "manseenddrama" to "Manseen Drama Terbaru",
        "manseenddrama short drama" to "Manseen Short Drama",
        "short drama china sub indo" to "Short Drama Sub Indo",
        "china minidrama" to "China Mini Drama"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val keyword = request.data
        val searchList = fetchTikTokFeed(keyword, page)

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = searchList,
                isHorizontalImages = false
            ),
            hasNext = searchList.isNotEmpty()
        )
    }

    private suspend fun fetchTikTokFeed(keyword: String, page: Int): List<SearchResponse> {
        val cursor = (page - 1) * 20
        val response = app.post(
            "https://www.tikwm.com/api/feed/search",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                "Referer" to "https://www.tikwm.com/"
            ),
            params = mapOf(
                "keywords" to keyword,
                "count" to "20",
                "cursor" to cursor.toString()
            )
        ).text

        val json = mapper.readTree(response)
        val code = json.get("code")?.asInt() ?: -1
        if (code != 0) return emptyList()

        val videosNode = json.get("data")?.get("videos") ?: return emptyList()
        val results = mutableListOf<SearchResponse>()

        for (v in videosNode) {
            val videoId = v.get("video_id")?.asText() ?: continue
            var title = v.get("title")?.asText()?.trim() ?: "TikTok Short Drama"
            if (title.isBlank()) title = "Short Drama #$videoId"

            val cover = v.get("cover")?.asText()
                ?: v.get("origin_cover")?.asText() ?: ""
            val play = v.get("play")?.asText()
                ?: v.get("wmplay")?.asText() ?: ""

            if (play.isBlank()) continue

            val dataPayload = "$videoId||$title||$cover||$play"
            val epNum = Regex("""(?i)(?:ep|episode|part|pt|eps|\be)\s*[\.\:\-]?\s*(\d+)""").find(title)?.groupValues?.get(1)?.toIntOrNull() ?: 1

            results.add(
                newAnimeSearchResponse(title, dataPayload, TvType.AsianDrama) {
                    this.posterUrl = cover
                    this.sub(epNum)
                }
            )
        }

        return results
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return fetchTikTokFeed(query, 1)
    }

    override suspend fun load(url: String): LoadResponse {
        val parts = url.split("||")
        val title = if (parts.size > 1 && parts[1].isNotBlank()) parts[1] else "TikTok Short Film"
        val poster = if (parts.size > 2 && parts[2].isNotBlank()) parts[2] else ""
        val playUrl = if (parts.size > 3 && parts[3].isNotBlank()) parts[3] else if (url.startsWith("http")) url else ""

        val epNum = Regex("""(?i)(?:ep|episode|part|pt|eps|\be)\s*[\.\:\-]?\s*(\d+)""").find(title)?.groupValues?.get(1)?.toIntOrNull() ?: 1

        val episodesList = listOf(
            newEpisode(playUrl) {
                this.name = if (epNum > 1) "Episode $epNum" else "Episode 1"
                this.episode = epNum
                this.posterUrl = poster
            }
        )

        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodesList) {
            this.posterUrl = poster
            this.plot = "TikTok Short Film China - @manseenddrama"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parts = data.split("||")
        val playUrl = if (parts.size > 3 && parts[3].isNotBlank()) parts[3] else if (data.startsWith("http")) data else ""

        if (playUrl.isNotBlank()) {
            callback.invoke(
                newExtractorLink(
                    name = "TikTok HD",
                    source = this.name,
                    url = playUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = "https://www.tiktok.com/"
                    this.quality = Qualities.P1080.value
                }
            )
            return true
        }

        return false
    }
}
