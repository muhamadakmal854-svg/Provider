package com.mts.drakorid

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamTape
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

class BloggerCom : ExtractorApi() {
    override var name = "BloggerCom"
    override var mainUrl = "https://blogger.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, referer = referer ?: mainUrl).document

        // Use for() loops for suspend functions - forEach{} breaks suspend context
        for (el in doc.select("video source[src], video[src], source[src]")) {
            val src = el.attr("src").trim()
            if (src.isNotBlank() && (src.startsWith("http") || src.startsWith("//"))) {
                val u = if (src.startsWith("//")) "https:$src" else src
                try { loadExtractor(u, url, subtitleCallback, callback) } catch (_: Exception) {}
            }
        }

        for (ifr in doc.select("iframe[src], iframe[data-src]")) {
            val s1 = ifr.attr("src").trim()
            val src = if (s1.isNotBlank()) s1 else ifr.attr("data-src").trim()
            if (src.isNotBlank() && (src.startsWith("http") || src.startsWith("//"))) {
                val u = if (src.startsWith("//")) "https:$src" else src
                try { loadExtractor(u, url, subtitleCallback, callback) } catch (_: Exception) {}
            }
        }

        // Regex is not suspend - forEach is OK here
        for (script in doc.select("script")) {
            val content = script.data()
            if (content.isBlank()) continue
            val rx = Regex("""https?://\S+\.(?:mp4|m3u8|webm)\S*""")
            rx.findAll(content).forEach { m ->
                val videoUrl = m.value
                callback(
                    newExtractorLink(
                        source = name, name = name, url = videoUrl,
                        type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = url
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }
    }
}

class GembengCom : StreamWishExtractor() {
    override var name = "GembengCom"
    override var mainUrl = "https://gembeng.com"
}

class PsLarinpaymentCom : StreamWishExtractor() {
    override var name = "PsLarinpaymentCom"
    override var mainUrl = "https://ps.larinpayment.com"
}

class Prx1559AntVmwesaOnline : StreamWishExtractor() {
    override var name = "Prx1559AntVmwesaOnline"
    override var mainUrl = "https://prx-1559-ant.vmwesa.online"
}

class StreamtapeCom : StreamTape() {
    override var name = "StreamtapeCom"
    override var mainUrl = "https://streamtape.com"
}

class PzEerfumerelCom : StreamWishExtractor() {
    override var name = "PzEerfumerelCom"
    override var mainUrl = "https://pz.eerfumerel.com"
}

class KisskhMegaplaySu : StreamWishExtractor() {
    override var name = "KisskhMegaplaySu"
    override var mainUrl = "https://kisskh.megaplay.su"
}

class Prx1328AntVmwesaOnline : StreamWishExtractor() {
    override var name = "Prx1328AntVmwesaOnline"
    override var mainUrl = "https://prx-1328-ant.vmwesa.online"
}
