package com.mtsflix.anichinanichin

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AnichinPlugin: Plugin() {
    override fun load() {
        registerMainAPI(AnichinProvider())
    }
}
