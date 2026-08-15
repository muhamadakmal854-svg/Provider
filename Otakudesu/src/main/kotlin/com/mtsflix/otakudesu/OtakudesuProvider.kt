package com.mtsflix.otakudesu

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class OtakudesuProvider : MainAPI() {
    override var mainUrl = "https://otakudesu.blog"
    override var name = "Otakudesu"
    override var supportedTypes = setOf(TvType.Movie)
}
