package com.iqiyi

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class iQIYIPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(iQIYI())
    }
}
