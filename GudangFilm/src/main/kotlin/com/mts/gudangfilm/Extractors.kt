package com.mts.gudangfilm

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

// ─── DEAN EDWARDS UNPACKER HELPER ─────────────────────────────────────────────
object DeanEdwardsHelper {
    private const val CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    fun unpack(html: String): String {
        val matches = Regex("""eval\(function\(p,a,c,k,e,d\)[\s\S]+?return p\}'*\(([\s\S]+?)\.split\('\|'\)(?:,\d+,\{\})?\)\)""").findAll(html)
        val unpackedList = mutableListOf<String>()

        for (match in matches) {
            val args = match.groupValues[1].trim()
            val argMatch = Regex("""^['"]([\s\S]+?)['"]\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*['"]([\s\S]*?)['"]$""").find(args)
                ?: continue
            val p = argMatch.groupValues[1]
            val a = argMatch.groupValues[2].toIntOrNull() ?: 62
            val c = argMatch.groupValues[3].toIntOrNull() ?: 0
            val k = argMatch.groupValues[4].split("|")

            fun encode(num: Int, base: Int): String {
                fun e(n: Int): String {
                    val prefix = if (n < base) "" else e(n / base)
                    val rem = n % base
                    val ch = if (rem < CHARS.length) CHARS[rem] else (if (rem > 35) (rem + 29).toChar() else CHARS[rem])
                    return prefix + ch
                }
                return e(num)
            }

            val lookup = HashMap<String, String>()
            for (i in 0 until c) {
                val key = encode(i, a)
                lookup[key] = if (i < k.size && k[i].isNotBlank()) k[i] else key
            }

            val unpacked = Regex("""\b\w+\b""").replace(p) { m -> lookup[m.value] ?: m.value }
            unpackedList.add(unpacked)
        }
        return unpackedList.joinToString("\n")
    }
}

// ─── 1. VIDHIDE / MORENCIUS / CALLISTANISE EXTRACTOR ─────────────────────────
open class VidHideExtractor(
    override val name: String = "VidHide",
    override val mainUrl: String = "https://vidhidehub.com"
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
            val domain = try {
                val u = java.net.URL(cleanUrl)
                "${u.protocol}://${u.host}"
            } catch (_: Exception) { mainUrl }

            val response = app.get(
                cleanUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to (referer ?: "$domain/")
                )
            )
            val pageHtml = response.text

            // 1. Unpack Dean Edwards packed JS
            val unpackedCustom = DeanEdwardsHelper.unpack(pageHtml)
            val unpackedDefault = runCatching { getAndUnpack(pageHtml) }.getOrDefault("")
            val fullContent = "$pageHtml\n$unpackedCustom\n$unpackedDefault"
            val normalizedContent = fullContent
                .replace("\\'", "'")
                .replace("\\\"", "\"")
                .replace("\\/", "/")

            // 2. Extract subtitles (.vtt)
            val vttRegex = Regex("""https?://[^'"\s<>]+\.vtt[^'"\s<>]*""")
            vttRegex.findAll(normalizedContent).forEach { vttMatch ->
                val vttUrl = vttMatch.value
                val lang = when {
                    vttUrl.contains("_ind", true) || vttUrl.contains("indonesian", true) -> "Indonesian"
                    vttUrl.contains("_eng", true) || vttUrl.contains("english", true) -> "English"
                    vttUrl.contains("_mal", true) || vttUrl.contains("malay", true) -> "Malay"
                    else -> "Subtitle"
                }
                subtitleCallback.invoke(SubtitleFile(lang, vttUrl))
            }

            val seenUrls = hashSetOf<String>()

            // 3. Extract M3U8 Master URLs
            val m3u8Regex = Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
            m3u8Regex.findAll(normalizedContent).forEach { match ->
                val m3u8Url = match.value
                if (seenUrls.add(m3u8Url)) {
                    generateM3u8(name, m3u8Url, cleanUrl).forEach(callback)
                }
            }

            // 4. Extract direct MP4 URLs
            val mp4Regex = Regex("""https?://[^'"\s<>]+\.mp4[^'"\s<>]*""")
            mp4Regex.findAll(normalizedContent).forEach { match ->
                val mp4Url = match.value
                if (seenUrls.add(mp4Url)) {
                    val quality = when {
                        mp4Url.contains("1080") -> Qualities.P1080.value
                        mp4Url.contains("720") -> Qualities.P720.value
                        mp4Url.contains("480") -> Qualities.P480.value
                        mp4Url.contains("360") -> Qualities.P360.value
                        else -> Qualities.P720.value
                    }
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = "$name MP4",
                            url = mp4Url,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = cleanUrl
                            this.quality = quality
                        }
                    )
                }
            }
        }
    }
}

