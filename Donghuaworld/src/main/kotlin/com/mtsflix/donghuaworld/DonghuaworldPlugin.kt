package com.mtsflix.donghuaworlddonghuaworld

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DonghuaworldPlugin: Plugin() {
    override fun load() {
        registerMainAPI(DonghuaworldProvider())
    }
}
