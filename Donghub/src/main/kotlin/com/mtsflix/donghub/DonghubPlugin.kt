package com.mtsflix.donghub

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DonghubPlugin: Plugin() {
    override fun load() {
        registerMainAPI(DonghubProvider())
    }
}
