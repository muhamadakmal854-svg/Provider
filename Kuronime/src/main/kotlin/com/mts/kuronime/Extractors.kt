package com.mts.kuronime

import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class KuronimeMoe : StreamWishExtractor() {
    override var name = "KuronimeMoe"
    override var mainUrl = "https://kuronime.moe"
}

class AssetsProductionLinktrEe : StreamWishExtractor() {
    override var name = "AssetsProductionLinktrEe"
    override var mainUrl = "https://assets.production.linktr.ee"
}

class AccessLineMe : StreamWishExtractor() {
    override var name = "AccessLineMe"
    override var mainUrl = "https://access.line.me"
}
