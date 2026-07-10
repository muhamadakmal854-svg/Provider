package com.mts.dutafilm

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

class MessengerCom : StreamWishExtractor() {
    override var name = "MessengerCom"
    override var mainUrl = "https://messenger.com"
}

class MetaCom : StreamWishExtractor() {
    override var name = "MetaCom"
    override var mainUrl = "https://meta.com"
}

class MetaAi : StreamWishExtractor() {
    override var name = "MetaAi"
    override var mainUrl = "https://meta.ai"
}

class ThreadsCom : StreamWishExtractor() {
    override var name = "ThreadsCom"
    override var mainUrl = "https://threads.com"
}

class OrOnenessparmackCom : StreamWishExtractor() {
    override var name = "OrOnenessparmackCom"
    override var mainUrl = "https://or.onenessparmack.com"
}

class Dutafilm77MantabMen : StreamWishExtractor() {
    override var name = "Dutafilm77MantabMen"
    override var mainUrl = "https://dutafilm77.mantab.men"
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
