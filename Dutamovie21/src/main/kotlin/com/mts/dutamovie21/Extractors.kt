package com.mts.dutamovie21

import com.lagradost.cloudstream3.extractors.StreamWishExtractor
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

class EmbedpyroxXyz : StreamWishExtractor() {
    override val name = "EmbedpyroxXyz"
    override val mainUrl = "https://embedpyrox.xyz"
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
