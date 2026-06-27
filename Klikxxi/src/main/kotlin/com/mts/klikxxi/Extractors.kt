package com.mts.klikxxi

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class ZombiemealCom : StreamWishExtractor() {
    override val name = "ZombiemealCom"
    override val mainUrl = "https://zombiemeal.com"
}

class VipIdlix21Pro : StreamWishExtractor() {
    override val name = "VipIdlix21Pro"
    override val mainUrl = "https://vip.idlix21.pro"
}

class EmbedpyroxXyz : ExtractorApi() {
    override val name = "EmbedpyroxXyz"
    override val mainUrl = "https://embedpyrox.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = url.replace(92.toChar().toString(), "")
        val id = cleanUrl.substringAfter("/video/").substringBefore("/").substringBefore("?")
        if (id.isEmpty()) return

        val ajaxUrl = "$mainUrl/player/index.php?data=$id&do=getVideo"
        val response = app.post(
            url = ajaxUrl,
            data = mapOf("hash" to id, "r" to (referer ?: "")),
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to cleanUrl,
                "Origin" to mainUrl,
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
            )
        )
        if (!response.isSuccessful) return
        val text = response.text
        val securedLink = Regex("\"securedLink\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
        if (securedLink != null) {
            val finalUrl = securedLink.replace("\\/", "/")
            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = finalUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = cleanUrl
                }
            )
        }
    }
}

class AbyssplayerCom : StreamWishExtractor() {
    override val name = "AbyssplayerCom"
    override val mainUrl = "https://abyssplayer.com"
}

class CvGenipspillionCom : StreamWishExtractor() {
    override val name = "CvGenipspillionCom"
    override val mainUrl = "https://cv.genipspillion.com"
}

class MorenciusCom : StreamWishExtractor() {
    override val name = "MorenciusCom"
    override val mainUrl = "https://morencius.com"
}

class PortalMgaOrgMt : StreamWishExtractor() {
    override val name = "PortalMgaOrgMt"
    override val mainUrl = "https://portal.mga.org.mt"
}

class RedorangeComMt : StreamWishExtractor() {
    override val name = "RedorangeComMt"
    override val mainUrl = "https://redorange.com.mt"
}

class Server9HdigitalCom : StreamWishExtractor() {
    override val name = "Server9HdigitalCom"
    override val mainUrl = "https://9hdigital.com"
}

class UseTypekitNet : StreamWishExtractor() {
    override val name = "UseTypekitNet"
    override val mainUrl = "https://use.typekit.net"
}

class VincentdesignCa : StreamWishExtractor() {
    override val name = "VincentdesignCa"
    override val mainUrl = "https://vincentdesign.ca"
}

class ResponsiblegamblingOrg : StreamWishExtractor() {
    override val name = "ResponsiblegamblingOrg"
    override val mainUrl = "https://responsiblegambling.org"
}

class AjaxAspnetcdnCom : StreamWishExtractor() {
    override val name = "AjaxAspnetcdnCom"
    override val mainUrl = "https://ajax.aspnetcdn.com"
}

class AppFive9Eu : StreamWishExtractor() {
    override val name = "AppFive9Eu"
    override val mainUrl = "https://app.five9.eu"
}

class GambleawareOrg : StreamWishExtractor() {
    override val name = "GambleawareOrg"
    override val mainUrl = "https://gambleaware.org"
}

class IYtimgCom : StreamWishExtractor() {
    override val name = "IYtimgCom"
    override val mainUrl = "https://i.ytimg.com"
}

class YoutuBe : StreamWishExtractor() {
    override val name = "YoutuBe"
    override val mainUrl = "https://youtu.be"
}
