package com.mts.ngefilm

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// ==================== STREAMWISH EXTRACTORS ====================
class MorenciusCom : StreamWishExtractor() {
    override var name = "Morencius (StreamWish)"
    override var mainUrl = "https://morencius.com"
}

class Ratu89Com : StreamWishExtractor() {
    override var name = "Ratu89"
    override var mainUrl = "https://ratu89.com"
}

class Gratu89Com : StreamWishExtractor() {
    override var name = "Gratu89"
    override var mainUrl = "https://gratu89.com"
}

class HalalhomecookingCom : StreamWishExtractor() {
    override var name = "Halalhomecooking"
    override var mainUrl = "https://halalhomecooking.com"
}

class New38NgefilmSite : StreamWishExtractor() {
    override var name = "New38NgefilmSite"
    override var mainUrl = "https://new38.ngefilm.site"
}

class New39NgefilmSite : StreamWishExtractor() {
    override var name = "New39NgefilmSite"
    override var mainUrl = "https://new39.ngefilm.site"
}

class EmbedwishCom : StreamWishExtractor() {
    override var name = "Embedwish"
    override var mainUrl = "https://embedwish.com"
}

class AhvshCom : StreamWishExtractor() {
    override var name = "Ahvsh"
    override var mainUrl = "https://ahvsh.com"
}

class FilelionsLive : StreamWishExtractor() {
    override var name = "Filelions"
    override var mainUrl = "https://filelions.live"
}

// ==================== ABYSS EXTRACTORS ====================
open class AbyssplayerCom : ExtractorApi() {
    override var name = "Abyss"
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
            val cleanUrl = url.replace("\\", "").trim()
            val ref = referer ?: mainUrl
            val pageHtml = app.get(cleanUrl, headers = mapOf("Referer" to ref, "User-Agent" to USER_AGENT)).text

