package com.mts.klikxxi

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ZombiemealCom : StreamWishExtractor() {
    override var name = "ZombiemealCom"
    override var mainUrl = "https://zombiemeal.com"
}

class VipIdlix21Pro : StreamWishExtractor() {
    override var name = "VipIdlix21Pro"
    override var mainUrl = "https://vip.idlix21.pro"
}

class EmbedpyroxXyz : ExtractorApi() {
    override var name = "EmbedpyroxXyz"
    override var mainUrl = "https://embedpyrox.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = url.replace(92.toChar().toString(), "")
        val id = cleanUrl.substringAfter("/video/").substringBefore("/").substringBefore("?")
        if (id.isEmpty()) return

        val ajaxUrl = "$mainUrl/player/index.php?data=$id&do=getVideo"
        val response = app.post(
            url = ajaxUrl,
            data = mapOf("hash" to id, "r" to (referer ?: "")),
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to cleanUrl,
                "Origin" to mainUrl,
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
            )
        )
        if (!response.isSuccessful) return
        val text = response.text
        val securedLink = Regex("\"securedLink\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
        if (securedLink != null) {
            val finalUrl = securedLink.replace("\\/", "/")
            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = finalUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = cleanUrl
                }
            )
        }
    }
}

class AbyssplayerCom : ExtractorApi() {
    override var name = "AbyssplayerCom"
    override var mainUrl = "https://abyssplayer.com"
    override val requiresReferer = true

