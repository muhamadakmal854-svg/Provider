package com.mydramawave

import android.util.Base64
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@PublishedApi
internal val myDramaWaveJsonMapper = jacksonObjectMapper().apply {
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

internal inline fun <reified T> tryParseMyDramaWaveJson(value: String): T? {
    return runCatching {
        myDramaWaveJsonMapper.readValue(value, T::class.java)
    }.getOrNull()
}

class MyDramaWave : MainAPI() {
    override var mainUrl = "https://m.mydramawave.com"
    private val nativeApiUrl = "https://apiv2.free-reels.com/frv2-api"
    private val fallbackApiUrl = "https://api.mydramawave.com/h5-api"

    override var name = "MyDramaWave"
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.AsianDrama,
        TvType.Movie
    )

    private val secureRandom = SecureRandom()
    private val deviceId = (1..32)
        .map { "0123456789abcdef"[secureRandom.nextInt(16)] }
        .joinToString("")

    private val sessionId = java.util.UUID.randomUUID().toString()

    private val authSalt = "8IAcbWyCsVhYv82S2eofRqK1DF3nNDAv&"
    private val nativeLoginSalt = "8IAcbWyCsVhYv82S2eofRqK1DF3nNDAv"
    private val cryptoKey = "2r36789f45q01ae5"

    private var sessionToken: String? = null
    private var sessionSecret: String? = null
    private val sessionLock = Mutex()

    private data class NativeCategory(
        val key: String,
        val name: String,
        val tabKey: String,
        val posIndex: Int,
        val type: TvType = TvType.AsianDrama
    )

    private val nativeCategories = listOf(
        NativeCategory("popular", "Popular", "993", 10000),
        NativeCategory("new", "Latest", "995", 10000)
    )

    override val mainPage = mainPageOf(
        "popular" to "Popular",
        "new" to "Latest"
    )

    private fun decryptIfNeeded(raw: String): String {
        val text = raw.trim()
        if (text.startsWith("{") || text.startsWith("[")) return text

        return runCatching {
            val decoded = Base64.decode(text, Base64.DEFAULT)
            if (decoded.size <= 16) return@runCatching text

            val iv = decoded.copyOfRange(0, 16)
            val payload = decoded.copyOfRange(16, decoded.size)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKey = SecretKeySpec(cryptoKey.toByteArray(Charsets.UTF_8), "AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))

            String(cipher.doFinal(payload), Charsets.UTF_8)
        }.getOrDefault(text)
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun getNativeHeaders(isVip: Boolean = false): MutableMap<String, String> {
        val ts = System.currentTimeMillis()
        val signature = md5(authSalt + (sessionSecret ?: ""))

        val headers = mutableMapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "OpCountryCode" to "US",
            "X-AppEngine-Country" to "US",
            "app-language" to "en",
            "prefer_country" to "US",
            "locale" to "en-US",
            "language" to "en-US",
            "country" to "US",
            "session-id" to sessionId,
            "device-id" to deviceId,
            "device" to "android",
            "Authorization" to "oauth_signature=$signature,oauth_token=${sessionToken ?: "undefined"},ts=$ts"
        )
        return headers
    }

    private suspend fun ensureSession() {
        if (!sessionToken.isNullOrBlank()) return

        sessionLock.withLock {
            if (!sessionToken.isNullOrBlank()) return@withLock

            val loginSig = md5(nativeLoginSalt + deviceId)
            val reqBody = mapOf(
                "device_id" to deviceId,
                "device_name" to "Android TV",
                "device_sign" to loginSig
            ).toJson().toRequestBody("application/json".toMediaTypeOrNull())

            val res = app.post(
                "$nativeApiUrl/anonymous/login",
                headers = getNativeHeaders(),
                requestBody = reqBody
            ).text

            val authData = tryParseMyDramaWaveJson<NativeAuthResponse>(res)
            sessionToken = authData?.data?.authKey ?: authData?.data?.token
            sessionSecret = authData?.data?.authSecret.orEmpty()
        }
    }

    private fun extractMovies(dataObj: UniversalFeedData?, dest: MutableList<UniversalItem>) {
        if (dataObj == null) return
        fun extract(itemsList: List<UniversalItem>?) {
            itemsList?.forEach { item ->
                val itemType = item.type.orEmpty()
                if (itemType.contains("banner", true)) return@forEach
                if (itemType.equals("ad", true)) return@forEach
                val title = item.title ?: item.name
                val id = item.id?.toString() ?: item.key ?: item.seriesId?.toString()
                if (!title.isNullOrBlank() && !id.isNullOrBlank()) {
                    dest.add(item)
                }
                extract(item.items)
                extract(item.list)
            }
        }
        extract(dataObj.items)
        extract(dataObj.list)
        extract(dataObj.components)
        extract(dataObj.modules)
    }

    private suspend fun getCategoryPage(
        category: NativeCategory,
        page: Int
    ): Pair<List<UniversalItem>, Boolean> {
        val indexUrl =
            "$nativeApiUrl/homepage/v2/tab/index?tab_key=${category.tabKey}&position_index=${category.posIndex}&rec_trigger=0"

        val res = app.get(indexUrl, headers = getNativeHeaders()).text
        val moduleIndex = tryParseMyDramaWaveJson<UniversalFeedResponse>(res)?.data
            ?: return emptyList<UniversalItem>() to false

        if (page <= 1) {
            val items = mutableListOf<UniversalItem>()
            extractMovies(moduleIndex, items)
            val hasMore = moduleIndex.pageInfo?.hasMore == true || !moduleIndex.pageInfo?.next.isNullOrBlank()
            return items.distinctBy { it.stableId() } to hasMore
        }

        val recommendModule = moduleIndex.items?.firstOrNull { it.type == "recommend" }
            ?: moduleIndex.list?.firstOrNull { it.type == "recommend" }
            ?: moduleIndex.modules?.firstOrNull { it.type == "recommend" }

        val recommendKey = recommendModule?.moduleKey ?: category.tabKey
        var currentNext = moduleIndex.pageInfo?.next
        var currentData: UniversalFeedData? = null

        for (i in 1 until page) {
            if (currentNext.isNullOrBlank()) break
            val reqBody = mapOf(
                "module_key" to recommendKey,
                "next" to currentNext
            ).toJson().toRequestBody("application/json".toMediaTypeOrNull())

            val feedRes = app.post(
                "$nativeApiUrl/homepage/v2/tab/feed",
                headers = getNativeHeaders(),
                requestBody = reqBody
            ).text
            currentData = tryParseMyDramaWaveJson<UniversalFeedResponse>(feedRes)?.data
            currentNext = currentData?.pageInfo?.next
        }

        val items = mutableListOf<UniversalItem>()
        extractMovies(currentData, items)
        val hasMore = currentData?.pageInfo?.hasMore == true || !currentNext.isNullOrBlank()
        return items.distinctBy { it.stableId() } to hasMore
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureSession()
        val category = nativeCategories.find { it.key == request.data }
            ?: throw ErrorLoadingException("Category not found: ${request.data}")
        val (rawItems, hasMore) = getCategoryPage(category, page)
        val items = rawItems.mapNotNull { item ->
            item.toSearchResponse(category.type)
        }.distinctBy { it.url }
        return newHomePageResponse(request.name, items, hasNext = hasMore)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        ensureSession()
        val nextToken = if (page <= 1) "" else "offset=${(page - 1) * 20}&page_size=20"
        val reqBody = mapOf(
            "keyword" to query,
            "next" to nextToken
        ).toJson().toRequestBody("application/json".toMediaTypeOrNull())

        val res = app.post(
            "$nativeApiUrl/search/drama",
            headers = getNativeHeaders(),
            requestBody = reqBody
        ).text

        val searchItems = mutableListOf<UniversalItem>()
        val dataObj = tryParseMyDramaWaveJson<UniversalFeedResponse>(res)?.data
        extractMovies(dataObj, searchItems)
        val hasMore = dataObj?.pageInfo?.hasMore == true || !dataObj?.pageInfo?.next.isNullOrBlank()
        val list = searchItems.mapNotNull { item ->
            item.toSearchResponse(TvType.AsianDrama)
        }.distinctBy { it.url }
        return newSearchResponseList(list, hasNext = hasMore)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return search(query, 1).items
    }

    override suspend fun load(url: String): LoadResponse {
        ensureSession()
        val seriesId = url.substringAfterLast("/").substringBefore("?").trim()
        if (seriesId.isBlank()) throw ErrorLoadingException("Series ID empty.")

        var info: DramaInfo? = null
        runCatching {
            val resRaw = app.get(
                "$nativeApiUrl/drama/info_v2?series_id=$seriesId",
                headers = getNativeHeaders(isVip = true)
            ).text
            info = tryParseMyDramaWaveJson<NativeDetailResponse>(resRaw)?.data?.info
        }

        val mainCover = fixUrlNull(info?.cover ?: info?.verticalCover)
        val mainTitle = info?.name ?: "Drama"
        val mainPlot = info?.desc
        val episodes = mutableListOf<Episode>()

        info?.episodeList?.forEachIndexed { index, ep ->
            episodes.add(
                newEpisode(ep.toJson()) {
                    this.name = ep.name ?: "Episode ${index + 1}"
                    this.episode = ep.index ?: (index + 1)
                    this.posterUrl = fixUrlNull(ep.cover)
                }
            )
        }

        return newTvSeriesLoadResponse(mainTitle, url, TvType.AsianDrama, episodes) {
            this.posterUrl = mainCover
            this.plot = mainPlot
            this.comingSoon = episodes.isEmpty()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val ep = tryParseMyDramaWaveJson<NativeEpisode>(data) ?: return false
        val videoUrl = ep.externalAudioH264 ?: ep.externalAudioH265 ?: ep.m3u8Url ?: ep.videoUrl
        var found = false

        if (!videoUrl.isNullOrBlank()) {
            val isM3u8 = videoUrl.contains(".m3u8", true)
            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = fixUrl(videoUrl),
                    type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.headers = mapOf(
                        "Origin" to mainUrl,
                        "Referer" to "$mainUrl/"
                    )
                }
            )
            found = true
        }

        ep.subtitleList?.forEach { sub ->
            val subUrl = sub.vtt ?: sub.subtitle
            if (!subUrl.isNullOrBlank()) {
                subtitleCallback(
                    newSubtitleFile(
                        sub.displayName ?: sub.language ?: "Subtitle",
                        fixUrl(subUrl)
                    )
                )
            }
        }
        return found
    }

    private data class NativeAuthResponse(
        @JsonProperty("data") val data: NativeAuthData?
    )
    private data class NativeAuthData(
        @JsonProperty("authKey") val authKey: String?,
        @JsonProperty("token") val token: String?,
        @JsonProperty("authSecret") val authSecret: String?
    )
    private data class UniversalFeedResponse(
        @JsonProperty("data") val data: UniversalFeedData?
    )
    private data class UniversalFeedData(
        @JsonProperty("items") val items: List<UniversalItem>?,
        @JsonProperty("list") val list: List<UniversalItem>?,
        @JsonProperty("components") val components: List<UniversalItem>?,
        @JsonProperty("modules") val modules: List<UniversalItem>?,
        @JsonProperty("pageInfo") val pageInfo: UniversalPageInfo?
    )
    private data class UniversalPageInfo(
        @JsonProperty("hasMore") val hasMore: Boolean?,
        @JsonProperty("next") val next: String?
    )
    private data class UniversalItem(
        @JsonProperty("id") val id: Any?,
        @JsonProperty("key") val key: String?,
        @JsonProperty("seriesId") val seriesId: Any?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("cover") val cover: String?,
        @JsonProperty("verticalCover") val verticalCover: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("desc") val desc: String?,
        @JsonProperty("items") val items: List<UniversalItem>?,
        @JsonProperty("list") val list: List<UniversalItem>?
    ) {
        fun stableId(): String = (id?.toString() ?: key ?: seriesId?.toString() ?: "")
        fun toSearchResponse(tvType: TvType): SearchResponse? {
            val titleStr = title ?: name ?: return null
            val idStr = id?.toString() ?: key ?: seriesId?.toString() ?: return null
            val linkUrl = "https://m.mydramawave.com/drama/$idStr"
            return newAnimeSearchResponse(titleStr, linkUrl, tvType) {
                this.posterUrl = fixUrlNull(cover ?: verticalCover)
            }
        }
    }
    private data class NativeDetailResponse(
        @JsonProperty("data") val data: NativeDetailData?
    )
    private data class NativeDetailData(
        @JsonProperty("info") val info: DramaInfo?
    )
    private data class DramaInfo(
        @JsonProperty("cover") val cover: String?,
        @JsonProperty("verticalCover") val verticalCover: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("desc") val desc: String?,
        @JsonProperty("episodeList") val episodeList: List<NativeEpisode>?
    )
    private data class NativeEpisode(
        @JsonProperty("name") val name: String?,
        @JsonProperty("index") val index: Int?,
        @JsonProperty("cover") val cover: String?,
        @JsonProperty("videoUrl") val videoUrl: String?,
        @JsonProperty("m3u8Url") val m3u8Url: String?,
        @JsonProperty("externalAudioH264") val externalAudioH264: String?,
        @JsonProperty("externalAudioH265") val externalAudioH265: String?,
        @JsonProperty("subtitleList") val subtitleList: List<NativeSubtitle>?
    )
    private data class NativeSubtitle(
        @JsonProperty("language") val language: String?,
        @JsonProperty("displayName") val displayName: String?,
        @JsonProperty("vtt") val vtt: String?,
        @JsonProperty("subtitle") val subtitle: String?
    )
}
