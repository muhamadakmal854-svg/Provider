package com.mts.dotdrama

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DotDramaPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DotDrama())
    }
}
