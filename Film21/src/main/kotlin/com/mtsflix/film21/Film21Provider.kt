package com.mtsflix.film21film21

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class Film21Provider : MainAPI() {
    override var mainUrl = "http://178.128.91.191/"
    override var name = "Film21"
    override var supportedTypes = setOf(TvType.Movie)
}
