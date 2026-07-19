package com.sad25kag.drakorkita

import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.Base64
import kotlinx.coroutines.runBlocking

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
                element.attr("data-embed"),
                element.attr("data-link"),
                element.attr("data-stream")
            ).forEach { raw ->
                val normalized = normalizeUrl(raw, mainUrl)
                if (isEmbedCandidate(normalized)) {
                    candidates.add(normalized)
                }
            }
        }

        val html = document.html()
        val regexes = listOf(
            Regex("""https?:\\?/\\?/[^"'\s<>]+\.(?:m3u8|mp4|mkv)[^"'\s<>]*""", RegexOption.IGNORE_CASE),
            Regex("""https?:\\?/\\?/[^"'\s<>]*(?:embed|player|video|stream|drive|file)[^"'\s<>]*""", RegexOption.IGNORE_CASE),
            Regex("""//[^"'\s<>]+\.(?:m3u8|mp4|mkv)[^"'\s<>]*""", RegexOption.IGNORE_CASE)
        )

        regexes.forEach { regex ->
            regex.findAll(html).forEach { match ->
                val normalized = normalizeUrl(match.value, mainUrl)
                if (isEmbedCandidate(normalized)) {
                    candidates.add(normalized)
                }
            }
        }

        return candidates.toList()
    }

    fun isEmbedCandidate(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        val ignoredExtensions = listOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".css", ".js", ".svg", ".ico", ".woff", ".woff2", ".ttf")
        if (ignoredExtensions.any { lower.endsWith(it) || lower.contains("$it?") }) return false

        val knownKeywords = listOf(
            ".m3u8", ".mp4", "embed", "player", "stream", "video", "drive", "file",
            "dood", "streamwish", "filemoon", "vidhide", "mixdrop", "streamtape",
            "lulustream", "krakenfiles", "pixeldrain", "gofile", "mediafire", "hxfile"
        )
        return knownKeywords.any { lower.contains(it) }
    }

    suspend fun resolvePayloadLinks(
        payload: ApiPayload,
        mainUrl: String,
        headers: Map<String, String>,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundAny = false

        fun emitLink(
            source: String,
            name: String,
            url: String,
            referer: String = mainUrl,
            quality: Int = Qualities.Unknown.value,
            type: ExtractorLinkType = ExtractorLinkType.VIDEO
        ) {
            val normalized = normalizeUrl(url, mainUrl)
            if (normalized.isNotBlank()) {
                foundAny = true
                val linkType = if (normalized.contains(".m3u8", ignoreCase = true)) ExtractorLinkType.M3U8 else type
                callback(
                    newExtractorLink(
                        source = source,
                        name = name,
                        url = normalized,
                        type = linkType
                    ) {
                        this.referer = referer
                        this.quality = quality
                    }
                )
            }
        }

        val pageDoc = runCatching {
            val res = app.get(payload.detailUrl, headers = headers)
            Jsoup.parse(res.text, payload.detailUrl)
        }.getOrNull()

        if (pageDoc != null) {
            val candidates = extractEmbedCandidates(pageDoc, mainUrl)
            candidates.forEach { candidate ->
                resolveDirectOrExtractor(
                    candidateUrl = candidate,
                    referer = payload.detailUrl,
                    headers = headers,
                    subtitleCallback = subtitleCallback,
                    callback = callback,
                    onDirectLink = { label, streamUrl ->
                        emitLink("DrakorKita", "DrakorKita - $label", streamUrl, referer = payload.detailUrl)
                    }
                )
            }
        }

        val formResult = fetchFromAjaxForm(payload, headers)
        if (formResult.isNotBlank()) {
            val clean = normalizeUrl(formResult, mainUrl)
            if (clean.isNotBlank()) {
                resolveDirectOrExtractor(
                    candidateUrl = clean,
                    referer = payload.detailUrl,
                    headers = headers,
                    subtitleCallback = subtitleCallback,
                    callback = callback,
                    onDirectLink = { label, streamUrl ->
                        emitLink("DrakorKita", "DrakorKita - $label", streamUrl, referer = payload.detailUrl)
                    }
                )
            }
        }

        val jsonResult = fetchFromJsonApi(payload, headers)
        jsonResult.forEach { candidate ->
            val clean = normalizeUrl(candidate, mainUrl)
            if (clean.isNotBlank()) {
                resolveDirectOrExtractor(
                    candidateUrl = clean,
                    referer = payload.detailUrl,
                    headers = headers,
                    subtitleCallback = subtitleCallback,
                    callback = callback,
                    onDirectLink = { label, streamUrl ->
                        emitLink("DrakorKita", "DrakorKita - $label", streamUrl, referer = payload.detailUrl)
                    }
                )
            }
        }

        return foundAny
    }

    private suspend fun resolveDirectOrExtractor(
        candidateUrl: String,
        referer: String,
        headers: Map<String, String>,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        onDirectLink: (label: String, streamUrl: String) -> Unit
    ) {
        val lower = candidateUrl.lowercase()
        when {
            lower.contains(".m3u8") -> onDirectLink("HLS", candidateUrl)
            lower.contains(".mp4") -> onDirectLink("MP4", candidateUrl)
            else -> {
                val loaded = loadExtractor(candidateUrl, referer, subtitleCallback, callback)
                if (!loaded) {
                    runCatching {
                        val doc = app.get(candidateUrl, headers = headers, referer = referer).document
                        extractEmbedCandidates(doc, referer).forEach { nested ->
                            val nLower = nested.lowercase()
                            if (nLower.contains(".m3u8") || nLower.contains(".mp4")) {
                                onDirectLink("Direct", nested)
                            } else {
                                loadExtractor(nested, candidateUrl, subtitleCallback, callback)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun fetchFromAjaxForm(payload: ApiPayload, headers: Map<String, String>): String {
        return runCatching {
            val bodyData = mapOf(
                "action" to "get_player",
                "movie_id" to payload.movieId,
                "episode_id" to payload.episodeId,
                "server_xid" to payload.serverXid,
                "tag" to payload.tag,
                "c" to payload.c,
                "t" to payload.t,
                "ver" to payload.ver
            )
            val res = app.post(
                url = "${payload.cApiHost.trimEnd('/')}/wp-admin/admin-ajax.php",
                data = bodyData,
                headers = headers + mapOf(
                    "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to payload.detailUrl
                )
            ).text

            extractUrlFromText(res)
        }.getOrDefault("")
    }

    private suspend fun fetchFromJsonApi(payload: ApiPayload, headers: Map<String, String>): List<String> {
        return runCatching {
            val endpoint = "${payload.cApiHost.trimEnd('/')}/api/v1/stream"
            val query = mapOf(
                "detail_url" to payload.detailUrl,
                "title" to payload.title,
                "movie_id" to payload.movieId,
                "episode_id" to payload.episodeId,
                "server_xid" to payload.serverXid,
                "tag" to payload.tag,
                "c" to payload.c,
                "t" to payload.t,
                "ver" to payload.ver,
                "is_mob" to payload.isMob,
                "is_uc" to payload.isUc,
                "media_type" to payload.mediaType
            ).entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }

            val fullUrl = "$endpoint?$query"
            val text = app.get(
                url = fullUrl,
                headers = headers + mapOf(
                    "Accept" to "application/json, text/plain, */*",
                    "Referer" to payload.detailUrl
                )
            ).text

            val results = mutableListOf<String>()
            val root = JSONObject(text)

            fun collect(obj: Any?) {
                when (obj) {
                    is JSONObject -> {
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val v = obj.opt(k)
                            if (k.contains("url", ignoreCase = true) || k.contains("file", ignoreCase = true) || k.contains("link", ignoreCase = true) || k.contains("src", ignoreCase = true) || k.contains("embed", ignoreCase = true)) {
                                if (v is String && isEmbedCandidate(v)) {
                                    results.add(v)
                                }
                            }
                            collect(v)
                        }
                    }
                    is org.json.JSONArray -> {
                        for (i in 0 until obj.length()) {
                            collect(obj.opt(i))
                        }
                    }
                }
            }

            collect(root)
            results.distinct()
        }.getOrDefault(emptyList())
    }

    private fun extractUrlFromText(text: String): String {
        if (text.isBlank()) return ""

        val iframeSrc = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)
        if (!iframeSrc.isNullOrBlank()) return iframeSrc

        val unpacked = runCatching { JsUnpacker.unpackAndCombine(text) }.getOrNull().orEmpty()
        val targetText = if (unpacked.isNotBlank()) unpacked else text

        val direct = Regex("""https?:\\?/\\?/[^"'\s<>]+\.(?:m3u8|mp4)[^"'\s<>]*""", RegexOption.IGNORE_CASE)
            .find(targetText)?.groupValues?.getOrNull(0)
        if (!direct.isNullOrBlank()) return direct

        val embed = Regex("""https?:\\?/\\?/[^"'\s<>]*(?:embed|player|video|stream|drive|file)[^"'\s<>]*""", RegexOption.IGNORE_CASE)
            .find(targetText)?.groupValues?.getOrNull(0)
        if (!embed.isNullOrBlank()) return embed

        return ""
    }
}
