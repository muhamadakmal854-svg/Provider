package com.mts.klikxxid

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class OracleCom : StreamWishExtractor() {
    override var name = "OracleCom"
    override var mainUrl = "https://oracle.com"
}

class ConsentTrustarcCom : StreamWishExtractor() {
    override var name = "ConsentTrustarcCom"
    override var mainUrl = "https://consent.trustarc.com"
}

class LoginApiaryIo : StreamWishExtractor() {
    override var name = "LoginApiaryIo"
    override var mainUrl = "https://login.apiary.io"
}

class ApiaryIo : StreamWishExtractor() {
    override var name = "ApiaryIo"
    override var mainUrl = "https://apiary.io"
}

class ImgprodcmsrtmicrosoftcomAkamaizedNet : StreamWishExtractor() {
    override var name = "ImgprodcmsrtmicrosoftcomAkamaizedNet"
    override var mainUrl = "https://img-prod-cms-rt-microsoft-com.akamaized.net"
}

class CopilotMicrosoftCom : StreamWishExtractor() {
    override var name = "CopilotMicrosoftCom"
    override var mainUrl = "https://copilot.microsoft.com"
}

class SupportMicrosoftCom : StreamWishExtractor() {
    override var name = "SupportMicrosoftCom"
    override var mainUrl = "https://support.microsoft.com"
}

class ProductsOfficeCom : StreamWishExtractor() {
    override var name = "ProductsOfficeCom"
    override var mainUrl = "https://products.office.com"
}

class OnedriveLiveCom : StreamWishExtractor() {
    override var name = "OnedriveLiveCom"
    override var mainUrl = "https://onedrive.live.com"
}

class OutlookLiveCom : StreamWishExtractor() {
    override var name = "OutlookLiveCom"
    override var mainUrl = "https://outlook.live.com"
}

class AssetsMailerliteCom : StreamWishExtractor() {
    override var name = "AssetsMailerliteCom"
    override var mainUrl = "https://assets.mailerlite.com"
}

class C0WpCom : StreamWishExtractor() {
    override var name = "C0WpCom"
    override var mainUrl = "https://c0.wp.com"
}

class JsHsscriptsCom : StreamWishExtractor() {
    override var name = "JsHsscriptsCom"
    override var mainUrl = "https://js.hs-scripts.com"
}

class I0WpCom : StreamWishExtractor() {
    override var name = "I0WpCom"
    override var mainUrl = "https://i0.wp.com"
}

class LakemedelsverketSe : StreamWishExtractor() {
    override var name = "LakemedelsverketSe"
    override var mainUrl = "https://lakemedelsverket.se"
}

class EmbedTawkTo : StreamWishExtractor() {
    override var name = "EmbedTawkTo"
    override var mainUrl = "https://embed.tawk.to"
}

class LitLibguidesCom : StreamWishExtractor() {
    override var name = "LitLibguidesCom"
    override var mainUrl = "https://lit.libguides.com"
}

class TextbuddyCom : StreamWishExtractor() {
    override var name = "TextbuddyCom"
    override var mainUrl = "https://textbuddy.com"
}

class TheresanaiforthatCom : StreamWishExtractor() {
    override var name = "TheresanaiforthatCom"
    override var mainUrl = "https://theresanaiforthat.com"
}

class ProducthuntCom : StreamWishExtractor() {
    override var name = "ProducthuntCom"
    override var mainUrl = "https://producthunt.com"
}

class IbanCom : StreamWishExtractor() {
    override var name = "IbanCom"
    override var mainUrl = "https://iban.com"
}

class MensjournalCom : StreamWishExtractor() {
    override var name = "MensjournalCom"
    override var mainUrl = "https://mensjournal.com"
}

class RadaronlineCom : StreamWishExtractor() {
    override var name = "RadaronlineCom"
    override var mainUrl = "https://radaronline.com"
}

class EntrepreneurCom : StreamWishExtractor() {
    override var name = "EntrepreneurCom"
    override var mainUrl = "https://entrepreneur.com"
}

class DailycallerCom : StreamWishExtractor() {
    override var name = "DailycallerCom"
    override var mainUrl = "https://dailycaller.com"
}

class NdtvCom : StreamWishExtractor() {
    override var name = "NdtvCom"
    override var mainUrl = "https://ndtv.com"
}

class MicrosoftCom : StreamWishExtractor() {
    override var name = "MicrosoftCom"
    override var mainUrl = "https://microsoft.com"
}

class VapehusetSe : StreamWishExtractor() {
    override var name = "VapehusetSe"
    override var mainUrl = "https://vapehuset.se"
}

class BuycheapestfollowersCom : StreamWishExtractor() {
    override var name = "BuycheapestfollowersCom"
    override var mainUrl = "https://buycheapestfollowers.com"
}

class AitexthumanizerCom : StreamWishExtractor() {
    override var name = "AitexthumanizerCom"
    override var mainUrl = "https://ai-text-humanizer.com"
}

class Views4YouCom : StreamWishExtractor() {
    override var name = "Views4YouCom"
    override var mainUrl = "https://views4you.com"
}

class VoeSx : Voe() {
    override var name = "VoeSx"
    override var mainUrl = "https://voe.sx"
}

class StaticVeevcdnCo : StreamWishExtractor() {
    override var name = "StaticVeevcdnCo"
    override var mainUrl = "https://static.veevcdn.co"
}

class RisdanlyCom : StreamWishExtractor() {
    override var name = "RisdanlyCom"
    override var mainUrl = "https://risdanly.com"
}

class Form6MbrCom : StreamWishExtractor() {
    override var name = "Form6MbrCom"
    override var mainUrl = "https://form.6mbr.com"
}

