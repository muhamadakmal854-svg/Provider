package com.mtsflix.dutamovie21

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class Dutamovie21Provider : MainAPI() {
    override var mainUrl = "https://austincomputerworks.org"
    override var name = "Dutamovie21"
    override var supportedTypes = setOf(TvType.Movie)
}
