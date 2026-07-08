package com.mts.donghub

import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class DqEndebedouseShop : StreamWishExtractor() {
    override var name = "DqEndebedouseShop"
    override var mainUrl = "https://dq.endebedouse.shop"
}

class GeoDailymotionCom : Dailymotion() {
    override var name = "GeoDailymotionCom"
    override var mainUrl = "https://geo.dailymotion.com"
}

class DailymotionCom : Dailymotion() {
    override var name = "DailymotionCom"
    override var mainUrl = "https://dailymotion.com"
}

class TickcounterCom : StreamWishExtractor() {
    override var name = "TickcounterCom"
    override var mainUrl = "https://tickcounter.com"
}
