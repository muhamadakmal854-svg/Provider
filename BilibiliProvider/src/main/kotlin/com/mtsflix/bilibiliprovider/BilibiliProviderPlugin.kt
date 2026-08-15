package com.mtsflix.bilibiliprovider

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class BilibiliProviderPlugin: Plugin() {
    override fun load(context: Context) {
        BilibiliProvider.context = context
        registerMainAPI(BilibiliIndonesiaProvider())
    }
}
