package com.mtsflix.anoboy

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class AnoboyProvider : MainAPI() {
    override var mainUrl = "https://anoboy.si"
    override var name = "Anoboy"
    override var supportedTypes = setOf(TvType.Movie)
}
