package com.mts.dailymotion

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Dailymotion : MainAPI() {
    override var mainUrl = "https://www.dailymotion.com"
    override var name = "Dailymotion"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val hasQuickSearch = true
    override var lang = "all"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Others
    )

    companion object {
        private const val apiBaseUrl = "https://api.dailymotion.com"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

        fun getPoster(item: DmVideoItem): String? {
            return item.thumbnail1080
                ?: item.thumbnail720
                ?: item.thumbnailLarge
                ?: item.thumbnail
                ?: "https://www.dailymotion.com/thumbnail/video/${item.id}"
        }

        fun getBackdrop(item: DmVideoItem): String? {
            return item.thumbnail1080
                ?: item.thumbnail720
                ?: item.thumbnailLarge
                ?: item.thumbnail
        }

        fun formatYear(timestamp: Long?): Int? {
            if (timestamp == null || timestamp <= 0) return null
            val date = Date(timestamp * 1000)
            val sdf = SimpleDateFormat("yyyy", Locale.US)
            return sdf.format(date).toIntOrNull()
        }

        fun formatDate(timestamp: Long?): String? {
            if (timestamp == null || timestamp <= 0) return null
            val date = Date(timestamp * 1000)
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
            return sdf.format(date)
        }
    }

    data class DmVideoListResponse(
        @JsonProperty("page") val page: Int? = null,
        @JsonProperty("limit") val limit: Int? = null,
        @JsonProperty("has_more") val hasMore: Boolean? = null,
        @JsonProperty("list") val list: List<DmVideoItem>? = null
    )

    data class DmVideoItem(
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("thumbnail_1080_url") val thumbnail1080: String? = null,
        @JsonProperty("thumbnail_720_url") val thumbnail720: String? = null,
        @JsonProperty("thumbnail_large_url") val thumbnailLarge: String? = null,
        @JsonProperty("thumbnail_url") val thumbnail: String? = null,
        @JsonProperty("duration") val duration: Long? = null,
        @JsonProperty("created_time") val createdTime: Long? = null,
        @JsonProperty("channel") val channel: String? = null,
        @JsonProperty("views_total") val viewsTotal: Long? = null,
        @JsonProperty("rating") val rating: Double? = null,
        @JsonProperty("tags") val tags: List<String>? = null,
        @JsonProperty("owner.screenname") val ownerScreenName: String? = null,
        @JsonProperty("owner.username") val ownerUserName: String? = null
    )

    data class DmMetadataResponse(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("duration") val duration: Long? = null,
        @JsonProperty("qualities") val qualities: Map<String, List<DmQualityItem>>? = null,
        @JsonProperty("subtitles") val subtitles: DmSubtitlesObj? = null
    )

    data class DmQualityItem(
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("url") val url: String? = null
    )

    data class DmSubtitlesObj(
        @JsonProperty("enable") val enable: Boolean? = null,
        @JsonProperty("data") val data: Map<String, DmSubtitleData>? = null
    )

    data class DmSubtitleData(
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("urls") val urls: List<String>? = null
    )

    // Tampilan Beranda Netflix Mengikut Kategori BaseURL Dailymotion
    override val mainPage = mainPageOf(
        "$apiBaseUrl/videos?flags=featured&sort=visited&limit=30" to "🌟 Pilihan Utama (Featured Spotlight)",
        "$apiBaseUrl/videos?sort=trending&limit=30" to "🔥 Trending Hari Ini (Trending Now)",
        "$apiBaseUrl/videos?channel=tv&sort=trending&limit=30" to "🎬 Siri TV & Filem (TV & Cinema)",
        "$apiBaseUrl/videos?channel=music&sort=trending&limit=30" to "🎵 Muzik & Konsert (Music & Live)",
        "$apiBaseUrl/videos?channel=sport&sort=trending&limit=30" to "⚽ Sukan & Sorotan (Sports Highlights)",
        "$apiBaseUrl/videos?channel=news&sort=recent&limit=30" to "📰 Berita & Informasi (News & Current Affairs)",
        "$apiBaseUrl/videos?channel=fun&sort=trending&limit=30" to "😂 Komedi & Jenaka (Comedy & Entertainment)",
        "$apiBaseUrl/videos?channel=videogames&sort=trending&limit=30" to "🎮 Permainan & Animasi (Gaming & Animation)",
        "$apiBaseUrl/videos?channel=tech&sort=trending&limit=30" to "🚀 Teknologi & Sains (Tech & Science)",
        "$apiBaseUrl/videos?channel=shortfilms&sort=visited&limit=30" to "📽️ Filem Pendek (Short Films & Cinema)",
        "$apiBaseUrl/videos?channel=lifestyle&sort=trending&limit=30" to "🌿 Gaya Hidup & Kembara (Lifestyle & Travel)",
        "$apiBaseUrl/videos?channel=animals&sort=trending&limit=30" to "🐾 Haiwan & Alam Semula Jadi (Animals & Nature)",
        "$apiBaseUrl/videos?channel=auto&sort=trending&limit=30" to "🚗 Dunia Permotoran (Cars & Auto)",
        "$apiBaseUrl/videos?sort=recent&limit=30" to "🕒 Terkini (Recently Uploaded)"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val baseRequestUrl = request.data
        val queryUrl = if (baseRequestUrl.contains("page=")) {
            baseRequestUrl.replace(Regex("""page=\d+"""), "page=$page")
        } else {
            "$baseRequestUrl&page=$page&fields=id,title,description,thumbnail_1080_url,thumbnail_720_url,thumbnail_large_url,duration,created_time,channel,views_total,rating"
        }

        val res = app.get(
            queryUrl,
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "$mainUrl/"
            ),
            timeout = 15
        )
        val data = tryParseJson<DmVideoListResponse>(res.text)

        val items = data?.list?.mapNotNull { item ->
            val poster = getPoster(item)
            val vUrl = "$mainUrl/video/${item.id}"
            newMovieSearchResponse(item.title ?: "Dailymotion Video", vUrl, TvType.Movie) {
                this.posterUrl = poster
            }
        } ?: emptyList()

        return newHomePageResponse(
            request.name,
            items,
            hasNext = data?.hasMore ?: (items.size >= 25)
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val searchUrl = "$apiBaseUrl/videos?search=$encoded&limit=40&fields=id,title,description,thumbnail_1080_url,thumbnail_720_url,thumbnail_large_url,duration,created_time,channel,views_total,rating"
        val res = app.get(
            searchUrl,
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "$mainUrl/"
            ),
            timeout = 15
        )
        val data = tryParseJson<DmVideoListResponse>(res.text)

        return data?.list?.mapNotNull { item ->
            val poster = getPoster(item)
            val vUrl = "$mainUrl/video/${item.id}"
            newMovieSearchResponse(item.title ?: "Dailymotion Video", vUrl, TvType.Movie) {
                this.posterUrl = poster
            }
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val videoId = when {
            url.contains("/video/") -> url.substringAfter("/video/").substringBefore("?").substringBefore("/")
            url.contains("video=") -> url.substringAfter("video=").substringBefore("&")
            else -> Regex("""([a-zA-Z0-9]{7,})""").find(url)?.groupValues?.getOrNull(1) ?: url.trim()
        }

        val detailApiUrl = "$apiBaseUrl/video/$videoId?fields=id,title,description,thumbnail_1080_url,thumbnail_720_url,thumbnail_large_url,thumbnail_url,duration,created_time,channel,tags,owner.screenname,owner.username,views_total,rating"
        val res = app.get(
            detailApiUrl,
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "$mainUrl/"
            ),
            timeout = 15
        )
        val item = tryParseJson<DmVideoItem>(res.text) ?: return null

        val title = item.title ?: "Dailymotion Video"
        val poster = getPoster(item)
        val backdrop = getBackdrop(item) ?: poster
        val year = formatYear(item.createdTime)
        val fullUrl = "$mainUrl/video/${item.id}"

        val plotText = buildString {
            if (!item.description.isNullOrBlank()) {
                append(item.description.trim())
                append("\n\n")
            }
            val metaParts = mutableListOf<String>()
            if (!item.channel.isNullOrBlank()) {
                metaParts.add("Saluran: ${item.channel.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }}")
            }
            if (!item.ownerScreenName.isNullOrBlank()) {
                metaParts.add("Pencipta: ${item.ownerScreenName}")
            }
            val dateStr = formatDate(item.createdTime)
            if (dateStr != null) {
                metaParts.add("Tarikh Rilis: $dateStr")
            }
            if (item.viewsTotal != null && item.viewsTotal > 0) {
                metaParts.add("Tontonan: ${String.format(Locale.US, "%,d", item.viewsTotal)} views")
            }
            if (metaParts.isNotEmpty()) {
                append(metaParts.joinToString(" • "))
            }
        }

        val tagsList = mutableListOf<String>()
        if (!item.channel.isNullOrBlank()) {
            tagsList.add(item.channel.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() })
        }
        item.tags?.let { tagsList.addAll(it.take(10)) }

        return newMovieLoadResponse(title, fullUrl, TvType.Movie, fullUrl) {
            this.posterUrl = poster
            this.backgroundPosterUrl = backdrop
            this.year = year
            this.plot = plotText
            this.tags = tagsList
            this.duration = item.duration?.let { (it / 60).toInt().coerceAtLeast(1) }
            if (item.rating != null) {
                this.score = Score.from10(item.rating.toString())
            }
            addTrailer(fullUrl)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val videoId = when {
            data.contains("/video/") -> data.substringAfter("/video/").substringBefore("?").substringBefore("/")
            data.contains("video=") -> data.substringAfter("video=").substringBefore("&")
            data.contains("url=") -> data.substringAfter("url=").substringBefore("&")
            else -> Regex("""([a-zA-Z0-9]{7,})""").find(data)?.groupValues?.getOrNull(1) ?: data.trim()
        }

        if (videoId.isBlank()) return false

        try {
            val geoUrl = "https://geo.dailymotion.com/player/xkyen.html?video=$videoId"
            val geoRes = try {
                app.get(
                    geoUrl,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to "$mainUrl/"
                    ),
                    timeout = 10
                )
            } catch (_: Exception) { null }

            val sessionCookies = geoRes?.cookies ?: emptyMap()
            val cookieHeader = if (sessionCookies.isNotEmpty()) {
                sessionCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            } else ""

            val streamHeaders = mutableMapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "https://geo.dailymotion.com/"
            )
            if (cookieHeader.isNotBlank()) {
                streamHeaders["Cookie"] = cookieHeader
            }

            val metaUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
            val metaRes = app.get(metaUrl, headers = streamHeaders, timeout = 12)
            val metadata = tryParseJson<DmMetadataResponse>(metaRes.text)

            // Pengekstrakan Sari Kata Khusus Eng, Ind, My & Semua Bahasa Tersedia
            metadata?.subtitles?.data?.forEach { (langCode, subData) ->
                val rawLabel = subData.label ?: langCode
                val cleanLang = when {
                    langCode.startsWith("id", ignoreCase = true) || langCode.startsWith("in", ignoreCase = true) || rawLabel.contains("Indonesian", ignoreCase = true) || rawLabel.contains("Indonesia", ignoreCase = true) -> "Indonesian (Ind)"
                    langCode.startsWith("ms", ignoreCase = true) || langCode.startsWith("my", ignoreCase = true) || rawLabel.contains("Malay", ignoreCase = true) || rawLabel.contains("Melayu", ignoreCase = true) -> "Malay (My)"
                    langCode.startsWith("en", ignoreCase = true) || rawLabel.contains("English", ignoreCase = true) -> "English (Eng)"
                    else -> rawLabel.ifBlank { langCode.uppercase() }
                }

                subData.urls?.forEach { subUrl ->
                    if (subUrl.isNotBlank()) {
                        subtitleCallback(
                            newSubtitleFile(
                                cleanLang,
                                subUrl
                            )
                        )
                    }
                }
            }

            var streamFound = false

            // 1. Ekstrak Auto Master HLS (Multi-Quality Dalam Pemain)
            val autoList = metadata?.qualities?.get("auto")
            val autoUrl = autoList?.firstOrNull()?.url
            if (!autoUrl.isNullOrBlank()) {
                streamFound = true
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = "${this.name} - Auto HLS (Multi-Quality)",
                        url = autoUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://geo.dailymotion.com/"
                        this.headers = streamHeaders
                    }
                )

                // Jana resolusi diskrit (1080p, 720p, 480p, 360p, dll.)
                try {
                    generateM3u8(
                        source = this.name,
                        streamUrl = autoUrl,
                        referer = "https://geo.dailymotion.com/",
                        headers = streamHeaders
                    ).forEach(callback)
                } catch (_: Exception) {}
            }

            // 2. Ekstrak Kualiti MP4 / Stream Diskrit Lain Jika Ada
            metadata?.qualities?.forEach { (qKey, qList) ->
                if (!qKey.equals("auto", ignoreCase = true)) {
                    qList.forEach { qItem ->
                        val qUrl = qItem.url
                        if (!qUrl.isNullOrBlank()) {
                            streamFound = true
                            val qualityLabel = when (qKey) {
                                "1080" -> "1080p Full HD"
                                "720" -> "720p HD"
                                "480" -> "480p SD"
                                "360" -> "360p"
                                "240" -> "240p"
                                "144" -> "144p"
                                else -> "$qKey"
                            }
                            val qualityVal = when (qKey) {
                                "1080" -> Qualities.P1080.value
                                "720" -> Qualities.P720.value
                                "480" -> Qualities.P480.value
                                "360" -> Qualities.P360.value
                                "240" -> Qualities.P240.value
                                else -> Qualities.Unknown.value
                            }
                            val isM3u8 = qUrl.contains(".m3u8", ignoreCase = true)
                            callback(
                                newExtractorLink(
                                    source = this.name,
                                    name = "${this.name} - $qualityLabel",
                                    url = qUrl,
                                    type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = "https://geo.dailymotion.com/"
                                    this.quality = qualityVal
                                    this.headers = streamHeaders
                                }
                            )
                        }
                    }
                }
            }

            return streamFound
        } catch (_: Exception) {
            return false
        }
    }
}
