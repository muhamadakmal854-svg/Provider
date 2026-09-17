package com.mts.sflix

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class SFlixPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SFlix())
    }
}
