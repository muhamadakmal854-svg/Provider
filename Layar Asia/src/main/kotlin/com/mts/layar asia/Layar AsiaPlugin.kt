package com.mts.layar asia

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

import com.lagradost.cloudstream3.plugins.Plugin

import android.content.Context

@CloudstreamPlugin

class Layar AsiaPlugin : Plugin() {

    override fun load(context: Context) {

        registerMainAPI(Layar AsiaProvider())
        registerExtractorAPI(LayarasiaUpnsLive())
        registerExtractorAPI(MirroredTo())
        registerExtractorAPI(PlayerVimeoCom())
        registerExtractorAPI(CdnAmpprojectOrg())
        registerExtractorAPI(D32J6O160Xr4GfCloudfrontNet())

    }

}

