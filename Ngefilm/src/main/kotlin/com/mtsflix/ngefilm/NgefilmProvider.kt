package com.mtsflix.ngefilmngefilm

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class NgefilmProvider : MainAPI() {
    override var mainUrl = "https://new37.ngefilm.site"
    override var name = "Ngefilm"
    override var supportedTypes = setOf(TvType.Movie)
}
