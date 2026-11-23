package com.mts.dramadunyam

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DramadunyamPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Dramadunyam())
    }
}