open class MorenciusExtractor : VidHideExtractor("Morencius (VidHide)", "https://morencius.com")
open class CallistaniseExtractor : VidHideExtractor("Callistanise (VidHide)", "https://callistanise.com")
open class VidhidePlusExtractor : VidHideExtractor("VidHidePlus", "https://vidhideplus.com")
open class VidhidePreExtractor : VidHideExtractor("VidHidePre", "https://vidhidepre.com")
open class VidhideProExtractor : VidHideExtractor("VidHidePro", "https://vidhidepro.com")

// ─── 2. BYSEQ / FILEMOON EXTRACTOR ──────────────────────────────────────────
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
            val cleanUrl = url.replace("\\", "").trim()
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

            val unpacked = DeanEdwardsHelper.unpack(pageHtml).ifBlank {
                runCatching { getAndUnpack(pageHtml) }.getOrDefault("")
            }
            val content = if (unpacked.isNotBlank()) "$pageHtml\n$unpacked" else pageHtml

            Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""").findAll(content).forEach { match ->
                generateM3u8(name, match.value, "$domain/").forEach(callback)
            }

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

// ─── 3. ABYSSCDN / HYDRAX / SORA EXTRACTOR ──────────────────────────────────
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
            val cleanUrl = url.replace("\\", "").trim()
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

            val unpacked = DeanEdwardsHelper.unpack(pageHtml).ifBlank {
                runCatching { getAndUnpack(pageHtml) }.getOrDefault("")
            }
            val m3u8Regex = Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
            m3u8Regex.findAll(unpacked.ifBlank { pageHtml }).forEach { match ->
                generateM3u8(name, match.value, domain).forEach(callback)
            }

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

// ─── 4. EFEKSTREAM EXTRACTOR ─────────────────────────────────────────────────
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
            val cleanUrl = url.replace("\\", "").trim()
            val domain = try {
                val u = java.net.URL(cleanUrl)
                "${u.protocol}://${u.host}"
            } catch (_: Exception) { mainUrl }

            val response = app.get(
                cleanUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to (referer ?: "http://159.223.65.15/")
                )
            )
            val pageHtml = response.text

            val unpackedCustom = DeanEdwardsHelper.unpack(pageHtml)
            val unpackedDefault = runCatching { getAndUnpack(pageHtml) }.getOrDefault("")
            val fullContent = "$pageHtml\n$unpackedCustom\n$unpackedDefault"
            val normalizedContent = fullContent
                .replace("\\'", "'")
                .replace("\\\"", "\"")
                .replace("\\/", "/")

            val fileRegex = Regex("""['"]label['"]\s*:\s*['"]([^'"]+)['"][\s\S]*?['"]file['"]\s*:\s*['"]([^'"]+)['"]""")
            val matches = fileRegex.findAll(normalizedContent).toList()

            val seenUrls = hashSetOf<String>()

            for (match in matches) {
                val label = match.groupValues[1].trim()
                var file = match.groupValues[2].trim()
                if (file.startsWith("/")) {
                    file = "$domain$file"
                }
                if (!file.startsWith("http") || !seenUrls.add(file)) continue

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

            // Fallback: Parse direct download page (&down=load)
            val dlUrl = if (cleanUrl.contains("?")) "$cleanUrl&down=load" else "$cleanUrl?down=load"
            runCatching {
                val dlDoc = app.get(
                    dlUrl,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                        "Referer" to cleanUrl
                    )
                ).document

                dlDoc.select("a[href*='/stream/']").forEach { a ->
                    var href = a.attr("href").trim()
                    if (href.startsWith("/")) {
                        href = "$domain$href"
                    }
                    if (href.startsWith("http") && seenUrls.add(href)) {
                        val text = a.text().trim()
                        val quality = when {
                            text.contains("1080", true) -> Qualities.P1080.value
                            text.contains("720", true) -> Qualities.P720.value
                            text.contains("480", true) -> Qualities.P480.value
                            text.contains("360", true) -> Qualities.P360.value
                            else -> Qualities.P720.value
                        }
                        val qLabel = when (quality) {
                            Qualities.P1080.value -> "1080p"
                            Qualities.P720.value -> "720p"
                            Qualities.P480.value -> "480p"
                            Qualities.P360.value -> "360p"
                            else -> "HD"
                        }
                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = "$name $qLabel",
                                url = href,
                                type = ExtractorLinkType.VIDEO
                            ) {
                                this.referer = cleanUrl
                                this.quality = quality
                            }
                        )
                    }
                }
            }

            Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""").findAll(normalizedContent).forEach { m ->
                generateM3u8(name, m.value, cleanUrl).forEach(callback)
            }

            Regex("""https?://[^'"\s<>]+\.mp4[^'"\s<>]*""").findAll(normalizedContent).forEach { m ->
                val mp4Url = m.value
                if (seenUrls.add(mp4Url)) {
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
}

// ─── 5. STREAMP2P EXTRACTOR ──────────────────────────────────────────────────
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
            val cleanUrl = url.replace("\\", "").trim()
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

            val unpacked = DeanEdwardsHelper.unpack(html).ifBlank {
                runCatching { getAndUnpack(html) }.getOrDefault("")
            }
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
