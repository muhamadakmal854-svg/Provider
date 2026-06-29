package com.mts.donghub

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

class DailymotionCom : Dailymotion() {
    override val name = "DailymotionCom"
    override val mainUrl = "https://dailymotion.com"
}

class TickcounterCom : StreamWishExtractor() {
    override val name = "TickcounterCom"
    override val mainUrl = "https://tickcounter.com"
}
