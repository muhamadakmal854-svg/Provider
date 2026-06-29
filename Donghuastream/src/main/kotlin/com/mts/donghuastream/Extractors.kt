package com.mts.donghuastream

import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class DonghuastreamCoIn : StreamWishExtractor() {
    override val name = "DonghuastreamCoIn"
    override val mainUrl = "https://donghuastream.co.in"
}

class AssetscdnWatchdisneyfeCom : StreamWishExtractor() {
    override val name = "AssetscdnWatchdisneyfeCom"
    override val mainUrl = "https://assets-cdn.watchdisneyfe.com"
}

class DonghuastreamIn : StreamWishExtractor() {
    override val name = "DonghuastreamIn"
    override val mainUrl = "https://donghuastream.in"
}

class VikingfileCom : StreamWishExtractor() {
    override val name = "VikingfileCom"
    override val mainUrl = "https://vikingfile.com"
}
