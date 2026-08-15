package com.mtsflix.donghuafilm

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class DonghuaFilmProvider : MainAPI() {
    override var mainUrl = "https://donghuafilm.com"
    override var name = "DonghuaFilm"
    override var supportedTypes = setOf(TvType.Movie)
}
