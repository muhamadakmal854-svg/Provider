package com.mgtv

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class MGTVPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(MGTV())
    }
}
