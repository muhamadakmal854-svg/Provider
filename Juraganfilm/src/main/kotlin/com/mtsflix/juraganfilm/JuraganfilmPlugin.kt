package com.mtsflix.juraganfilm

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class JuraganfilmPlugin: Plugin() {
    override fun load() {
        registerMainAPI(JuraganfilmProvider())
    }
}
