package com.mts.nontondrama

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class NontonDramaPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(NontonDramaProvider())
        registerExtractorAPI(Gn1r5nOrg())
        registerExtractorAPI(P2PExtractor())
        registerExtractorAPI(EmturbovidExtractor())
        registerExtractorAPI(AbyssPlayer())
    }
}
