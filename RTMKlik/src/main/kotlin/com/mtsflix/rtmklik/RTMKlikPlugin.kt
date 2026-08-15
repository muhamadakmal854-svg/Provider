package com.mtsflix.rtmklikrtmklik

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class RTMKlikPlugin: Plugin() {
    override fun load() {
        registerMainAPI(RTMKlikProvider())
    }
}
