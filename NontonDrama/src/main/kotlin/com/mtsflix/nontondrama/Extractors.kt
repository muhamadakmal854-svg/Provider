package com.mtsflix.nontondrama

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.app
import org.json.JSONObject
import org.jsoup.Jsoup
import android.util.Log
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import java.net.URI
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.loadExtractor
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec



class Gn1r5nOrg : StreamWishExtractor() {
    override var name = "Gn1r5nOrg"
    override var mainUrl = "https://gn1r5n.org"
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
        val json = runCatching { org.json.JSONObject(text) }.getOrNull()
        if (json != null) {
            val refHeader = mapOf("Referer" to mainUrl, "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            if (json.has("securedLink")) {
                val s1 = json.getString("securedLink").replace(92.toChar().toString() + "/", "/")
                if (s1.isNotBlank()) {
                    callback(newExtractorLink(name, "$name - Server 1", s1, ExtractorLinkType.M3U8) {
                        this.referer = mainUrl
                        this.headers = refHeader
                    })
                }
            }
            if (json.has("videoSource")) {
                val s2 = json.getString("videoSource").replace(92.toChar().toString() + "/", "/")
                if (s2.isNotBlank()) {
                    callback(newExtractorLink(name, "$name - Server 2", s2, if (s2.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                        this.referer = mainUrl
                        this.headers = refHeader
                    })
                }
            }
            if (json.has("hlsVideoTiktok")) {
                val s3 = json.getString("hlsVideoTiktok").replace(92.toChar().toString() + "/", "/")
                if (s3.isNotBlank()) {
                    callback(newExtractorLink(name, "$name - Server 3", s3, ExtractorLinkType.M3U8) {
                        this.referer = mainUrl
                        this.headers = refHeader
                    })
                }
            }
        }
    }
}

class MorenciusCom : StreamWishExtractor() {
    override var name = "MorenciusCom"
    override var mainUrl = "https://morencius.com"
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

class RpmPlayShare : ExtractorApi() {
    override var name = "RpmPlayShare"
    override var mainUrl = "https://endstar.rpmplay.me"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val ref = referer ?: mainUrl
        val response = app.get(url, referer = ref, headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        ))
        val doc = response.document

