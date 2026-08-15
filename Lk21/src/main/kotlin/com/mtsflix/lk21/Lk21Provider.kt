package com.mtsflix.lk21lk21

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class Lk21Provider : MainAPI() {
    override var mainUrl = "https://tv11.lk21official.cc"
    override var name = "Lk21"
    override var supportedTypes = setOf(TvType.Movie)
}
