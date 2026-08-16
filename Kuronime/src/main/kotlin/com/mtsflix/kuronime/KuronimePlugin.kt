package com.mtsflix.kuronimekuronime

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class KuronimePlugin: Plugin() {
    override fun load() {
        registerMainAPI(KuronimeProvider())
    }
}
