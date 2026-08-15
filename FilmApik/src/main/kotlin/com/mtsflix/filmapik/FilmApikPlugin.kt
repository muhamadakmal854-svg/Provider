package com.mtsflix.filmapikfilmapik

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FilmApikPlugin: Plugin() {
    override fun load() {
        registerMainAPI(FilmApikProvider())
    }
}
