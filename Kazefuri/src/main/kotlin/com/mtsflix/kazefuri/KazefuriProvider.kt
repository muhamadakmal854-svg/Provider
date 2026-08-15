package com.mtsflix.kazefuri

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class KazefuriProvider : MainAPI() {
    override var mainUrl = "https://sv4.kazefuri.cloud"
    override var name = "Kazefuri"
    override var supportedTypes = setOf(TvType.Movie)
}
