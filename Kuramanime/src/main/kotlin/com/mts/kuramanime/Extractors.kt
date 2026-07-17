package com.mts.kuramanime

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

class KuramaSubindoNet : StreamWishExtractor() {
    override var name = "KuramaSubindoNet"
    override var mainUrl = "https://kurama.subindo.net"
}

class KuramashopNet : StreamWishExtractor() {
    override var name = "KuramashopNet"
    override var mainUrl = "https://kuramashop.net"
}

class TrakteerId : StreamWishExtractor() {
    override var name = "TrakteerId"
    override var mainUrl = "https://trakteer.id"
}

class SaweriaCo : StreamWishExtractor() {
    override var name = "SaweriaCo"
    override var mainUrl = "https://saweria.co"
}

open class KuramaRpmvipCom : ExtractorApi() {
    override var name = "KuramaRpmvipCom"
    override var mainUrl = "https://kurama.rpmvip.com"
    override val requiresReferer = true

    private fun decryptAesCbc(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val spec = javax.crypto.spec.SecretKeySpec(key, "AES")
        val parameterSpec = javax.crypto.spec.IvParameterSpec(iv)
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, spec, parameterSpec)
        val decrypted = cipher.doFinal(ciphertext)
        if (decrypted.isEmpty()) return decrypted
        val pad = decrypted[decrypted.size - 1].toInt()
        if (pad in 1..16) {
            return decrypted.copyOfRange(0, decrypted.size - pad)
        }
        return decrypted
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val cleanUrl = url.replace(92.toChar().toString(), "")
            val id = if (cleanUrl.contains("#")) {
                cleanUrl.substringAfter("#").substringBefore("?").substringBefore("&")
            } else if (cleanUrl.contains("id=")) {
                cleanUrl.substringAfter("id=").substringBefore("&")
            } else {
                cleanUrl.substringAfter("/embed/").substringBefore("?").substringBefore("&")
            }
            if (id.isEmpty()) return

            val refEncoded = java.net.URLEncoder.encode(referer ?: "taroscafe.com", "UTF-8")
            val apiRes = app.get(
                url = "$mainUrl/api/v1/video?id=$id&w=1920&h=1080&r=$refEncoded",
                headers = mapOf(
                    "Referer" to cleanUrl,
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "X-Requested-With" to "XMLHttpRequest"
                )
            )
            if (!apiRes.isSuccessful) return
            val hexData = apiRes.text.trim()
            if (hexData.isEmpty()) return

            val ciphertext = hexData.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

            val key = "kiemtienmua911ca".toByteArray(Charsets.UTF_8)
            val iv = "1234567890oiuytr".toByteArray(Charsets.UTF_8)

            val decryptedBytes = decryptAesCbc(ciphertext, key, iv)
            val decryptedStr = String(decryptedBytes, Charsets.UTF_8)

            val json = org.json.JSONObject(decryptedStr)
            if (json.has("cfNative")) {
                val cfNative = json.getString("cfNative")
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name - Server 1",
                        url = cfNative,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = cleanUrl
                    }
                )
            }
            if (json.has("source")) {
                val sourceUrl = json.getString("source")
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name - Server 2",
                        url = sourceUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = cleanUrl
                    }
                )
            }
            if (json.has("hlsVideoTiktok")) {
                val ttUrl = json.getString("hlsVideoTiktok")
                val finalTt = if (ttUrl.startsWith("http")) ttUrl else "$mainUrl$ttUrl"
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name - Server 3",
                        url = finalTt,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = cleanUrl
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

open class KuramaP2pstreamOnline : ExtractorApi() {
    override var name = "KuramaP2pstreamOnline"
    override var mainUrl = "https://kurama.p2pstream.online"
    override val requiresReferer = true

    private fun decryptAesCbc(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val spec = javax.crypto.spec.SecretKeySpec(key, "AES")
        val parameterSpec = javax.crypto.spec.IvParameterSpec(iv)
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, spec, parameterSpec)
        val decrypted = cipher.doFinal(ciphertext)
        if (decrypted.isEmpty()) return decrypted
        val pad = decrypted[decrypted.size - 1].toInt()
        if (pad in 1..16) {
            return decrypted.copyOfRange(0, decrypted.size - pad)
        }
        return decrypted
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val cleanUrl = url.replace(92.toChar().toString(), "")
            val id = if (cleanUrl.contains("#")) {
                cleanUrl.substringAfter("#").substringBefore("?").substringBefore("&")
            } else if (cleanUrl.contains("id=")) {
                cleanUrl.substringAfter("id=").substringBefore("&")
            } else {
                cleanUrl.substringAfter("/embed/").substringBefore("?").substringBefore("&")
            }
            if (id.isEmpty()) return

            val refEncoded = java.net.URLEncoder.encode(referer ?: "taroscafe.com", "UTF-8")
            val apiRes = app.get(
                url = "$mainUrl/api/v1/video?id=$id&w=1920&h=1080&r=$refEncoded",
                headers = mapOf(
                    "Referer" to cleanUrl,
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "X-Requested-With" to "XMLHttpRequest"
                )
            )
            if (!apiRes.isSuccessful) return
            val hexData = apiRes.text.trim()
            if (hexData.isEmpty()) return

            val ciphertext = hexData.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

            val key = "kiemtienmua911ca".toByteArray(Charsets.UTF_8)
            val iv = "1234567890oiuytr".toByteArray(Charsets.UTF_8)

            val decryptedBytes = decryptAesCbc(ciphertext, key, iv)
            val decryptedStr = String(decryptedBytes, Charsets.UTF_8)

            val json = org.json.JSONObject(decryptedStr)
            if (json.has("cfNative")) {
                val cfNative = json.getString("cfNative")
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name - Server 1",
                        url = cfNative,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = cleanUrl
                    }
                )
            }
            if (json.has("source")) {
                val sourceUrl = json.getString("source")
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name - Server 2",
                        url = sourceUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = cleanUrl
                    }
                )
            }
            if (json.has("hlsVideoTiktok")) {
                val ttUrl = json.getString("hlsVideoTiktok")
                val finalTt = if (ttUrl.startsWith("http")) ttUrl else "$mainUrl$ttUrl"
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name - Server 3",
                        url = finalTt,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = cleanUrl
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
