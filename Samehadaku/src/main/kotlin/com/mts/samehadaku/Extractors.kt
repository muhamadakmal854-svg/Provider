package com.mts.samehadaku

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

class V1SamehadakuHow : StreamWishExtractor() {
    override var name = "V1SamehadakuHow"
    override var mainUrl = "https://v1.samehadaku.how"
}

class G66MoonMe : StreamWishExtractor() {
    override var name = "G66MoonMe"
    override var mainUrl = "https://g66moon.me"
}

class Tinig22Com : StreamWishExtractor() {
    override var name = "Tinig22Com"
    override var mainUrl = "https://tinig22.com"
}

class Dsuown9Evwz4YCloudfrontNet : StreamWishExtractor() {
    override var name = "Dsuown9Evwz4YCloudfrontNet"
    override var mainUrl = "https://dsuown9evwz4y.cloudfront.net"
}

class JuningroupCom : StreamWishExtractor() {
    override var name = "JuningroupCom"
    override var mainUrl = "https://juningroup.com"
}

class CdnAmpprojectOrg : StreamWishExtractor() {
    override var name = "CdnAmpprojectOrg"
    override var mainUrl = "https://cdn.ampproject.org"
}

class ApkblockS3Apnortheast1AmazonawsCom : StreamWishExtractor() {
    override var name = "ApkblockS3Apnortheast1AmazonawsCom"
    override var mainUrl = "https://apk-block.s3.ap-northeast-1.amazonaws.com"
}

class HistoryJlfafafa3Com : StreamWishExtractor() {
    override var name = "HistoryJlfafafa3Com"
    override var mainUrl = "https://history.jlfafafa3.com"
}

class ApkdepotS3Apnortheast1AmazonawsCom : StreamWishExtractor() {
    override var name = "ApkdepotS3Apnortheast1AmazonawsCom"
    override var mainUrl = "https://apk-depot.s3.ap-northeast-1.amazonaws.com"
}

class SlotjanjiCom : StreamWishExtractor() {
    override var name = "SlotjanjiCom"
    override var mainUrl = "https://slotjanji.com"
}

class QMeqrCom : StreamWishExtractor() {
    override var name = "QMeqrCom"
    override var mainUrl = "https://q.me-qr.com"
}

class GacortokyoCom : StreamWishExtractor() {
    override var name = "GacortokyoCom"
    override var mainUrl = "https://gacortokyo.com"
}

class JgjayaCom : StreamWishExtractor() {
    override var name = "JgjayaCom"
    override var mainUrl = "https://jg-jaya.com"
}

class Royal22Fun : StreamWishExtractor() {
    override var name = "Royal22Fun"
    override var mainUrl = "https://royal22.fun"
}

class Royal22GCom : StreamWishExtractor() {
    override var name = "Royal22GCom"
    override var mainUrl = "https://royal22g.com"
}

class Royal22Cc : StreamWishExtractor() {
    override var name = "Royal22Cc"
    override var mainUrl = "https://royal22.cc"
}

class OnlineThunderbirdhotelsCom : StreamWishExtractor() {
    override var name = "OnlineThunderbirdhotelsCom"
    override var mainUrl = "https://online.thunderbirdhotels.com"
}

class GacorTo : StreamWishExtractor() {
    override var name = "GacorTo"
    override var mainUrl = "https://gacor.to"
}

class Nex4DpoolsCom : StreamWishExtractor() {
    override var name = "Nex4DpoolsCom"
    override var mainUrl = "https://nex4dpools.com"
}

class GacorGg : StreamWishExtractor() {
    override var name = "GacorGg"
    override var mainUrl = "https://gacor.gg"
}

class ApkbankS3Apsoutheast1AmazonawsCom : StreamWishExtractor() {
    override var name = "ApkbankS3Apsoutheast1AmazonawsCom"
    override var mainUrl = "https://apk-bank.s3.ap-southeast-1.amazonaws.com"
}

class WapBmmega88Com : StreamWishExtractor() {
    override var name = "WapBmmega88Com"
    override var mainUrl = "https://wap.bmmega88.com"
}

class D2Rzzcn1Jnr24XCloudfrontNet : StreamWishExtractor() {
    override var name = "D2Rzzcn1Jnr24XCloudfrontNet"
    override var mainUrl = "https://d2rzzcn1jnr24x.cloudfront.net"
}

class WapBm88Net : StreamWishExtractor() {
    override var name = "WapBm88Net"
    override var mainUrl = "https://wap.bm-88.net"
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
