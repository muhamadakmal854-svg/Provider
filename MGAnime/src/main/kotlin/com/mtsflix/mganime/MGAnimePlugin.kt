package com.mtsflix.mganime

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class MGAnimePlugin: Plugin() {
    override fun load() {
        registerMainAPI(MGAnimeProvider())
    }
}
