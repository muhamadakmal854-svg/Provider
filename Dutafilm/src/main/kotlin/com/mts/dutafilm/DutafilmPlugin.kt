package com.mts.dutafilm

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DutafilmPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DutafilmProvider())
        registerExtractorAPI(MessengerCom())
        registerExtractorAPI(MetaCom())
        registerExtractorAPI(MetaAi())
        registerExtractorAPI(ThreadsCom())
        registerExtractorAPI(OrOnenessparmackCom())
        registerExtractorAPI(Dutafilm77MantabMen())
        registerExtractorAPI(PlayerXExtractor())
    }
}