class Emas188Jun25Com : StreamWishExtractor() {
    override var name = "Emas188Jun25Com"
    override var mainUrl = "https://emas188jun25.com"
}

class Emas188Love : StreamWishExtractor() {
    override var name = "Emas188Love"
    override var mainUrl = "https://emas188.love"
}

class OneOneOneOne : StreamWishExtractor() {
    override var name = "OneOneOneOne"
    override var mainUrl = "https://one.one.one.one"
}

class LivechatCom : StreamWishExtractor() {
    override var name = "LivechatCom"
    override var mainUrl = "https://livechat.com"
}

class MyLivechatincCom : StreamWishExtractor() {
    override var name = "MyLivechatincCom"
    override var mainUrl = "https://my.livechatinc.com"
}

class AccountsLivechatCom : StreamWishExtractor() {
    override var name = "AccountsLivechatCom"
    override var mainUrl = "https://accounts.livechat.com"
}

class Emas188May14Ink : StreamWishExtractor() {
    override var name = "Emas188May14Ink"
    override var mainUrl = "https://emas188may14.ink"
}

class ImgLulucdnCom : StreamWishExtractor() {
    override var name = "ImgLulucdnCom"
    override var mainUrl = "https://img.lulucdn.com"
}

class Dnj8Ngd6Kq6YTnmrOrg : StreamWishExtractor() {
    override var name = "Dnj8Ngd6Kq6YTnmrOrg"
    override var mainUrl = "https://dnj8ngd6kq6y.tnmr.org"
}

class Dh8Azcl753E1ECloudfrontNet : StreamWishExtractor() {
    override var name = "Dh8Azcl753E1ECloudfrontNet"
    override var mainUrl = "https://dh8azcl753e1e.cloudfront.net"
}

class YfDiasyrmunionicCom : StreamWishExtractor() {
    override var name = "YfDiasyrmunionicCom"
    override var mainUrl = "https://yf.diasyrmunionic.com"
}

class Server36784GomatinequisheoiCom : StreamWishExtractor() {
    override var name = "Server36784GomatinequisheoiCom"
    override var mainUrl = "https://36784.gomatinequisheoi.com"
}

class LuluvidCom : StreamWishExtractor() {
    override var name = "LuluvidCom"
    override var mainUrl = "https://luluvid.com"
}

class LulustreamCom : StreamWishExtractor() {
    override var name = "LulustreamCom"
    override var mainUrl = "https://lulustream.com"
}

class Cdn1015CdntnmrOrg : StreamWishExtractor() {
    override var name = "Cdn1015CdntnmrOrg"
    override var mainUrl = "https://cdn1015.cdn-tnmr.org"
}

class PlayerXExtractor : ExtractorApi() {
    override var name = "PlayerXExtractor"
    override var mainUrl = "https://ezplayer.stream"
    override val requiresReferer = true

    private fun decryptAesCbc(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val spec = javax.crypto.spec.SecretKeySpec(key, "AES")
        val parameterSpec = javax.crypto.spec.IvParameterSpec(iv)
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, spec, parameterSpec)
        return cipher.doFinal(ciphertext)
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val cleanUrl = url.replace(92.toChar().toString(), "")
            val uri = java.net.URI(cleanUrl)
            val host = uri.host ?: "playerx.ezplayer.stream"
            val hash = uri.fragment ?: ""
            if (hash.isEmpty()) return
            val id = hash.replace("#", "")
            if (id.isEmpty()) return

            val parentReferer = referer ?: "https://ww105.pencurimoviesubmalay.guru/"
            val videoUrl = "https://$host/api/v1/video?id=$id"

            val responseHex = app.get(videoUrl, headers = mapOf("Referer" to parentReferer)).text.trim()
            if (responseHex.isEmpty()) return

            val ciphertext = responseHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val key = "kiemtienmua911ca".toByteArray(Charsets.UTF_8)
            val iv = "1234567890oiuytr".toByteArray(Charsets.UTF_8)

            val decryptedBytes = decryptAesCbc(ciphertext, key, iv)
            val decryptedStr = String(decryptedBytes, Charsets.UTF_8)

            val metadata = org.json.JSONObject(decryptedStr)
            val pk = metadata.optJSONObject("pk")
            val k = pk?.optString("k") ?: ""
            val kx = pk?.optString("kx") ?: ""
            val title = metadata.optString("title", "PlayerX Stream")

            val qualityVal = when {
                title.contains("2160p", true) || title.contains("4k", true) -> Qualities.P2160.value
                title.contains("1080p", true) -> Qualities.P1080.value
                title.contains("720p", true) -> Qualities.P720.value
                title.contains("480p", true) -> Qualities.P480.value
                title.contains("360p", true) -> Qualities.P360.value
                else -> Qualities.Unknown.value
            }

            val cfNative = metadata.optString("cfNative")
            val source = metadata.optString("source")

            if (cfNative.isNotEmpty()) {
                var finalUrl = cfNative
                if (k.isNotEmpty() && !finalUrl.contains("k=")) {
                    finalUrl += if (finalUrl.contains("?")) "&k=$k&kx=$kx" else "?k=$k&kx=$kx"
                }
                callback(
                    newExtractorLink(
                        source = "PlayerX",
                        name = "PlayerX (CF)",
                        url = finalUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://$host/"
                        this.quality = qualityVal
                    }
                )
            }

            if (source.isNotEmpty()) {
                var finalUrl = source
                if (k.isNotEmpty() && !finalUrl.contains("k=")) {
                    finalUrl += if (finalUrl.contains("?")) "&k=$k&kx=$kx" else "?k=$k&kx=$kx"
                }
                callback(
                    newExtractorLink(
                        source = "PlayerX",
                        name = "PlayerX Direct",
                        url = finalUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://$host/"
                        this.quality = qualityVal
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
