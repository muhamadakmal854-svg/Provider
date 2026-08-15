package com.mtsflix.rtmklikrtmklik

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class RTMKlikProvider : MainAPI() {
    override var mainUrl = "https://rtmklik.rtm.gov.my/live/tv/tv1"
    override var name = "RTMKlik"
    override var supportedTypes = setOf(TvType.Movie)
}
