package com.mts.pusatfilm21

import android.util.Base64
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Extractor untuk AbyssCDN / Hydrax / Sora player.
 * Melakukan dekripsi blok AES-CTR dan menghasilkan link MP4 Sora terus yang boleh dimainkan.
 * Menyokong playhydrax.com, abyssplayer.com, dan abyss.to.
 */
open class AbyssCdn(
    override val name: String = "Hydrax",
    override val mainUrl: String = "https://playhydrax.com"
) : ExtractorApi() {
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
        runCatching {
            val cleanUrl = url.replace("\\", "").trim()
            val domain = try {
                val u = java.net.URL(cleanUrl)
                "${u.protocol}://${u.host}/"
            } catch (_: Exception) { "$mainUrl/" }

            val response = app.get(
                cleanUrl,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to (referer ?: domain)
                )
            )
            val pageHtml = response.text

            // 1. Direct m3u8 in page if present
            val unpacked = runCatching { getAndUnpack(pageHtml) }.getOrDefault("")
            val m3u8Regex = Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
            m3u8Regex.findAll(unpacked.ifBlank { pageHtml }).forEach { match ->
                generateM3u8(name, match.value, domain).forEach(callback)
            }

            // 2. Decrypt datas block using AES-CTR algorithm
            val base64Str = Regex("""const\s+datas\s*=\s*"([^"]+)"""").find(pageHtml)?.groupValues?.get(1) ?: return@runCatching
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            val latin1Str = String(decodedBytes, Charsets.ISO_8859_1)
            val json = JSONObject(latin1Str)
            val slug = json.optString("slug")
            val userId = json.opt("user_id")?.toString().orEmpty()
            val md5Id = json.opt("md5_id")?.toString().orEmpty()
            val media = json.optString("media")

            if (slug.isBlank() || media.isBlank() || userId.isBlank() || md5Id.isBlank()) return@runCatching

            val keyStr = "$userId:$slug:$md5Id"
            val keyBytesStr = md5(keyStr.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            val key = keyBytesStr.toByteArray(Charsets.UTF_8)
            val iv = key.sliceArray(0 until 16)

            val mediaCiphertext = media.toByteArray(Charsets.ISO_8859_1)
            val decryptedMediaBytes = decryptAesCtr(mediaCiphertext, key, iv)
            val decryptedMediaStr = String(decryptedMediaBytes, Charsets.UTF_8)
            val mediaJson = JSONObject(decryptedMediaStr)

            // Emit MP4 direct sources
            val mp4 = mediaJson.optJSONObject("mp4")
            val sources = mp4?.optJSONArray("sources")
            if (sources != null) {
                for (i in 0 until sources.length()) {
                    val src = sources.optJSONObject(i) ?: continue
                    val label = src.optString("label", "HD")
                    val srcUrl = src.optString("url", "")
                    val srcPath = src.optString("path", "")
                    if (srcUrl.isBlank() || srcPath.isBlank()) continue

                    val finalStreamUrl = if (srcUrl.endsWith("/")) "$srcUrl$srcPath" else "$srcUrl/$srcPath"

                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = "$name $label",
                            url = finalStreamUrl,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = domain
                            this.headers = mapOf(
                                "Referer" to domain,
                                "User-Agent" to USER_AGENT
                            )
                            this.quality = parseQuality(label)
                        }
                    )
                }
            }

            // Emit HLS if present
            val hlsUrl = mediaJson.optString("hls", "")
            if (hlsUrl.isNotBlank()) {
                generateM3u8(name, hlsUrl, domain).forEach(callback)
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
 * Menguraikan butang server-item dan pautan data-frame (Base64), kemudian menghantar ke extractor yang tepat.
 */
class KotakajaibMe : ExtractorApi() {
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
                    "User-Agent" to USER_AGENT,
                    "Referer" to (referer ?: "https://v4.pusatfilm21info.net/")
                )
            ).document
        }.getOrNull() ?: return

        // 1. Baca atribut data-frame pada .server-item dan mana-mana elemen bertag data-frame
        val frameItems = doc.select(".server-item, [data-frame]")
        for (item in frameItems) {
            val rawB64 = item.attr("data-frame")
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
 * Membuka kunci skrip terenkripsi XOR (var k=..., b=atob(...)) dan mengekstrak pautan MP4 / HLS.
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
                    "User-Agent" to USER_AGENT,
                    "Referer" to (referer ?: "https://kotakajaib.me/")
                )
            ).text
        }.getOrNull() ?: return

        // Nyahkod cipher XOR
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

                // Ekstrak HLS jika ada
                val hlsMatch = Regex("""HLS\s*=\s*"([^"]+)"""").find(decScript)?.groupValues?.get(1)
                if (!hlsMatch.isNullOrBlank()) {
                    val fullHls = if (hlsMatch.startsWith("//")) "https:$hlsMatch" else hlsMatch
                    generateM3u8(name, fullHls, "$mainUrl/").forEach(callback)
                }

                // Ekstrak MP4BASE jika ada
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

        // Direct m3u8 fallback regex
        Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""").findAll(pageHtml).forEach { m ->
            generateM3u8(name, m.value, "$mainUrl/").forEach(callback)
        }
    }
}

/**
 * Extractor untuk Turbovid / Emturbovid (turbovidhls.com, emturbovid.com)
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

            // 1. Direct master M3U8 from CDN
            if (id.isNotBlank()) {
                val directM3u8 = "https://cdn4.turboviplay.com/data3/$id/$id.m3u8"
                runCatching {
                    generateM3u8(name, directM3u8, domain).forEach(callback)
                }
            }

            // 2. Scan player page for any embedded m3u8
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
    override val name = "Masukestin"
    override val mainUrl = "https://masukestin.to"
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
