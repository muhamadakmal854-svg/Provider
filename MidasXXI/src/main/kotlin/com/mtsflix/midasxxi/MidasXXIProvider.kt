package com.mtsflix.midasxximidasxxi

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class MidasXXIProvider : MainAPI() {
    override var mainUrl = "https://unairi.ac.id"
    override var name = "MidasXXI"
    override var supportedTypes = setOf(TvType.Movie)
}
