package com.mtsflix.mytvlivemytvlive

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class MYTVLivePlugin: Plugin() {
    override fun load() {
        registerMainAPI(MYTVLiveProvider())
    }
}
