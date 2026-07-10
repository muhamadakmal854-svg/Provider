package com.mts.oploverz

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

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
