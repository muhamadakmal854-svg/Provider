package com.mts.nontondrama

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.app
import org.json.JSONObject
import org.jsoup.Jsoup
import android.util.Log
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import java.net.URI
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.loadExtractor



class Gn1r5nOrg : StreamWishExtractor() {
    override var name = "Gn1r5nOrg"
    override var mainUrl = "https://gn1r5n.org"
}



open class P2PExtractor : ExtractorApi() {
    override var name = "P2P"
    override var mainUrl = "https://cloud.hownetwork.xyz"
    override val requiresReferer = false
    data class HownetworkResponse(val file: String?, val link: String?, val label: String?)

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val id = url.substringAfter("id=").substringBefore("&")
        val apiUrl = "$mainUrl/api2.php?id=$id"
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
            "Referer" to url,
            "Origin" to mainUrl,
            "X-Requested-With" to "XMLHttpRequest"
        )
        val formBody = mapOf("r" to "https://playeriframe.sbs/", "d" to "cloud.hownetwork.xyz")
        val sources = mutableListOf<ExtractorLink>()
        try {
            val response = app.post(apiUrl, headers = headers, data = formBody).text
            val json = tryParseJson<HownetworkResponse>(response)
            val videoUrl = json?.file ?: json?.link
            if (!videoUrl.isNullOrBlank()) {
                sources.add(newExtractorLink(source = name, name = name, url = videoUrl, type = ExtractorLinkType.M3U8) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                })
            }
        } catch (e: Exception) { e.printStackTrace() }
        return sources
    }
}

open class EmturbovidExtractor : ExtractorApi() {
    override var name = "Emturbovid"
    override var mainUrl = "https://emturbovid.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val finalReferer = referer ?: "$mainUrl/"
        val sources = mutableListOf<ExtractorLink>()
        
        try {
            val response = app.get(url, referer = finalReferer)
            val playerScript = response.document.selectXpath("//script[contains(text(),'var urlPlay')]").html()
            
            if (playerScript.isNotBlank()) {
                val m3u8Url = playerScript.substringAfter("var urlPlay = '").substringBefore("'")
                val originUrl = try { URI(finalReferer).let { "${it.scheme}://${it.host}" } } catch (e: Exception) { mainUrl }
                
                val headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Referer" to finalReferer,
                    "Origin" to originUrl
                )
                
                sources.add(newExtractorLink(source = name, name = name, url = m3u8Url, type = ExtractorLinkType.M3U8) {
                    this.referer = finalReferer
                    this.quality = Qualities.Unknown.value
                    this.headers = headers
                })
            }
        } catch (e: Exception) { e.printStackTrace() }
        return sources
    }
}

class AbyssPlayer : ExtractorApi() {
    override val name = "AbyssPlayer"
    override val mainUrl = "https://abyssplayer.com"
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
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9"
        ))
        val doc = response.document
        val scripts = doc.select("script").joinToString(" ") { it.data() }

        val m3u8Regex = Regex("""["'](https?://[^"']+\\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE)
        val mp4Regex  = Regex("""["'](https?://[^"']+\\.mp4[^"']*)["']""", RegexOption.IGNORE_CASE)
        val fileRegex = Regex("""(?:file|src)["']?\\s*:\\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
        val hlsRegex  = Regex("""hls\\.loadSource\\(["'](https?://[^"']+)["']\\)""", RegexOption.IGNORE_CASE)

        val videoUrl = m3u8Regex.find(scripts)?.groupValues?.get(1)
            ?: hlsRegex.find(scripts)?.groupValues?.get(1)
            ?: fileRegex.find(scripts)?.groupValues?.get(1)
            ?: mp4Regex.find(scripts)?.groupValues?.get(1)
            ?: doc.selectFirst("source[src]")?.attr("src")
            ?: doc.selectFirst("video[src]")?.attr("src")

        if (videoUrl != null) {
            val isM3u8 = videoUrl.contains(".m3u8")
            if (isM3u8) {
                generateM3u8(this.name, videoUrl, ref).forEach(callback)
            } else {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name   = this.name,
                        url    = videoUrl,
                        type   = ExtractorLinkType.VIDEO
                    ) {
                        quality = Qualities.Unknown.value
                        this.referer = ref
                    }
                )
            }
        }
    }
}

