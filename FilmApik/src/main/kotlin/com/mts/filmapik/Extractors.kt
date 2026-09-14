package com.mts.filmapik

import android.util.Base64
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// ─── 1. EFEKSTREAM / VIP SERVER EXTRACTOR ─────────────────────────────────────
open class EfekStream : ExtractorApi() {
    override val name: String = "EfekStream (VIP Server)"
    override val mainUrl: String = "https://v2.efek.stream"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        runCatching {
            val cleanUrl = url.replace("\\", "")
            val domain = try {
                val u = java.net.URL(cleanUrl)
                "${u.protocol}://${u.host}"
            } catch (_: Exception) { mainUrl }

            val response = app.get(
                cleanUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to (referer ?: "https://filmapik.college/")
                )
            )
            val pageHtml = response.text

            // 1. Unpack Dean Edwards packed JS
            val unpacked = runCatching { getAndUnpack(pageHtml) }.getOrDefault("")
            val contentToSearch = if (unpacked.isNotBlank()) "$pageHtml\n$unpacked" else pageHtml

            // 2. Extract sources from jwplayer setup in unpacked script
            // Example: {'label':'720p','type':'video/mp4','file':'/stream/720/ErEgrAYhIezE4xD/__001'}
            val fileRegex = Regex("""['"]label['"]\s*:\s*['"]([^'"]+)['"]\s*,\s*['"]type['"]\s*:\s*['"]video/mp4['"]\s*,\s*['"]file['"]\s*:\s*['"]([^'"]+)['"]""")
            val matches = fileRegex.findAll(contentToSearch).toList()

            if (matches.isNotEmpty()) {
                for (match in matches) {
                    val label = match.groupValues[1].trim()
                    var file = match.groupValues[2].trim().replace("\\", "")
                    if (file.startsWith("/")) {
                        file = "$domain$file"
                    }
                    val quality = when {
                        label.contains("1080", true) -> Qualities.P1080.value
                        label.contains("720", true) -> Qualities.P720.value
                        label.contains("480", true) -> Qualities.P480.value
                        label.contains("360", true) -> Qualities.P360.value
                        else -> Qualities.Unknown.value
                    }

                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = "$name $label",
                            url = file,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = cleanUrl
                            this.quality = quality
                        }
                    )
                }
            }

            // 3. Fallback: Search for any direct m3u8 or mp4 URLs
            Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""").findAll(contentToSearch).forEach { m ->
                generateM3u8(name, m.value, cleanUrl).forEach(callback)
            }

            Regex("""https?://[^'"\s<>]+\.mp4[^'"\s<>]*""").findAll(contentToSearch).forEach { m ->
                val mp4Url = m.value
                val q = when {
                    mp4Url.contains("1080") -> Qualities.P1080.value
                    mp4Url.contains("720") -> Qualities.P720.value
                    mp4Url.contains("480") -> Qualities.P480.value
                    mp4Url.contains("360") -> Qualities.P360.value
                    else -> Qualities.P720.value
                }
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name Direct",
                        url = mp4Url,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = cleanUrl
                        this.quality = q
                    }
                )
            }
        }
    }
}

