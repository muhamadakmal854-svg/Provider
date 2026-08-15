package com.mtsflix.kuramanime

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class KuramanimeProvider : MainAPI() {
    override var mainUrl = "https://m2.kuramanime.ing"
    override var name = "Kuramanime"
    override var supportedTypes = setOf(TvType.Movie)
}
