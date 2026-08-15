package com.mtsflix.gudangfilmgudangfilm

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class GudangFilmPlugin: Plugin() {
    override fun load() {
        registerMainAPI(GudangFilmProvider())
    }
}
