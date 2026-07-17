package com.mts.klikxxi

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
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.extractors.FastdlP2pstreamOnline
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import java.security.MessageDigest

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

            val json = org.json.JSONObject(latin1Str)
            val slug = json.getString("slug")
            val userId = json.getString("user_id")
            val md5Id = json.getString("md5_id")
            val media = json.getString("media")

            val keyStr = "$userId:$slug:$md5Id"
            val keyBytesStr = md5(keyStr.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            val key = keyBytesStr.toByteArray(Charsets.UTF_8)
            val iv = key.sliceArray(0 until 16)

            val mediaCiphertext = android.util.Base64.decode(media, android.util.Base64.DEFAULT)
            val decryptedMediaBytes = decryptAesCtr(mediaCiphertext, key, iv)
            val decryptedMediaStr = String(decryptedMediaBytes, Charsets.UTF_8)

            val mediaJson = org.json.JSONObject(decryptedMediaStr)
            val mp4 = mediaJson.getJSONObject("mp4")
            val sources = mp4.getJSONArray("sources")
            val domainsObj = if (mp4.has("domains")) mp4.getJSONObject("domains") else if (mediaJson.has("domains")) mediaJson.getJSONObject("domains") else org.json.JSONObject()

            for (i in 0 until sources.length()) {
                val src = sources.getJSONObject(i)
                val size = src.getLong("size")
                val resId = src.getInt("res_id")
                val label = src.getString("label")
                val sub = src.getString("sub")

                val domain = domainsObj.getString(sub)

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

class MorenciusCom : StreamWishExtractor() {
    override var name = "MorenciusCom"
    override var mainUrl = "https://morencius.com"
}

class Playerp2pXyz : FastdlP2pstreamOnline() {
    override var name = "Playerp2pXyz"
    override var mainUrl = "https://playerp2p.xyz"
}

class FastdlP2pstreamOnline : ExtractorApi() {
    override var name = "FastdlP2pstreamOnline"
    override var mainUrl = "https://fastdl.p2pstream.online"
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

