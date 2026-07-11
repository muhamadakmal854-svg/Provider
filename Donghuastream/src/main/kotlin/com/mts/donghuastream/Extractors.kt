package com.mts.donghuastream

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.extractors.Odnoklassniki
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class PlayStreamplayCoIn : StreamWishExtractor() {
    override var name = "PlayStreamplayCoIn"
    override var mainUrl = "https://play.streamplay.co.in"
}

class GeoDailymotionCom : Dailymotion() {
    override var name = "GeoDailymotionCom"
    override var mainUrl = "https://geo.dailymotion.com"
}

class VikingfileCom : StreamWishExtractor() {
    override var name = "VikingfileCom"
    override var mainUrl = "https://vikingfile.com"
}

class RumbleCom : ExtractorApi() {
    override var name = "RumbleCom"
    override var mainUrl = "https://rumble.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer ?: "$mainUrl/")
        val scriptData = response.document.selectFirst("script:containsData(mp4)")?.data()
            ?.substringAfter("{\"mp4")?.substringBefore("\"evt\":{")
        if (scriptData == null) return

        val regex = Regex("""#url#:#(.*?)#|h#:(.*?)\\\\}""".replace('#', '"'))
        val matches = regex.findAll(scriptData)
        val processedUrls = mutableSetOf<String>()

        for (match in matches) {
            val rawUrl = match.groupValues[1]
            if (rawUrl.isBlank()) continue
            val cleanedUrl = rawUrl.replace("\\/", "/")
            if (!cleanedUrl.contains("rumble.com")) continue
            if (!cleanedUrl.endsWith(".m3u8")) continue
            if (!processedUrls.add(cleanedUrl)) continue

            val m3u8Response = app.get(cleanedUrl)
            val variantCount = "#EXT-X-STREAM-INF".toRegex().findAll(m3u8Response.text).count()
            if (variantCount > 1) {
                callback.invoke(
                    newExtractorLink(
                        this@RumbleCom.name,
                        "Rumble",
                        cleanedUrl,
                        ExtractorLinkType.M3U8
                    )
                )
                break
            }
        }
    }
}

class OkRu : Odnoklassniki() {
    override var name = "OkRu"
    override var mainUrl = "https://ok.ru"
}
