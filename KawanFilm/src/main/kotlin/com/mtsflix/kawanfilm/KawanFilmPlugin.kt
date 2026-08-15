package com.mtsflix.kawanfilmkawanfilm

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class KawanFilmPlugin: Plugin() {
    override fun load() {
        registerMainAPI(KawanFilmProvider())
    }
}
