package com.mts.sorastream

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class SorastreamPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SorastreamProvider())
    }
}
