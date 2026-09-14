package com.mts.donghub

import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class GeoDailymotionCom : Dailymotion() {
    override var name = "GeoDailymotionCom"
    override var mainUrl = "https://geo.dailymotion.com"
}

class DailymotionCom : Dailymotion() {
    override var name = "DailymotionCom"
    override var mainUrl = "https://dailymotion.com"
}

class MorenciusCom : StreamWishExtractor() {
    override var name = "MorenciusCom"
    override var mainUrl = "https://morencius.com"
}

class KiRooserlyxoseShop : StreamWishExtractor() {
    override var name = "KiRooserlyxoseShop"
    override var mainUrl = "https://ki.rooserlyxose.shop"
}

@CloudstreamPlugin
class DonghubPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Donghub())
        registerExtractorAPI(GeoDailymotionCom())
        registerExtractorAPI(DailymotionCom())
        registerExtractorAPI(MorenciusCom())
        registerExtractorAPI(KiRooserlyxoseShop())
    }
}
