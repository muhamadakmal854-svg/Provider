package com.mtsflix.kawanfilmkawanfilm

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class KawanFilmProvider : MainAPI() {
    override var mainUrl = "https://web.kawanfilm21.co"
    override var name = "KawanFilm"
    override var supportedTypes = setOf(TvType.Movie)
}
