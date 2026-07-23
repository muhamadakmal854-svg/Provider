package com.sad25kag.drakorkita

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.Base64

object DrakorKitaResolver {
    data class ApiPayload(
        val detailUrl: String,
        val title: String,
        val movieId: String,
        val episodeId: String,
        val serverXid: String,
        val tag: String,
        val c: String,
        val t: String,
        val ver: String,
        val cApiHost: String,
        val isMob: String,
        val isUc: String,
        val mediaType: String
    )

    fun normalizeUrl(url: String, mainUrl: String): String {
        val trimmed = url.trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .replace("\\/", "/")
        return when {
            trimmed.isBlank() -> ""
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> mainUrl.trimEnd('/') + trimmed
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> trimmed
        }
    }

    /**
     * Validates that a URL returned by the API is actually usable (not a broken placeholder).
     * The API returns broken URLs like:
     * - "https://drakorkita.stream/#"        (empty P2P hash)
     * - "https://abysscdn.com/?v=?sub=...&lang=" (empty Hydrax v param)
     * - "https://dqt.my.id/e/.html"          (empty SB hash path)
     */
    private fun isValidVideoApiUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()

        // P2P: drakorkita.stream/#HASH - reject if hash is empty or just "#"
        if (lower.contains("drakorkita.stream")) {
            val fragment = url.substringAfter("#", "").trim()
            return fragment.length > 3  // needs a real hash
        }

        // Hydrax: abysscdn.com/?v=HASH - reject if ?v= is empty or starts with "?"
        if (lower.contains("abysscdn.com")) {
            val vParam = url.substringAfter("?v=", "").substringBefore("&").trim()
            return vParam.isNotBlank() && !vParam.startsWith("?")
        }

        // StreamSB: dqt.my.id/e/HASH.html - reject if HASH is empty
        if (lower.contains("dqt.my.id/e/")) {
            val hash = url.substringAfter("/e/", "").substringBefore(".").trim()
            return hash.length > 3  // needs a real hash
        }

        // handal.bid stream - validate similar to dqt
        if (lower.contains("handal.bid/e/")) {
            val hash = url.substringAfter("/e/", "").substringBefore(".").trim()
            return hash.length > 3
        }

