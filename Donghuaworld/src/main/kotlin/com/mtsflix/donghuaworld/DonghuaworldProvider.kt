package com.mtsflix.donghuaworld

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class DonghuaworldProvider : MainAPI() {
    override var mainUrl = "https://donghuaworld.com"
    override var name = "Donghuaworld"
    override var supportedTypes = setOf(TvType.Movie)
}
