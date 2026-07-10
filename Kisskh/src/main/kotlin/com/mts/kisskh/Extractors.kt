package com.mts.kisskh

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

class MozillaOrg : StreamWishExtractor() {
    override var name = "MozillaOrg"
    override var mainUrl = "https://mozilla.org"
}

class AppleCom : StreamWishExtractor() {
    override var name = "AppleCom"
    override var mainUrl = "https://apple.com"
}

class VivaldiCom : StreamWishExtractor() {
    override var name = "VivaldiCom"
    override var mainUrl = "https://vivaldi.com"
}

class MicrosoftCom : StreamWishExtractor() {
    override var name = "MicrosoftCom"
    override var mainUrl = "https://microsoft.com"
}

class BraveCom : StreamWishExtractor() {
    override var name = "BraveCom"
    override var mainUrl = "https://brave.com"
}

class Server20577834Fs1Hubspotusercontentna1Net : StreamWishExtractor() {
    override var name = "Server20577834Fs1Hubspotusercontentna1Net"
    override var mainUrl = "https://20577834.fs1.hubspotusercontent-na1.net"
}

class Makaagency4740449HssitesCom : StreamWishExtractor() {
    override var name = "Makaagency4740449HssitesCom"
    override var mainUrl = "https://maka-agency-4740449.hs-sites.com"
}

class Server4740449HssitesCom : StreamWishExtractor() {
    override var name = "Server4740449HssitesCom"
    override var mainUrl = "https://4740449.hs-sites.com"
}

class MakaagencyCom : StreamWishExtractor() {
    override var name = "MakaagencyCom"
    override var mainUrl = "https://maka-agency.com"
}

class DocsJwplayerCom : StreamWishExtractor() {
    override var name = "DocsJwplayerCom"
    override var mainUrl = "https://docs.jwplayer.com"
}

class AtlassianCom : StreamWishExtractor() {
    override var name = "AtlassianCom"
    override var mainUrl = "https://atlassian.com"
}

class TagiviCom : StreamWishExtractor() {
    override var name = "TagiviCom"
    override var mainUrl = "https://tagivi.com"
}

class TickcounterCom : StreamWishExtractor() {
    override var name = "TickcounterCom"
    override var mainUrl = "https://tickcounter.com"
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
