package com.mtsflix.film21film21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class Film21Plugin: Plugin() {
    override fun load() {
        registerMainAPI(Film21Provider())
    }
}
