package com.mts.hdtoday

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class HDTodayPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(HDToday())
    }
}
