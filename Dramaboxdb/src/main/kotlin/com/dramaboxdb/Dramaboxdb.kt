package com.dramaboxdb

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

class Dramaboxdb : MainAPI() {
    override var mainUrl = "https://www.dramaboxdb.com"
    override var name = "Dramaboxdb"
    override var lang = "en"
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

    data class NextData(
        @JsonProperty("props") val props: NextProps?
    )
    data class NextProps(
        @JsonProperty("pageProps") val pageProps: NextPageProps?
    )
    data class NextPageProps(
        @JsonProperty("bookList") val bookList: List<BookItem>?,
        @JsonProperty("bookInfo") val bookInfo: BookInfo?,
        @JsonProperty("chapterList") val chapterList: List<ChapterItem>?
    )
    data class BookItem(
        @JsonProperty("bookId") val bookId: Long?,
        @JsonProperty("bookName") val bookName: String?,
        @JsonProperty("cover") val cover: String?,
        @JsonProperty("bookNameLower") val bookNameLower: String?,
        @JsonProperty("bookNameEn") val bookNameEn: String?
    )
    data class BookInfo(
        @JsonProperty("bookId") val bookId: Long?,
        @JsonProperty("bookName") val bookName: String?,
        @JsonProperty("cover") val cover: String?,
        @JsonProperty("introduction") val introduction: String?,
        @JsonProperty("bookNameLower") val bookNameLower: String?,
        @JsonProperty("bookNameEn") val bookNameEn: String?
    )
    data class ChapterItem(
        @JsonProperty("id") val id: Long?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("indexStr") val indexStr: String?,
        @JsonProperty("unlock") val unlock: Boolean?,
        @JsonProperty("m3u8Url") val m3u8Url: String?,
        @JsonProperty("mp4") val mp4: String?,
        @JsonProperty("cover") val cover: String?
    )

    override val mainPage = mainPageOf(
        "genres" to "Browse All"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = "$mainUrl/genres"
        val html = app.get(pageUrl).text
        val jsonStr = Regex("""<script id="__NEXT_DATA__" type="application/json">([^<]+)</script>""").find(html)?.groupValues?.get(1)
            ?: throw ErrorLoadingException("Failed to load genres")
        val parsed = mapper.readValue(jsonStr, NextData::class.java)
        val books = parsed.props?.pageProps?.bookList ?: emptyList()
        val items = books.map {
            val slug = it.bookNameLower ?: it.bookNameEn ?: it.bookId.toString()
            val url = "$mainUrl/movie/${it.bookId}/$slug"
            newAnimeSearchResponse(it.bookName ?: "Drama", url, TvType.AsianDrama) {
                this.posterUrl = it.cover
            }
        }
        return newHomePageResponse(request.name, items, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val pageUrl = "$mainUrl/search?keyword=${query.replace(" ", "%20")}"
        val html = app.get(pageUrl).text
        val jsonStr = Regex("""<script id="__NEXT_DATA__" type="application/json">([^<]+)</script>""").find(html)?.groupValues?.get(1)
            ?: return emptyList()
        val parsed = mapper.readValue(jsonStr, NextData::class.java)
        val books = parsed.props?.pageProps?.bookList ?: emptyList()
        return books.map {
            val slug = it.bookNameLower ?: it.bookNameEn ?: it.bookId.toString()
            val url = "$mainUrl/movie/${it.bookId}/$slug"
            newAnimeSearchResponse(it.bookName ?: "Drama", url, TvType.AsianDrama) {
                this.posterUrl = it.cover
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val html = app.get(url).text
        val jsonStr = Regex("""<script id="__NEXT_DATA__" type="application/json">([^<]+)</script>""").find(html)?.groupValues?.get(1)
            ?: return null
        val parsed = mapper.readValue(jsonStr, NextData::class.java)
        val book = parsed.props?.pageProps?.bookInfo ?: return null
        val chapters = parsed.props?.pageProps?.chapterList ?: emptyList()

        val episodeList = chapters.mapIndexed { idx, ch ->
            val m3u8 = ch.m3u8Url ?: ch.mp4 ?: ""
            val title = ch.indexStr ?: ch.name ?: "Episode ${idx + 1}"
            newEpisode(m3u8) {
                this.name = title
                this.episode = idx + 1
                this.posterUrl = ch.cover ?: book.cover ?: ""
            }
        }.filter { !it.data.isNullOrBlank() }

        return newTvSeriesLoadResponse(
            book.bookName ?: "Drama",
            url,
            TvType.AsianDrama,
            episodeList
        ) {
            this.posterUrl = book.cover
            this.plot = book.introduction
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false
        val isM3u8 = data.contains(".m3u8", ignoreCase = true)
        callback(
            newExtractorLink(
                source = name,
                name = name,
                url = data,
                type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            ) {
                this.headers = mapOf(
                    "Origin" to mainUrl,
                    "Referer" to "$mainUrl/"
                )
            }
        )
        return true
    }
}