            val rx = Regex("""const datas\s*=\s*"([^"]+)"""")
            val base64Str = rx.find(pageHtml)?.groupValues?.get(1) ?: return
            val decodedBytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
            val latin1Str = String(decodedBytes, Charsets.ISO_8859_1)

            val json = JSONObject(latin1Str)
            val slug = json.optString("slug")
            val userId = json.optString("user_id")
            val md5Id = json.optString("md5_id")
            val media = json.optString("media")
            if (media.isBlank()) return

            val keyStr = "$userId:$slug:$md5Id"
            val keyBytesStr = md5(keyStr.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            val key = keyBytesStr.toByteArray(Charsets.UTF_8)
            val iv = key.sliceArray(0 until 16)

            // CRUCIAL FIX: media is raw bytes in Latin-1 representation, NOT Base64
            val mediaCiphertext = media.toByteArray(Charsets.ISO_8859_1)
            val decryptedMediaBytes = decryptAesCtr(mediaCiphertext, key, iv)
            val decryptedMediaStr = String(decryptedMediaBytes, Charsets.UTF_8)

            val mediaJson = JSONObject(decryptedMediaStr)
            val mp4 = mediaJson.optJSONObject("mp4") ?: return
            val sources = mp4.optJSONArray("sources") ?: return

            for (i in 0 until sources.length()) {
                val src = sources.optJSONObject(i) ?: continue
                val label = src.optString("label", "HD")
                val srcUrl = src.optString("url")
                val srcPath = src.optString("path")

                val finalStreamUrl = if (srcUrl.isNotBlank() && srcPath.isNotBlank()) {
                    "$srcUrl/$srcPath"
                } else null

                if (finalStreamUrl != null) {
                    val q = when (label.lowercase()) {
                        "360p" -> Qualities.P360.value
                        "480p" -> Qualities.P480.value
                        "720p" -> Qualities.P720.value
                        "1080p" -> Qualities.P1080.value
                        else -> Qualities.Unknown.value
                    }
                    callback(
                        newExtractorLink(
                            source = name,
                            name = "$name $label",
                            url = finalStreamUrl,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = cleanUrl
                            this.quality = q
                            this.headers = mapOf(
                                "User-Agent" to USER_AGENT,
                                "Referer" to cleanUrl
                            )
                        }
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class PlayerAbyssplayerCom : AbyssplayerCom() {
    override var mainUrl = "https://player.abyssplayer.com"
}

class PlayAbyssplayerCom : AbyssplayerCom() {
    override var mainUrl = "https://play.abyssplayer.com"
}

// ==================== OTHER EXTRACTORS ====================
class PlaycinematicCom : ExtractorApi() {
    override var name = "Playcinematic"
    override var mainUrl = "https://playcinematic.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = url.replace("\\", "")
        val id = cleanUrl.substringAfter("/video/").substringBefore("/").substringBefore("?")
        val streamUrl = if (id.isNotBlank()) "$mainUrl/stream/$id#.mp4" else null

        if (streamUrl != null) {
            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = streamUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = cleanUrl
                    this.headers = mapOf("User-Agent" to USER_AGENT, "Referer" to cleanUrl)
                    this.quality = Qualities.P720.value
                }
            )
        }
    }
}

class EmbedpyroxXyz : ExtractorApi() {
    override var name = "Embedpyrox"
    override var mainUrl = "https://embedpyrox.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = url.replace("\\", "")
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
        val json = runCatching { JSONObject(text) }.getOrNull()
        if (json != null) {
            val refHeader = mapOf("Referer" to mainUrl, "User-Agent" to USER_AGENT)
            if (json.has("securedLink")) {
                val s1 = json.getString("securedLink").replace("\\/", "/")
                if (s1.isNotBlank()) {
                    callback(newExtractorLink(name, "$name - Server 1", s1, ExtractorLinkType.M3U8) {
                        this.referer = mainUrl
                        this.headers = refHeader
                    })
                }
            }
            if (json.has("videoSource")) {
                val s2 = json.getString("videoSource").replace("\\/", "/")
                if (s2.isNotBlank()) {
                    callback(newExtractorLink(name, "$name - Server 2", s2, if (s2.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
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
        val scripts = doc.select("script").joinToString(" ") { it.data() }

        val m3u8Regex = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE)
        val sourceRegex = Regex("""file["']?\s*:\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)

        val videoUrl = m3u8Regex.find(scripts)?.groupValues?.get(1)
            ?: sourceRegex.find(scripts)?.groupValues?.get(1)

        if (videoUrl != null) {
            val isM3u8 = videoUrl.contains(".m3u8")
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = videoUrl,
                    type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
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
        val sourceRegex = Regex("""file["']?\s*:\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)

        val videoUrl = m3u8Regex.find(scripts)?.groupValues?.get(1)
            ?: sourceRegex.find(scripts)?.groupValues?.get(1)

        if (videoUrl != null) {
            val isM3u8 = videoUrl.contains(".m3u8")
            if (isM3u8) {
                generateM3u8(this.name, videoUrl, ref).forEach(callback)
            } else {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = videoUrl,
                        type = ExtractorLinkType.VIDEO
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

open class PlaycdnExtractor : ExtractorApi() {
    override val name = "PlayCDN"
    override val mainUrl = "https://playcdn.de"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = url.replace("\\", "").trim()
        val slug = cleanUrl.substringAfter("playcdn.de/").substringBefore("?").substringBefore("/").substringBefore("#")
        if (slug.isBlank()) return

        runCatching {
            val verifyRes = app.get(
                "$mainUrl/verify/$slug",
                headers = mapOf("Referer" to cleanUrl, "User-Agent" to USER_AGENT)
            ).text
            val json = JSONObject(verifyRes)
            val fileUrl = json.optString("fileUrl")
            if (fileUrl.isNotBlank()) {
                listOf("1080", "720", "480", "360").forEach { qual ->
                    val qualM3u8 = fileUrl.replace(Regex("""/\\d+\\.m3u8"""), "/$qual.m3u8")
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = "$name ${qual}p",
                            url = qualM3u8,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = "$mainUrl/"
                            this.quality = qual.toIntOrNull() ?: Qualities.Unknown.value
                        }
                    )
                }
            }
        }
    }
}

open class VideonodeExtractor : ExtractorApi() {
    override val name = "Videonode"
    override val mainUrl = "https://videonode.de"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = url.replace("\\", "").trim()
        val host = when {
            cleanUrl.contains("/turbovip/") -> "turbovip"
            cleanUrl.contains("/hydrax/") -> "hydrax"
            cleanUrl.contains("/cast/") -> "cast"
            cleanUrl.contains("/p2p/") -> "p2p"
            else -> cleanUrl.substringAfter("/iframe/").substringAfter("/iframe3/").substringBefore("/")
        }
        val id = cleanUrl.removeSuffix("/").substringAfterLast("/").substringBefore("?").substringBefore("#")
        if (host.isBlank() || id.isBlank()) return

        val res = runCatching {
            app.post(
                "$mainUrl/api.php",
                data = mapOf("host" to host, "id" to id),
                headers = mapOf(
                    "Referer" to cleanUrl,
                    "Origin" to mainUrl,
                    "User-Agent" to USER_AGENT,
                    "Content-Type" to "application/x-www-form-urlencoded"
                )
            ).text
        }.getOrNull() ?: return

        val embedUrl = runCatching { JSONObject(res).optString("embedUrl") }.getOrNull()
        if (!embedUrl.isNullOrBlank()) {
            loadExtractor(embedUrl, cleanUrl, subtitleCallback, callback)
        }
    }
}
