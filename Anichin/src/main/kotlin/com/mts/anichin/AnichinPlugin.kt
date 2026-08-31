package com.mts.anichin

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnichinPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Anichin(context))
    }
}
