package com.mts.pencarimovie

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class PencariMoviePlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(PencariMovie(context))
        openSettings = { ctx ->
            PencariMovieSettingsDialog.open(ctx)
        }
    }
}
