package com.mts.rtmklik

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class RTMKlikProvider : MainAPI() {
    override var mainUrl              = "https://rtmklik.rtm.gov.my/live/tv/tv1"
    override var name                 = "RTMKlik"
    override var lang                 = "ms"
    override val hasMainPage          = true
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(
        TvType.Live,
        TvType.Others
    )

    override val mainPage = mainPageOf(
        "https://rtmklik.rtm.gov.my/live/tv/tv1" to "Saluran TV Live"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = mutableListOf<SearchResponse>()
        items.add(newLiveSearchResponse("Dewan Negara", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Dewan Negara&stream=https://rtmklik.rtm.gov.my/live/tv/dewannegara&logo=https://rtm-images.glueapi.io/420x0/live_channel/PDN_bckg.png") { this.posterUrl = "https://rtm-images.glueapi.io/420x0/live_channel/PDN_bckg.png" })
        items.add(newLiveSearchResponse("Dewan Rakyat", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Dewan Rakyat&stream=https://rtmklik.rtm.gov.my/live/tv/dewanrakyat&logo=https://rtm-images.glueapi.io/420x0/live_channel/PDR_bckg.png") { this.posterUrl = "https://rtm-images.glueapi.io/420x0/live_channel/PDR_bckg.png" })
        items.add(newLiveSearchResponse("Berita RTM", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Berita RTM&stream=https://rtmklik.rtm.gov.my/live/tv/berita&logo=https://rtm-images.glueapi.io/420x0/system/BERITACelcomeDigi.png") { this.posterUrl = "https://rtm-images.glueapi.io/420x0/system/BERITACelcomeDigi.png" })
        items.add(newLiveSearchResponse("Sukan+", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Sukan+&stream=https://rtmklik.rtm.gov.my/live/tv/sukan&logo=https://rtm-images.glueapi.io/420x0/system/sukanCDnoDolby.png") { this.posterUrl = "https://rtm-images.glueapi.io/420x0/system/sukanCDnoDolby.png" })
        items.add(newLiveSearchResponse("Okey", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Okey&stream=https://rtmklik.rtm.gov.my/live/tv/okey&logo=https://rtm-images.glueapi.io/420x0/system/okeyCDnoDolby.png") { this.posterUrl = "https://rtm-images.glueapi.io/420x0/system/okeyCDnoDolby.png" })
        items.add(newLiveSearchResponse("TV2", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=TV2&stream=https://rtmklik.rtm.gov.my/live/tv/tv2&logo=https://rtm-images.glueapi.io/420x0/system/tv2CDnoDolby.png") { this.posterUrl = "https://rtm-images.glueapi.io/420x0/system/tv2CDnoDolby.png" })
        items.add(newLiveSearchResponse("TV1", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=TV1&stream=https://rtmklik.rtm.gov.my/live/tv/tv1&logo=https://rtm-images.glueapi.io/420x0/live_channel/TV1CelcomDigi.png") { this.posterUrl = "https://rtm-images.glueapi.io/420x0/live_channel/TV1CelcomDigi.png" })
        return newHomePageResponse(request.name, items, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val items = mutableListOf<SearchResponse>()
        items.add(newLiveSearchResponse("Dewan Negara", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Dewan Negara&stream=https://rtmklik.rtm.gov.my/live/tv/dewannegara&logo=https://rtm-images.glueapi.io/420x0/live_channel/PDN_bckg.png") { this.posterUrl = "https://rtm-images.glueapi.io/420x0/live_channel/PDN_bckg.png" })
        items.add(newLiveSearchResponse("Dewan Rakyat", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Dewan Rakyat&stream=https://rtmklik.rtm.gov.my/live/tv/dewanrakyat&logo=https://rtm-images.glueapi.io/420x0/live_channel/PDR_bckg.png") { this.posterUrl = "https://rtm-images.glueapi.io/420x0/live_channel/PDR_bckg.png" })
        items.add(newLiveSearchResponse("Berita RTM", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Berita RTM&stream=https://rtmklik.rtm.gov.my/live/tv/berita&logo=https://rtm-images.glueapi.io/420x0/system/BERITACelcomeDigi.png") { this.posterUrl = "https://rtm-images.glueapi.io/420x0/system/BERITACelcomeDigi.png" })
        items.add(newLiveSearchResponse("Sukan+", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Sukan+&stream=https://rtmklik.rtm.gov.my/live/tv/sukan&logo=https://rtm-images.glueapi.io/420x0/system/sukanCDnoDolby.png") { this.posterUrl = "https://rtm-images.glueapi.io/420x0/system/sukanCDnoDolby.png" })
        items.add(newLiveSearchResponse("Okey", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Okey&stream=https://rtmklik.rtm.gov.my/live/tv/okey&logo=https://rtm-images.glueapi.io/420x0/system/okeyCDnoDolby.png") { this.posterUrl = "https://rtm-images.glueapi.io/420x0/system/okeyCDnoDolby.png" })
        items.add(newLiveSearchResponse("TV2", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=TV2&stream=https://rtmklik.rtm.gov.my/live/tv/tv2&logo=https://rtm-images.glueapi.io/420x0/system/tv2CDnoDolby.png") { this.posterUrl = "https://rtm-images.glueapi.io/420x0/system/tv2CDnoDolby.png" })
        items.add(newLiveSearchResponse("TV1", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=TV1&stream=https://rtmklik.rtm.gov.my/live/tv/tv1&logo=https://rtm-images.glueapi.io/420x0/live_channel/TV1CelcomDigi.png") { this.posterUrl = "https://rtm-images.glueapi.io/420x0/live_channel/TV1CelcomDigi.png" })
        return items.filter { it.name.contains(query, ignoreCase = true) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val title = if (url.contains("title=")) url.substringAfter("title=").substringBefore("&") else name
        val streamUrl = if (url.contains("stream=")) url.substringAfter("stream=").substringBefore("&") else url
        val logo = if (url.contains("logo=")) url.substringAfter("logo=").substringBefore("&") else ""

        val cleanTitle = java.net.URLDecoder.decode(title, "UTF-8")
        val cleanStream = java.net.URLDecoder.decode(streamUrl, "UTF-8")
        val cleanLogo = java.net.URLDecoder.decode(logo, "UTF-8")

        return newLiveStreamLoadResponse(cleanTitle, url, cleanStream) {
            this.posterUrl = if (cleanLogo.isNotBlank()) cleanLogo else null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isClipped: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val cleanData = java.net.URLDecoder.decode(data, "UTF-8")
        if (cleanData.contains(".m3u8") || cleanData.contains("m3u8")) {
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "$name Live HLS",
                    url = cleanData,
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8 = true
                )
            )
        } else {
            loadExtractor(cleanData, mainUrl, subtitleCallback, callback)
        }
        return true
    }
}
