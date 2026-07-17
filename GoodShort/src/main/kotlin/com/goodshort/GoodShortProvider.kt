package com.goodshort

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.databind.DeserializationFeature

class GoodShortProvider : MainAPI() {
    override var mainUrl = "https://www.goodshort.com"
    override var name = "GoodShort"
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

    // JSON Model Classes
    data class HomeResponse(
        @JsonProperty("status") val status: Int,
        @JsonProperty("data") val data: HomeData?
    )
    data class HomeData(
        @JsonProperty("pageColumns") val pageColumns: List<PageColumn>?
    )
    data class PageColumn(
        @JsonProperty("columnName") val columnName: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("items") val items: List<BookItem>?,
        @JsonProperty("list") val list: List<BookItem>?
    )
    data class BookItem(
        @JsonProperty("id") val id: Long?,
        @JsonProperty("bookId") val bookId: Long?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("cover") val cover: String?,
        @JsonProperty("introduction") val introduction: String?,
        @JsonProperty("bookResourceUrl") val bookResourceUrl: String?
    )

    data class DetailResponse(
        @JsonProperty("status") val status: Int,
        @JsonProperty("data") val data: DetailData?
    )
    data class DetailData(
        @JsonProperty("book") val book: BookDetail?,
        @JsonProperty("chapterVoList") val chapterVoList: List<ChapterItem>?
    )
    data class BookDetail(
        @JsonProperty("bookId") val bookId: Long?,
        @JsonProperty("bookName") val bookName: String?,
        @JsonProperty("cover") val cover: String?,
        @JsonProperty("introduction") val introduction: String?,
        @JsonProperty("bookResourceUrl") val bookResourceUrl: String?
    )
    data class ChapterItem(
        @JsonProperty("id") val id: Long?,
        @JsonProperty("chapterName") val chapterName: String?,
        @JsonProperty("price") val price: Int?,
        @JsonProperty("m3u8Path") val m3u8Path: String?,
        @JsonProperty("image") val image: String?,
        @JsonProperty("chapterResourceUrl") val chapterResourceUrl: String?
    )

    data class ChapterPageResponse(
        @JsonProperty("status") val status: Int,
        @JsonProperty("data") val data: ChapterPageData?
    )
    data class ChapterPageData(
        @JsonProperty("records") val records: List<ChapterItem>?
    )

    data class SearchResponseData(
        @JsonProperty("status") val status: Int,
        @JsonProperty("data") val data: SearchData?
    )
    data class SearchData(
        @JsonProperty("page") val page: SearchPage?
    )
    data class SearchPage(
        @JsonProperty("records") val records: List<BookItem>?
    )

    override val mainPage = mainPageOf(
        "Most Trending" to "Most Trending",
        "Top in GoodShort" to "Top in GoodShort",
        "Hot List" to "Hot List",
        "Love Stories" to "Love Stories"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homePayload = mapOf("language" to "en")
        val res = app.post(
            "$mainUrl/hwycreels/home/index",
            headers = mapOf("Content-Type" to "application/json;charset=UTF-8"),
            json = homePayload
        ).text
        val parsed = mapper.readValue(res, HomeResponse::class.java)
        val columns = parsed.data?.pageColumns ?: emptyList()
        
        val matchedCol = columns.firstOrNull { 
            (it.columnName ?: it.name ?: "").equals(request.name, ignoreCase = true) 
        }
        val items = (matchedCol?.items ?: matchedCol?.list ?: emptyList()).mapNotNull {
            val title = it.name ?: it.title ?: return@mapNotNull null
            val id = it.bookId ?: it.id ?: return@mapNotNull null
            val cover = it.cover ?: ""
            val url = "$mainUrl/drama/${it.bookResourceUrl ?: id.toString()}"
            newAnimeSearchResponse(title, url, TvType.AsianDrama) {
                this.posterUrl = cover
            }
        }
        return newHomePageResponse(request.name, items, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchPayload = mapOf(
            "keyword" to query,
            "pageNo" to 1,
            "pageSize" to 40
        )
        val res = app.post(
            "$mainUrl/hwycreels/book/search/seo",
            headers = mapOf("Content-Type" to "application/json;charset=UTF-8"),
            json = searchPayload
        ).text
        val parsed = mapper.readValue(res, SearchResponseData::class.java)
        return (parsed.data?.page?.records ?: emptyList()).mapNotNull {
            val title = it.name ?: it.title ?: return@mapNotNull null
            val id = it.bookId ?: it.id ?: return@mapNotNull null
            val cover = it.cover ?: ""
            val url = "$mainUrl/drama/${it.bookResourceUrl ?: id.toString()}"
            newAnimeSearchResponse(title, url, TvType.AsianDrama) {
                this.posterUrl = cover
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val bookIdStr = url.substringAfterLast("-").substringAfterLast("/").trim()
        val bookId = bookIdStr.toLongOrNull() ?: return null

        val detailPayload = mapOf("bookId" to bookId.toString())
        val detailRes = app.post(
            "$mainUrl/hwycreels/book/detail",
            headers = mapOf("Content-Type" to "application/json;charset=UTF-8"),
            json = detailPayload
        ).text
        val parsed = mapper.readValue(detailRes, DetailResponse::class.java)
        val book = parsed.data?.book ?: return null

        val chapterPayload = mapOf(
            "bookId" to bookId.toString(),
            "pageNo" to 1,
            "pageSize" to 500
        )
        val chapterRes = app.post(
            "$mainUrl/hwycreels/chapter/page",
            headers = mapOf("Content-Type" to "application/json;charset=UTF-8"),
            json = chapterPayload
        ).text
        val parsedChapters = mapper.readValue(chapterRes, ChapterPageResponse::class.java)
        val chapters = parsedChapters.data?.records ?: parsed.data?.chapterVoList ?: emptyList()

        val episodeList = chapters.mapIndexed { idx, ch ->
            val chId = ch.id ?: (idx + 1).toLong()
            val chName = ch.chapterName ?: "Episode ${idx + 1}"
            val m3u8 = ch.m3u8Path ?: ""
            
            newEpisode(m3u8) {
                this.name = chName
                this.episode = idx + 1
                this.posterUrl = ch.image ?: book.cover ?: ""
            }
        }.filter { !it.data.isNullOrBlank() } // Only include playable episodes (m3u8 not empty)

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
        
        callback(
            newExtractorLink(
                source = name,
                name = name,
                url = data,
                type = ExtractorLinkType.M3U8
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
