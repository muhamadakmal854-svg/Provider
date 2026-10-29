package com.mts.dailymotion

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DailymotionPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Dailymotion())
    }
}
