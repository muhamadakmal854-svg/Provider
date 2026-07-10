package com.mts.dutafilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.json.JSONObject
import org.json.JSONArray
import android.util.Base64
import java.nio.charset.StandardCharsets


abstract class BaseFixProvider : MainAPI() {

    fun fixUrl(url: String, referer: String): String {
        if (url.isBlank()) return ""
        if (url.startsWith("http")) return url
        if (url.startsWith("//")) return "https:" + url
        return try {
            val u = java.net.URL(referer)
            if (url.startsWith("/")) {
                "${u.protocol}://${u.host}$url"
            } else {
                val path = u.path.substringBeforeLast("/")
                "${u.protocol}://${u.host}$path/$url"
            }
        } catch (_: Exception) {
            if (url.startsWith("/")) "$mainUrl$url" else "$mainUrl/$url"
        }
    }

    fun Element.extractPosterUrl(): String {
        for (attr in listOf("data-src", "data-lazy-src", "data-lazy", "data-cfsrc",
                             "data-original", "data-image", "data-bg", "src")) {
            val v = this.attr(attr)
            if (v.isNotBlank() && !v.contains("data:image") &&
                (v.startsWith("http") || v.startsWith("//"))) {
                return if (v.startsWith("//")) "https:$v" else v
            }
        }
        val srcset = this.attr("srcset")
        if (srcset.isNotBlank()) {
            return srcset.trim().split(",").firstOrNull()
                ?.trim()?.split(" ")?.firstOrNull { it.startsWith("http") || it.startsWith("//") }?.let {
                    if (it.startsWith("//")) "https:$it" else it
                } ?: ""
        }
        val style = this.attr("style")
        if (style.contains("background") && style.contains("url(")) {
            val urlStart = style.indexOf("url(") + 4
            val raw = style.substring(urlStart)
            val dq = 34.toChar().toString()
            val sq = 39.toChar().toString()
            val cleaned = raw.replace(dq, "").replace(sq, "")
            val urlEnd = cleaned.indexOf(")")
            if (urlEnd > 0) {
                val candidate = cleaned.substring(0, urlEnd).trim()
                if (candidate.startsWith("http") || candidate.startsWith("//")) {
                    return if (candidate.startsWith("//")) "https:$candidate" else candidate
                }
            }
        }
        return ""
    }

    fun Element.toSearchResponse(mainUrl: String): SearchResponse? {
        val a = (if (this.tagName() == "a") this else this.selectFirst("a")) ?: return null
        val href = a.attr("href").let { h -> if (h.startsWith("http")) h else "$mainUrl$h" }
        if (href.isBlank() || href == mainUrl || href.contains("javascript")) return null
        
        val img = this.selectFirst("img") ?: this.selectFirst("[data-src], [data-lazy-src], [data-original]")
        val title = this.selectFirst(
            ".entry-title, h2.entry-title, h2, h3, .title, .film-name, .movie-title, .item-title, .tt, .ttl, .bigor .tt, .name"
        )?.text()?.trim()
            ?: a.attr("title").trim().ifEmpty { img?.attr("alt")?.trim() ?: "" }
                .ifEmpty { img?.attr("title")?.trim() ?: "" }
                .ifEmpty { a.text().trim() }
        
        if (title.isBlank()) return null
        
        var src = img?.extractPosterUrl() ?: ""
        if (src.isEmpty()) src = this.extractPosterUrl()
        if (src.isEmpty()) {
            this.select("[style*=background], [style*=url]").forEach { el ->
                val u = el.extractPosterUrl()
                if (u.isNotEmpty()) { src = u; return@forEach }
            }
        }
        
        val hrefLower = href.lowercase()
        val typeLabel = this.selectFirst(".type, .label, .badge, [class*=type], [class*=label], .quality")?.text()?.lowercase() ?: ""
        
        val isTv = hrefLower.contains("/tvshows/") || hrefLower.contains("/series/") ||
                   hrefLower.contains("/episode/") || hrefLower.contains("/tv/") ||
                   typeLabel.contains("series") || typeLabel.contains("drama") ||
                   typeLabel.contains("episode") || typeLabel.contains("ongoing")
                   
        return if (isTv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = src }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = src }
        }
    }

    suspend fun parseMultiRowHome(
        entries: List<Pair<String, String>>,
        itemSelector: String
    ): HomePageResponse {
        val lists = entries.map { (path, label) ->
            val pageUrl = if (path.startsWith("http")) path else "$mainUrl/$path"
            val items = try {
                val doc = app.get(pageUrl, headers = mapOf("Referer" to mainUrl)).document
                doc.select(itemSelector).mapNotNull { it.toSearchResponse(mainUrl) }.distinctBy { it.url }
            } catch (_: Exception) {
                emptyList<SearchResponse>()
            }
            HomePageList(label, items)
        }.filter { it.list.isNotEmpty() }
        return newHomePageResponse(lists, hasNext = false)
    }
}

