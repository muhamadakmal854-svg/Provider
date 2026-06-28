package com.mts.xxxxx

import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class StaticahXhcdnCom : StreamWishExtractor() {
    override val name = "StaticahXhcdnCom"
    override val mainUrl = "https://static-ah.xhcdn.com"
}

class IcvrmnssXhcdnCom : StreamWishExtractor() {
    override val name = "IcvrmnssXhcdnCom"
    override val mainUrl = "https://ic-vrm-nss.xhcdn.com"
}

class CollectorXhaccessCom : StreamWishExtractor() {
    override val name = "CollectorXhaccessCom"
    override val mainUrl = "https://collector.xhaccess.com"
}

class ThumbvnssXhcdnCom : StreamWishExtractor() {
    override val name = "ThumbvnssXhcdnCom"
    override val mainUrl = "https://thumb-v-nss.xhcdn.com"
}
