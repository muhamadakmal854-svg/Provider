package com.mts.donghub

import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class AhCuneiblabbedCyou : StreamWishExtractor() {
    override val name = "AhCuneiblabbedCyou"
    override val mainUrl = "https://ah.cuneiblabbed.cyou"
}

class GeoDailymotionCom : Dailymotion() {
    override val name = "GeoDailymotionCom"
    override val mainUrl = "https://geo.dailymotion.com"
}