        val m3u8Regex = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE)
        val mp4Regex  = Regex("""["'](https?://[^"']+\.mp4[^"']*)["']""", RegexOption.IGNORE_CASE)
        val sourceRegex = Regex("""file["']?\s*:\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)

        val scripts = doc.select("script").joinToString(" ") { it.data() }

        val videoUrl = m3u8Regex.find(scripts)?.groupValues?.get(1)
            ?: sourceRegex.find(scripts)?.groupValues?.get(1)
            ?: mp4Regex.find(scripts)?.groupValues?.get(1)

        if (videoUrl != null) {
            val isM3u8 = videoUrl.contains(".m3u8")
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name   = this.name,
                    url    = videoUrl,
                    type   = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    quality = Qualities.Unknown.value
                    this.referer = ref
                }
            )
        } else {
            val iframeSrc = doc.selectFirst("iframe[src]")?.attr("src") ?: return
            val cleanUrl = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else iframeSrc
            loadExtractor(cleanUrl, url, subtitleCallback, callback)
        }
    }
}

class Embed4MePlay : ExtractorApi() {
    override var name = "Embed4MePlay"
    override var mainUrl = "https://endstar.embed4me.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val ref = referer ?: mainUrl
        val response = app.get(url, referer = ref, headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        ))
        val doc = response.document
        val scripts = doc.select("script").joinToString(" ") { it.data() }

        val m3u8Regex = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE)
        val mp4Regex  = Regex("""["'](https?://[^"']+\.mp4[^"']*)["']""", RegexOption.IGNORE_CASE)
        val sourceRegex = Regex("""file["']?\s*:\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)

        val videoUrl = m3u8Regex.find(scripts)?.groupValues?.get(1)
            ?: sourceRegex.find(scripts)?.groupValues?.get(1)
            ?: mp4Regex.find(scripts)?.groupValues?.get(1)

        if (videoUrl != null) {
            val isM3u8 = videoUrl.contains(".m3u8")
            if (isM3u8) {
                generateM3u8(this.name, videoUrl, ref).forEach(callback)
            } else {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name   = this.name,
                        url    = videoUrl,
                        type   = ExtractorLinkType.VIDEO
                    ) {
                        quality = Qualities.Unknown.value
                        this.referer = ref
                    }
                )
            }
        } else {
            val iframeSrc = doc.selectFirst("iframe[src]")?.attr("src") ?: return
            val cleanUrl = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else iframeSrc
            loadExtractor(cleanUrl, url, subtitleCallback, callback)
        }
    }
}

class GoogleVideo : ExtractorApi() {
    override var name = "GoogleVideo"
    override var mainUrl = "https://googlevideo.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        callback(
            newExtractorLink(
                source = name,
                name = name,
                url = url,
                type = ExtractorLinkType.VIDEO
            ) {
                this.quality = Qualities.Unknown.value
            }
        )
    }
}



open class P2PExtractor : ExtractorApi() {
    override var name = "P2P"
    override var mainUrl = "https://cloud.hownetwork.xyz"
    override val requiresReferer = false
    data class HownetworkResponse(val file: String?, val link: String?, val label: String?)

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val id = url.substringAfter("id=").substringBefore("&")
        val apiUrl = "$mainUrl/api2.php?id=$id"
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
            "Referer" to url,
            "Origin" to mainUrl,
            "X-Requested-With" to "XMLHttpRequest"
        )
        val formBody = mapOf("r" to "https://playeriframe.sbs/", "d" to "cloud.hownetwork.xyz")
        val sources = mutableListOf<ExtractorLink>()
        try {
            val response = app.post(apiUrl, headers = headers, data = formBody).text
            val json = tryParseJson<HownetworkResponse>(response)
            val videoUrl = json?.file ?: json?.link
            if (!videoUrl.isNullOrBlank()) {
                sources.add(newExtractorLink(source = name, name = name, url = videoUrl, type = ExtractorLinkType.M3U8) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                })
            }
        } catch (e: Exception) { e.printStackTrace() }
        return sources
    }
}

open class EmturbovidExtractor : ExtractorApi() {
    override var name = "Emturbovid"
    override var mainUrl = "https://emturbovid.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val finalReferer = referer ?: "$mainUrl/"
        val sources = mutableListOf<ExtractorLink>()
        
        try {
            val response = app.get(url, referer = finalReferer)
            val playerScript = response.document.selectXpath("//script[contains(text(),'var urlPlay')]").html()
            
            if (playerScript.isNotBlank()) {
                val m3u8Url = playerScript.substringAfter("var urlPlay = '").substringBefore("'")
                val originUrl = try { URI(finalReferer).let { "${it.scheme}://${it.host}" } } catch (e: Exception) { mainUrl }
                
                val headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Referer" to finalReferer,
                    "Origin" to originUrl
                )
                
                sources.add(newExtractorLink(source = name, name = name, url = m3u8Url, type = ExtractorLinkType.M3U8) {
                    this.referer = finalReferer
                    this.quality = Qualities.Unknown.value
                    this.headers = headers
                })
            }
        } catch (e: Exception) { e.printStackTrace() }
        return sources
    }
}

class AbyssPlayer : ExtractorApi() {
    override val name = "AbyssPlayer"
    override val mainUrl = "https://abyssplayer.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val ref = referer ?: mainUrl
        val response = app.get(url, referer = ref, headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9"
        ))
        val doc = response.document
        val scripts = doc.select("script").joinToString(" ") { it.data() }

        val m3u8Regex = Regex("""["'](https?://[^"']+\\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE)
        val mp4Regex  = Regex("""["'](https?://[^"']+\\.mp4[^"']*)["']""", RegexOption.IGNORE_CASE)
        val fileRegex = Regex("""(?:file|src)["']?\\s*:\\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
        val hlsRegex  = Regex("""hls\\.loadSource\\(["'](https?://[^"']+)["']\\)""", RegexOption.IGNORE_CASE)

        val videoUrl = m3u8Regex.find(scripts)?.groupValues?.get(1)
            ?: hlsRegex.find(scripts)?.groupValues?.get(1)
            ?: fileRegex.find(scripts)?.groupValues?.get(1)
            ?: mp4Regex.find(scripts)?.groupValues?.get(1)
            ?: doc.selectFirst("source[src]")?.attr("src")
            ?: doc.selectFirst("video[src]")?.attr("src")

        if (videoUrl != null) {
            val isM3u8 = videoUrl.contains(".m3u8")
            if (isM3u8) {
                generateM3u8(this.name, videoUrl, ref).forEach(callback)
            } else {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name   = this.name,
                        url    = videoUrl,
                        type   = ExtractorLinkType.VIDEO
                    ) {
                        quality = Qualities.Unknown.value
                        this.referer = ref
                    }
                )
            }
        }
    }
}

