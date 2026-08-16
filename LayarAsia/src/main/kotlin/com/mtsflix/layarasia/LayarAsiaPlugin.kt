package com.mtsflix.layarasialayarasia

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class LayarAsiaPlugin: Plugin() {
    override fun load() {
        registerMainAPI(LayarAsiaProvider())
    }
}
