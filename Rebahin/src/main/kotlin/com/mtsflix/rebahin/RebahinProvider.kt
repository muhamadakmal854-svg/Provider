package com.mtsflix.rebahin

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class RebahinProvider : MainAPI() {
    override var mainUrl = "https://165.232.44.215"
    override var name = "Rebahin"
    override var supportedTypes = setOf(TvType.Movie)
}
