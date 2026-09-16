package com.mts.kiblatfilm21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Kiblatfilm21Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Kiblatfilm21())
        registerExtractorAPI(PlayerAbyssplayerCom())
        registerExtractorAPI(AbyssplayerCom())
        registerExtractorAPI(MorenciusCom())
        registerExtractorAPI(Embed4Me())
        registerExtractorAPI(PlayerP2P())
        registerExtractorAPI(UpnsLive())
    }
}
