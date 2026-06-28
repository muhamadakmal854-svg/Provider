package com.mts.idlix

import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class CvGenipspillionCom : StreamWishExtractor() {
    override val name = "CvGenipspillionCom"
    override val mainUrl = "https://cv.genipspillion.com"
}
