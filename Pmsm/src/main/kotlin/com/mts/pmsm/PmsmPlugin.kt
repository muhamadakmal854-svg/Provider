package com.mts.pmsm

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class PencurimoviesubmalayPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(PencurimoviesubmalayProvider())
        registerExtractorAPI(Dsvplay())
        registerExtractorAPI(Hglink())
    }
}
