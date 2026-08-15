package com.mtsflix.mytvlive

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class MYTVLiveProvider : MainAPI() {
    override var mainUrl = "https://mana2.my/live"
    override var name = "MYTVLive"
    override var supportedTypes = setOf(TvType.Movie)
}
