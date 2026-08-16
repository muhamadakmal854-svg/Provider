package com.mydramawave

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class MyDramaWavePlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(MyDramaWave())
    }
}
