package com.mynimeku

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class MynimekuPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Mynimeku())
    }
}
