package com.mts.kuramanime

import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class KuramaSubindoNet : StreamWishExtractor() {
    override var name = "KuramaSubindoNet"
    override var mainUrl = "https://kurama.subindo.net"
}

class KuramashopNet : StreamWishExtractor() {
    override var name = "KuramashopNet"
    override var mainUrl = "https://kuramashop.net"
}

class TrakteerId : StreamWishExtractor() {
    override var name = "TrakteerId"
    override var mainUrl = "https://trakteer.id"
}

class SaweriaCo : StreamWishExtractor() {
    override var name = "SaweriaCo"
    override var mainUrl = "https://saweria.co"
}
