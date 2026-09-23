package com.mts.f2movies

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class F2MoviesPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(F2Movies())
    }
}
