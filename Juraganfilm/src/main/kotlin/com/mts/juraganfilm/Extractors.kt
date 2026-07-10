package com.mts.juraganfilm

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Indo666Com : StreamWishExtractor() {
    override var name = "Indo666Com"
    override var mainUrl = "https://indo666.com"
}

class ApiLivechatincCom : StreamWishExtractor() {
    override var name = "ApiLivechatincCom"
    override var mainUrl = "https://api.livechatinc.com"
}

class FkuponCom : StreamWishExtractor() {
    override var name = "FkuponCom"
    override var mainUrl = "https://fkupon.com"
}

class ItunesAppleCom : StreamWishExtractor() {
    override var name = "ItunesAppleCom"
    override var mainUrl = "https://itunes.apple.com"
}

class DnsperfCom : StreamWishExtractor() {
    override var name = "DnsperfCom"
    override var mainUrl = "https://dnsperf.com"
}

class MediaBioSite : StreamWishExtractor() {
    override var name = "MediaBioSite"
    override var mainUrl = "https://media.bio.site"
}

class MyLivechatincCom : StreamWishExtractor() {
    override var name = "MyLivechatincCom"
    override var mainUrl = "https://my.livechatinc.com"
}

class AccountsLivechatCom : StreamWishExtractor() {
    override var name = "AccountsLivechatCom"
    override var mainUrl = "https://accounts.livechat.com"
}

class Cina777Com : StreamWishExtractor() {
    override var name = "Cina777Com"
    override var mainUrl = "https://cina777.com"
}

class TLy : StreamWishExtractor() {
    override var name = "TLy"
    override var mainUrl = "https://t.ly"
}

class Cina1Com : StreamWishExtractor() {
    override var name = "Cina1Com"
    override var mainUrl = "https://cina1.com"
}

class WapCina777Com : StreamWishExtractor() {
    override var name = "WapCina777Com"
    override var mainUrl = "https://wap.cina777.com"
}

class HistoryJlfafafa3Com : StreamWishExtractor() {
    override var name = "HistoryJlfafafa3Com"
    override var mainUrl = "https://history.jlfafafa3.com"
}

class WapCina3Xyz : StreamWishExtractor() {
    override var name = "WapCina3Xyz"
    override var mainUrl = "https://wap.cina3.xyz"
}

class Bravobet77Monster : StreamWishExtractor() {
    override var name = "Bravobet77Monster"
    override var mainUrl = "https://bravobet77.monster"
}

class Bravobet77Webns2Live : StreamWishExtractor() {
    override var name = "Bravobet77Webns2Live"
    override var mainUrl = "https://bravobet77.web-ns2.live"
}

class StoretnIn : StreamWishExtractor() {
    override var name = "StoretnIn"
    override var mainUrl = "https://storetn.in"
}

class LivechatCom : StreamWishExtractor() {
    override var name = "LivechatCom"
    override var mainUrl = "https://livechat.com"
}

class TextCom : StreamWishExtractor() {
    override var name = "TextCom"
    override var mainUrl = "https://text.com"
}

class PlatformTextCom : StreamWishExtractor() {
    override var name = "PlatformTextCom"
    override var mainUrl = "https://platform.text.com"
}

class WindblueCfd : StreamWishExtractor() {
    override var name = "WindblueCfd"
    override var mainUrl = "https://windblue.cfd"
}

class JsStripeCom : StreamWishExtractor() {
    override var name = "JsStripeCom"
    override var mainUrl = "https://js.stripe.com"
}

class AppOnescreenerCom : StreamWishExtractor() {
    override var name = "AppOnescreenerCom"
    override var mainUrl = "https://app.onescreener.com"
}

class ApkblockS3Apnortheast1AmazonawsCom : StreamWishExtractor() {
    override var name = "ApkblockS3Apnortheast1AmazonawsCom"
    override var mainUrl = "https://apk-block.s3.ap-northeast-1.amazonaws.com"
}

class VpnnawalaSite : StreamWishExtractor() {
    override var name = "VpnnawalaSite"
    override var mainUrl = "https://vpnnawala.site"
}

class Ratu89Com : StreamWishExtractor() {
    override var name = "Ratu89Com"
    override var mainUrl = "https://ratu89.com"
}

class Vpn89Site : StreamWishExtractor() {
    override var name = "Vpn89Site"
    override var mainUrl = "https://vpn89.site"
}

class Gratu89Com : StreamWishExtractor() {
    override var name = "Gratu89Com"
    override var mainUrl = "https://gratu89.com"
}

class BioSite : StreamWishExtractor() {
    override var name = "BioSite"
    override var mainUrl = "https://bio.site"
}

class Gaza88Com : StreamWishExtractor() {
    override var name = "Gaza88Com"
    override var mainUrl = "https://gaza88.com"
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
