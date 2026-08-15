package com.mtsflix.indoxxi

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class IndoxxiProvider : MainAPI() {
    override var mainUrl = "https://taroscafe.com"
    override var name = "Indoxxi"
    override var supportedTypes = setOf(TvType.Movie)
}
