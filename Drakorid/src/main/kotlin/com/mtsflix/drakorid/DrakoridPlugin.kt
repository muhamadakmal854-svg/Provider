package com.mtsflix.drakorid

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

import com.lagradost.cloudstream3.plugins.Plugin

import android.content.Context

@CloudstreamPlugin

class DrakoridPlugin : Plugin() {

    override fun load(context: Context) {

        registerMainAPI(DrakoridProvider())
        registerExtractorAPI(AbyssplayerCom())
        registerExtractorAPI(BloggerCom())
        registerExtractorAPI(GembengCom())
        registerExtractorAPI(PsLarinpaymentCom())
        registerExtractorAPI(Prx1559AntVmwesaOnline())
        registerExtractorAPI(StreamtapeCom())
        registerExtractorAPI(PzEerfumerelCom())
        registerExtractorAPI(KisskhMegaplaySu())
        registerExtractorAPI(Prx1328AntVmwesaOnline())
        registerExtractorAPI(GoogleVideo())

    }

}

