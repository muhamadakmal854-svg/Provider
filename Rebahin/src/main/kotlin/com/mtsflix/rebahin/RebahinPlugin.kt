package com.mtsflix.rebahin

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class RebahinPlugin: Plugin() {
    override fun load() {
        registerMainAPI(RebahinProvider())
    }
}
