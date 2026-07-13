package com.mts.kisskh

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.app

// Extractor for delivery.kisskh.best CDN (direct M3U8)
class KisskhDelivery : ExtractorApi() {
    override val name = "KisskhDelivery"
    override val mainUrl = "https://delivery.kisskh.best"
    override val requiresReferer = false
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        callback(
            newExtractorLink(
                source = name,
                name = name,
                url = url,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "https://kisskh.buzz/"
                this.headers = mapOf("Origin" to "https://kisskh.buzz")
            }
        )
    }
}

// Extractor for media.aniwatchtv.cam CDN (direct M3U8)
class AniwatchtvExtractor : ExtractorApi() {
    override val name = "AniwatchtvExtractor"
    override val mainUrl = "https://media.aniwatchtv.cam"
    override val requiresReferer = false
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        callback(
            newExtractorLink(
                source = name,
                name = name,
                url = url,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "https://kisskh.buzz/"
                this.headers = mapOf("Origin" to "https://kisskh.buzz")
            }
        )
    }
}

// Extractor for sooplive CDN (direct M3U8)
class SoopliveExtractor : ExtractorApi() {
    override val name = "SoopliveExtractor"
    override val mainUrl = "https://vod-normal-global-cdn-z02.sooplive.co.kr"
    override val requiresReferer = false
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        callback(
            newExtractorLink(
                source = name,
                name = name,
                url = url,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "https://kisskh.buzz/"
            }
        )
    }
}

// Extractor for api.bibox.space CDN (direct M3U8 with jpg segments)
class BiboxExtractor : ExtractorApi() {
    override val name = "BiboxExtractor"
    override val mainUrl = "https://api.bibox.space"
    override val requiresReferer = false
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        callback(
            newExtractorLink(
                source = name,
                name = name,
                url = url,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "https://kisskh.buzz/"
                this.headers = mapOf("Origin" to "https://kisskh.buzz")
            }
        )
    }
}