// ─── 2. ABYSSCDN / HYDRAX / SORA EXTRACTOR ──────────────────────────────────
open class AbyssCdn(
    override val name: String = "AbyssCDN",
    override val mainUrl: String = "https://abysscdn.com"
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
            val cleanUrl = url.replace("\\", "")
            val domain = try {
                val u = java.net.URL(cleanUrl)
                "${u.protocol}://${u.host}/"
            } catch (_: Exception) { "$mainUrl/" }

            val response = app.get(
                cleanUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to domain
                )
            )
            val pageHtml = response.text

            // 1. Check unpacked m3u8
            val unpacked = runCatching { getAndUnpack(pageHtml) }.getOrDefault("")
            val m3u8Regex = Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
            m3u8Regex.findAll(unpacked.ifBlank { pageHtml }).forEach { match ->
                generateM3u8(name, match.value, domain).forEach(callback)
            }

            // 2. Decrypt datas block using Sora algorithm
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

            val mediaCiphertext = try {
                Base64.decode(media, Base64.DEFAULT)
            } catch (_: Exception) {
                media.toByteArray(Charsets.ISO_8859_1)
            }
            val decryptedMediaBytes = decryptAesCtr(mediaCiphertext, key, iv)
            val decryptedMediaStr = String(decryptedMediaBytes, Charsets.UTF_8)
            val mediaJson = JSONObject(decryptedMediaStr)

            val mp4 = mediaJson.optJSONObject("mp4") ?: return@runCatching
            val sources = mp4.optJSONArray("sources") ?: return@runCatching
            val domainsObj = if (mp4.has("domains")) mp4.optJSONObject("domains") else if (mediaJson.has("domains")) mediaJson.optJSONObject("domains") else null
            val domainsArr = if (mp4.has("domains")) mp4.optJSONArray("domains") else if (mediaJson.has("domains")) mediaJson.optJSONArray("domains") else null

            for (i in 0 until sources.length()) {
                val src = sources.optJSONObject(i) ?: continue
                val label = src.optString("label").ifBlank { src.optString("quality") }
                val file = src.optString("file").ifBlank { src.optString("src") }
                if (file.isBlank()) continue

                val quality = when {
                    label.contains("1080", true) -> Qualities.P1080.value
                    label.contains("720", true) -> Qualities.P720.value
                    label.contains("480", true) -> Qualities.P480.value
                    label.contains("360", true) -> Qualities.P360.value
                    else -> Qualities.Unknown.value
                }

                val targetDomains = mutableListOf<String>()
                if (domainsArr != null) {
                    for (dIdx in 0 until domainsArr.length()) {
                        val d = domainsArr.optString(dIdx)
                        if (d.isNotBlank()) targetDomains.add(d)
                    }
                }
                if (domainsObj != null) {
                    val keys = domainsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val d = domainsObj.optString(k)
                        if (d.isNotBlank()) targetDomains.add(d)
                    }
                }

                val candidateDomains = if (targetDomains.isNotEmpty()) targetDomains.distinct() else listOf("s1.sssrr.org", "s2.sssrr.org", "s3.sssrr.org", "sora.sssrr.org")
                val streamUrl = if (file.startsWith("http")) {
                    file
                } else {
                    "https://${candidateDomains.first()}/sora/$file"
                }

                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name ${label.ifBlank { "HD" }}",
                        url = streamUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = domain
                        this.quality = quality
                    }
                )
            }
        }
    }
}

open class AbyssPlayer : AbyssCdn("Hydrax (AbyssPlayer)", "https://abyssplayer.com")
open class AbyssTo : AbyssCdn("Abyss", "https://abyss.to")
open class ShortAbyssCdn : AbyssCdn("AbyssCDN", "https://short.abysscdn.com")
open class MovieAbyssCdn : AbyssCdn("AbyssCDN", "https://movie.abysscdn.com")

// ─── 3. BYSEQ / FILEMOON EXTRACTOR ──────────────────────────────────────────
open class ByseqExtractor(
    override val name: String = "FileMoon (Byseq)",
    override val mainUrl: String = "https://byseqekaho.com"
) : ExtractorApi() {
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        runCatching {
            val cleanUrl = url.replace("\\", "")
            val domain = try {
                val u = java.net.URL(cleanUrl)
                "${u.protocol}://${u.host}"
            } catch (_: Exception) { mainUrl }

            val response = app.get(
                cleanUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to "$domain/"
                )
            )
            val pageHtml = response.text

            // 1. Unpack JS
            val unpacked = runCatching { getAndUnpack(pageHtml) }.getOrDefault("")
            val content = if (unpacked.isNotBlank()) "$pageHtml\n$unpacked" else pageHtml

            // 2. Direct HLS
            Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""").findAll(content).forEach { match ->
                generateM3u8(name, match.value, "$domain/").forEach(callback)
            }

            // 3. Direct MP4
            Regex("""https?://[^'"\s<>]+\.mp4[^'"\s<>]*""").findAll(content).forEach { match ->
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name MP4",
                        url = match.value,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "$domain/"
                        this.quality = Qualities.P1080.value
                    }
                )
            }
        }
    }
}

open class FilemoonMirror : ByseqExtractor("FileMoon", "https://filemoon.sx")

// ─── 4. STREAMP2P EXTRACTOR ──────────────────────────────────────────────────
open class StreamP2PExtractor : ExtractorApi() {
    override val name: String = "StreamP2P"
    override val mainUrl: String = "https://strp2p.site"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        runCatching {
            val cleanUrl = url.replace("\\", "")
            val domain = try {
                val u = java.net.URL(cleanUrl)
                "${u.protocol}://${u.host}"
            } catch (_: Exception) { mainUrl }

            val response = app.get(
                cleanUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to "$domain/"
                )
            )
            val html = response.text

            val unpacked = runCatching { getAndUnpack(html) }.getOrDefault("")
            val content = if (unpacked.isNotBlank()) "$html\n$unpacked" else html

            Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""").findAll(content).forEach { match ->
                generateM3u8(name, match.value, "$domain/").forEach(callback)
            }
            Regex("""https?://[^'"\s<>]+\.mp4[^'"\s<>]*""").findAll(content).forEach { match ->
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name Direct",
                        url = match.value,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "$domain/"
                        this.quality = Qualities.P720.value
                    }
                )
            }
        }
    }
}
