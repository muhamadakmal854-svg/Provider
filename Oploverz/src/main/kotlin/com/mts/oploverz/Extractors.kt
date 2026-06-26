package com.mts.oploverz

import com.lagradost.cloudstream3.extractors.StreamWishExtractor

class AcscdnCom : StreamWishExtractor() {
    override val name = "AcscdnCom"
    override val mainUrl = "https://acscdn.com"
}

class BloggerCom : StreamWishExtractor() {
    override val name = "BloggerCom"
    override val mainUrl = "https://blogger.com"
}
