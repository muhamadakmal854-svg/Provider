package com.mtsflix.drakorid

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DrakoridPlugin: Plugin() {
    override fun load() {
        registerMainAPI(DrakoridProvider())
    }
}
