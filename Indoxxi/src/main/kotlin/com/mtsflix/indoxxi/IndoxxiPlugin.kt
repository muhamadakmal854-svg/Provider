package com.mtsflix.indoxxiindoxxi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class IndoxxiPlugin: Plugin() {
    override fun load() {
        registerMainAPI(IndoxxiProvider())
    }
}
