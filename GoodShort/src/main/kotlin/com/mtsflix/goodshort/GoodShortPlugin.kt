package com.mtsflix.goodshort

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class GoodShortPlugin: Plugin() {
    override fun load() {
        registerMainAPI(GoodShortProvider())
    }
}
