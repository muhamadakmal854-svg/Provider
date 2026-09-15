package com.mts.pusatfilm21

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
            "${u.protocol}://${u.host}/"
        } catch (_: Exception) { "$mainUrl/" }

        val pageHtml = runCatching {
            app.get(
                cleanUrl,
                headers = mapOf(
                    "User-Agent" to UA,
                    "Referer" to (referer ?: domain)
                )
            ).text
        }.getOrNull() ?: return

        // 1. Direct m3u8 dalam page jika ada
        val unpacked = runCatching { getAndUnpack(pageHtml) }.getOrDefault("")
        Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""").findAll(unpacked.ifBlank { pageHtml }).forEach { match ->
            generateM3u8(name, match.value, domain).forEach(callback)
        }

        // Cari blok data terenkripsi
        val encrypted = Regex("""const\s+datas\s*=\s*"([^"]+)"""").find(pageHtml)?.groupValues?.get(1) ?: return

        var soraCount = 0

        // Kaedah 1: enc-dec.app API untuk mendapatkan pautan langsung Sora (video/mp4)
        runCatching {
            val response = app.post(
                "https://enc-dec.app/api/dec-abyss",
                headers = mapOf(
                    "User-Agent" to UA,
                    "Origin" to "https://playhydrax.com",
                    "Referer" to "https://playhydrax.com/"
                ),
                requestBody = """{"text":"$encrypted"}""".trimIndent()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
            ).text
            val json = JSONObject(response).optJSONObject("result")
            val sources = json?.optJSONArray("sources")
            if (sources != null && sources.length() > 0) {
                for (i in 0 until sources.length()) {
                    val src = sources.optJSONObject(i) ?: continue
                    if (src.optBoolean("status", true)) {
                        val srcUrl = src.optString("url")
                        val type = src.optString("type", "HD")
                        if (srcUrl.isNotBlank()) {
                            callback.invoke(
                                newExtractorLink(
                                    source = name,
                                    name = "$name $type",
                                    url = srcUrl,
                                    type = ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = "https://playhydrax.com/"
                                    this.headers = mapOf(
                                        "Referer" to "https://playhydrax.com/",
                                        "User-Agent" to UA
                                    )
                                    this.quality = parseQuality(type)
                                }
                            )
                            soraCount++
                        }
                    }
                }
            }
        }

        // Kaedah 2: Penyahsulitan tempatan AES-CTR (Sandaran kukuh sekiranya enc-dec gagal atau tidak lengkap)
        runCatching {
            val decodedBytes = Base64.decode(encrypted, Base64.DEFAULT)
            val latin1Str = String(decodedBytes, Charsets.ISO_8859_1)

            val mapper = ObjectMapper()
            val jsonNode = mapper.readTree(latin1Str)
            val slug = jsonNode.get("slug")?.asText().orEmpty()
            val userId = jsonNode.get("user_id")?.asText().orEmpty()
            val md5Id = jsonNode.get("md5_id")?.asText().orEmpty()
            val media = jsonNode.get("media")?.asText().orEmpty()

            if (slug.isNotBlank() && userId.isNotBlank() && md5Id.isNotBlank() && media.isNotBlank()) {
                val keyStr = "$userId:$slug:$md5Id"
                val keyBytesStr = md5(keyStr.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                val key = keyBytesStr.toByteArray(Charsets.UTF_8)
                val iv = key.sliceArray(0 until 16)

                val mediaCiphertext = media.toByteArray(Charsets.ISO_8859_1)
                val decryptedMediaBytes = decryptAesCtr(mediaCiphertext, key, iv)
                val decryptedMediaStr = String(decryptedMediaBytes, Charsets.UTF_8)
                val decNode = mapper.readTree(decryptedMediaStr)

                val sources = decNode.get("mp4")?.get("sources")
                if (sources != null && sources.isArray) {
                    for (src in sources) {
                        val label = src.get("label")?.asText() ?: "HD"
                        val srcUrl = src.get("url")?.asText().orEmpty()
                        val srcPath = src.get("path")?.asText().orEmpty()
                        if (srcUrl.isBlank() || srcPath.isBlank()) continue

                        val rawStream = if (srcUrl.endsWith("/")) "$srcUrl$srcPath" else "$srcUrl/$srcPath"
                        val finalStreamUrl = if (rawStream.contains(".mp4") || rawStream.contains(".m3u8")) rawStream else "$rawStream#.mp4"

                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = if (soraCount > 0) "$name $label (Backup)" else "$name $label",
                                url = finalStreamUrl,
                                type = ExtractorLinkType.VIDEO
                            ) {
                                this.referer = "https://playhydrax.com/"
                                this.headers = mapOf(
                                    "Referer" to "https://playhydrax.com/",
                                    "User-Agent" to UA
                                )
                                this.quality = parseQuality(label)
                            }
                        )
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
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Referer" to (referer ?: "https://v4.pusatfilm21info.net/")
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
