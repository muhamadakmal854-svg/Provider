package com.mts.tubitv

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class TUBITVPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TUBITV())
    }
}
