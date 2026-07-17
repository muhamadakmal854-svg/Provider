package com.mts.kuronime

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

class KuronimeMoe : StreamWishExtractor() {
    override var name = "KuronimeMoe"
    override var mainUrl = "https://kuronime.moe"
}

class AssetsProductionLinktrEe : StreamWishExtractor() {
    override var name = "AssetsProductionLinktrEe"
    override var mainUrl = "https://assets.production.linktr.ee"
}

class AccessLineMe : StreamWishExtractor() {
    override var name = "AccessLineMe"
    override var mainUrl = "https://access.line.me"
}

class Playsobat : ExtractorApi() {
    override var name = "Playsobat"
    override var mainUrl = "https://playsobat.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val cleanUrl = url.replace(92.toChar().toString(), "")
            val response = app.get(cleanUrl, headers = mapOf("Referer" to (referer ?: mainUrl))).text

            val payloadRegex = Regex("window\\\\.payload\\\\s*=\\\\s*\\\"(.*?)\\\"")
            val match = payloadRegex.find(response)?.groupValues?.get(1) ?: return

            val cleanPayloadStr = unescapeJsString(match)
            val payloadJson = org.json.JSONObject(cleanPayloadStr)
            val ivBase64 = payloadJson.getString("iv")
            val dataBase64 = payloadJson.getString("data")

            val key = "96fb393f57087e9333cc067bf4aa378e".toByteArray(Charsets.UTF_8)
            val iv = android.util.Base64.decode(ivBase64, android.util.Base64.DEFAULT)
            val ciphertext = android.util.Base64.decode(dataBase64, android.util.Base64.DEFAULT)

            val decryptedBytes = decryptAesCbc(ciphertext, key, iv)
            val decryptedStr = String(decryptedBytes, Charsets.UTF_8)

            val linksJson = org.json.JSONObject(decryptedStr)
            linksJson.keys().forEach { serverKey ->
                val serverUrl = linksJson.getString(serverKey)
                if (serverUrl.isNotBlank()) {
                    loadExtractor(serverUrl, cleanUrl, subtitleCallback, callback)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun decryptAesCbc(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val spec = javax.crypto.spec.SecretKeySpec(key, "AES")
        val parameterSpec = javax.crypto.spec.IvParameterSpec(iv)
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, spec, parameterSpec)
        return cipher.doFinal(ciphertext)
    }

    private fun unescapeJsString(str: String): String {
        val builder = java.lang.StringBuilder()
        var i = 0
        while (i < str.length) {
            val c = str[i]
            if (c == '\\' && i + 1 < str.length) {
                val next = str[i + 1]
                when (next) {
                    'n' -> builder.append('\n')
                    'r' -> builder.append('\r')
                    't' -> builder.append('\t')
                    'b' -> builder.append('\b')
                    'f' -> builder.append('\u000C')
                    '"' -> builder.append('"')
                    '\'' -> builder.append('\'')
                    '\\' -> builder.append('\\')
                    '/' -> builder.append('/')
                    else -> builder.append(next)
                }
                i += 2
            } else {
                builder.append(c)
                i++
            }
        }
        return builder.toString()
    }
}
