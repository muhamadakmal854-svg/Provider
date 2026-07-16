package com.mts.donghub

import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class KiRooserlyxoseShop : StreamWishExtractor() {
    override var name = "KiRooserlyxoseShop"
    override var mainUrl = "https://ki.rooserlyxose.shop"
}

class DailymotionCom : Dailymotion() {
    override var name = "DailymotionCom"
    override var mainUrl = "https://dailymotion.com"
}

class GeoDailymotionCom : Dailymotion() {
    override var name = "GeoDailymotionCom"
    override var mainUrl = "https://geo.dailymotion.com"
}

class MorenciusCom : StreamWishExtractor() {
    override var name = "MorenciusCom"
    override var mainUrl = "https://morencius.com"
}
