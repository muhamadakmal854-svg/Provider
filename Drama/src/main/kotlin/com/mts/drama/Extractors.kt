package com.mts.drama

import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class TeleLk21De : StreamWishExtractor() {
    override val name = "TeleLk21De"
    override val mainUrl = "https://tele.lk21.de"
}

class D21Team : StreamWishExtractor() {
    override val name = "D21Team"
    override val mainUrl = "https://d21.team"
}

class Tv4NontondramaMy : StreamWishExtractor() {
    override val name = "Tv4NontondramaMy"
    override val mainUrl = "https://tv4.nontondrama.my"
}
