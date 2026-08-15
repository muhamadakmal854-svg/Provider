package com.mtsflix.kuramanime

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class KuramanimePlugin: Plugin() {
    override fun load() {
        registerMainAPI(KuramanimeProvider())
    }
}
