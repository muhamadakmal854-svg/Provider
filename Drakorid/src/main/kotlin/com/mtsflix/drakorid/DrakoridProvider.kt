package com.mtsflix.drakorid

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class DrakoridProvider : MainAPI() {
    override var mainUrl = "https://drakorid.cam"
    override var name = "Drakorid"
    override var supportedTypes = setOf(TvType.Movie)
}