    private fun decryptAesCtr(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val spec = javax.crypto.spec.SecretKeySpec(key, "AES")
        val parameterSpec = javax.crypto.spec.IvParameterSpec(iv)
        val cipher = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, spec, parameterSpec)
        return cipher.doFinal(ciphertext)
    }

    private fun md5(input: ByteArray): ByteArray {
        val md = java.security.MessageDigest.getInstance("MD5")
        return md.digest(input)
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val cleanUrl = url.replace(92.toChar().toString(), "")
            val pageHtml = app.get(cleanUrl, headers = mapOf("Referer" to (referer ?: mainUrl))).text

            val rx = Regex("const datas\\s*=\\s*\"([^\"]+)\"")
            val base64Str = rx.find(pageHtml)?.groupValues?.get(1) ?: return
            val decodedBytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
            val latin1Str = String(decodedBytes, Charsets.ISO_8859_1)

            val slugStart = latin1Str.indexOf("\"slug\":\"") + 8
            val slugEnd = latin1Str.indexOf("\"", slugStart)
            val slug = latin1Str.substring(slugStart, slugEnd)

            val userIdStart = latin1Str.indexOf("\"user_id\":") + 10
            val userIdEnd = latin1Str.indexOf(",", userIdStart)
            val userId = latin1Str.substring(userIdStart, userIdEnd)

            val md5IdStart = latin1Str.indexOf("\"md5_id\":") + 9
            val md5IdEnd = latin1Str.indexOf(",", md5IdStart)
            val md5Id = latin1Str.substring(md5IdStart, md5IdEnd)

            val mediaStart = latin1Str.indexOf("\"media\":\"") + 9
            val mediaEnd = latin1Str.indexOf("\",\"config\"")
            val media = latin1Str.substring(mediaStart, mediaEnd)

            val keyStr = "$userId:$slug:$md5Id"
            val keyBytesStr = md5(keyStr.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            val key = keyBytesStr.toByteArray(Charsets.UTF_8)
            val iv = key.sliceArray(0 until 16)

            val mediaCiphertext = media.toByteArray(Charsets.ISO_8859_1)
            val decryptedMediaBytes = decryptAesCtr(mediaCiphertext, key, iv)
            val decryptedMediaStr = String(decryptedMediaBytes, Charsets.UTF_8)

            val mediaJson = org.json.JSONObject(decryptedMediaStr)
            val mp4 = mediaJson.getJSONObject("mp4")
            val sources = mp4.getJSONArray("sources")

            for (i in 0 until sources.length()) {
                val src = sources.getJSONObject(i)
                val size = src.getLong("size")
                val resId = src.getInt("res_id")
                val label = src.getString("label")
                val sub = src.getString("sub")

                var domain = ""
                try {
                    val domainsObj = if (mp4.has("domains")) mp4.get("domains") else if (mediaJson.has("domains")) mediaJson.get("domains") else null
                    if (domainsObj is org.json.JSONArray) {
                        for (d in 0 until domainsObj.length()) {
                            val dStr = domainsObj.getString(d)
                            if (dStr.contains(sub)) {
                                domain = dStr
                                break
                            }
                        }
                    } else if (domainsObj is org.json.JSONObject) {
                        domain = domainsObj.getString(sub)
                    }
                } catch (_: Exception) {}
                if (domain.isEmpty()) {
                    domain = "$sub.sssrr.org"
                }

                val pathStr = "/mp4/$md5Id/$resId/$size?v=$slug"
                val sizeStr = size.toString()
                val digitBytes = sizeStr.map { it.toString().toInt().toByte() }.toByteArray()
                val sizeHashHex = md5(digitBytes).joinToString("") { "%02x".format(it) }

                val pathKey = sizeHashHex.toByteArray(Charsets.UTF_8)
                val pathIv = pathKey.sliceArray(0 until 16)

                val pathBytes = pathStr.toByteArray(Charsets.UTF_8)
                val encryptedPathBytes = decryptAesCtr(pathBytes, pathKey, pathIv)

                val b64Once = android.util.Base64.encodeToString(encryptedPathBytes, android.util.Base64.NO_WRAP)
                val b64Twice = android.util.Base64.encodeToString(b64Once.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                val cleanPath = b64Twice.replace("=", "").replace("\n", "").replace("\r", "")

                val finalStreamUrl = "https://$domain/sora/$size/$cleanPath"

                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name - $label",
                        url = finalStreamUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = cleanUrl
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

class CvGenipspillionCom : StreamWishExtractor() {
    override var name = "CvGenipspillionCom"
    override var mainUrl = "https://cv.genipspillion.com"
}

class MorenciusCom : StreamWishExtractor() {
    override var name = "MorenciusCom"
    override var mainUrl = "https://morencius.com"
}

class PortalMgaOrgMt : StreamWishExtractor() {
    override var name = "PortalMgaOrgMt"
    override var mainUrl = "https://portal.mga.org.mt"
}

class RedorangeComMt : StreamWishExtractor() {
    override var name = "RedorangeComMt"
    override var mainUrl = "https://redorange.com.mt"
}

class Server9HdigitalCom : StreamWishExtractor() {
    override var name = "Server9HdigitalCom"
    override var mainUrl = "https://9hdigital.com"
}

class UseTypekitNet : StreamWishExtractor() {
    override var name = "UseTypekitNet"
    override var mainUrl = "https://use.typekit.net"
}

class VincentdesignCa : StreamWishExtractor() {
    override var name = "VincentdesignCa"
    override var mainUrl = "https://vincentdesign.ca"
}

class ResponsiblegamblingOrg : StreamWishExtractor() {
    override var name = "ResponsiblegamblingOrg"
    override var mainUrl = "https://responsiblegambling.org"
}

class AjaxAspnetcdnCom : StreamWishExtractor() {
    override var name = "AjaxAspnetcdnCom"
    override var mainUrl = "https://ajax.aspnetcdn.com"
}

class AppFive9Eu : StreamWishExtractor() {
    override var name = "AppFive9Eu"
    override var mainUrl = "https://app.five9.eu"
}

class GambleawareOrg : StreamWishExtractor() {
    override var name = "GambleawareOrg"
    override var mainUrl = "https://gambleaware.org"
}

class IYtimgCom : StreamWishExtractor() {
    override var name = "IYtimgCom"
    override var mainUrl = "https://i.ytimg.com"
}

class YoutuBe : StreamWishExtractor() {
    override var name = "YoutuBe"
    override var mainUrl = "https://youtu.be"
}
