package com.mts.rebahin

import android.util.Base64
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Extractor untuk AbyssCDN / Hydrax / Sora player.
 * 1. Kaedah Utama: Menghubungi API enc-dec.app untuk mendapatkan direct video/mp4 stream Sora (1080p, 720p, 480p, 360p).
 * 2. Kaedah Sandaran: Penyahsulitan tempatan AES-CTR menggunakan Jackson ObjectMapper tanpa ralat aksara kawalan JSON.
 */
open class AbyssCdn(
    override val name: String = "Hydrax",
    override val mainUrl: String = "https://playhydrax.com"
) : ExtractorApi() {
    override val requiresReferer = false

    companion object {
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

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
        val cleanUrl = url.replace("\\", "").trim()
        val domain = try {
            val u = java.net.URL(cleanUrl)
            "${u.protocol}://${u.host}"
        } catch (_: Exception) {
            "https://playhydrax.com"
        }

        // ==========================================
        // KAEDAH 1: Sora API enc-dec.app (Direct MP4)
        // ==========================================
        val soraSuccess = runCatching {
            val apiRes = app.get(
                "https://enc-dec.app/api/dec-abyss?url=${cleanUrl}",
                headers = mapOf(
                    "User-Agent" to UA,
                    "Accept" to "application/json, text/plain, */*"
                ),
                timeout = 10
            ).text

            if (apiRes.isNotBlank()) {
                val mapper = ObjectMapper()
                val rootNode = mapper.readTree(apiRes)
                val status = rootNode.path("status").asText()
                val sourcesNode = rootNode.path("sources")

                if ((status == "success" || sourcesNode.isArray) && sourcesNode.size() > 0) {
                    for (i in 0 until sourcesNode.size()) {
                        val src = sourcesNode.get(i)
                        val fileUrl = src.path("file").asText()
                        val label = src.path("label").asText().ifBlank { "HD" }

                        if (fileUrl.isNotBlank() && fileUrl.startsWith("http")) {
                            val qual = parseQuality(label)
                            callback.invoke(
                                newExtractorLink(
                                    source = name,
                                    name = "$name - $label (Sora Direct)",
                                    url = fileUrl,
                                    type = ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = "$domain/"
                                    this.quality = qual
                                }
                            )
                        }
                    }
                    return@runCatching true
                }
            }
            false
        }.getOrDefault(false)

        if (soraSuccess) return

        // ==========================================
        // KAEDAH 2: Local AES-CTR Decryption Fallback
        // ==========================================
        runCatching {
            val html = app.get(cleanUrl, headers = mapOf("User-Agent" to UA, "Referer" to (referer ?: "$domain/"))).text
            val match = Regex("""(['"])(?:(?!\1).)*\\datas?\\?\s*[:=]\s*\\?(['"])(.*?)\2""").find(html)
                ?: Regex("""datas\\s*[:=]\\s*['"]([^'"]+)""").find(html)
                ?: return@runCatching

            val ciphertextB64 = match.groupValues.lastOrNull { it.isNotBlank() && it.length > 20 }
                ?: return@runCatching
            val rawCiphertext = Base64.decode(ciphertextB64, Base64.DEFAULT)
            if (rawCiphertext.size <= 32) return@runCatching

            val slice = rawCiphertext.copyOfRange(rawCiphertext.size - 32, rawCiphertext.size - 16)
            val iv = rawCiphertext.copyOfRange(rawCiphertext.size - 16, rawCiphertext.size)
            val key = md5(slice + md5(domain.toByteArray(Charsets.UTF_8)))
            val encryptedData = rawCiphertext.copyOfRange(0, rawCiphertext.size - 32)

            val decryptedBytes = decryptAesCtr(encryptedData, key, iv)
            val decryptedStr = String(decryptedBytes, Charsets.UTF_8).trim()

            val jsonStart = decryptedStr.indexOf('{')
            val jsonEnd = decryptedStr.lastIndexOf('}')
            if (jsonStart != -1 && jsonEnd > jsonStart) {
                val jsonStr = decryptedStr.substring(jsonStart, jsonEnd + 1)
                val mapper = ObjectMapper()
                val rootNode = mapper.readTree(jsonStr)

                val fields = listOf("fullhd", "hd", "sd", "mplus", "origin")
                for (f in fields) {
                    val streamNode = rootNode.path(f)
                    if (!streamNode.isMissingNode && !streamNode.isNull) {
                        var streamUrl = streamNode.asText()
                        if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                            if (!streamUrl.endsWith(".mp4", ignoreCase = true) && !streamUrl.contains(".mp4#")) {
                                streamUrl = "$streamUrl#.mp4"
                            }
                            val label = f.uppercase()
                            val qual = when (f) {
                                "fullhd" -> Qualities.P1080.value
                                "hd" -> Qualities.P720.value
                                "sd" -> Qualities.P480.value
                                else -> Qualities.P720.value
                            }
                            callback.invoke(
                                newExtractorLink(
                                    source = name,
                                    name = "$name - $label (Hydrax)",
                                    url = streamUrl,
                                    type = ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = "$domain/"
                                    this.quality = qual
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun parseQuality(label: String): Int {
        return when {
            label.contains("1080") -> Qualities.P1080.value
            label.contains("720") -> Qualities.P720.value
            label.contains("480") -> Qualities.P480.value
            label.contains("360") -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }
}

class Playhydrax : AbyssCdn("Hydrax", "https://playhydrax.com")
class AbyssPlayer : AbyssCdn("AbyssPlayer", "https://abyssplayer.com")
class AbyssTo : AbyssCdn("Abyss", "https://abyss.to")

/**
 * Extractor untuk Kotakajaib gateway (kotakajaib.me)
 */
open class KotakajaibMe : ExtractorApi() {
    override var name = "Kotakajaib"
    override var mainUrl = "https://kotakajaib.me"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = url.replace("\\", "").trim()
        val doc = runCatching {
            app.get(
                cleanUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Referer" to (referer ?: "https://kotakajaib.me/")
                )
            ).document
        }.getOrNull() ?: return

        // 1. Baca atribut data-frame pada .server-item dan mana-mana elemen bertag data-frame
        val frameItems = doc.select(".server-item, [data-frame]")
        for (item in frameItems) {
            val rawB64 = item.attr("data-frame").trim()
            if (rawB64.isBlank()) continue
            val decoded = runCatching {
                String(Base64.decode(rawB64, Base64.DEFAULT), Charsets.UTF_8).trim()
            }.getOrNull() ?: continue

            val finalUrl = when {
                decoded.startsWith("//") -> "https:$decoded"
                decoded.startsWith("http") -> decoded
                else -> ""
            }
            if (finalUrl.isBlank()) continue

            when {
                finalUrl.contains("playhydrax.com") -> {
                    Playhydrax().getUrl(finalUrl, cleanUrl, subtitleCallback, callback)
                }
                finalUrl.contains("emturbovid.com") || finalUrl.contains("turboviplay.com") -> {
                    Emturbovid().getUrl(finalUrl, cleanUrl, subtitleCallback, callback)
                }
                finalUrl.contains("gdriveplayer.to") -> {
                    Gdriveplayer().getUrl(finalUrl, cleanUrl, subtitleCallback, callback)
                }
                finalUrl.contains("abyss") -> {
                    AbyssPlayer().getUrl(finalUrl, cleanUrl, subtitleCallback, callback)
                }
                else -> {
                    loadExtractor(finalUrl, cleanUrl, subtitleCallback, callback)
                }
            }
        }

        // 2. Imbas sebarang iframe langsung dalam kotakajaib
        doc.select("iframe").forEach { ifr ->
            val src = ifr.attr("src").ifBlank { ifr.attr("data-src") }
            if (src.isNotBlank() && !src.contains("googletagmanager") && !src.contains("googleads")) {
                val fullIframe = if (src.startsWith("//")) "https:$src" else src
                loadExtractor(fullIframe, cleanUrl, subtitleCallback, callback)
            }
        }
    }
}

/**
 * Extractor untuk Gdriveplayer (gdriveplayer.to)
 */
class Gdriveplayer : ExtractorApi() {
    override var name = "GDPlayer"
    override var mainUrl = "https://gdriveplayer.to"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = if (url.startsWith("//")) "https:$url" else url.replace("\\", "").trim()
        val pageHtml = runCatching {
            app.get(
                cleanUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Referer" to (referer ?: "https://kotakajaib.me/")
                )
            ).text
        }.getOrNull() ?: return

        val match = Regex("""var\s+k\s*=\s*"([^"]+)",\s*b\s*=\s*atob\("([^"]+)"\)""").find(pageHtml)
        if (match != null) {
            val key = match.groupValues[1]
            val b64 = match.groupValues[2]
            val bBytes = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()
            if (bBytes != null && key.isNotEmpty()) {
                val decoded = StringBuilder()
                val keyBytes = key.toByteArray(Charsets.UTF_8)
                for (i in bBytes.indices) {
                    val decodedByte = bBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()
                    decoded.append(decodedByte.toChar())
                }
                val decScript = decoded.toString()

                val hlsMatch = Regex("""HLS\s*=\s*"([^"]+)"""").find(decScript)?.groupValues?.get(1)
                if (!hlsMatch.isNullOrBlank()) {
                    val fullHls = if (hlsMatch.startsWith("//")) "https:$hlsMatch" else hlsMatch
                    generateM3u8(name, fullHls, "$mainUrl/").forEach(callback)
                }

                val mp4Base = Regex("""MP4BASE\s*=\s*"([^"]+)"""").find(decScript)?.groupValues?.get(1)
                if (!mp4Base.isNullOrBlank()) {
                    val baseFixed = if (mp4Base.startsWith("//")) "https:$mp4Base" else mp4Base
                    val stream360 = "$baseFixed&res=360"
                    callback.invoke(
                        newExtractorLink(name, "$name 360p", stream360, ExtractorLinkType.VIDEO) {
                            this.referer = cleanUrl
                            this.quality = Qualities.P360.value
                        }
                    )
                    val stream720 = "$baseFixed&res=720"
                    callback.invoke(
                        newExtractorLink(name, "$name 720p", stream720, ExtractorLinkType.VIDEO) {
                            this.referer = cleanUrl
                            this.quality = Qualities.P720.value
                        }
                    )
                }
            }
        }

        Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""").findAll(pageHtml).forEach { m ->
            generateM3u8(name, m.value, "$mainUrl/").forEach(callback)
        }
    }
}

/**
 * Extractor untuk Turbovid / Emturbovid
 */
open class TurbovidExtractor(
    override val name: String = "Turbovid",
    override val mainUrl: String = "https://turbovidhls.com"
) : ExtractorApi() {
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        runCatching {
            val cleanUrl = url.replace("\\", "").trim()
            val id = cleanUrl.removeSuffix("/").substringAfterLast("/").substringBefore("?").substringBefore("#")
            val domain = try {
                val u = java.net.URL(cleanUrl)
                "${u.protocol}://${u.host}/"
            } catch (_: Exception) { "$mainUrl/" }

            if (id.isNotBlank()) {
                val directM3u8 = "https://cdn4.turboviplay.com/data3/$id/$id.m3u8"
                runCatching {
                    generateM3u8(name, directM3u8, domain).forEach(callback)
                }
            }

            val pageHtml = runCatching {
                app.get(
                    cleanUrl,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to (referer ?: domain)
                    )
                ).text
            }.getOrDefault("")

            if (pageHtml.isNotBlank()) {
                val m3u8Regex = Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
                val foundM3u8 = m3u8Regex.findAll(pageHtml).map { it.value }.toSet()
                foundM3u8.forEach { m3u8Url ->
                    if (!m3u8Url.contains("/data3/$id/$id.m3u8")) {
                        generateM3u8(name, m3u8Url, domain).forEach(callback)
                    }
                }
            }
        }
    }
}

class Emturbovid : TurbovidExtractor("Turbovid", "https://emturbovid.com")

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
        }
    }
}

class MasukestinExtractor : ExtractorApi() {
    override var name = "Masukestin"
    override var mainUrl = "https://masukestin.to"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        runCatching {
            val cleanUrl = url.replace("\\", "").trim()
            val text = app.get(cleanUrl, headers = mapOf("User-Agent" to USER_AGENT)).text
            val m3u8Regex = Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
            m3u8Regex.findAll(text).forEach { match ->
                generateM3u8(name, match.value, cleanUrl).forEach(callback)
            }
        }
    }
}

open class VidHideExtractor(
    override val name: String = "VidHide",
    override val mainUrl: String = "https://vidhide.com"
) : ExtractorApi() {
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url, headers = mapOf("Referer" to (referer ?: mainUrl))).text
        val packed = getAndUnpack(res)
        Regex("""(?:file|source)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").find(packed)?.groupValues?.get(1)?.let { link ->
            generateM3u8(name, link, url).forEach(callback)
        }
    }
}

class StreamP2PExtractor : ExtractorApi() {
    override val name = "StreamP2P"
    override val mainUrl = "https://streamp2p.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val html = app.get(url, headers = mapOf("Referer" to (referer ?: mainUrl))).text
        Regex("""file\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").find(html)?.groupValues?.get(1)?.let { m3u8 ->
            generateM3u8(name, m3u8, url).forEach(callback)
        }
    }
}
