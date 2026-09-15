package com.mts.nontondrama

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class NontonDramaPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(NontonDramaProvider())
        registerExtractorAPI(AbyssCdn())
        registerExtractorAPI(AbyssPlayer())
        registerExtractorAPI(AbyssTo())
        registerExtractorAPI(TurbovidExtractor())
        registerExtractorAPI(Emturbovid())
        registerExtractorAPI(Gn1r5nOrg())
        registerExtractorAPI(Luluvid())
        registerExtractorAPI(KrakenfilesExtractor())
        registerExtractorAPI(PlaycinematicCom())
        registerExtractorAPI(EmbedpyroxXyz())
    }
}
