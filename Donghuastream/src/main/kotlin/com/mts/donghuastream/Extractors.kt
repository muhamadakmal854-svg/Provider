package com.mts.donghuastream

import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class Porto : StreamWishExtractor() {
    override val name = "Porto"
    override val mainUrl = "https://porto"
}

class VikingfileCom : StreamWishExtractor() {
    override val name = "VikingfileCom"
    override val mainUrl = "https://vikingfile.com"
}
