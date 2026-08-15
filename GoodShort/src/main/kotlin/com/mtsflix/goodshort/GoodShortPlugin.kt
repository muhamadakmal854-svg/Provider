package com.mtsflix.goodshortgoodshort

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class GoodShortPlugin: Plugin() {
    override fun load() {
        registerMainAPI(GoodShortProvider())
    }
}
