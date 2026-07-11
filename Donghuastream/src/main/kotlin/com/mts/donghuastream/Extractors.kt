package com.mts.donghuastream

import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.extractors.Odnoklassniki
import com.lagradost.cloudstream3.extractors.Rumble
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class PlayStreamplayCoIn : StreamWishExtractor() {
    override var name = "PlayStreamplayCoIn"
    override var mainUrl = "https://play.streamplay.co.in"
}

class GeoDailymotionCom : Dailymotion() {
    override var name = "GeoDailymotionCom"
    override var mainUrl = "https://geo.dailymotion.com"
}

class VikingfileCom : StreamWishExtractor() {
    override var name = "VikingfileCom"
    override var mainUrl = "https://vikingfile.com"
}

class RumbleCom : Rumble() {
    override var name = "RumbleCom"
    override var mainUrl = "https://rumble.com"
}

class OkRu : Odnoklassniki() {
    override var name = "OkRu"
    override var mainUrl = "https://ok.ru"
}
