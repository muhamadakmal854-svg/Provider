package com.mtsflix.layarasialayarasia

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class LayarAsiaProvider : MainAPI() {
    override var mainUrl = "https://server-1.layar.asia"
    override var name = "LayarAsia"
    override var supportedTypes = setOf(TvType.Movie)
}
