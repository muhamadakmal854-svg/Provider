package com.mtsflix.animasu

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class AnimasuProvider : MainAPI() {
    override var mainUrl = "https://v1.animasu.work"
    override var name = "Animasu"
    override var supportedTypes = setOf(TvType.Movie)
}
