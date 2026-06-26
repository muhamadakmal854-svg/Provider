package com.mts.dutamovie21

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class KetikLive : StreamWishExtractor() {
    override val name = "KetikLive"
    override val mainUrl = "https://ketik.live"
}

class BokinshopCom : StreamWishExtractor() {
    override val name = "BokinshopCom"
    override val mainUrl = "https://bokinshop.com"
}

class GacorVin : StreamWishExtractor() {
    override val name = "GacorVin"
    override val mainUrl = "https://gacor.vin"
}

class Upload18Cc : StreamWishExtractor() {
    override val name = "Upload18Cc"
    override val mainUrl = "https://upload18.cc"
}

class EmbedpyroxXyz : ExtractorApi() {
    override val name = "EmbedpyroxXyz"
    override val mainUrl = "https://embedpyrox.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = url.replace(92.toChar().toString(), "")
        val id = cleanUrl.substringAfter("/video/").substringBefore("/").substringBefore("?")
        if (id.isEmpty()) return

        val ajaxUrl = "$mainUrl/player/index.php?data=$id&do=getVideo"
        val response = app.post(
            url = ajaxUrl,
            data = mapOf("hash" to id, "r" to (referer ?: "")),
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to cleanUrl,
                "Origin" to mainUrl,
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
            )
        )
        if (!response.isSuccessful) return
        val text = response.text
        val securedLink = Regex("\"securedLink\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
        if (securedLink != null) {
            val finalUrl = securedLink.replace("\\/", "/")
            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = finalUrl,
                    referer = cleanUrl,
                    type = ExtractorLinkType.M3U8
                )
            )
        }
    }
}

class IamcdnNet : StreamWishExtractor() {
    override val name = "IamcdnNet"
    override val mainUrl = "https://iamcdn.net"
}

class RedditCom : StreamWishExtractor() {
    override val name = "RedditCom"
    override val mainUrl = "https://reddit.com"
}

class TumblrCom : StreamWishExtractor() {
    override val name = "TumblrCom"
    override val mainUrl = "https://tumblr.com"
}

class McYandex : StreamWishExtractor() {
    override val name = "McYandex"
    override val mainUrl = "https://mc.yandex."
}

class IYtimgCom : StreamWishExtractor() {
    override val name = "IYtimgCom"
    override val mainUrl = "https://i.ytimg.com"
}

class YoutuBe : StreamWishExtractor() {
    override val name = "YoutuBe"
    override val mainUrl = "https://youtu.be"
}
