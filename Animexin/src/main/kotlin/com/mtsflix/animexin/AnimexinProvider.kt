package com.mtsflix.animexinanimexin

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class AnimexinProvider : MainAPI() {
    override var mainUrl = "https://animexin.dev"
    override var name = "Animexin"
    override var supportedTypes = setOf(TvType.Movie)
}
