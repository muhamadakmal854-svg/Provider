package com.mtsflix.filmkita21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FilmKita21Plugin: Plugin() {
    override fun load() {
        registerMainAPI(FilmKita21Provider())
    }
}
