package com.mtsflix.anoboy

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AnoboyPlugin: Plugin() {
    override fun load() {
        registerMainAPI(AnoboyProvider())
    }
}
