package com.mtsflix.oploverzoploverz

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class OploverzPlugin: Plugin() {
    override fun load() {
        registerMainAPI(OploverzProvider())
    }
}
