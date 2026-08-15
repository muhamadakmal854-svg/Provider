package com.mtsflix.pusatfilm21pusatfilm21

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class Pusatfilm21Provider : MainAPI() {
    override var mainUrl = "https://v4.pusatfilm21info.com/"
    override var name = "Pusatfilm21"
    override var supportedTypes = setOf(TvType.Movie)
}
