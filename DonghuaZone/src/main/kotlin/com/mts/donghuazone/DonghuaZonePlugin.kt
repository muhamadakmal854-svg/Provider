package com.mts.donghuazone

import com.lagradost.cloudstream3.extractors.Dailymotion
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

@CloudstreamPlugin
class DonghuaZonePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DonghuaZone())
        registerExtractorAPI(GeoDailymotionCom())
        registerExtractorAPI(DailymotionCom())
    }
}
