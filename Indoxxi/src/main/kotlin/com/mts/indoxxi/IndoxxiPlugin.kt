package com.mts.indoxxi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class IndoxxiPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(IndoxxiProvider())
        registerExtractorAPI(CvGenipspillionCom())
    }
}
