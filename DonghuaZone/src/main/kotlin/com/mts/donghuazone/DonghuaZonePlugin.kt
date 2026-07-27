package com.mts.donghuazone

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

import com.lagradost.cloudstream3.plugins.Plugin

import android.content.Context

@CloudstreamPlugin

class DonghuaZonePlugin : Plugin() {

    override fun load(context: Context) {

        registerMainAPI(DonghuaZoneProvider())
        registerExtractorAPI(BloggerCom())
        registerExtractorAPI(GeoDailymotionCom())
        registerExtractorAPI(DailymotionCom())
        registerExtractorAPI(GoogleVideo())

    }

}