        return true  // assume valid for other URLs
    }

    fun extractEmbedCandidates(document: Document, mainUrl: String): List<String> {
        val candidates = linkedSetOf<String>()

        document.select("iframe[src], embed[src], video[src], source[src]").forEach { element ->
            val src = element.attr("src").ifBlank { element.attr("data-src") }
            normalizeUrl(src, mainUrl).takeIf { it.isNotBlank() }?.let(candidates::add)
        }

        document.select("a[href], button[data-src], button[data-url], div[data-src], div[data-url], li[data-src], li[data-url]").forEach { element ->
            listOf(
                element.attr("href"),
                element.attr("data-src"),
                element.attr("data-url"),
                element.attr("data-video"),
                element.attr("data-link"),
                element.attr("data-embed")
            ).forEach { raw ->
                val fixed = normalizeUrl(raw, mainUrl)
                if (isPlayableOrEmbed(fixed)) candidates.add(fixed)
            }
        }

        document.select("option[value]").forEach { option ->
            val raw = option.attr("value").trim()
            val direct = normalizeUrl(raw, mainUrl)
            if (isPlayableOrEmbed(direct)) candidates.add(direct)

            decodeBase64(raw)?.let { decoded ->
                val decodedDoc = Jsoup.parse(decoded)
                decodedDoc.select("iframe[src], embed[src], video[src], source[src], a[href]").forEach { element ->
                    val url = normalizeUrl(
                        element.attr("src").ifBlank { element.attr("href") },
                        mainUrl
                    )
                    if (isPlayableOrEmbed(url)) candidates.add(url)
                }
                extractUrlsFromText(decoded, mainUrl).forEach(candidates::add)
            }
        }

        extractUnpackedUrls(document, mainUrl).forEach(candidates::add)
        extractUrlsFromText(document.html(), mainUrl).forEach(candidates::add)

        return candidates.filter { it.isNotBlank() }.distinct()
    }

    suspend fun extractSubtitles(document: Document, mainUrl: String): List<SubtitleFile> {
        return document.select("track[src], a[href$=.srt], a[href$=.vtt]").mapNotNull { element ->
            val url = normalizeUrl(element.attr("src").ifBlank { element.attr("href") }, mainUrl)
            if (url.isBlank()) return@mapNotNull null
            val label = element.attr("srclang")
                .ifBlank { element.attr("label") }
                .ifBlank { element.text() }
                .ifBlank { "Indonesia" }
            newSubtitleFile(label, url)
        }.distinctBy { it.url }
    }

    suspend fun resolveApiPlayback(
        providerName: String,
        mainUrl: String,
        payload: ApiPayload,
        ajaxHeaders: Map<String, String>,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val candidates = linkedSetOf<String>()
        val baseApi = payload.cApiHost.trimEnd('/')
        val candidateApiHosts = listOf(
            baseApi,
            "$mainUrl/c_api",
            "https://api.nonton.bid/c_api",
            "https://drakorindo18.kita.baby/c_api",
            "https://drakor43.nicewap.sbs/c_api"
        ).distinct()

        for (cApiHost in candidateApiHosts) {
            var episodeIdSeed = payload.episodeId
            var serverXidSeed = payload.serverXid
            var tagSeed = payload.tag

            if (episodeIdSeed.isBlank() && payload.movieId.isNotBlank()) {
                val episodeJson = apiGetJson(
                    url = "$cApiHost/episode_mob.php" +
                        "?is_mob=${payload.isMob}" +
                        "&is_uc=${payload.isUc}" +
                        "&movie_id=${encode(payload.movieId)}" +
                        "&tag=${encode(payload.tag)}" +
                        "&c=${encode(payload.c)}" +
                        "&t=${encode(payload.t)}" +
                        "&ver=${encode(payload.ver)}",
                    headers = ajaxHeaders,
                    referer = payload.detailUrl
                )
                episodeIdSeed = episodeJson?.optString("first_ep_id").orEmpty()
                serverXidSeed = episodeJson?.optString("server_xid").orEmpty()
                    .ifBlank { serverXidSeed }
                tagSeed = episodeJson?.optString("tag").orEmpty()
                    .ifBlank { tagSeed }
            }

            if (episodeIdSeed.isNotBlank()) {
                // Try all video API endpoints and validate responses
                listOf(
                    "video_sb.php" to "sb_url",
                    "video_hydrax.php" to "hydrax_url",
                    "video_p2p.php" to "p2p_url"
                ).forEach { (endpoint, field) ->
                    val json = apiGetJson(
                        url = "$cApiHost/$endpoint" +
                            "?is_mob=${payload.isMob}" +
                            "&is_uc=${payload.isUc}" +
                            "&id=${encode(episodeIdSeed)}" +
                            "&qua=${encode(tagSeed)}" +
                            "&server_id=${encode(serverXidSeed)}" +
                            "&cat=${encode(tagSeed)}" +
                            "&tag=${encode(payload.ver)}" +
                            "&c=${encode(payload.c)}" +
                            "&t=${encode(payload.t)}",
                        headers = ajaxHeaders,
                        referer = payload.detailUrl
                    )
                    val rawUrl = json?.optString(field).orEmpty().replace("\\/", "/")
                    if (rawUrl.isNotBlank()) {
                        val normalized = normalizeUrl(rawUrl, mainUrl)
                        // Only add if URL is valid (not a broken placeholder)
                        if (isValidVideoApiUrl(normalized)) {
                            candidates.add(normalized)
                        }
                    }
                }

                // CRITICAL FIX: The ep_id from episode_mob.php IS the StreamSB file code.
                // The API's video_sb.php always returns dqt.my.id with empty hash because
                // dqt.my.id has expired/deleted the files. However, streamsb.net (the original
                // StreamSB host) still serves the same file codes. Construct URLs directly.
                val streamSbDomains = listOf(
                    "https://streamsb.net",
                    "https://sbembed.com",
                    "https://dqt.my.id"
                )
                streamSbDomains.forEach { sbDomain ->
                    candidates.add("$sbDomain/e/$episodeIdSeed.html")
                }

                // Also try the direct video.php endpoint (sometimes works)
                val videoJson = apiGetJson(
                    url = "$cApiHost/video.php" +
                        "?is_mob=${payload.isMob}" +
                        "&is_uc=${payload.isUc}" +
                        "&id=${encode(episodeIdSeed)}" +
                        "&qua=${encode(tagSeed)}" +
                        "&server_id=${encode(serverXidSeed)}" +
                        "&cat=${encode(tagSeed)}" +
                        "&tag=${encode(payload.ver)}" +
                        "&c=${encode(payload.c)}" +
                        "&t=${encode(payload.t)}",
                    headers = ajaxHeaders,
                    referer = payload.detailUrl
                )
                videoJson?.let { json ->
                    listOf("file", "source", "video", "hls", "url", "download").forEach { field ->
                        val value = json.optString(field)
                        if (value.isNotBlank()) {
                            extractUrlsFromText(value, mainUrl).forEach { url ->
                                if (isValidVideoApiUrl(url)) candidates.add(url)
                            }
                        }
                    }
                    json.optJSONObject("dl")?.let { dl ->
                        dl.keys().forEach { key ->
                            val value = normalizeUrl(dl.optString(key), mainUrl)
                            if (value.isNotBlank() && isValidVideoApiUrl(value)) candidates.add(value)
                        }
                    }
                }
            }

            // Break early only if we have non-StreamSB-fallback candidates
            // Always continue for StreamSB fallback since we add those unconditionally
            if (candidates.any { !it.contains("streamsb.net") && !it.contains("sbembed.com") }) break
        }

        if (candidates.isEmpty()) return false

        return resolveCandidates(
            providerName = providerName,
            mainUrl = mainUrl,
            pageUrl = payload.detailUrl,
            candidates = candidates.toList(),
            subtitleCallback = subtitleCallback,
            callback = callback
        )
    }

    suspend fun resolveCandidates(
        providerName: String,
        mainUrl: String,
        pageUrl: String,
        candidates: List<String>,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val seen = linkedSetOf<String>()
        var handled = false

        suspend fun resolve(link: String, referer: String, depth: Int) {
            val fixed = normalizeUrl(link, mainUrl)
            if (fixed.isBlank() || !seen.add(fixed) || depth > 2) return

            when {
                fixed.contains(".m3u8", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            providerName,
                            providerName,
                            fixed,
                            ExtractorLinkType.M3U8
                        ) {
                            this.referer = referer
                            this.quality = parseQuality(fixed)
                        }
                    )
                    handled = true
                }
                fixed.contains(".mp4", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            providerName,
                            providerName,
                            fixed,
                            ExtractorLinkType.VIDEO
                        ) {
                            this.referer = referer
                            this.quality = parseQuality(fixed)
                        }
                    )
                    handled = true
                }
                else -> {
                    runCatching {
                        var extractorCount = 0
                        loadExtractor(fixed, referer, subtitleCallback) { l ->
                            callback.invoke(l)
                            extractorCount++
                        }
                        if (extractorCount > 0) handled = true
                    }

                    if (shouldScanNestedPage(fixed)) {
                        runCatching {
                            val response = app.get(
                                url = fixed,
                                headers = mapOf(
                                    "User-Agent" to "Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Mobile Safari/537.36",
                                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                                ),
                                referer = referer
                            )
                            val body = response.text
                            if (response.isSuccessful &&
                                !body.contains("Video not found", ignoreCase = true) &&
                                !body.contains("expired or has been deleted", ignoreCase = true) &&
                                !body.contains("File is no longer available", ignoreCase = true)
                            ) {
                                val doc = response.document
                                extractSubtitles(doc, mainUrl).forEach(subtitleCallback)
                                extractEmbedCandidates(doc, mainUrl).forEach { nested ->
                                    resolve(nested, fixed, depth + 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        candidates.forEach { resolve(it, pageUrl, 0) }
        return handled
    }

    private fun decodeBase64(value: String): String? {
        val normalized = value.trim()
        if (normalized.isBlank()) return null
        val padded = normalized.padEnd(normalized.length + ((4 - normalized.length % 4) % 4), '=')
        return runCatching {
            String(Base64.getDecoder().decode(padded))
        }.getOrElse {
            runCatching { String(Base64.getUrlDecoder().decode(padded)) }.getOrNull()
        }
    }

    private suspend fun apiGetJson(
        url: String,
        headers: Map<String, String>,
        referer: String
    ): JSONObject? {
        return runCatching {
            JSONObject(
                app.get(
                    url = url,
                    headers = headers,
                    referer = referer
                ).text
            )
        }.getOrNull()
    }

    private fun extractUnpackedUrls(document: Document, mainUrl: String): List<String> {
        val results = linkedSetOf<String>()
        document.select("script").forEach { script ->
            val scriptText = script.data().ifBlank { script.html() }
            if (scriptText.contains("function(p,a,c,k,e,d)")) {
                runCatching { getAndUnpack(scriptText) }.getOrNull()?.let { unpacked ->
                    extractUrlsFromText(unpacked, mainUrl).forEach(results::add)
                    buildDqtHlsCandidates(unpacked).forEach(results::add)
                }
            }
        }
        return results.toList()
    }

    private fun buildDqtHlsCandidates(unpacked: String): List<String> {
        val direct = Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
            .find(unpacked)
            ?.value
            ?.replace("\\/", "/")
        if (!direct.isNullOrBlank()) return listOf(direct)

        val tokens = Regex("""['"]([A-Za-z0-9_-]{8,})['"]""")
            .findAll(unpacked)
            .map { it.groupValues[1] }
            .toList()
        val streamId = tokens.firstOrNull { it.length >= 20 }
        val folder = tokens.firstOrNull { it.length >= 12 && it != streamId }
        val expires = Regex("""\b(17\d{8,})\b""")
            .findAll(unpacked)
            .map { it.groupValues[1] }
            .firstOrNull()
        val fileId = Regex("""file_code['"]?\s*[:=]\s*['"]?([A-Za-z0-9_-]+)""")
            .find(unpacked)
            ?.groupValues
            ?.getOrNull(1)
            ?: Regex("""\b([A-Za-z0-9]{10,})_n\b""")
                .find(unpacked)
                ?.groupValues
                ?.getOrNull(1)

        if (streamId.isNullOrBlank() || folder.isNullOrBlank() || expires.isNullOrBlank() || fileId.isNullOrBlank()) {
            return emptyList()
        }

        return listOf("https://dqt.my.id/stream/$streamId/$folder/$expires/$fileId/master.m3u8")
    }

    private fun extractUrlsFromText(text: String, mainUrl: String): List<String> {
        val results = linkedSetOf<String>()
        val normalized = text
            .replace("\\/", "/")
            .replace("&amp;", "&")
        val urlRegex = Regex("""https?://[^'"\\\s<>]+|//[^'"\s<>]+""")
        urlRegex.findAll(normalized).forEach { match ->
            val fixed = normalizeUrl(match.value, mainUrl)
                .trimEnd(',', '.', ';', ')', ']', '}')
            if (isPlayableOrEmbed(fixed)) results.add(fixed)
        }
        return results.toList()
    }

    private fun isPlayableOrEmbed(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        return lower.contains("/embed") ||
            lower.contains("/e/") ||
            lower.contains("/v/") ||
            lower.contains("iframe") ||
            lower.contains("player") ||
            lower.contains("stream") ||
            lower.contains("watch") ||
            lower.contains("abysscdn") ||
            lower.contains("dqt.my.id") ||
            lower.contains("uyeshare") ||
            lower.contains("drakorkita.stream") ||
            lower.contains("handal.bid") ||
            lower.contains("uyeshare.cc") ||
            lower.contains("/download/") ||
            lower.contains(".m3u8") ||
            lower.contains(".mp4")
    }

    private fun shouldScanNestedPage(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("dqt.my.id") ||
            lower.contains("abysscdn") ||
            lower.contains("drakorkita.stream") ||
            lower.contains("uyeshare") ||
            lower.contains("handal.bid")
    }

    private fun parseQuality(url: String): Int {
        return Regex("""(2160|1440|1080|720|480|360|240)p""", RegexOption.IGNORE_CASE)
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: Regex("""(?:^|[^\d])(2160|1440|1080|720|480|360|240)(?:[^\d]|$)""")
                .find(url)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
