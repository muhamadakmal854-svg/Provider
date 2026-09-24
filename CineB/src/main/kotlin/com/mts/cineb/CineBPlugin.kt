package com.mts.cineb

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class CineBPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(CineB())
    }
}
