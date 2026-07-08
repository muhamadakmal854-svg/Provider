package com.mts.dutafilm

import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class MessengerCom : StreamWishExtractor() {
    override var name = "MessengerCom"
    override var mainUrl = "https://messenger.com"
}

class MetaCom : StreamWishExtractor() {
    override var name = "MetaCom"
    override var mainUrl = "https://meta.com"
}

class MetaAi : StreamWishExtractor() {
    override var name = "MetaAi"
    override var mainUrl = "https://meta.ai"
}

class ThreadsCom : StreamWishExtractor() {
    override var name = "ThreadsCom"
    override var mainUrl = "https://threads.com"
}

class OrOnenessparmackCom : StreamWishExtractor() {
    override var name = "OrOnenessparmackCom"
    override var mainUrl = "https://or.onenessparmack.com"
}

class Dutafilm77MantabMen : StreamWishExtractor() {
    override var name = "Dutafilm77MantabMen"
    override var mainUrl = "https://dutafilm77.mantab.men"
}
