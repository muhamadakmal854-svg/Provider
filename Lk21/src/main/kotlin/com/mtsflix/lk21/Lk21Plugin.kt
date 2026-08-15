package com.mtsflix.lk21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class Lk21Plugin: Plugin() {
    override fun load() {
        registerMainAPI(Lk21Provider())
    }
}
