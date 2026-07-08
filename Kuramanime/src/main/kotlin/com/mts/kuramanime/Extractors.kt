package com.mts.kuramanime

import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class KuramaSubindoNet : StreamWishExtractor() {
    override val name = "KuramaSubindoNet"
    override val mainUrl = "https://kurama.subindo.net"
}

class KuramashopNet : StreamWishExtractor() {
    override val name = "KuramashopNet"
    override val mainUrl = "https://kuramashop.net"
}

class TrakteerId : StreamWishExtractor() {
    override val name = "TrakteerId"
    override val mainUrl = "https://trakteer.id"
}

class SaweriaCo : StreamWishExtractor() {
    override val name = "SaweriaCo"
    override val mainUrl = "https://saweria.co"
}
