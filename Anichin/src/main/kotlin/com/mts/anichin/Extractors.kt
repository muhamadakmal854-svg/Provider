package com.mts.anichin

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI
import android.util.Base64
import kotlin.text.Regex
import org.jsoup.Jsoup
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeJSON
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable




class Geodailymotion : Dailymotion() {
    override val name = "GeoDailymotion"
    override val mainUrl = "https://geo.dailymotion.com"
}

open class Dailymotion : ExtractorApi() {
    override val mainUrl = "https://www.dailymotion.com"
    override val name = "Dailymotion"
    override val requiresReferer = false
    private val baseUrl = "https://www.dailymotion.com"

    private val videoIdRegex = "^[kx][a-zA-Z0-9]+$".toRegex()

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = getEmbedUrl(url) ?: return
        val id = getVideoId(embedUrl) ?: return
        val metaDataUrl = "$baseUrl/player/metadata/video/$id"
        val response = app.get(metaDataUrl, referer = embedUrl).text
        val qualityUrlRegex = Regex(""""url"\s*:\s*"([^"]+)"""")
        val subtitlesRegex = Regex(""""subtitles"\s*:\s*\{[^}]*"data"\s*:\s*(\[[^\]]*\])""")

        val urls = qualityUrlRegex.findAll(response)
            .map { it.groupValues[1] }
            .toList().filter { it.contains(".m3u8") }

        urls.forEach { videoUrl ->
            getStream(videoUrl, this.name, callback)
        }

        val subtitlesMatches = subtitlesRegex.findAll(response).map { it.groupValues[1] }.toList()
        subtitlesMatches.forEach { subtitleJson ->
            val subRegex = Regex("""\{\s*"label"\s*:\s*"([^"]+)",\s*"urls"\s*:\s*\["([^"]+)"""")
            subRegex.findAll(subtitleJson).forEach { match ->
                val label = match.groupValues[1]
                val subUrl = match.groupValues[2]
                
                subtitleCallback(newSubtitleFile(label, subUrl))
            }
        }
    }

    private fun getEmbedUrl(url: String): String? {
        if (url.contains("/embed/") || url.contains("/video/")) return url
        if (url.contains("geo.dailymotion.com")) {
            val videoId = url.substringAfter("video=")
            return "$baseUrl/embed/video/$videoId"
        }
        return null
    }

    private fun getVideoId(url: String): String? {
        val path = URI(url).path
        val id = path.substringAfter("/video/")
        return if (id.matches(videoIdRegex)) id else null
    }

    private suspend fun getStream(
        streamLink: String,
        name: String,
        callback: (ExtractorLink) -> Unit
    ) {
        return generateM3u8(name, streamLink, "").forEach(callback)
    }
}






class OkRuSSL : Odnoklassniki() {
    override var name    = "OkRuSSL"
    override var mainUrl = "https://ok.ru"
}

class OkRuHTTP : Odnoklassniki() {
    override var name    = "OkRuHTTP"
    override var mainUrl = "http://ok.ru"
}

open class Odnoklassniki : ExtractorApi() {
    override val name            = "Odnoklassniki"
    override val mainUrl         = "https://odnoklassniki.ru"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to mainUrl,
            "User-Agent" to USER_AGENT,
        )
        val embedUrl = url.replace("/video/","/videoembed/")
        val videoReq  = app.get(embedUrl, headers=headers).text.replace("\\&quot;", "\"").replace("\\\\", "\\")
            .replace(Regex("\\\\u([0-9A-Fa-f]{4})")) { matchResult ->
                Integer.parseInt(matchResult.groupValues[1], 16).toChar().toString()
            }

        val videosStr = Regex(""""videos":(\[[^]]*])""").find(videoReq)?.groupValues?.get(1) ?: throw ErrorLoadingException("Video not found")
        val videos    = AppUtils.tryParseJson<List<OkRuVideo>>(videosStr) ?: throw ErrorLoadingException("Video not found")

        for (video in videos) {

            val videoUrl  = if (video.url.startsWith("//")) "https:${video.url}" else video.url

            val quality   = video.name.uppercase()
                .replace("MOBILE", "144p")
                .replace("LOWEST", "240p")
                .replace("LOW",    "360p")
                .replace("SD",     "480p")
                .replace("HD",     "720p")
                .replace("FULL",   "1080p")
                .replace("QUAD",   "1440p")
                .replace("ULTRA",  "4k")

            callback.invoke(
                newExtractorLink(
                    source  = this.name,
                    name    = this.name,
                    url     = videoUrl,
                    type    = INFER_TYPE
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = getQualityFromName(quality)
                    this.headers = headers
                }
            )
        }
    }

    data class OkRuVideo(
        @JsonProperty("name") val name: String,
        @JsonProperty("url")  val url: String,
    )
}




class Rumble : ExtractorApi() {
    override var name = "Rumble"
    override var mainUrl = "https://rumble.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer ?: "$mainUrl/")
        val scriptData = response.document.selectFirst("script:containsData(mp4)")?.data()
            ?.substringAfter("{\"mp4")?.substringBefore("\"evt\":{")
        if (scriptData == null) return

        val regex = """"url":"(.*?)"|h":(.*?)\}""".toRegex()
        val matches = regex.findAll(scriptData)

