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

    private fun isValidVideoApiUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()

        if (lower.contains("drakorkita.stream")) {
            val fragment = url.substringAfter("#", "").trim()
            return fragment.length > 3
        }

        if (lower.contains("abysscdn.com")) {
            val vParam = url.substringAfter("?v=", "").substringBefore("&").trim()
            return vParam.isNotBlank() && !vParam.startsWith("?")
        }

        if (lower.contains("dqt.my.id/e/")) {
            val hash = url.substringAfter("/e/", "").substringBefore(".").trim()
            return hash.length > 3
        }

        if (lower.contains("handal.bid/e/")) {
            val hash = url.substringAfter("/e/", "").substringBefore(".").trim()
            return hash.length > 3
        }

        return true
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
            "https://api.nonton.bid/c_api",
            "$mainUrl/c_api",
            "https://drakorindo18.kita.baby/c_api",
            "https://drakor43.nicewap.sbs/c_api"
        ).filter { it.isNotBlank() }.distinct()

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
                        "&cat=${encode(payload.tag)}" +
                        "&tag=${encode(payload.ver)}" +
                        "&c=${encode(payload.c)}" +
                        "&t=${encode(payload.t)}",
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
                // 1. Call server.php to get real-time server information
                val svrJson = apiGetJson(
                    url = "$cApiHost/server.php" +
                        "?is_mob=${payload.isMob}" +
                        "&is_uc=${payload.isUc}" +
                        "&episode_id=${encode(episodeIdSeed)}" +
                        "&cat=${encode(tagSeed)}" +
                        "&tag=${encode(payload.ver)}" +
                        "&server_xid=${encode(serverXidSeed)}" +
                        "&c=${encode(payload.c)}" +
                        "&t=${encode(payload.t)}",
                    headers = ajaxHeaders,
                    referer = payload.detailUrl
                )
                val dataObj = svrJson?.optJSONObject("data")
                val svrLists = svrJson?.optString("server_lists").orEmpty()
                val qua = dataObj?.optString("qua")?.ifBlank { "web" } ?: "web"
                val res = dataObj?.optString("res")?.ifBlank { "480" } ?: "480"

                // 2. Query video_hydrax.php
                val hasHydrax = dataObj?.optString("hydrax_status") == "1" ||
                    dataObj?.optString("ptype") == "hydrax" ||
                    svrLists.contains("loadVideoHYDRAX") ||
                    svrLists.contains("HYDRAX", ignoreCase = true)
                if (hasHydrax) {
                    val hydraxJson = apiGetJson(
                        url = "$cApiHost/video_hydrax.php" +
                            "?is_mob=${payload.isMob}" +
                            "&is_uc=${payload.isUc}" +
                            "&id=${encode(episodeIdSeed)}" +
                            "&qua=${encode(qua)}" +
                            "&res=${encode(res)}" +
                            "&server_id=${encode(serverXidSeed)}" +
                            "&cat=${encode(tagSeed)}" +
                            "&tag=${encode(payload.ver)}" +
                            "&c=${encode(payload.c)}" +
                            "&t=${encode(payload.t)}",
                        headers = ajaxHeaders,
                        referer = payload.detailUrl
                    )
                    val hydraxUrl = hydraxJson?.optString("hydrax_url").orEmpty().replace("\\/", "/")
                    if (isValidVideoApiUrl(hydraxUrl)) {
                        candidates.add(normalizeUrl(hydraxUrl, mainUrl))
                    }
                    val hydraxId = dataObj?.optString("hydrax_id").orEmpty()
                    if (hydraxId.isNotBlank()) {
                        candidates.add("https://abysscdn.com/?v=$hydraxId")
                    }
                }

                // 3. Query video_p2p.php
                val hasP2p = dataObj?.optString("p2p_status") == "1" ||
                    dataObj?.optString("ptype") == "p2p" ||
                    svrLists.contains("loadVideoP2P") ||
                    svrLists.contains("P2P", ignoreCase = true)
                if (hasP2p) {
                    val p2pJson = apiGetJson(
                        url = "$cApiHost/video_p2p.php" +
                            "?is_mob=${payload.isMob}" +
                            "&is_uc=${payload.isUc}" +
                            "&id=${encode(episodeIdSeed)}" +
                            "&qua=${encode(qua)}" +
                            "&res=${encode(res)}" +
                            "&server_id=${encode(serverXidSeed)}" +
                            "&cat=${encode(tagSeed)}" +
                            "&tag=${encode(payload.ver)}" +
                            "&c=${encode(payload.c)}" +
                            "&t=${encode(payload.t)}",
                        headers = ajaxHeaders,
                        referer = payload.detailUrl
                    )
                    val p2pUrl = p2pJson?.optString("p2p_url").orEmpty().replace("\\/", "/")
                    if (isValidVideoApiUrl(p2pUrl)) {
                        candidates.add(normalizeUrl(p2pUrl, mainUrl))
                    }
                    val p2pId = dataObj?.optString("p2p_id").orEmpty()
                    if (p2pId.isNotBlank()) {
                        candidates.add("https://drakorkita.stream/#$p2pId")
                    }
                }

                // 4. Query video_sb.php
                val hasSb = dataObj?.optString("sb_status") == "1" ||
                    dataObj?.optString("ptype") == "sb" ||
                    svrLists.contains("loadVideoSB") ||
                    svrLists.contains("SB", ignoreCase = true)
                if (hasSb) {
                    val sbJson = apiGetJson(
                        url = "$cApiHost/video_sb.php" +
                            "?is_mob=${payload.isMob}" +
                            "&is_uc=${payload.isUc}" +
                            "&id=${encode(episodeIdSeed)}" +
                            "&qua=${encode(qua)}" +
                            "&server_id=${encode(serverXidSeed)}" +
                            "&cat=${encode(tagSeed)}" +
                            "&tag=${encode(payload.ver)}" +
                            "&c=${encode(payload.c)}" +
                            "&t=${encode(payload.t)}",
                        headers = ajaxHeaders,
                        referer = payload.detailUrl
                    )
                    val sbUrl = sbJson?.optString("sb_url").orEmpty().replace("\\/", "/")
                    if (isValidVideoApiUrl(sbUrl)) {
                        candidates.add(normalizeUrl(sbUrl, mainUrl))
                    }
                }

                // 5. Query video.php (Local / PlayerJS / direct file / download slug)
                val videoJson = apiGetJson(
                    url = "$cApiHost/video.php" +
                        "?is_mob=${payload.isMob}" +
                        "&is_uc=${payload.isUc}" +
                        "&id=${encode(episodeIdSeed)}" +
                        "&qua=${encode(qua)}" +
                        "&server_id=${encode(serverXidSeed)}" +
                        "&cat=${encode(tagSeed)}" +
                        "&tag=${encode(payload.ver)}" +
                        "&c=${encode(payload.c)}" +
                        "&t=${encode(payload.t)}",
                    headers = ajaxHeaders,
                    referer = payload.detailUrl
                )
                videoJson?.let { json ->
                    val fileContent = json.optString("file")
                    if (fileContent.isNotBlank()) {
                        extractUrlsFromText(fileContent, mainUrl).forEach { url ->
                            if (isValidVideoApiUrl(url)) candidates.add(url)
                        }
                    }
                    val dlSlug = json.optString("download")
                    val dlId = dlSlug.substringAfterLast("/").trim()
                    if (dlId.isNotBlank()) {
                        // 6. Query dlfilemob.php for alternative download & stream URLs
                        val dlJson = apiGetJson(
                            url = "$cApiHost/dlfilemob.php?id=${encode(dlId)}&is_mob=${payload.isMob}&t=${encode(payload.t)}&c=${encode(payload.c)}",
                            headers = ajaxHeaders,
                            referer = payload.detailUrl
                        )
                        dlJson?.let { dlj ->
                            listOf("link", "linksb", "linksbp", "linkp2p", "linkfilemoon", "download").forEach { field ->
                                val v = dlj.optString(field)
                                if (v.isNotBlank()) {
                                    extractUrlsFromText(v, mainUrl).forEach { url ->
                                        if (isValidVideoApiUrl(url)) candidates.add(url)
                                    }
                                }
                            }
                        }
                    }
                }

                // 7. StreamSB Fallback URLs
                val streamSbDomains = listOf(
                    "https://streamsb.net",
                    "https://sbembed.com",
                    "https://dqt.my.id"
                )
                streamSbDomains.forEach { sbDomain ->
                    candidates.add("$sbDomain/e/$episodeIdSeed.html")
                }
            }

            if (candidates.isNotEmpty()) break
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
            val res = app.get(url = url, headers = headers, referer = referer)
            val txt = res.text.trim()
            if (txt.startsWith("{") && txt.endsWith("}")) {
                JSONObject(txt)
            } else null
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
}
