package com.mts.pencurimovie

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
