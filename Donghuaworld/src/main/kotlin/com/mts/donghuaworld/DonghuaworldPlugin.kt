package com.mts.donghuaworld

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

import com.lagradost.cloudstream3.plugins.Plugin

import android.content.Context

@CloudstreamPlugin

class DonghuaworldPlugin : Plugin() {

    override fun load(context: Context) {

        registerMainAPI(DonghuaworldProvider())
        registerExtractorAPI(GeoDailymotionCom())

    }

}

