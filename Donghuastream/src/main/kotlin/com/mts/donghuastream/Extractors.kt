package com.mts.donghuastream

import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class Aqle3Com : StreamWishExtractor() {
    override val name = "Aqle3Com"
    override val mainUrl = "https://aqle3.com"
}

class AzzzCom : StreamWishExtractor() {
    override val name = "AzzzCom"
    override val mainUrl = "https://a-zzz.com"
}

class GeoDailymotionCom : Dailymotion() {
    override val name = "GeoDailymotionCom"
    override val mainUrl = "https://geo.dailymotion.com"
}

class PlayStreamplayCoIn : StreamWishExtractor() {
    override val name = "PlayStreamplayCoIn"
    override val mainUrl = "https://play.streamplay.co.in"
}
