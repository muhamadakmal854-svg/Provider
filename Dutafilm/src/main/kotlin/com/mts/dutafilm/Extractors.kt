package com.mts.dutafilm

import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class MessengerCom : StreamWishExtractor() {
    override val name = "MessengerCom"
    override val mainUrl = "https://messenger.com"
}

class MetaCom : StreamWishExtractor() {
    override val name = "MetaCom"
    override val mainUrl = "https://meta.com"
}

class MetaAi : StreamWishExtractor() {
    override val name = "MetaAi"
    override val mainUrl = "https://meta.ai"
}

class ThreadsCom : StreamWishExtractor() {
    override val name = "ThreadsCom"
    override val mainUrl = "https://threads.com"
}

class OrOnenessparmackCom : StreamWishExtractor() {
    override val name = "OrOnenessparmackCom"
    override val mainUrl = "https://or.onenessparmack.com"
}

class Dutafilm77MantabMen : StreamWishExtractor() {
    override val name = "Dutafilm77MantabMen"
    override val mainUrl = "https://dutafilm77.mantab.men"
}
