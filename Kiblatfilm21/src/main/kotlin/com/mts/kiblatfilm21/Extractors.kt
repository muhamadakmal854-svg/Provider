package com.mts.kiblatfilm21

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import org.json.JSONObject
import org.json.JSONArray

open class PlayerAbyssplayerCom : ExtractorApi() {
    override var name = "Abyss Player"
    override var mainUrl = "https://player.abyssplayer.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val ref = referer ?: "https://kiblatfilm21.com/"
            val response = app.get(url, headers = mapOf("Referer" to ref, "User-Agent" to USER_AGENT)).text

            val datasRegex = Regex("""datas\s*=\s*["']([^"']+)["']""")
            val datasMatch = datasRegex.find(response)
            if (datasMatch != null) {
                val b64Payload = datasMatch.groupValues[1]
                val decodedJsonStr = String(Base64.decode(b64Payload, Base64.DEFAULT))
                val json = JSONObject(decodedJsonStr)

                val slug = json.optString("slug")
                val md5Id = json.opt("md5_id")?.toString() ?: ""
                val userId = json.opt("user_id")?.toString() ?: ""
                val encryptedMedia = json.optString("media")

                if (slug.isNotEmpty() && encryptedMedia.isNotEmpty()) {
                    // Try AES-128-CTR or AES-128-CBC with md5 key
                    val keyString = "$slug$userId$md5Id"
                    val md5 = MessageDigest.getInstance("MD5").digest(keyString.toByteArray())
                    val keySpec = SecretKeySpec(md5, "AES")

                    val mediaBytes = Base64.decode(encryptedMedia, Base64.DEFAULT)
                    if (mediaBytes.size > 16) {
                        val iv = mediaBytes.copyOfRange(0, 16)
                        val cipherText = mediaBytes.copyOfRange(16, mediaBytes.size)

                        // 1. Try CTR mode
                        var decryptedText: String? = null
                        try {
                            val cipherCtr = Cipher.getInstance("AES/CTR/NoPadding")
                            cipherCtr.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
                            val plainBytes = cipherCtr.doFinal(cipherText)
                            decryptedText = String(plainBytes)
                        } catch (e: Exception) {
                            // ignore CTR failure
                        }

                        // 2. Try CBC mode if CTR didn't produce valid json
                        if (decryptedText == null || !decryptedText.contains("sources")) {
                            try {
                                val cipherCbc = Cipher.getInstance("AES/CBC/PKCS5Padding")
                                cipherCbc.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
                                val plainBytes = cipherCbc.doFinal(cipherText)
                                decryptedText = String(plainBytes)
                            } catch (e: Exception) {
                                // ignore CBC failure
                            }
                        }

                        if (decryptedText != null && (decryptedText.contains("file") || decryptedText.contains("sources"))) {
                            parseSourcesJson(decryptedText, ref, callback)
                            return
                        }
                    }
                }
            }

            // Fallback: check HTML for direct m3u8 or mp4
            val m3u8Regex = Regex("""https?://[^\s"']+\.m3u8[^\s"']*""", RegexOption.IGNORE_CASE)
            m3u8Regex.findAll(response).forEach { m ->
                generateM3u8(name, m.value, ref).forEach(callback)
            }

            val mp4Regex = Regex("""https?://[^\s"']+\.mp4[^\s"']*""", RegexOption.IGNORE_CASE)
            mp4Regex.findAll(response).forEach { m ->
                callback(
                    newExtractorLink(
                        name,
                        "$name Direct MP4",
                        m.value,
                        ExtractorLinkType.VIDEO
                    ) {
                        this.referer = ref
                    }
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerAbyssplayerCom", "getUrl error: ${e.message}")
        }
    }

