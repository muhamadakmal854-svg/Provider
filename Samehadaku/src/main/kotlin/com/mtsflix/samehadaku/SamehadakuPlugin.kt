package com.mtsflix.samehadaku

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SamehadakuPlugin: Plugin() {
    override fun load() {
        registerMainAPI(SamehadakuProvider())
    }
}
