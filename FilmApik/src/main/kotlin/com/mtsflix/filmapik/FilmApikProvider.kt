package com.mtsflix.filmapik

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class FilmApikProvider : MainAPI() {
    override var mainUrl = "http://167.172.70.31/"
    override var name = "FilmApik"
    override var supportedTypes = setOf(TvType.Movie)
}
