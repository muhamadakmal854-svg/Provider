package com.mts.klikxxid

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.app
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import org.jsoup.Jsoup
import android.util.Log
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor

open class Strp2pBaseExtractor(override val name: String, override val mainUrl: String) : ExtractorApi() {
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val cleanUrl = url.replace("/e/", "/#")
            val hashIndex = cleanUrl.indexOf('#')
            if (hashIndex < 0) return
            val id = cleanUrl.substring(hashIndex + 1)
            
            val baseUri = java.net.URI(cleanUrl)
            val domainUrl = "${baseUri.scheme}://${baseUri.host}"
            val videoApiUrl = "$domainUrl/api/v1/video?id=$id"
            
            val resText = app.get(videoApiUrl, headers = mapOf(
                "Referer" to cleanUrl,
                "Accept" to "application/json, text/plain, */*",
                "X-Requested-With" to "XMLHttpRequest"
            )).text.trim()
            
            if (resText.isBlank()) return
            
            val decrypted = decryptAes128Cbc(
                resText,
                "kiemtienmua911ca",
                "1234567890oiuytr"
            )
            
            val obj = JSONObject(decrypted)
            
            // 1. cfNative
            val cfNative = obj.optString("cfNative", "")
            if (cfNative.isNotBlank()) {
                callback(
                    newExtractorLink(
                        source = name,
                        name = "Cloudflare Proxy",
                        url = cfNative,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$domainUrl/"
                        this.headers = mapOf("Origin" to domainUrl)
                    }
                )
            }
            
            // 2. Direct source M3U8
            val source = obj.optString("source", "")
            if (source.isNotBlank()) {
                callback(
                    newExtractorLink(
                        source = name,
                        name = "Direct IP",
                        url = source,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$domainUrl/"
                        this.headers = mapOf("Origin" to domainUrl)
                    }
                )
            }
            
            // 3. Subtitles (if any)
            val tracks = obj.optJSONArray("tracks")
            if (tracks != null) {
                for (i in 0 until tracks.length()) {
                    val track = tracks.optJSONObject(i) ?: continue
                    val file = track.optString("file", "")
                    val label = track.optString("label", "")
                    if (file.isNotBlank()) {
                        subtitleCallback(
                            com.lagradost.cloudstream3.SubtitleFile(
                                lang = label.ifBlank { "English" },
                                url = file
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Strp2pExtractor", "Error in ${name}: ${e.message}")
        }
    }

    private fun decryptAes128Cbc(hexCipher: String, keyStr: String, ivStr: String): String {
        val keySpec = SecretKeySpec(keyStr.toByteArray(Charsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(ivStr.toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        
        val cipherBytes = hexCipher.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val decryptedBytes = cipher.doFinal(cipherBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}

class Strp2pExtractor : Strp2pBaseExtractor("Strp2p", "https://strp2p.site")
class UpnsExtractor : Strp2pBaseExtractor("Upns", "https://upns.one")
class HgcloudExtractor : Strp2pBaseExtractor("Hgcloud", "https://hgcloud.to")



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

class IbanCom : StreamWishExtractor() {
    override var name = "IbanCom"
    override var mainUrl = "https://iban.com"
}

class Views4YouCom : StreamWishExtractor() {
    override var name = "Views4YouCom"
    override var mainUrl = "https://views4you.com"
}

class ImgLulucdnCom : StreamWishExtractor() {
    override var name = "ImgLulucdnCom"
    override var mainUrl = "https://img.lulucdn.com"
}

class Iujj82L8X5NtTnmrOrg : StreamWishExtractor() {
    override var name = "Iujj82L8X5NtTnmrOrg"
    override var mainUrl = "https://iujj82l8x5nt.tnmr.org"
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

class LuluvdoCom : StreamWishExtractor() {
    override var name = "LuluvdoCom"
    override var mainUrl = "https://luluvdo.com"
}

class LulustreamCom : StreamWishExtractor() {
    override var name = "LulustreamCom"
    override var mainUrl = "https://lulustream.com"
}

class VoeSx : Voe() {
    override var name = "VoeSx"
    override var mainUrl = "https://voe.sx"
}

class VeevTo : StreamWishExtractor() {
    override var name = "VeevTo"
    override var mainUrl = "https://veev.to"
}

class MediaFastcheckerUs : StreamWishExtractor() {
    override var name = "MediaFastcheckerUs"
    override var mainUrl = "https://media.fastchecker.us"
}

class RisdanlyCom : StreamWishExtractor() {
    override var name = "RisdanlyCom"
    override var mainUrl = "https://risdanly.com"
}

class ItunesAppleCom : StreamWishExtractor() {
    override var name = "ItunesAppleCom"
    override var mainUrl = "https://itunes.apple.com"
}

class DnsperfCom : StreamWishExtractor() {
    override var name = "DnsperfCom"
    override var mainUrl = "https://dnsperf.com"
}

class Emas188May14Ink : StreamWishExtractor() {
    override var name = "Emas188May14Ink"
    override var mainUrl = "https://emas188may14.ink"
}

class XfilesharingproDocsApiaryIo : StreamWishExtractor() {
    override var name = "XfilesharingproDocsApiaryIo"
    override var mainUrl = "https://xfilesharingpro.docs.apiary.io"
}

