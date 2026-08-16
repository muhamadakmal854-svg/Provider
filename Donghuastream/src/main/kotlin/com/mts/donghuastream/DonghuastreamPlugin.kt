package com.mts.donghuastream

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

import com.lagradost.cloudstream3.plugins.Plugin

import android.content.Context

@CloudstreamPlugin

class DonghuastreamPlugin : Plugin() {

    override fun load(context: Context) {

        registerMainAPI(DonghuastreamProvider())
        registerExtractorAPI(PlayStreamplayCoIn())
        registerExtractorAPI(GeoDailymotionCom())
        registerExtractorAPI(VikingfileCom())
        registerExtractorAPI(RumbleCom())
        registerExtractorAPI(OkRu())

    }

}

