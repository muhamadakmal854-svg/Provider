package com.mts.kuronime

import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class KuronimeMoe : StreamWishExtractor() {
    override val name = "KuronimeMoe"
    override val mainUrl = "https://kuronime.moe"
}

class AssetsProductionLinktrEe : StreamWishExtractor() {
    override val name = "AssetsProductionLinktrEe"
    override val mainUrl = "https://assets.production.linktr.ee"
}

class AccessLineMe : StreamWishExtractor() {
    override val name = "AccessLineMe"
    override val mainUrl = "https://access.line.me"
}