    private fun parseSourcesJson(jsonStr: String, ref: String, callback: (ExtractorLink) -> Unit) {
        try {
            val json = JSONObject(jsonStr)
            val sources = json.optJSONArray("sources") ?: JSONArray()
            for (i in 0 until sources.length()) {
                val s = sources.getJSONObject(i)
                val file = s.optString("file")
                val label = s.optString("label", "HD")
                val quality = when {
                    label.contains("1080") -> Qualities.P1080.value
                    label.contains("720") -> Qualities.P720.value
                    label.contains("480") -> Qualities.P480.value
                    label.contains("360") -> Qualities.P360.value
                    else -> Qualities.Unknown.value
                }
                if (file.isNotEmpty()) {
                    if (file.contains(".m3u8")) {
                        generateM3u8(name, file, ref).forEach(callback)
                    } else {
                        callback(
                            newExtractorLink(
                                name,
                                "$name $label",
                                file,
                                ExtractorLinkType.VIDEO
                            ) {
                                this.referer = ref
                                this.quality = quality
                            }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerAbyssplayerCom", "parseSourcesJson error: ${e.message}")
        }
    }
}

open class AbyssplayerCom : PlayerAbyssplayerCom() {
    override var name = "Abyss Player Alt"
    override var mainUrl = "https://abyssplayer.com"
}

open class MorenciusCom : StreamWishExtractor() {
    override var name = "Morencius (StreamWish)"
    override var mainUrl = "https://morencius.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val ref = referer ?: "https://kiblatfilm21.com/"
            val response = app.get(url, headers = mapOf("Referer" to ref, "User-Agent" to USER_AGENT)).text

            // Check unpacked JS
            val unpacked = getAndUnpack(response)
            val contentToSearch = if (unpacked.isNotEmpty()) "$response\n$unpacked" else response

            val m3u8Regex = Regex("""https?://[^\s"']+\.m3u8[^\s"']*""", RegexOption.IGNORE_CASE)
            var found = false
            m3u8Regex.findAll(contentToSearch).forEach { m ->
                found = true
                generateM3u8(name, m.value, ref).forEach(callback)
            }

            if (!found) {
                // Delegate to parent StreamWishExtractor
                super.getUrl(url, ref, subtitleCallback, callback)
            }
        } catch (e: Exception) {
            android.util.Log.e("MorenciusCom", "getUrl error: ${e.message}")
        }
    }
}

open class Embed4Me : ExtractorApi() {
    override var name = "Embed4Me"
    override var mainUrl = "https://dm21.embed4me.vip"
    override val requiresReferer = true

    private fun decryptAes128Cbc(hexCipher: String, keyStr: String, ivStr: String): String {
        val cipherBytes = ByteArray(hexCipher.length / 2)
        for (i in cipherBytes.indices) {
            val index = i * 2
            cipherBytes[i] = hexCipher.substring(index, index + 2).toInt(16).toByte()
        }
        val keySpec = SecretKeySpec(keyStr.toByteArray(Charsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(ivStr.toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val ref = referer ?: "https://kiblatfilm21.com/"
            val host = java.net.URI(url).host ?: "dm21.embed4me.vip"
            val id = url.substringAfter("#", "").substringBefore("&")

            if (id.isNotEmpty()) {
                val apiUrl = "https://$host/api/v1/video?id=$id&w=1920&h=1080&r=kiblatfilm21.com"
                val response = app.get(
                    apiUrl,
                    headers = mapOf(
                        "Referer" to url,
                        "Origin" to "https://$host",
                        "User-Agent" to USER_AGENT
                    )
                ).text.trim()

                if (response.isNotEmpty() && !response.startsWith("{")) {
                    val decrypted = decryptAes128Cbc(response, "kiemtienmua911ca", "1234567890oiuytr")
                    val json = JSONObject(decrypted)
                    val cfNative = json.optString("cfNative")
                    val source = json.optString("source")

                    if (cfNative.isNotEmpty() && cfNative.contains(".m3u8")) {
                        generateM3u8(name, cfNative, url).forEach(callback)
                    }
                    if (source.isNotEmpty() && source.contains(".m3u8") && source != cfNative) {
                        generateM3u8("$name (Direct)", source, url).forEach(callback)
                    }
                    return
                }
            }

            // Fallback: check page HTML
            val pageHtml = app.get(url, headers = mapOf("Referer" to ref, "User-Agent" to USER_AGENT)).text
            val m3u8Regex = Regex("""https?://[^\s"']+\.m3u8[^\s"']*""", RegexOption.IGNORE_CASE)
            m3u8Regex.findAll(pageHtml).forEach { m ->
                generateM3u8(name, m.value, url).forEach(callback)
            }
        } catch (e: Exception) {
            android.util.Log.e("Embed4Me", "getUrl error: ${e.message}")
        }
    }
}

open class PlayerP2P : Embed4Me() {
    override var name = "PlayerP2P"
    override var mainUrl = "https://live.playerp2p.online"
}

open class UpnsLive : Embed4Me() {
    override var name = "Upns"
    override var mainUrl = "https://dm21.upns.live"
}
