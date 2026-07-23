package com.mtsflix.lk21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Lk21Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Lk21Provider())
        registerExtractorAPI(EmturbovidExtractor())
        registerExtractorAPI(P2PExtractor())
        registerExtractorAPI(F16Extractor())
    }
}
