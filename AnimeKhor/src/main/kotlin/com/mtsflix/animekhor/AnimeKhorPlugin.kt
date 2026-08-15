package com.mtsflix.animekhor

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AnimeKhorPlugin: Plugin() {
    override fun load() {
        registerMainAPI(AnimeKhorProvider())
    }
}
