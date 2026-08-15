package com.mtsflix.layarotakulayarotaku

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class LayarOtakuPlugin: Plugin() {
    override fun load() {
        registerMainAPI(LayarOtakuProvider())
    }
}
