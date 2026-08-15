package com.mtsflix.animexinanimexin

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AnimexinPlugin: Plugin() {
    override fun load() {
        registerMainAPI(AnimexinProvider())
    }
}
