package com.mtsflix.otakudesu

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class OtakudesuPlugin: Plugin() {
    override fun load() {
        registerMainAPI(OtakudesuProvider())
    }
}
