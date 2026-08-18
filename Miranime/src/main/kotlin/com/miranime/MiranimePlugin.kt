package com.miranime

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class MiranimePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Miranime())
    }
}
