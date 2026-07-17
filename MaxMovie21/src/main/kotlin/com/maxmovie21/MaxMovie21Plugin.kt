package com.maxmovie21

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class MaxMovie21Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(MaxMovie21())
    }
}
