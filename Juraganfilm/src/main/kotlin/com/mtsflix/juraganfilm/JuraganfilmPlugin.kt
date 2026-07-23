package com.mtsflix.juraganfilm

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class JuraganfilmPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(JuraganfilmProvider())
    }
}
