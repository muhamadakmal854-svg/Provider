package com.sad25kag.drakorkita

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getAndUnpack

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
        val unpacked = getAndUnpack(text)
        val m3u8Regex = Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
        val matches = m3u8Regex.findAll(unpacked.ifBlank { text }).toList()

        matches.forEach { match ->
            generateM3u8(name, match.value, mainUrl).forEach(callback)
        }
    }
}

class StbP2P : P2pStreamExtractor("STBP2P", "https://stb.strp2p.com")
class Playerupnone : P2pStreamExtractor("UPNP2P", "https://player.upn.one")
class FastdlP2P : P2pStreamExtractor("FastDLP2P", "https://fastdl.p2pstream.online")
class P2PStreamOnline : P2pStreamExtractor("P2PStream", "https://p2pstream.online")
class Strp2pCom : P2pStreamExtractor("STRP2P", "https://strp2p.com")
class UpnOneCom : P2pStreamExtractor("UPNOne", "https://upn.one")
class DrakorKitaStream : P2pStreamExtractor("DrakorKitaP2P", "https://drakorkita.stream")