        val processedUrls = mutableSetOf<String>()

        for (match in matches) {
            val rawUrl = match.groupValues[1]
            if (rawUrl.isBlank()) continue

            val cleanedUrl = rawUrl.replace("\\/", "/")
            if (!cleanedUrl.contains("rumble.com")) continue
            if (!cleanedUrl.endsWith(".m3u8")) continue
            if (!processedUrls.add(cleanedUrl)) continue

            val m3u8Response = app.get(cleanedUrl)
            val variantCount = "#EXT-X-STREAM-INF".toRegex().findAll(m3u8Response.text).count()

            if (variantCount > 1) {
                callback.invoke(
                    newExtractorLink(
                        this@Rumble.name,   // source
                        "Rumble",       // name
                        cleanedUrl,         // url
                        ExtractorLinkType.M3U8 // type
                        // initializer tidak perlu diisi
                    )
                )
                break
            }
        }
    }
}




open class StreamRuby : ExtractorApi() {
    override val name = "StreamRuby"
    override val mainUrl = "https://rubyvidhub.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
//        Log.d("streamrubby", "url = $url")
        val id = "embed-([a-zA-Z0-9]+)\\.html".toRegex().find(url)?.groupValues?.get(1) ?: return
//        Log.d("streamrubby", "id = $id")
        val response = app.post(
            "$mainUrl/dl", data = mapOf(
                "op" to "embed",
                "file_code" to id,
                "auto" to "1",
                "referer" to "",
            ), referer = referer
        )
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        }
        val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script ?: return)?.groupValues?.getOrNull(1)
//        Log.d("streamrubby", "m3u8 = $m3u8")
        callback.invoke(newExtractorLink(
            source = this.name,
            name = this.name,
            url  = m3u8.toString(),
            type = ExtractorLinkType.M3U8,
            {
                quality = Qualities.Unknown.value
                this.referer = mainUrl
            }
        ))
    }
}

class svanila : StreamRuby() {
    override var name = "svanila"
    override var mainUrl = "https://streamruby.net"
}

class svilla : StreamRuby() {
    override var name = "svilla"
    override var mainUrl = "https://streamruby.com"
}





class Vidguardto1 : Vidguardto() {
    override val mainUrl = "https://bembed.net"
}

class Vidguardto2 : Vidguardto() {
    override val mainUrl = "https://listeamed.net"
}

class Vidguardto3 : Vidguardto() {
    override val mainUrl = "https://vgfplay.com"
}

open class Vidguardto : ExtractorApi() {
    override val name = "Vidguard"
    override val mainUrl = "https://vidguard.to"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(getEmbedUrl(url))
        val resc = res.document.select("script:containsData(eval)").firstOrNull()?.data()
        resc?.let {
            val jsonStr2 = AppUtils.parseJson<SvgObject>(runJS2(it))
            val watchlink = sigDecode(jsonStr2.stream)

            callback.invoke(
                newExtractorLink(
                    this.name,
                    name,
                    watchlink,
                ) {
                    this.referer = mainUrl
                }
            )
        }
    }

    private fun sigDecode(url: String): String {
        val sig = url.split("sig=")[1].split("&")[0]
        val t = sig.chunked(2)
            .joinToString("") { (Integer.parseInt(it, 16) xor 2).toChar().toString() }
            .let {
                val padding = when (it.length % 4) {
                    2 -> "=="
                    3 -> "="
                    else -> ""
                }
                String(Base64.decode(it + padding, Base64.DEFAULT))
            }
            .dropLast(5)
            .reversed()
            .toCharArray()
            .apply {
                for (i in indices step 2) {
                    if (i + 1 < size) {
                        this[i] = this[i + 1].also { this[i + 1] = this[i] }
                    }
                }
            }
            .concatToString()
            .dropLast(5)
        return url.replace(sig, t)
    }

    private fun runJS2(hideMyHtmlContent: String): String {
        var result = ""
        val r = Runnable {
            val rhino = Context.enter()
            val scope: Scriptable = rhino.initSafeStandardObjects()
            scope.put("window", scope, scope)
            try {
                rhino.evaluateString(
                    scope,
                    hideMyHtmlContent,
                    "JavaScript",
                    1,
                    null
                )
                val svgObject = scope.get("svg", scope)
                result = if (svgObject is NativeObject) {
                    NativeJSON.stringify(
                        Context.getCurrentContext(),
                        scope,
                        svgObject,
                        null,
                        null
                    ).toString()
                } else {
                    Context.toString(svgObject)
                }
            } catch (e: Exception) {
                Log.e("runJS", "Error executing JavaScript: ${e.message}")
            } finally {
                Context.exit()
            }
        }
        val t = Thread(null, r, "thread_rhino", 8 * 1024 * 1024)
        t.start()
        try {
            t.join()
        } catch (e: InterruptedException) {
            Log.e("runJS", "Thread interrupted: ${e.message}")
        }
        return result
    }

    private fun getEmbedUrl(url: String): String {
        return url.takeIf { it.contains("/d/") || it.contains("/v/") }
            ?.replace("/d/", "/e/")?.replace("/v/", "/e/") ?: url
    }

    data class SvgObject(
        val stream: String,
        val hash: String
    )
}
