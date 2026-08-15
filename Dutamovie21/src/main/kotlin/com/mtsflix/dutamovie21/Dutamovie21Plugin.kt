package com.mtsflix.dutamovie21dutamovie21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class Dutamovie21Plugin: Plugin() {
    override fun load() {
        registerMainAPI(Dutamovie21Provider())
    }
}
