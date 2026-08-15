package com.mtsflix.samehadakusamehadaku

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class SamehadakuProvider : MainAPI() {
    override var mainUrl = "https://v2.samehadaku.how"
    override var name = "Samehadaku"
    override var supportedTypes = setOf(TvType.Movie)
}
