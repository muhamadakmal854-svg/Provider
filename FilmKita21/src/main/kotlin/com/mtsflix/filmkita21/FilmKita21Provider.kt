package com.mtsflix.filmkita21filmkita21

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class FilmKita21Provider : MainAPI() {
    override var mainUrl = "https://s12.iix.llc/"
    override var name = "FilmKita21"
    override var supportedTypes = setOf(TvType.Movie)
}
