package com.mts.kuronime

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class KuronimePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(KuronimeProvider())
        registerExtractorAPI(KuronimeMoe())
        registerExtractorAPI(AssetsProductionLinktrEe())
        registerExtractorAPI(AccessLineMe())
        registerExtractorAPI(PlayerXExtractor())
    }
}
