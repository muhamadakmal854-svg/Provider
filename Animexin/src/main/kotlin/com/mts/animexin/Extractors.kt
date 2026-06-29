package com.mts.animexin

import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class DqEndebedouseShop : StreamWishExtractor() {
    override val name = "DqEndebedouseShop"
    override val mainUrl = "https://dq.endebedouse.shop"
}

class GeoDailymotionCom : Dailymotion() {
    override val name = "GeoDailymotionCom"
    override val mainUrl = "https://geo.dailymotion.com"
}

class MirroredTo : StreamWishExtractor() {
    override val name = "MirroredTo"
    override val mainUrl = "https://mirrored.to"
}

class IsLysategriphusCfd : StreamWishExtractor() {
    override val name = "IsLysategriphusCfd"
    override val mainUrl = "https://is.lysategriphus.cfd"
}
