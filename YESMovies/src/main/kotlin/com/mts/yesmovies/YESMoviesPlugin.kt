package com.mts.yesmovies

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class YESMoviesPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(YESMovies())
    }
}
