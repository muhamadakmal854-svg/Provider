package com.mts.donghub

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class KiRooserlyxoseShop : StreamWishExtractor() {
    override var name = "KiRooserlyxoseShop"
    override var mainUrl = "https://ki.rooserlyxose.shop"
}

class DailymotionCom : Dailymotion() {
    override var name = "DailymotionCom"
    override var mainUrl = "https://dailymotion.com"
}

class GeoDailymotionCom : Dailymotion() {
    override var name = "GeoDailymotionCom"
    override var mainUrl = "https://geo.dailymotion.com"
}

class MorenciusCom : ExtractorApi() {
    override var name = "MorenciusCom"
    override var mainUrl = "https://morencius.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val ref = referer ?: mainUrl
        val response = app.get(url, referer = ref, headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        ))
        val doc = response.document
        val scripts = doc.select("script").joinToString(" ") { it.data() }

        val m3u8Regex   = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE)
        val mp4Regex    = Regex("""["'](https?://[^"']+\.mp4[^"']*)["']""", RegexOption.IGNORE_CASE)
        val fileRegex   = Regex("""file["']?\s*:\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
        val sourceRegex = Regex("""src["']?\s*:\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)

        val videoUrl = m3u8Regex.find(scripts)?.groupValues?.get(1)
            ?: fileRegex.find(scripts)?.groupValues?.get(1)
            ?: mp4Regex.find(scripts)?.groupValues?.get(1)
            ?: sourceRegex.find(scripts)?.groupValues?.get(1)

        if (videoUrl != null) {
            val isM3u8 = videoUrl.contains(".m3u8")
            if (isM3u8) {
                generateM3u8(this.name, videoUrl, ref).forEach(callback)
            } else {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name   = this.name,
                        url    = videoUrl,
                        type   = ExtractorLinkType.VIDEO
                    ) {
                        quality = Qualities.Unknown.value
                        this.referer = ref
                    }
                )
            }
        }
    }
}
