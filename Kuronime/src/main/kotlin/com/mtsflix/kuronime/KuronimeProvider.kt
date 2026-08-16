package com.mtsflix.kuronimekuronime

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class KuronimeProvider : MainAPI() {
    override var mainUrl = "https://kuronime.sbs"
    override var name = "Kuronime"
    override var supportedTypes = setOf(TvType.Movie)
}
