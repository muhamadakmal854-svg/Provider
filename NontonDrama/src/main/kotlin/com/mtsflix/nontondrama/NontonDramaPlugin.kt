package com.mtsflix.nontondramanontondrama

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class NontonDramaPlugin: Plugin() {
    override fun load() {
        registerMainAPI(NontonDramaProvider())
    }
}