class DutafilmProvider : BaseFixProvider() {
    override var mainUrl        = "https://dutafilm77.mantab.men"
    override var name           = "Dutafilm"
    override var lang           = "id"
    override var hasMainPage    = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "explore?media_type=movie" to "Movies List",
        "explore?media_type=tv" to "Series List",
        "explore?category=1" to "Anime"
    )

    private fun Element.posterUrl(): String {
        for (attr in listOf("data-src", "data-lazy-src", "data-lazy", "data-cfsrc",
                             "data-original", "data-image", "data-bg", "src")) {
            val v = this.attr(attr)
            if (v.isNotBlank() && !v.contains("data:image") &&
                (v.startsWith("http") || v.startsWith("//"))) {
                return if (v.startsWith("//")) "https:$v" else v
            }
        }
        return ""
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = if (page > 1) {
            "$mainUrl/${request.data}&page=$page"
        } else {
            "$mainUrl/${request.data}"
        }
        val items = scrapeList(pageUrl)
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return scrapeList("$mainUrl/explore?q=${query.replace(" ", "+")}")
    }

    private suspend fun scrapeList(pageUrl: String): List<SearchResponse> {
        val doc = app.get(pageUrl, headers = mapOf("Referer" to mainUrl)).document
        return doc.select("div.mv-content-items a, a:has(div.mv)").mapNotNull {
            val href  = it.attr("href").let { h -> if (h.startsWith("http")) h else "$mainUrl$h" }
            if (href.isBlank() || href == mainUrl || href.contains("javascript")) return@mapNotNull null
            
            val img   = it.selectFirst("img") ?: it.selectFirst("[data-src], [data-lazy-src], [data-original]")
            val title = it.selectFirst(".mv-desc")?.text()?.substringBefore("(")?.trim()
                ?: it.attr("title").trim().ifEmpty { img?.attr("alt")?.trim() ?: "" }.ifEmpty { img?.attr("title")?.trim() ?: "" }.ifEmpty { it.text().trim() }
            if (title.isBlank()) return@mapNotNull null
            val src   = img?.posterUrl() ?: ""
            
            val typeLabel = it.selectFirst(".mv-epi, .qualz, .type, .label, .badge")?.text()?.lowercase() ?: ""
            val isTv = typeLabel.contains("eps") || href.contains("tvshows") || href.contains("series")
            
            if (isTv) {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = src }
            } else {
                newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = src }
            }
        }.distinctBy { it.url }
    }

    private fun decryptDutafilm(encoded: String): String {
        val sb = java.lang.StringBuilder()
        encoded.split(".").forEach { chunk ->
            if (chunk.isBlank()) return@forEach
            try {
                val decodedBytes = Base64.decode(chunk, Base64.DEFAULT)
                val decodedStr = String(decodedBytes, StandardCharsets.UTF_8)
                val digits = decodedStr.replace(Regex("\\D"), "")
                if (digits.isNotEmpty()) {
                    val code = digits.toInt()
                    sb.append(code.toChar())
                }
            } catch (_: Exception) {}
        }
        return sb.toString()
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc    = app.get(url, headers = mapOf("Referer" to mainUrl)).document
        val title  = doc.selectFirst("meta[property='og:title']")?.attr("content")?.substringBefore("Sub Indo")?.substringBefore("(")?.trim()
            ?: doc.selectFirst("h2, h1, .mv-title")?.text()?.trim() ?: return null
        
        val poster = doc.selectFirst("meta[property='og:image'], [itemprop=image]")?.attr("content")
            ?: doc.selectFirst(".poster img, img.mv-poster")?.let { img ->
                listOf("data-src","data-lazy-src","data-lazy","data-cfsrc","src")
                    .map { img.attr(it) }
                    .firstOrNull { it.isNotBlank() && it.startsWith("http") }
            }
            
        val plot   = doc.selectFirst("p[style*=text-align:justify]")?.text()?.trim()
            ?: doc.selectFirst(".wp-content p, .description p, .film-description")?.text()?.trim()
            
        val year   = doc.selectFirst("meta[property='og:title']")?.attr("content")?.let {
            val match = Regex("\\((20\\d{2}|19\\d{2})\\)").find(it)
            match?.groupValues?.get(1)?.toIntOrNull()
        } ?: doc.text().let {
            val match = Regex("\\((20\\d{2}|19\\d{2})\\)").find(it)
            match?.groupValues?.get(1)?.toIntOrNull()
        }
        
        var encVar = ""
        doc.select("script").forEach { script ->
            val code = script.html()
            if (code.contains("atob") && code.contains("split") && code.contains("fromCharCode")) {
                val match = Regex("\\b[a-zA-Z0-9_]{4,12}='([a-zA-Z0-9.\\-_=]{100,})'").find(code)
                if (match != null) {
                    encVar = match.groupValues[1]
                }
            }
        }
        if (encVar.isBlank()) return null
        
        val decrypted = decryptDutafilm(encVar)
        
        val c = Regex("var c = '([^']*)'").find(decrypted)?.groupValues?.get(1) ?: return null
        val t = Regex("var t = '([^']*)'").find(decrypted)?.groupValues?.get(1) ?: return null
        val cApiHost = Regex("var c_api_host = '([^']*)'").find(decrypted)?.groupValues?.get(1) ?: return null
        val mediaType = Regex("get_link\\('[^']+',\\s*'([^']*)'\\)").find(decrypted)?.groupValues?.get(1) ?: "movie"
        val movieId = Regex("initEpisodeList\\('([^']*)',").find(decrypted)?.groupValues?.get(1)
            ?: Regex("get_link\\('([^']*)',").find(decrypted)?.groupValues?.get(1) ?: return null
            
        val isTv = mediaType == "tv"
        
        val epUrl = "$cApiHost/episode_mob.php?is_mob=0&is_uc=0&movie_id=$movieId&cat=hs&tag=ind&c=$c&t=$t"
        val epRes = app.get(epUrl, headers = mapOf("Referer" to url, "Origin" to mainUrl)).text
        val epJson = JSONObject(epRes)
        val epHtml = epJson.optString("episode_lists", "")
        
        val epDoc = Jsoup.parse(epHtml)
        val epLinks = epDoc.select("a[data-epid]")
        
        return if (isTv) {
            val eps = epLinks.mapIndexed { i, a ->
                val epId = a.attr("data-epid")
                val epCat = a.attr("data-cat")
                val epTag = a.attr("data-tag")
                val epServerXid = a.attr("data-server_xid")
                
                val payloadObj = JSONObject()
                payloadObj.put("episode_id", epId)
                payloadObj.put("cat", epCat)
                payloadObj.put("tag", epTag)
                payloadObj.put("server_xid", epServerXid)
                payloadObj.put("c", c)
                payloadObj.put("t", t)
                payloadObj.put("c_api_host", cApiHost)
                payloadObj.put("movie_url", url)
                
                newEpisode(payloadObj.toString()) {
                    this.name    = a.text().trim().ifEmpty { "Episode ${i + 1}" }
                    this.episode = a.text().trim().toIntOrNull() ?: (i + 1)
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, eps) {
                this.posterUrl = poster; this.plot = plot; this.year = year
            }
        } else {
            val firstEp = epLinks.firstOrNull()
            val payloadObj = JSONObject()
            if (firstEp != null) {
                payloadObj.put("episode_id", firstEp.attr("data-epid"))
                payloadObj.put("cat", firstEp.attr("data-cat"))
                payloadObj.put("tag", firstEp.attr("data-tag"))
                payloadObj.put("server_xid", firstEp.attr("data-server_xid"))
            } else {
                payloadObj.put("episode_id", movieId)
                payloadObj.put("cat", "ss")
                payloadObj.put("tag", "ind")
                payloadObj.put("server_xid", "")
            }
            payloadObj.put("c", c)
            payloadObj.put("t", t)
            payloadObj.put("c_api_host", cApiHost)
            payloadObj.put("movie_url", url)
            
            newMovieLoadResponse(title, url, TvType.Movie, payloadObj.toString()) {
                this.posterUrl = poster; this.plot = plot; this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (!data.startsWith("{") || !data.endsWith("}")) return false
        val payload = JSONObject(data)
        val episodeId = payload.optString("episode_id", "")
        val cat = payload.optString("cat", "")
        val tag = payload.optString("tag", "")
        val serverXid = payload.optString("server_xid", "")
        val c = payload.optString("c", "")
        val t = payload.optString("t", "")
        val cApiHost = payload.optString("c_api_host", "")
        val movieUrl = payload.optString("movie_url", mainUrl)
        
        if (episodeId.isBlank()) return false
        
        val serverUrl = "$cApiHost/server_mob.php?is_mob=0&is_uc=0&episode_id=$episodeId&cat=$cat&tag=$tag&server_xid=$serverXid&c=$c&t=$t"
        val serverRes = app.get(serverUrl, headers = mapOf("Referer" to movieUrl, "Origin" to mainUrl)).text
        val serverJson = JSONObject(serverRes)
        val dataObj = serverJson.optJSONObject("data") ?: return false
        
        val hydraxId = dataObj.optString("hydrax_id", "")
        val sbId = dataObj.optString("sb_id", "")
        val p2pId = dataObj.optString("p2p_id", "")
        val ptype = dataObj.optString("ptype", "")
        val serverId = dataObj.optString("server_id", "")
        
        val res = dataObj.optString("res", "480")
        val qua = dataObj.optString("qua", "web")
        
        suspend fun resolveHydrax(hId: String) {
            val url = "$cApiHost/video_hydrax.php?is_mob=0&is_uc=0&id=$episodeId&qua=$qua&res=$res&server_id=$serverId&cat=$cat&tag=$tag&c=$c&t=$t"
            try {
                val resText = app.get(url, headers = mapOf("Referer" to movieUrl, "Origin" to mainUrl)).text
                val resJson = JSONObject(resText)
                val hydraxUrl = resJson.optString("hydrax_url", "")
                if (hydraxUrl.isNotBlank()) {
                    val cleanUrl = if (hydraxUrl.startsWith("//")) "https:$hydraxUrl" else hydraxUrl
                    loadExtractor(cleanUrl, movieUrl, subtitleCallback, callback)
                }
            } catch (_: Exception) {}
        }
        
        suspend fun resolveStreamSB(sId: String) {
            val url = "$cApiHost/video.php?is_mob=0&is_uc=0&id=$episodeId&qua=$qua&res=$res&server_id=$serverId&cat=$cat&tag=$tag&c=$c&t=$t"
            try {
                val resText = app.get(url, headers = mapOf("Referer" to movieUrl, "Origin" to mainUrl)).text
                val resJson = JSONObject(resText)
                val sbUrl = resJson.optString("sb_url", "")
                if (sbUrl.isNotBlank()) {
                    val cleanUrl = if (sbUrl.startsWith("//")) "https:$sbUrl" else sbUrl
                    loadExtractor(cleanUrl, movieUrl, subtitleCallback, callback)
                }
                val files = resJson.optString("file", "")
                if (files.isNotBlank()) {
                    files.split(",").forEach { part ->
                        if (part.contains("]http")) {
                            val label = part.substringAfter("[").substringBefore("]")
                                .replace(Regex("<[^>]*>"), "")
                            val fileUrl = part.substringAfter("]")
                            val cleanFileUrl = if (fileUrl.startsWith("//")) "https:$fileUrl" else fileUrl
                            val isM3u = cleanFileUrl.contains(".m3u8")
                            callback(
                                newExtractorLink(
                                    source = "Dutafilm Mirror",
                                    name = "Mirror - $label",
                                    url = cleanFileUrl,
                                    type = if (isM3u) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = movieUrl
                                }
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        
        suspend fun resolveP2P(pId: String) {
            val url = "$cApiHost/video_p2p.php?is_mob=0&is_uc=0&id=$episodeId&qua=$qua&res=$res&server_id=$serverId&cat=$cat&tag=$tag&c=$c&t=$t"
            try {
                val resText = app.get(url, headers = mapOf("Referer" to movieUrl, "Origin" to mainUrl)).text
                val resJson = JSONObject(resText)
                val p2pUrl = resJson.optString("p2p_url", "")
                if (p2pUrl.isNotBlank()) {
                    val cleanUrl = if (p2pUrl.startsWith("//")) "https:$p2pUrl" else p2pUrl
                    loadExtractor(cleanUrl, movieUrl, subtitleCallback, callback)
                }
            } catch (_: Exception) {}
        }
        
        when (ptype) {
            "hydrax" -> if (hydraxId.isNotBlank()) resolveHydrax(hydraxId)
            "sb" -> if (sbId.isNotBlank()) resolveStreamSB(sbId)
            "p2p" -> if (p2pId.isNotBlank()) resolveP2P(p2pId)
        }
        
        if (ptype != "hydrax" && hydraxId.isNotBlank()) resolveHydrax(hydraxId)
        if (ptype != "sb" && sbId.isNotBlank()) resolveStreamSB(sbId)
        if (ptype != "p2p" && p2pId.isNotBlank()) resolveP2P(p2pId)
        
        return true
    }
}
