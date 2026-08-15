package com.mtsflix.oploverz

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class OploverzProvider : MainAPI() {
    override var mainUrl = "https://oploverz.ch"
    override var name = "Oploverz"
    override var supportedTypes = setOf(TvType.Movie)
}
