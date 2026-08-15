package com.mtsflix.nontondrama

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class NontonDramaProvider : MainAPI() {
    override var mainUrl = "https://tv4.nontondrama.my"
    override var name = "NontonDrama"
    override var supportedTypes = setOf(TvType.Movie)
}
