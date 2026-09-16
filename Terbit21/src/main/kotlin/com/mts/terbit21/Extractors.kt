package com.mts.terbit21

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

open class Sf21RpmvidCom : ExtractorApi() {
    override var name = "Sf21RpmvidCom"
    override var mainUrl = "https://sf21.rpmvid.com"
    override val requiresReferer = true

    protected fun decryptAes128Cbc(hexCipher: String, keyStr: String, ivStr: String): String {
        val keySpec = SecretKeySpec(keyStr.toByteArray(Charsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(ivStr.toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val cipherBytes = hexCipher.trim().chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val decryptedBytes = cipher.doFinal(cipherBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = url.replace(92.toChar().toString(), "")
        val id = if (cleanUrl.contains("#")) {
            cleanUrl.substringAfter("#").substringBefore("&").substringBefore("/").trim()
        } else if (cleanUrl.contains("/e/")) {
            cleanUrl.substringAfter("/e/").substringBefore("&").substringBefore("/").trim()
        } else {
            cleanUrl.trimEnd('/').substringAfterLast("/").substringBefore("?").substringBefore("#").trim()
        }

        var found = false
        if (id.isNotBlank()) {
            try {
                val refHost = try { java.net.URI(referer ?: mainUrl).host ?: "162.244.95.227" } catch (_: Exception) { "162.244.95.227" }
                val videoApiUrl = "$mainUrl/api/v1/video?id=$id&w=1920&h=1080&r=$refHost"
                val res = app.get(
                    videoApiUrl,
                    headers = mapOf(
                        "Referer" to "$mainUrl/#$id",
                        "Origin" to mainUrl,
                        "User-Agent" to USER_AGENT,
                        "Accept" to "*/*"
                    ),
                    timeout = 15
                )
                if (res.isSuccessful && res.text.isNotBlank()) {
                    val decrypted = decryptAes128Cbc(res.text, "kiemtienmua911ca", "1234567890oiuytr")
                    val json = org.json.JSONObject(decrypted)

                    // 1. cfNative (Cloudflare Master M3U8)
                    val cfNative = json.optString("cfNative", "").trim()
                    if (cfNative.isNotBlank()) {
                        generateM3u8(name, cfNative, "$mainUrl/").forEach { link ->
                            found = true
                            callback(link)
                        }
                        callback(
                            newExtractorLink(
                                source = name,
                                name = "$name - Cloudflare",
                                url = cfNative,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.referer = "$mainUrl/"
                                this.quality = Qualities.Unknown.value
                            }
                        )
                        found = true
                    }

                    // 2. source (Direct IP Master M3U8)
                    val source = json.optString("source", "").trim()
                    if (source.isNotBlank() && source != cfNative) {
                        generateM3u8(name, source, "$mainUrl/").forEach { link ->
                            found = true
                            callback(link)
                        }
                        callback(
                            newExtractorLink(
                                source = name,
                                name = "$name - Direct",
                                url = source,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.referer = "$mainUrl/"
                                this.quality = Qualities.Unknown.value
                            }
                        )
                        found = true
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(name, "API decrypt error: ${e.message}")
            }
        }

        // Fallback: parse HTML for direct sources, scripts, or iframes
        if (!found) {
            try {
                val doc = app.get(cleanUrl, referer = referer ?: mainUrl).document
                for (el in doc.select("video source[src], video[src], source[src]")) {
                    val src = el.attr("src").trim()
                    if (src.isNotBlank() && (src.startsWith("http") || src.startsWith("//"))) {
                        val u = if (src.startsWith("//")) "https:$src" else src
                        try { loadExtractor(u, cleanUrl, subtitleCallback, callback) } catch (_: Exception) {}
                    }
                }
                for (ifr in doc.select("iframe[src], iframe[data-src]")) {
                    val s1 = ifr.attr("src").trim()
                    val src = if (s1.isNotBlank()) s1 else ifr.attr("data-src").trim()
                    if (src.isNotBlank() && (src.startsWith("http") || src.startsWith("//"))) {
                        val u = if (src.startsWith("//")) "https:$src" else src
                        try { loadExtractor(u, cleanUrl, subtitleCallback, callback) } catch (_: Exception) {}
                    }
                }
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
                                this.referer = cleanUrl
                                this.quality = Qualities.Unknown.value
                            }
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }
}

class Sf21VidplayerLive : Sf21RpmvidCom() {
    override var name = "Sf21VidplayerLive"
    override var mainUrl = "https://sf21.vidplayer.live"
}

open class AbyssplayerCom : ExtractorApi() {
    override var name = "AbyssplayerCom"
    override var mainUrl = "https://abyssplayer.com"
    override val requiresReferer = true

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
            val cleanUrl = url.replace(92.toChar().toString(), "")
            val ref = referer ?: mainUrl
            val pageHtml = app.get(cleanUrl, headers = mapOf("Referer" to ref)).text

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
            android.util.Log.e(name, "Abyss decrypt error: ${e.message}")
        }
    }
}

class PlayerAbyssplayerCom : AbyssplayerCom() {
    override var name = "PlayerAbyssplayerCom"
    override var mainUrl = "https://player.abyssplayer.com"
}

class MorenciusCom : StreamWishExtractor() {
    override var name = "MorenciusCom"
    override var mainUrl = "https://morencius.com"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        var found = false
        try {
            super.getUrl(url, referer, subtitleCallback) { link ->
                found = true
                callback(link)
            }
        } catch (_: Exception) {}

        if (!found) {
            try {
                val cleanUrl = url.replace(92.toChar().toString(), "")
                val resp = app.get(cleanUrl, headers = mapOf("Referer" to (referer ?: mainUrl), "User-Agent" to USER_AGENT)).text
                val m3u8Regex = Regex("""https?://[^\s"']+\.m3u8[^\s"']*""", RegexOption.IGNORE_CASE)
                m3u8Regex.findAll(resp).forEach { m ->
                    val m3u8 = m.value.trim()
                    generateM3u8(name, m3u8, cleanUrl).forEach(callback)
                    callback(
                        newExtractorLink(name, name, m3u8, ExtractorLinkType.M3U8) {
                            this.referer = cleanUrl
                            this.quality = Qualities.Unknown.value
                        }
                    )
                    found = true
                }
                if (!found && resp.contains("eval(function(p,a,c,k,e,d)")) {
                    val unpacked = getAndUnpack(resp)
                    m3u8Regex.findAll(unpacked).forEach { m ->
                        val m3u8 = m.value.trim()
                        generateM3u8(name, m3u8, cleanUrl).forEach(callback)
                        callback(
                            newExtractorLink(name, name, m3u8, ExtractorLinkType.M3U8) {
                                this.referer = cleanUrl
                                this.quality = Qualities.Unknown.value
                            }
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }
}

class New13Savefilm21InfoCom : StreamWishExtractor() {
    override var name = "New13Savefilm21InfoCom"
    override var mainUrl = "https://new13.savefilm21info.com"
}

class S3Bk21Net : StreamWishExtractor() {
    override var name = "S3Bk21Net"
    override var mainUrl = "https://s3.bk21.net"
}

class FileditchfilesMe : StreamWishExtractor() {
    override var name = "FileditchfilesMe"
    override var mainUrl = "https://fileditchfiles.me"
}

class HgcloudTo : StreamWishExtractor() {
    override var name = "HgcloudTo"
    override var mainUrl = "https://hgcloud.to"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = url.replace("/e/", "/#").replace(92.toChar().toString(), "")
        val id = cleanUrl.substringAfter("#").substringBefore("&").substringBefore("/").trim()
        var found = false
        if (id.isNotBlank()) {
            try {
                val refHost = try { java.net.URI(referer ?: mainUrl).host ?: "162.244.95.227" } catch (_: Exception) { "162.244.95.227" }
                val videoApiUrl = "$mainUrl/api/v1/video?id=$id&w=1920&h=1080&r=$refHost"
                val res = app.get(
                    videoApiUrl,
                    headers = mapOf(
                        "Referer" to "$mainUrl/#$id",
                        "Origin" to mainUrl,
                        "User-Agent" to USER_AGENT,
                        "Accept" to "*/*"
                    ),
                    timeout = 15
                )
                if (res.isSuccessful && res.text.isNotBlank()) {
                    val keySpec = SecretKeySpec("kiemtienmua911ca".toByteArray(Charsets.UTF_8), "AES")
                    val ivSpec = IvParameterSpec("1234567890oiuytr".toByteArray(Charsets.UTF_8))
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                    val cipherBytes = res.text.trim().chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    val decrypted = String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
                    val json = org.json.JSONObject(decrypted)

                    val cfNative = json.optString("cfNative", "").trim()
                    if (cfNative.isNotBlank()) {
                        generateM3u8(name, cfNative, "$mainUrl/").forEach(callback)
                        callback(
                            newExtractorLink(name, "$name - Cloudflare", cfNative, ExtractorLinkType.M3U8) {
                                this.referer = "$mainUrl/"
                                this.quality = Qualities.Unknown.value
                            }
                        )
                        found = true
                    }
                    val source = json.optString("source", "").trim()
                    if (source.isNotBlank() && source != cfNative) {
                        generateM3u8(name, source, "$mainUrl/").forEach(callback)
                        callback(
                            newExtractorLink(name, "$name - Direct", source, ExtractorLinkType.M3U8) {
                                this.referer = "$mainUrl/"
                                this.quality = Qualities.Unknown.value
                            }
                        )
                        found = true
                    }
                }
            } catch (_: Exception) {}
        }

        if (!found) {
            try {
                super.getUrl(url, referer, subtitleCallback) { link ->
                    found = true
                    callback(link)
                }
            } catch (_: Exception) {}
        }
    }
}

class MasukestinCom : StreamWishExtractor() {
    override var name = "MasukestinCom"
    override var mainUrl = "https://masukestin.com"
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
                "User-Agent" to USER_AGENT,
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
            )
        )
        if (!response.isSuccessful) return
        val text = response.text
        val json = runCatching { org.json.JSONObject(text) }.getOrNull()
        if (json != null) {
            val refHeader = mapOf("Referer" to mainUrl, "User-Agent" to USER_AGENT)
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
            if (isM3u8) {
                generateM3u8(this.name, videoUrl, ref).forEach(callback)
            }
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
