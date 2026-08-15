package com.mtsflix.pusatfilm21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class Pusatfilm21Plugin: Plugin() {
    override fun load() {
        registerMainAPI(Pusatfilm21Provider())
    }
}
