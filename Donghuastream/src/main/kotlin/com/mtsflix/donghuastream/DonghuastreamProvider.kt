package com.mtsflix.donghuastream

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class DonghuastreamProvider : MainAPI() {
    override var mainUrl = "https://donghuastream.org"
    override var name = "Donghuastream"
    override var supportedTypes = setOf(TvType.Movie)
}
