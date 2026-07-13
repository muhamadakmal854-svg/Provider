package com.mts.kisskh

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class KisskhPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(KisskhProvider())
        registerExtractorAPI(KisskhDelivery())
        registerExtractorAPI(AniwatchtvExtractor())
        registerExtractorAPI(SoopliveExtractor())
        registerExtractorAPI(BiboxExtractor())
    }
}
