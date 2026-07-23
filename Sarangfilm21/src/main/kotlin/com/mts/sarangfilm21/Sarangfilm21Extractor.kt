package com.mts.sarangfilm21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.getAndUnpack
import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AbyssExtractor : ExtractorApi() {
    override var name = "Abyss"
    override var mainUrl = "https://sarangfilm.diy"
    override val requiresReferer = false

    private fun decryptAesCtr(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val spec = SecretKeySpec(key, "AES")
        val parameterSpec = IvParameterSpec(iv)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, spec, parameterSpec)
        return cipher.doFinal(ciphertext)
    }

    private fun md5(input: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input)
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val cleanUrl = url.replace("\\", "")
            val domain = try {
                val u = java.net.URL(cleanUrl)
                "${u.protocol}://${u.host}/"
            } catch (_: Exception) { mainUrl }
            
            val pageHtml = app.get(cleanUrl, headers = mapOf("Referer" to domain, "User-Agent" to USER_AGENT)).text
            val base64Str = Regex("""const datas\s*=\s*"([^"]+)"""").find(pageHtml)?.groupValues?.get(1) ?: return
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            val latin1Str = String(decodedBytes, Charsets.ISO_8859_1)
            val json = JSONObject(latin1Str)
            val slug = json.getString("slug")
            val userId = json.getString("user_id")
            val md5Id = json.getString("md5_id")
            val media = json.getString("media")
            val keyStr = "$userId:$slug:$md5Id"
            val keyBytesStr = md5(keyStr.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            val key = keyBytesStr.toByteArray(Charsets.UTF_8)
            val iv = key.sliceArray(0 until 16)
            val mediaCiphertext = Base64.decode(media, Base64.DEFAULT)
            val decryptedMediaBytes = decryptAesCtr(mediaCiphertext, key, iv)
            val decryptedMediaStr = String(decryptedMediaBytes, Charsets.UTF_8)
            val mediaJson = JSONObject(decryptedMediaStr)
            val mp4 = mediaJson.getJSONObject("mp4")
            val sources = mp4.getJSONArray("sources")
            val domainsObj = if (mp4.has("domains")) mp4.optJSONObject("domains") else if (mediaJson.has("domains")) mediaJson.optJSONObject("domains") else null
            val domainsArr = if (mp4.has("domains")) mp4.optJSONArray("domains") else if (mediaJson.has("domains")) mediaJson.optJSONArray("domains") else null

            for (i in 0 until sources.length()) {
                val src = sources.getJSONObject(i)
                val size = src.getLong("size")
                val resId = src.getInt("res_id")
                val label = src.getString("label")
                val sub = src.getString("sub")
                var hostDomain = ""
                if (domainsObj != null) {
                    hostDomain = domainsObj.optString(sub, "")
                } else if (domainsArr != null) {
                    for (j in 0 until domainsArr.length()) {
                        val dStr = domainsArr.getString(j)
                        if (dStr.startsWith(sub)) {
                            hostDomain = dStr
                            break
                        }
                    }
                }
                if (hostDomain.isBlank()) {
                    hostDomain = "$sub.sssrr.org"
                }
                val pathStr = "/mp4/$md5Id/$resId/$size?v=$slug"
                val sizeStr = size.toString()
                val digitBytes = sizeStr.map { it.toString().toInt().toByte() }.toByteArray()
                val sizeHashHex = md5(digitBytes).joinToString("") { "%02x".format(it) }
                val pathKey = sizeHashHex.toByteArray(Charsets.UTF_8)
                val pathIv = pathKey.sliceArray(0 until 16)
                val pathBytes = pathStr.toByteArray(Charsets.UTF_8)
                val encryptedPathBytes = decryptAesCtr(pathBytes, pathKey, pathIv)
                val b64Once = Base64.encodeToString(encryptedPathBytes, Base64.NO_WRAP)
                val b64Twice = Base64.encodeToString(b64Once.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val cleanPath = b64Twice.replace("=", "").replace("\n", "").replace("\r", "")
                val finalStreamUrl = "https://$hostDomain/sora/$size/$cleanPath"

                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name - $label",
                        url = finalStreamUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = domain
                        this.quality = when (label.lowercase()) {
                            "360p" -> Qualities.P360.value
                            "480p" -> Qualities.P480.value
                            "720p" -> Qualities.P720.value
                            "1080p" -> Qualities.P1080.value
                            else -> Qualities.Unknown.value
                        }
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class SarangStreamWishExtractor : ExtractorApi() {
    override var name = "StreamWish"
    override var mainUrl = "https://sarangfilm.diy"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val domain = try {
                val u = java.net.URL(url)
                "${u.protocol}://${u.host}/"
            } catch (_: Exception) { mainUrl }

            val pageHtml = app.get(url, headers = mapOf("Referer" to domain, "User-Agent" to USER_AGENT)).text
            val unpacked = getAndUnpack(pageHtml)
            val m3u8Matches = Regex("""https?://[^\s"'\]+\.m3u8[^\s"'\]*""").findAll(unpacked).map { it.value }.toList()

            for (m3u8 in m3u8Matches) {
                callback(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = m3u8,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = domain
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
