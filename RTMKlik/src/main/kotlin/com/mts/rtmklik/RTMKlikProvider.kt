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
        items.add(newMovieSearchResponse("TV1", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=TV1&stream=https://d25tgymtnqzu8s.cloudfront.net/live/media0/tv1/HLS/tv1.m3u8?id=1&logo=https://rtm-images.glueapi.io/420x0/live_channel/TV1CelcomDigi.png", TvType.Live) { this.posterUrl = "https://rtm-images.glueapi.io/420x0/live_channel/TV1CelcomDigi.png" })
        items.add(newMovieSearchResponse("TV2", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=TV2&stream=https://d25tgymtnqzu8s.cloudfront.net/live/media0/tv2/HLS/tv2.m3u8?id=2&logo=https://rtm-images.glueapi.io/420x0/system/tv2CDnoDolby.png", TvType.Live) { this.posterUrl = "https://rtm-images.glueapi.io/420x0/system/tv2CDnoDolby.png" })
        items.add(newMovieSearchResponse("Okey", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Okey&stream=https://d25tgymtnqzu8s.cloudfront.net/live/media0/okey1/HLS/okey1.m3u8?id=3&logo=https://rtm-images.glueapi.io/420x0/system/okeyCDnoDolby.png", TvType.Live) { this.posterUrl = "https://rtm-images.glueapi.io/420x0/system/okeyCDnoDolby.png" })
        items.add(newMovieSearchResponse("Sukan+", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Sukan+&stream=https://d25tgymtnqzu8s.cloudfront.net/live/media0/sukan/HLS/sukan.m3u8?id=4&logo=https://rtm-images.glueapi.io/420x0/system/sukanCDnoDolby.png", TvType.Live) { this.posterUrl = "https://rtm-images.glueapi.io/420x0/system/sukanCDnoDolby.png" })
        items.add(newMovieSearchResponse("Berita RTM", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Berita RTM&stream=https://d25tgymtnqzu8s.cloudfront.net/live/media0/berita/HLS/berita.m3u8?id=5&logo=https://rtm-images.glueapi.io/420x0/system/BERITACelcomeDigi.png", TvType.Live) { this.posterUrl = "https://rtm-images.glueapi.io/420x0/system/BERITACelcomeDigi.png" })
        items.add(newMovieSearchResponse("Dewan Rakyat", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Dewan Rakyat&stream=https://d25tgymtnqzu8s.cloudfront.net/smil:rakyat/playlist.m3u8?id=7&logo=https://rtm-images.glueapi.io/420x0/live_channel/PDR_bckg.png", TvType.Live) { this.posterUrl = "https://rtm-images.glueapi.io/420x0/live_channel/PDR_bckg.png" })
        items.add(newMovieSearchResponse("Dewan Negara", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Dewan Negara&stream=https://d25tgymtnqzu8s.cloudfront.net/smil:negara/playlist.m3u8?id=8&logo=https://rtm-images.glueapi.io/420x0/live_channel/PDN_bckg.png", TvType.Live) { this.posterUrl = "https://rtm-images.glueapi.io/420x0/live_channel/PDN_bckg.png" })
        items.add(newMovieSearchResponse("RTM World", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=RTM World&stream=https://d25tgymtnqzu8s.cloudfront.net/event/smil:event1/playlist.m3u8?id=16&logo=https://rtm-images.glueapi.io/420x0/live_channel/TV1CelcomDigi.png", TvType.Live) { this.posterUrl = "https://rtm-images.glueapi.io/420x0/live_channel/TV1CelcomDigi.png" })
        return newHomePageResponse(request.name, items, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val items = mutableListOf<SearchResponse>()
        items.add(newMovieSearchResponse("TV1", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=TV1&stream=https://d25tgymtnqzu8s.cloudfront.net/live/media0/tv1/HLS/tv1.m3u8?id=1&logo=https://rtm-images.glueapi.io/420x0/live_channel/TV1CelcomDigi.png", TvType.Live) { this.posterUrl = "https://rtm-images.glueapi.io/420x0/live_channel/TV1CelcomDigi.png" })
        items.add(newMovieSearchResponse("TV2", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=TV2&stream=https://d25tgymtnqzu8s.cloudfront.net/live/media0/tv2/HLS/tv2.m3u8?id=2&logo=https://rtm-images.glueapi.io/420x0/system/tv2CDnoDolby.png", TvType.Live) { this.posterUrl = "https://rtm-images.glueapi.io/420x0/system/tv2CDnoDolby.png" })
        items.add(newMovieSearchResponse("Okey", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Okey&stream=https://d25tgymtnqzu8s.cloudfront.net/live/media0/okey1/HLS/okey1.m3u8?id=3&logo=https://rtm-images.glueapi.io/420x0/system/okeyCDnoDolby.png", TvType.Live) { this.posterUrl = "https://rtm-images.glueapi.io/420x0/system/okeyCDnoDolby.png" })
        items.add(newMovieSearchResponse("Sukan+", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Sukan+&stream=https://d25tgymtnqzu8s.cloudfront.net/live/media0/sukan/HLS/sukan.m3u8?id=4&logo=https://rtm-images.glueapi.io/420x0/system/sukanCDnoDolby.png", TvType.Live) { this.posterUrl = "https://rtm-images.glueapi.io/420x0/system/sukanCDnoDolby.png" })
        items.add(newMovieSearchResponse("Berita RTM", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Berita RTM&stream=https://d25tgymtnqzu8s.cloudfront.net/live/media0/berita/HLS/berita.m3u8?id=5&logo=https://rtm-images.glueapi.io/420x0/system/BERITACelcomeDigi.png", TvType.Live) { this.posterUrl = "https://rtm-images.glueapi.io/420x0/system/BERITACelcomeDigi.png" })
        items.add(newMovieSearchResponse("Dewan Rakyat", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Dewan Rakyat&stream=https://d25tgymtnqzu8s.cloudfront.net/smil:rakyat/playlist.m3u8?id=7&logo=https://rtm-images.glueapi.io/420x0/live_channel/PDR_bckg.png", TvType.Live) { this.posterUrl = "https://rtm-images.glueapi.io/420x0/live_channel/PDR_bckg.png" })
        items.add(newMovieSearchResponse("Dewan Negara", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=Dewan Negara&stream=https://d25tgymtnqzu8s.cloudfront.net/smil:negara/playlist.m3u8?id=8&logo=https://rtm-images.glueapi.io/420x0/live_channel/PDN_bckg.png", TvType.Live) { this.posterUrl = "https://rtm-images.glueapi.io/420x0/live_channel/PDN_bckg.png" })
        items.add(newMovieSearchResponse("RTM World", "https://rtmklik.rtm.gov.my/live/tv/tv1/watch?title=RTM World&stream=https://d25tgymtnqzu8s.cloudfront.net/event/smil:event1/playlist.m3u8?id=16&logo=https://rtm-images.glueapi.io/420x0/live_channel/TV1CelcomDigi.png", TvType.Live) { this.posterUrl = "https://rtm-images.glueapi.io/420x0/live_channel/TV1CelcomDigi.png" })
        return items.filter { it.name.contains(query, ignoreCase = true) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val title = if (url.contains("title=")) url.substringAfter("title=").substringBefore("&") else name
        val streamUrl = if (url.contains("stream=")) url.substringAfter("stream=").substringBefore("&") else url
        val logo = if (url.contains("logo=")) url.substringAfter("logo=").substringBefore("&") else ""

        val cleanTitle = java.net.URLDecoder.decode(title, "UTF-8")
        val cleanStream = java.net.URLDecoder.decode(streamUrl, "UTF-8")
        val cleanLogo = java.net.URLDecoder.decode(logo, "UTF-8")

        return newMovieLoadResponse(cleanTitle, url, TvType.Live, cleanStream) {
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
        val reqHeadersDesktop = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Referer" to "https://rtmklik.rtm.gov.my/",
            "Origin" to "https://rtmklik.rtm.gov.my"
        )
        val reqHeadersMobile = mapOf(
            "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
            "Referer" to "https://rtmklik.rtm.gov.my/",
            "Origin" to "https://rtmklik.rtm.gov.my"
        )

        if (cleanData.contains(".m3u8") || cleanData.contains("m3u8")) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "$name Live (Desktop UA)",
                    url = cleanData,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.headers = reqHeadersDesktop
                    this.referer = "https://rtmklik.rtm.gov.my/"
                    this.quality = Qualities.Unknown.value
                }
            )
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "$name Live (Mobile UA)",
                    url = cleanData,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.headers = reqHeadersMobile
                    this.referer = "https://rtmklik.rtm.gov.my/"
                    this.quality = Qualities.Unknown.value
                }
            )
        } else {
            loadExtractor(cleanData, mainUrl, subtitleCallback, callback)
        }
        return true
    }
}
