package com.mts.plex

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class PLEXPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(PLEX())
    }
}
