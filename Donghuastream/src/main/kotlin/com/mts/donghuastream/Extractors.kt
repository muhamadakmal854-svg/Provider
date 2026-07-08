package com.mts.donghuastream

import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class Porto : StreamWishExtractor() {
    override var name = "Porto"
    override var mainUrl = "https://porto"
}

class VikingfileCom : StreamWishExtractor() {
    override var name = "VikingfileCom"
    override var mainUrl = "https://vikingfile.com"
}
