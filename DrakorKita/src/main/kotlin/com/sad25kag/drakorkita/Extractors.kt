package com.sad25kag.drakorkita

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink

/**
 * Base extractor for P2P/WebTorrent-based streaming sites.
 * These sites use HLS streams served from a P2P CDN.
 */
open class P2pStreamExtractor(
    override val name: String,
    override val mainUrl: String
) : ExtractorApi() {
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer ?: "$mainUrl/")
        val text = response.text
        val unpacked = runCatching { getAndUnpack(text) }.getOrDefault("")
        val m3u8Regex = Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
        val matches = m3u8Regex.findAll(unpacked.ifBlank { text }).toList()

        matches.forEach { match ->
            generateM3u8(name, match.value, mainUrl).forEach(callback)
        }
    }
}

/**
 * Extractor for drakorkita.stream P2P player.
 * URL format: https://drakorkita.stream/#HASH
 * The HASH is used to call /api/v1/folder?id=HASH which returns torrent/stream info.
 */
class DrakorKitaStream : ExtractorApi() {
    override val name = "DrakorKitaP2P"
    override val mainUrl = "https://drakor.kita.mobi"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Extract hash from URL fragment: https://drakorkita.stream/#HASH
        val hash = url.substringAfter("#", "").substringBefore("&").trim()
        if (hash.length < 4) return  // empty or invalid hash

        runCatching {
            val apiUrl = "$mainUrl/api/v1/folder?id=$hash"
            val resp = app.get(
                apiUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Mobile Safari/537.36",
                    "Accept" to "application/json"
                ),
                referer = referer ?: "$mainUrl/"
            )
            if (!resp.isSuccessful) return@runCatching

            // Parse the folder/torrent info to find stream URLs
            val text = resp.text
            val m3u8Regex = Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
            m3u8Regex.findAll(text).forEach { match ->
                generateM3u8(name, match.value, mainUrl).forEach(callback)
            }

            // Also look for mp4 direct links
            val mp4Regex = Regex("""https?://[^'"\s<>]+\.mp4[^'"\s<>]*""")
            mp4Regex.findAll(text).forEach { match ->
                callback.invoke(
                    newExtractorLink(name, name, match.value, ExtractorLinkType.VIDEO) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }
    }
}

/**
 * Extractor for AbyssCDN/Hydrax player.
 * URL format: https://abysscdn.com/?v=HASH
 */
class AbyssCdn : P2pStreamExtractor("AbyssCDN", "https://abysscdn.com")

class StbP2P : P2pStreamExtractor("STBP2P", "https://stb.strp2p.com")
class Playerupnone : P2pStreamExtractor("UPNP2P", "https://player.upn.one")
class FastdlP2P : P2pStreamExtractor("FastDLP2P", "https://fastdl.p2pstream.online")
class P2PStreamOnline : P2pStreamExtractor("P2PStream", "https://p2pstream.online")
class Strp2pCom : P2pStreamExtractor("STRP2P", "https://strp2p.com")
class UpnOneCom : P2pStreamExtractor("UPNOne", "https://upn.one")
