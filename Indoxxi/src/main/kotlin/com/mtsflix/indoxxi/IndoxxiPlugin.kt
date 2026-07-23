package com.mtsflix.indoxxi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

import com.lagradost.cloudstream3.plugins.Plugin

import android.content.Context

@CloudstreamPlugin

class IndoxxiPlugin : Plugin() {

    override fun load(context: Context) {

        registerMainAPI(IndoxxiProvider())
        registerExtractorAPI(AbyssplayerCom())
        registerExtractorAPI(FastdlP2pstreamOnline())
        registerExtractorAPI(EmbedpyroxXyz())
        registerExtractorAPI(MorenciusCom())
        registerExtractorAPI(HgcloudTo())
        registerExtractorAPI(VeevTo())
        registerExtractorAPI(Dm21UpnsLive())
        registerExtractorAPI(HelvidNet())

    }

}

