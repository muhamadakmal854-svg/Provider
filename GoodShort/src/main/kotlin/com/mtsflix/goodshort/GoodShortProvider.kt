package com.mtsflix.goodshortgoodshort

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class GoodShortProvider : MainAPI() {
    override var mainUrl = "https://www.goodshort.com"
    override var name = "GoodShort"
    override var supportedTypes = setOf(TvType.Movie)
}
