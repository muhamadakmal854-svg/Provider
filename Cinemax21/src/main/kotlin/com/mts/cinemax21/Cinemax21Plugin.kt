package com.mts.cinemax21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Cinemax21Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Cinemax21Provider())
        registerExtractorAPI(D2Ww6N6ZilcrhvCloudfrontNet())
        registerExtractorAPI(Max389ProNet())
        registerExtractorAPI(MaxhokiMonster())
        registerExtractorAPI(LayardramaLive())
        registerExtractorAPI(WaMe())
        registerExtractorAPI(Vz02372100F95BcdnNet())
        registerExtractorAPI(MezInk())
    }
}
