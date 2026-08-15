package com.mtsflix.donghuazonedonghuazone

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DonghuaZonePlugin: Plugin() {
    override fun load() {
        registerMainAPI(DonghuaZoneProvider())
    }
}
