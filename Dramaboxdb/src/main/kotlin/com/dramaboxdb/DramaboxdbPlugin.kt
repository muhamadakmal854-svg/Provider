package com.dramaboxdb

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DramaboxdbPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Dramaboxdb())
    }
}
