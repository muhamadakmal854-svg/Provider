package com.mtsflix.layarotakulayarotaku

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class LayarOtakuProvider : MainAPI() {
    override var mainUrl = "https://www.xml-acronym-demystifier.org"
    override var name = "LayarOtaku"
    override var supportedTypes = setOf(TvType.Movie)
}
