package com.mtsflix.donghubdonghub

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class DonghubProvider : MainAPI() {
    override var mainUrl = "https://donghub.vip"
    override var name = "Donghub"
    override var supportedTypes = setOf(TvType.Movie)
}
