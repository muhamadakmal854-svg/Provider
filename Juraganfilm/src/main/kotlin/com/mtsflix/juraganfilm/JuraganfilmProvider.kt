package com.mtsflix.juraganfilmjuraganfilm

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class JuraganfilmProvider : MainAPI() {
    override var mainUrl = "https://tv47.juragan.film"
    override var name = "Juraganfilm"
    override var supportedTypes = setOf(TvType.Movie)
}
