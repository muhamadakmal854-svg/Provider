package com.mtsflix.gudangfilm

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class GudangFilmProvider : MainAPI() {
    override var mainUrl = "https://www.huazai6.com"
    override var name = "GudangFilm"
    override var supportedTypes = setOf(TvType.Movie)
}
