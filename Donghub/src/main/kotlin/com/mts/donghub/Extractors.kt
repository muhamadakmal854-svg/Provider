package com.mts.donghub

import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class TzWrongsakeCfd : StreamWishExtractor() {
    override val name = "TzWrongsakeCfd"
    override val mainUrl = "https://tz.wrongsake.cfd"
}

class GeoDailymotionCom : Dailymotion() {
    override val name = "GeoDailymotionCom"
    override val mainUrl = "https://geo.dailymotion.com"
}
