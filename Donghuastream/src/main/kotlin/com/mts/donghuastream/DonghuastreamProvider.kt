package com.mts.donghuastream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import java.security.MessageDigest

class DonghuastreamProvider : MainAPI() {

    override var mainUrl        = "https://donghuastream.org"
    override var name           = "DonghuaStream"
    override var lang           = "en"
    override val hasMainPage    = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Anime, TvType.OVA)

    override val mainPage = mainPageOf(
        "" to "Terbaru",
        "schedule" to "Jadual",
        "anime/?status=ongoing" to "Ongoing",
        "anime/?status=completed" to "Completed",
        "anime" to "Semua Anime"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path = request.data
        val cleanPath = path.removePrefix("/").removeSuffix("/")
        val pageUrl = if (path.startsWith("http")) {
            path + if (page > 1) "page/$page/" else ""
        } else {
            if (cleanPath.isEmpty()) {
                mainUrl + if (page > 1) "/page/$page/" else "/"
            } else {
                val parts = cleanPath.split("?")
                val basePath = parts[0].removeSuffix("/")
                val query = if (parts.size > 1) "?" + parts[1] else ""
                val pagedPath = if (page > 1) "$basePath/page/$page/" else "$basePath/"
                "$mainUrl/$pagedPath$query"
            }
        }
        return newHomePageResponse(request.name, scrapeList(pageUrl))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return scrapeList("$mainUrl/?s=${query.replace(" ", "+")}")
    }
    
    private fun Element.posterUrl(): String {
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
                ?.trim()?.split(" ")?.firstOrNull { it.startsWith("http") } ?: ""
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
                if (candidate.startsWith("http")) return candidate
            }
        }
        return ""
    }

    private fun Element.parseToResponse(): SearchResponse? {
        val a     = (if (this.tagName() == "a") this else this.selectFirst("a")) ?: return null
        val href  = a.attr("href").let { h -> if (h.startsWith("http")) h else "$mainUrl$h" }
        if (href.isBlank() || href == mainUrl || href.contains("javascript")) return null
        val title = this.selectFirst(".tt, .ttl, h2, .bigor .tt, .mdl-animepost .info .name, .film-name, h3")
            ?.text()?.trim()
            ?: a.attr("title").trim().ifEmpty { return null }
        val img   = this.selectFirst("img") ?: this.selectFirst("[data-src]")
        val src   = img?.posterUrl() ?: ""
        
        val hrefLower = href.lowercase()
        val isMovie = hrefLower.contains("/movie/") || hrefLower.contains("/movies/") || hrefLower.contains("/film/")
        return if (isMovie) {
            newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = src }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = src }
        }
    }

    private suspend fun scrapeList(pageUrl: String): List<SearchResponse> {
        var doc = try {
            app.get(pageUrl, headers = mapOf(
                "Referer" to mainUrl,
                "Accept"  to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )).document
        } catch (e: Exception) {
            null
        }

        val selector = ".listupd .bsx, .listupd .bs, .bsx, .bs, article.bs, .animpost, article.animpost, .animepost, article.animepost, article.item, .film-poster, .item-anime, .epbox, .out-thumb, .milist, .post-item, .hentry"
        var items = if (doc != null) {
            doc.select(selector).mapNotNull { it.parseToResponse() }
        } else emptyList<SearchResponse>()

        if (items.isEmpty() && doc != null) {
            val relaxedSelectors = listOf(
                "a:has(img)", "div:has(a:has(img))", "article", ".post", "[class*=item]", "[class*=post]", "li:has(a)"
            )
            for (sel in relaxedSelectors) {
                val found = doc.select(sel).mapNotNull { it.parseToResponse() }
                if (found.isNotEmpty()) {
                    items = found
                    break
                }
            }
        }

        if (items.isEmpty()) {
            val homeDoc = try {
                app.get(mainUrl, headers = mapOf(
                    "Referer" to mainUrl,
                    "Accept" to "text/html"
                )).document
            } catch (e: Exception) {
                null
            }
            if (homeDoc != null) {
                val homeItems = mutableListOf<SearchResponse>()
                val headings = homeDoc.select("h1, h2, h3, h4, .title, .widget-title")
                for (h in headings) {
                    val txt = h.text().lowercase()
                    if (txt.contains("series") || txt.contains("tv") || txt.contains("terbaru") || txt.contains("episode")) {
                        var sibling = h.nextElementSibling()
                        while (sibling != null) {
                            val links = sibling.select("a:has(img), .card, .item, article").mapNotNull { it.parseToResponse() }
                            if (links.isNotEmpty()) {
                                homeItems.addAll(links)
                                break
                            }
                            sibling = sibling.nextElementSibling()
                        }
                    }
                }
                if (homeItems.isEmpty()) {
                    val links = homeDoc.select("a:has(img)").mapNotNull { it.parseToResponse() }
                    homeItems.addAll(links)
                }
                if (homeItems.isNotEmpty()) {
                    items = homeItems
                }
            }
        }

        if (items.isEmpty()) {
            items = listOf(
                newTvSeriesSearchResponse("No valid data found - Refresh", mainUrl, TvType.TvSeries) {
                    posterUrl = ""
                }
            )
        }

        return items.distinctBy { it.url }
    }
    
    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("Referer" to mainUrl)).document
        val title  = doc.selectFirst("h1.entry-title, .thumb img, .film-poster img, .animposx .entry-title")?.let {
            if (it.tagName() == "img") it.attr("alt").trim() else it.text().trim()
        }?.trim() ?: return null
        val poster = doc.selectFirst(".thumb img, .seriesthumb img, .film-poster img, .entry-thumb img, .cover img")
            ?.let { img ->
                listOf("data-src","data-lazy-src","data-lazy","data-cfsrc","data-original","src")
                    .map { img.attr(it) }
                    .firstOrNull { it.isNotBlank() && it.startsWith("http") }
            }
        val plot   = doc.selectFirst(".entry-content p, .synp .deskripsi, [itemprop=description], .film-description p")
            ?.text()?.trim()
        val genres = doc.select(".genxed a, .genre-info a, .info-content .spe a[href*=genre], .film-genres a")
            .map { it.text() }
        val eps = doc.select(".eplister ul li a, .episodelist ul li a, .clps li a, .ep-list li a").mapNotNull { a ->
            val epTitle = a.selectFirst(".epl-title, .epl-num, span")?.text()?.trim()
                ?: a.text().trim()
            val epUrl   = a.attr("href")
            if (epUrl.isNotBlank()) {
                val epNum = Regex("(?i)episode\\s*(\\d+)").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("(\\d+)").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()
                newEpisode(epUrl) {
                    this.name = epTitle
                    this.episode = epNum
                }
            } else null
        }
        val sortedEps = if (eps.any { it.episode != null }) {
            eps.sortedBy { it.episode ?: 9999 }
        } else {
            eps.reversed()
        }
        val isTvSeries = sortedEps.size > 1 || url.contains("/series/") || url.contains("/tvshows/") || url.contains("/tv/") || url.contains("/season/")
        return if (isTvSeries && sortedEps.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, sortedEps) {
                this.posterUrl = poster; this.plot = plot; this.tags = genres
            }
        } else {
            val movieUrl = sortedEps.firstOrNull()?.data ?: url
            newMovieLoadResponse(title, movieUrl, TvType.Movie, movieUrl) {
                this.posterUrl = poster; this.plot = plot; this.tags = genres
            }
        }
    }
    
    private fun sha256(input: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private val vodCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    class AbyssExtractor : com.lagradost.cloudstream3.utils.ExtractorApi() {
        override val name = "Abyss"
        override val mainUrl = "https://abyssplayer.com"
        override val requiresReferer = true

        private fun decryptAesCtr(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
            val spec = javax.crypto.spec.SecretKeySpec(key, "AES")
            val parameterSpec = javax.crypto.spec.IvParameterSpec(iv)
            val cipher = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, spec, parameterSpec)
            return cipher.doFinal(ciphertext)
        }

        private fun md5(input: ByteArray): ByteArray {
            val md = java.security.MessageDigest.getInstance("MD5")
            return md.digest(input)
        }

        override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
            callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
        ) {
            try {
                val cleanUrl = url.replace(92.toChar().toString(), "")
                val pageHtml = app.get(cleanUrl, headers = mapOf("Referer" to (referer ?: mainUrl))).text

                val base64Str = Regex("const datas\\s*=\\s*\"([^\"]+)\"").find(pageHtml)?.groupValues?.get(1) ?: return
                val decodedBytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
                val latin1Str = String(decodedBytes, Charsets.ISO_8859_1)

                val json = org.json.JSONObject(latin1Str)
                val slug = json.getString("slug")
                val userId = json.getString("user_id")
                val md5Id = json.getString("md5_id")
                val media = json.getString("media")

                val keyStr = "$userId:$slug:$md5Id"
                val keyBytesStr = md5(keyStr.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                val key = keyBytesStr.toByteArray(Charsets.UTF_8)
                val iv = key.sliceArray(0 until 16)

                val mediaCiphertext = android.util.Base64.decode(media, android.util.Base64.DEFAULT)
                val decryptedMediaBytes = decryptAesCtr(mediaCiphertext, key, iv)
                val decryptedMediaStr = String(decryptedMediaBytes, Charsets.UTF_8)

                val mediaJson = org.json.JSONObject(decryptedMediaStr)
                val mp4 = mediaJson.getJSONObject("mp4")
                val sources = mp4.getJSONArray("sources")
                val domainsObj = if (mp4.has("domains")) mp4.getJSONObject("domains") else if (mediaJson.has("domains")) mediaJson.getJSONObject("domains") else org.json.JSONObject()

                for (i in 0 until sources.length()) {
                    val src = sources.getJSONObject(i)
                    val size = src.getLong("size")
                    val resId = src.getInt("res_id")
                    val label = src.getString("label")
                    val sub = src.getString("sub")

                    val domain = domainsObj.getString(sub)

                    val pathStr = "/mp4/$md5Id/$resId/$size?v=$slug"
                    
                    val sizeStr = size.toString()
                    val digitBytes = sizeStr.map { it.toString().toInt().toByte() }.toByteArray()
                    val sizeHashHex = md5(digitBytes).joinToString("") { "%02x".format(it) }
                    
                    val pathKey = sizeHashHex.toByteArray(Charsets.UTF_8)
                    val pathIv = pathKey.sliceArray(0 until 16)

                    val pathBytes = pathStr.toByteArray(Charsets.UTF_8)
                    val encryptedPathBytes = decryptAesCtr(pathBytes, pathKey, pathIv)

                    val b64Once = android.util.Base64.encodeToString(encryptedPathBytes, android.util.Base64.NO_WRAP)
                    val b64Twice = android.util.Base64.encodeToString(b64Once.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                    val cleanPath = b64Twice.replace("=", "").replace("\\n", "").replace("\\r", "")

                    val finalStreamUrl = "https://$domain/sora/$size/$cleanPath"

                    callback(
                        com.lagradost.cloudstream3.utils.newExtractorLink(
                            source = name,
                            name = "$name - $label",
                            url = finalStreamUrl,
                            type = com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
                        ) {
                            this.referer = cleanUrl
                            this.quality = when (label.lowercase()) {
                                "360p" -> com.lagradost.cloudstream3.utils.Qualities.P360.value
                                "480p" -> com.lagradost.cloudstream3.utils.Qualities.P480.value
                                "720p" -> com.lagradost.cloudstream3.utils.Qualities.P720.value
                                "1080p" -> com.lagradost.cloudstream3.utils.Qualities.P1080.value
                                else -> com.lagradost.cloudstream3.utils.Qualities.Unknown.value
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    object JsUnpacker {
        fun unpack(packed: String): String? {
            try {
                val regex = Regex("eval\\(function\\(p,a,c,k,e,.[^\\)]*\\)\\{.*\\}\\s*\\(\\s*([\\x27\\x22].*?[\\x27\\x22])\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*([\\x27\\x22].*?[\\x27\\x22])\\.split\\(")
                val match = regex.find(packed) ?: return null
                
                val pStr = match.groupValues[1]
                val a = match.groupValues[2].toIntOrNull() ?: return null
                val c = match.groupValues[3].toIntOrNull() ?: return null
                val kStr = match.groupValues[4]
                
                val p = pStr.substring(1, pStr.length - 1).replace(92.toChar().toString() + "'", "'").replace(92.toChar().toString() + 34.toChar().toString(), 34.toChar().toString())
                val k = kStr.substring(1, kStr.length - 1).split("|")
                
                val unbaser = Unbaser(a)
                val wordsRegex = Regex("\\b\\w+\\b")
                
                return wordsRegex.replace(p) { wordMatch ->
                    val word = wordMatch.value
                    val index = unbaser.unbase(word)
                    if (index < k.size && k[index].isNotEmpty()) {
                        k[index]
                    } else {
                        word
                    }
                }
            } catch (e: Exception) {
                return null
            }
        }
        
        private class Unbaser(val base: Int) {
            private val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            
            fun unbase(str: String): Int {
                if (str.isEmpty()) return 0
                var result = 0
                for (char in str) {
                    val valOf = alphabet.indexOf(char)
                    if (valOf >= 0 && valOf < base) {
                        result = result * base + valOf
                    }
                }
                return result
            }
        }
    }

    class GDPlayerExtractor : com.lagradost.cloudstream3.utils.ExtractorApi() {
        override val name = "GDPlayer"
        override val mainUrl = "https://play.streamplay.co.in"
        override val requiresReferer = true

        override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
            callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
        ) {
            try {
                val cleanUrl = url.replace(92.toChar().toString(), "")
                val uri = java.net.URI(cleanUrl)
                val domain = uri.host
                val id = cleanUrl.substringAfter("/embed/").substringBefore("/").substringBefore("?")
                if (id.isEmpty()) return

                val downloadPageUrl = "https://$domain/download/$id"
                val response = app.get(
                    downloadPageUrl,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                )
                if (!response.isSuccessful) return
                val html = response.text

                val unpacked = JsUnpacker.unpack(html) ?: ""
                val kaken = Regex("window\\.kaken\\s*=\\s*\"([^\"]+)\"").find(unpacked)?.groupValues?.get(1) ?: return

                val apiUrl = "https://$domain/api/"
                val apiResponse = app.post(
                    url = apiUrl,
                    requestBody = okhttp3.RequestBody.create(
                        null as okhttp3.MediaType?,
                        kaken
                    ),
                    headers = mapOf(
                        "Referer" to downloadPageUrl,
                        "X-Requested-With" to "XMLHttpRequest",
                        "Content-Type" to "text/plain",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    ),
                    verify = false
                )
                if (!apiResponse.isSuccessful) return
                val jsonText = apiResponse.text
                val json = org.json.JSONObject(jsonText)
                if (json.optString("status") == "ok") {
                    val sources = json.getJSONArray("sources")
                    for (i in 0 until sources.length()) {
                        val src = sources.getJSONObject(i)
                        val fileUrl = src.getString("file")
                        val label = src.optString("label", "1080p")
                        
                        callback(
                            com.lagradost.cloudstream3.utils.newExtractorLink(
                                source = name,
                                name = "$name - $label",
                                url = fileUrl,
                                type = if (fileUrl.contains(".m3u8")) com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 else com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
                            ) {
                                this.referer = downloadPageUrl
                                this.quality = when (label.lowercase()) {
                                    "360p" -> com.lagradost.cloudstream3.utils.Qualities.P360.value
                                    "480p" -> com.lagradost.cloudstream3.utils.Qualities.P480.value
                                    "720p" -> com.lagradost.cloudstream3.utils.Qualities.P720.value
                                    "1080p" -> com.lagradost.cloudstream3.utils.Qualities.P1080.value
                                    else -> com.lagradost.cloudstream3.utils.Qualities.Unknown.value
                                }
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GDPlayerExtractor", "Failed to extract: ${e.message}")
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
        parentCallback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
    ): Boolean {
        val isKlikxxi = this.name.contains("klikxxi", true) || this::class.java.simpleName.contains("klikxxi", true)
        val isStreamWish = false // Kept for unit test compatibility

        fun fixUrl(url: String): String {
            if (url.isBlank()) return ""
            if (url.startsWith("http")) return url
            if (url.startsWith("//")) return "https:$url"
            return try {
                val u = java.net.URL(data)
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

        val priorityList = listOf(
            "hydrax", "turbovip", "cast", "doodstream", "voe", "streamtape", 
            "vidguard", "mixdrop", "filemoon", "vidsrc", "upstream", "streamwish", 
            "vudeo", "supervideo", "streamhide", "vidlox", "dropload", "vidoza", 
            "embedrise", "userload", "faststream", "pelisnow", "rabbitstream", 
            "vizcloud", "mega", "mediafire", "terabox", "google", "dropbox", "onedrive",
            "gdplayer", "streamplay"
        )

        fun getPriorityRank(url: String): Int {
            val u = url.lowercase()
            for (i in priorityList.indices) {
                val keyword = priorityList[i]
                val matches = when (keyword) {
                    "doodstream" -> listOf("doodstream", "dood", "dsvplay", "doodcdn", "vide0", "ds2play", "ds2video", "doodstream", "doodla")
                    "streamwish" -> listOf("streamwish", "wish", "hglink", "hgcloud", "gendeng", "fkupon", "desacinta", "layarotaku", "layarwibu", "nekonime", "layarecchi", "subsource", "doimg", "anchurl", "certaker", "listeamed", "bigwarp", "cloudatacdn", "push-sdk", "gradehg", "hgplus", "awish", "wishembed")
                    "google" -> listOf("google", "gdrive", "drive.google")
                    else -> listOf(keyword)
                }
                if (matches.any { u.contains(it) }) {
                    return i
                }
            }
            return 999
        }

        // Layer 1: VOD Source Detector Engine
        fun classifySource(url: String): String {
            val rank = getPriorityRank(url)
            if (rank == 999) return "unknown"
            val keyword = priorityList[rank]
            return when (keyword) {
                "mega", "mediafire", "terabox", "google", "dropbox", "onedrive" -> "cloud"
                "vidsrc", "rabbitstream", "vizcloud", "hydrax", "turbovip", "cast", "pelisnow", "embedrise" -> "embed"
                "gdplayer", "streamplay" -> "embed"
                else -> "hosting"
            }
        }

        // Layer 5: Smart Player Compatibility Engine
        fun getPlayerType(url: String): String {
            val u = url.lowercase()
            return when {
                u.contains("iframe") || u.contains("/e/") || u.contains("/embed/") -> "iframe"
                u.contains(".m3u8") || u.contains("/hls/") -> "m3u8"
                u.contains(".mp4") -> "mp4"
                else -> "js-encrypted"
            }
        }

        // Layer 6: Validation & Cleaning Engine
        suspend fun validateAndEmitLink(link: com.lagradost.cloudstream3.utils.ExtractorLink): Boolean {
            val url = link.url
            
            // Layer 7: Cache & Reuse Engine check
            val cachedDirect = vodCache[url]
            if (cachedDirect != null && cachedDirect == "DEAD") return false
            if (cachedDirect != null) {
                parentCallback(
                    com.lagradost.cloudstream3.utils.newExtractorLink(
                        source = link.source,
                        name = link.name,
                        url = cachedDirect,
                        type = if (cachedDirect.contains(".m3u8")) com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 else com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
                    ) {
                        this.referer = link.referer
                        this.quality = link.quality
                        this.headers = link.headers
                    }
                )
                return true
            }

            try {
                val headersMap = mutableMapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Range" to "bytes=0-1024"
                )
                if (link.referer.isNotEmpty()) {
                    headersMap["Referer"] = link.referer
                }
                headersMap.putAll(link.headers)

                val res = app.get(
                    url = url,
                    headers = headersMap,
                    verify = false,
                    timeout = 8
                )

                if (res.isSuccessful) {
                    val finalUrl = res.url
                    val contentType = res.headers["Content-Type"]?.lowercase() ?: ""
                    val isPlayable = contentType.contains("video") || 
                                     contentType.contains("mpegurl") || 
                                     contentType.contains("application/x-mpegurl") || 
                                     contentType.contains("application/octet-stream") ||
                                     finalUrl.contains(".m3u8") || 
                                     finalUrl.contains(".mp4")

                    if (isPlayable) {
                        vodCache[url] = finalUrl
                        
                        parentCallback(
                            com.lagradost.cloudstream3.utils.newExtractorLink(
                                source = link.source,
                                name = link.name,
                                url = finalUrl,
                                type = if (finalUrl.contains(".m3u8")) com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 else com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
                            ) {
                                this.referer = link.referer
                                this.quality = link.quality
                                this.headers = link.headers
                            }
                        )
                        return true
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VODValidator", "Validation failed for ${url}: ${e.message}")
            }
            
            if (!isKlikxxi) {
                val isDirectFormat = url.contains(".m3u8") || url.contains(".mp4") || url.contains("/hls/")
                if (isDirectFormat) {
                    parentCallback(link)
                    return true
                }
            }
            
            vodCache[url] = "DEAD"
            return false
        }

        // Layer 3: Stream Resolver Engine (Core Recursive resolver)
        suspend fun resolveAndValidateStream(link: com.lagradost.cloudstream3.utils.ExtractorLink, depth: Int = 0): Boolean {
            if (depth > 5) return false
            val url = link.url
            
            val cachedDirect = vodCache[url]
            if (cachedDirect != null && cachedDirect == "DEAD") return false
            if (cachedDirect != null) {
                parentCallback(
                    com.lagradost.cloudstream3.utils.newExtractorLink(
                        source = link.source,
                        name = link.name,
                        url = cachedDirect,
                        type = if (cachedDirect.contains(".m3u8")) com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 else com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
                    ) {
                        this.referer = link.referer
                        this.quality = link.quality
                        this.headers = link.headers
                    }
                )
                return true
            }

            try {
                val headersMap = mutableMapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Range" to "bytes=0-1024"
                )
                if (link.referer.isNotEmpty()) {
                    headersMap["Referer"] = link.referer
                }
                headersMap.putAll(link.headers)

                val res = app.get(
                    url = url,
                    headers = headersMap,
                    verify = false,
                    timeout = 8
                )

                if (res.isSuccessful) {
                    val finalUrl = res.url
                    val contentType = res.headers["Content-Type"]?.lowercase() ?: ""
                    val isPlayable = contentType.contains("video") || 
                                     contentType.contains("mpegurl") || 
                                     contentType.contains("application/x-mpegurl") || 
                                     contentType.contains("application/octet-stream") ||
                                     finalUrl.contains(".m3u8") || 
                                     finalUrl.contains(".mp4")

                    if (isPlayable) {
                        vodCache[url] = finalUrl
                        parentCallback(
                            com.lagradost.cloudstream3.utils.newExtractorLink(
                                source = link.source,
                                name = link.name,
                                url = finalUrl,
                                type = if (finalUrl.contains(".m3u8")) com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 else com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
                            ) {
                                this.referer = link.referer
                                this.quality = link.quality
                                this.headers = link.headers
                            }
                        )
                        return true
                    } else {
                        val doc = res.document
                        val subSource = doc.selectFirst("source[src], video source[src], video[src]")?.attr("src")
                            ?: doc.selectFirst("iframe[src]")?.attr("src")
                        if (!subSource.isNullOrBlank()) {
                            val resolvedUrl = if (subSource.startsWith("http")) subSource else {
                                val u = java.net.URL(finalUrl)
                                if (subSource.startsWith("/")) "${u.protocol}://${u.host}$subSource" else "${finalUrl.substringBeforeLast("/")}/$subSource"
                            }
                            val nextLink = com.lagradost.cloudstream3.utils.newExtractorLink(
                                source = link.source,
                                name = link.name,
                                url = resolvedUrl,
                                type = if (resolvedUrl.contains(".m3u8")) com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 else com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
                            ) {
                                this.referer = finalUrl
                                this.quality = link.quality
                                this.headers = link.headers
                            }
                            return resolveAndValidateStream(nextLink, depth + 1)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VODResolver", "Resolution failed for ${url}: ${e.message}")
            }
            
            vodCache[url] = "DEAD"
            return false
        }

        // Retry helper with max 3 retries
        suspend fun resolveStreamWithRetry(link: com.lagradost.cloudstream3.utils.ExtractorLink, retries: Int = 3): Boolean {
            for (i in 0 until retries) {
                val success = resolveAndValidateStream(link)
                if (success) return true
            }
            return false
        }

        // Intercepting callback wrapper to validate/resolve all generated links with Retry
        val callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit = { link ->
            kotlinx.coroutines.runBlocking {
                val sourceClass = classifySource(link.url)
                if (sourceClass == "unknown") {
                    resolveStreamWithRetry(link, 3)
                } else {
                    validateAndEmitLink(link)
                }
            }
        }

        val doc = app.get(data, headers = mapOf("Referer" to mainUrl)).document

        // Layer 2: Unified Link Extractor (Standard Scraper part)
        suspend fun runStandardEngine(document: org.jsoup.nodes.Document): List<String> {
            val list = mutableListOf<String>()
            
            document.select("source[src], video source[src], video[src]").forEach { el ->
                val src = el.attr("src").trim()
                val finalUrl = fixUrl(src)
                if (finalUrl.isNotEmpty()) list.add(finalUrl)
            }

            document.select("iframe[src], iframe[data-src], iframe[data-litespeed-src], iframe[data-lazy-src], iframe.metaframe").forEach { iframe ->
                val s = iframe.attr("src")
                val ds = iframe.attr("data-src")
                val ls = iframe.attr("data-litespeed-src")
                val laz = iframe.attr("data-lazy-src")
                
                val rawSrc = if (s.isNotBlank()) s else if (ds.isNotBlank()) ds else if (ls.isNotBlank()) ls else laz
                val finalUrl = fixUrl(rawSrc.trim())
                if (finalUrl.isNotEmpty()) list.add(finalUrl)
            }

            document.select("select option, .mirror option, .server option, select.mirror option, select.server option, .mobius option").forEach { el ->
                listOf("value", "data-src", "data-link", "data-embed", "data-video", "data-url", "data-id").forEach { attr ->
                    val v = el.attr(attr).trim()
                    val finalUrl = fixUrl(v)
                    if (finalUrl.isNotEmpty()) list.add(finalUrl)
                }
            }

            document.select("a, button, li, div, span, .opt-sp, .opt-single, .mirror-item, div#downloadb li, div.download li").forEach { el ->
                val href = el.attr("href").trim()
                if (href.isNotBlank() && !href.startsWith("#") && !href.contains("javascript", true)) {
                    val finalUrl = fixUrl(href)
                    if (finalUrl.isNotEmpty()) list.add(finalUrl)
                }
                listOf("data-src", "data-link", "data-embed", "data-video", "data-id", "data-url", "data-content").forEach { attr ->
                    val v = el.attr(attr).trim()
                    val finalUrl = fixUrl(v)
                    if (finalUrl.isNotEmpty() && !v.contains("data:image")) {
                        list.add(finalUrl)
                    }
                }
            }

            val ajaxBtns = document.select("[data-post][data-nume], ul#playeroptionsul > li, li.zetaflix_player_option, .mirror-item")
            val ajaxOptions = ajaxBtns.mapNotNull {
                val post = it.attr("data-post")
                val nume = it.attr("data-nume")
                val type = it.attr("data-type").ifEmpty { "movie" }
                if (post.isNotEmpty() && nume.isNotEmpty()) {
                    Triple(post, nume, type)
                } else {
                    null
                }
            }.distinct()

            ajaxOptions.forEach { (post, nume, type) ->
                val actions = listOf(
                    "zt_main_ajax", "doo_player_ajax", "wp_ajax_doo_player", 
                    "action_player", "playvideo", "zeta_player_ajax",
                    "get_player_source", "ajax_player", "player_ajax", "bootstrap_ajax"
                )
                for (action in actions) {
                    try {
                        val pageBase = try {
                            val u = java.net.URL(data)
                            "${u.protocol}://${u.host}"
                        } catch (_: Exception) { mainUrl }
                        val response = app.post(
                            url = "$pageBase/wp-admin/admin-ajax.php",
                            data = mapOf(
                                "action" to action,
                                "post" to post,
                                "nume" to nume,
                                "type" to type
                            ),
                            referer = data,
                            headers = mapOf(
                                "X-Requested-With" to "XMLHttpRequest",
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                            )
                        )
                        if (!response.isSuccessful) continue
                        val json = response.text
                        if (json.isBlank() || json == "0" || json == "false" || json == "null") continue

                        val parsedDoc = org.jsoup.Jsoup.parse(json)
                        val iframeSrc = parsedDoc.selectFirst("iframe[src], iframe[data-src]")?.let { 
                            it.attr("src").ifEmpty { it.attr("data-src") } 
                        }

                        val embedUrl = iframeSrc
                            ?: Regex("""src=["']([^"']+)["']""").find(json)?.groupValues?.get(1)
                            ?: Regex("""href=["']([^"']+)["']""").find(json)?.groupValues?.get(1)
                            ?: Regex("""["'](https?:[^"']+)["']""").find(json)?.groupValues?.get(1)
                            ?: if (json.trim().startsWith("http")) json.trim() else null

                        if (embedUrl != null) {
                            val cleanUrl = fixUrl(embedUrl)
                            if (cleanUrl.isNotEmpty()) {
                                list.add(cleanUrl)
                                break
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            document.select("script").forEach { script ->
                val content = script.data()
                if (content.isNotBlank()) {
                    Regex("""https?://[a-zA-Z0-9.\\-_]+/[a-zA-Z0-9.\\-_\\?&=\\/~]+""").findAll(content).forEach { match ->
                        val url = match.value
                        if (!url.contains("google") && !url.contains("facebook") && !url.contains("analytics")) {
                            val finalUrl = fixUrl(url)
                            if (finalUrl.isNotEmpty()) list.add(finalUrl)
                        }
                    }
                }
            }

            return list
        }

        // Layer 2: Unified Link Extractor (Fallback Scraper part)
        fun runFallbackEngine(htmlContent: String): List<String> {
            val list = mutableListOf<String>()
            
            val commentsRegex = Regex("<!--(.*?)-->", RegexOption.DOT_MATCHES_ALL)
            commentsRegex.findAll(htmlContent).forEach { match ->
                val commentContent = match.groupValues[1]
                Regex("""(?:src|href)=["']([^"']+)["']""").findAll(commentContent).forEach { subMatch ->
                    val url = fixUrl(subMatch.groupValues[1])
                    if (url.isNotEmpty()) list.add(url)
                }
                Regex("""https?://[a-zA-Z0-9.\\\\-_]+/[a-zA-Z0-9.\\\\-_\\\\?&=\\\\/~]+""").findAll(commentContent).forEach { subMatch ->
                    val url = fixUrl(subMatch.value)
                    if (url.isNotEmpty() && !url.contains("google") && !url.contains("facebook")) {
                        list.add(url)
                    }
                }
            }

            Regex("""https?://[a-zA-Z0-9.\\\\-_]+/[a-zA-Z0-9.\\\\-_\\\\?&=\\\\/~]+\\\\.(?:m3u8|mp4)[a-zA-Z0-9.\\\\-_\\\\?&=\\\\/~]*""").findAll(htmlContent).forEach { match ->
                val url = fixUrl(match.value)
                if (url.isNotEmpty() && !url.contains("google") && !url.contains("facebook")) {
                    list.add(url)
                }
            }

            val hosterKeywords = listOf("streamwish", "dood", "voe.sx", "streamtape", "filemoon", "mp4upload", "gofile.io", "abyssplayer")
            Regex("""https?://[a-zA-Z0-9.\\\\-_]+/[a-zA-Z0-9.\\\\-_\\\\?&=\\\\/~]+""").findAll(htmlContent).forEach { match ->
                val url = match.value
                if (hosterKeywords.any { url.contains(it, true) }) {
                    val clean = fixUrl(url)
                    if (clean.isNotEmpty()) list.add(clean)
                }
            }

            return list
        }

        var targets = runStandardEngine(doc)
        if (targets.isEmpty()) {
            targets = runFallbackEngine(doc.outerHtml())
        }

        // Layer 4: Multi-Host Fallback Engine - Sort targets by fallback priority
        val sortedTargets = targets.distinct().sortedBy { getPriorityRank(it) }

        // Engine Selection & Processing Layer
        sortedTargets.forEach { raw ->
            val cleanedRaw = raw.trim()
            if (cleanedRaw.isBlank()) return@forEach

            var decodedUrl = ""
            try {
                val base64Str = cleanedRaw.filter { !it.isWhitespace() }
                val decoded = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
                val html = String(decoded, Charsets.UTF_8)
                val src = org.jsoup.Jsoup.parse(html).selectFirst(
                    "iframe[src], iframe[data-litespeed-src], iframe[data-lazy-src], iframe[data-src], source[src]"
                )?.let { ifr ->
                    val s = ifr.attr("src")
                    val ls = ifr.attr("data-litespeed-src")
                    val laz = ifr.attr("data-lazy-src")
                    val ds = ifr.attr("data-src")
                    if (s.isNotBlank()) s else if (ls.isNotBlank()) ls else if (laz.isNotBlank()) laz else ds
                } ?: if (html.startsWith("http")) html else ""
                
                if (src.isNotEmpty()) {
                    decodedUrl = fixUrl(src)
                }
            } catch (_: Exception) {}

            val finalUrl = if (decodedUrl.isNotEmpty()) decodedUrl else cleanedRaw
            if (finalUrl.startsWith("http") || finalUrl.startsWith("//")) {
                var cleanUrlEscaped = (if (finalUrl.startsWith("//")) "https:$finalUrl" else finalUrl).replace(92.toChar().toString(), "")
                if (cleanUrlEscaped.contains("/f/") || cleanUrlEscaped.contains("/d/")) {
                    val isWishOrDood = listOf("streamwish", "wish", "hglink", "hgcloud", "gendeng", "fkupon", "desacinta", "layarotaku", "layarwibu", "nekonime", "layarecchi", "subsource", "doimg", "anchurl", "certaker", "listeamed", "bigwarp", "cloudatacdn", "push-sdk", "gradehg", "hgplus", "streamplay", "awish", "wishembed", "vikingfile", "dood", "dsvplay", "doodcdn", "vide0", "ds2play", "ds2video", "doodstream", "doodla").any { cleanUrlEscaped.contains(it, true) }
                    if (isWishOrDood) {
                        cleanUrlEscaped = cleanUrlEscaped
                            .replace("/f/", "/e/")
                            .replace("/d/", "/e/")
                    }
                }
                
                val playerType = getPlayerType(cleanUrlEscaped)
                val sourceClass = classifySource(cleanUrlEscaped)

                // Route through appropriate extractors or resolving paths
                when {
                    sourceClass == "cloud" && cleanUrlEscaped.contains("gofile.io") -> {
                        try {
                            val contentId = cleanUrlEscaped.substringAfter("/d/").substringBefore("/").substringBefore("?")
                            if (contentId.isNotEmpty()) {
                                val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                val accResponse = app.post(
                                    url = "https://api.gofile.io/accounts",
                                    headers = mapOf(
                                        "User-Agent" to userAgent,
                                        "Accept" to "*/*",
                                        "Referer" to "https://gofile.io/",
                                        "Origin" to "https://gofile.io"
                                    )
                                )
                                if (accResponse.isSuccessful) {
                                    val responseText = accResponse.text
                                    val apiToken = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(responseText)?.groupValues?.get(1)
                                    if (apiToken != null) {
                                        val timeSlot = System.currentTimeMillis() / 1000 / 14400
                                        val salt = "5d4f7g8sd45fsd"
                                        val tokenData = "$userAgent::en-US::$apiToken::$timeSlot::$salt"
                                        val websiteToken = sha256(tokenData)
                                        
                                        val contentUrl = "https://api.gofile.io/contents/$contentId?contentFilter=&page=1&pageSize=1000&sortField=name&sortDirection=1"
                                        val contentResponse = app.get(
                                            url = contentUrl,
                                            headers = mapOf(
                                                "User-Agent" to userAgent,
                                                "Accept" to "*/*",
                                                "Referer" to "https://gofile.io/",
                                                "Origin" to "https://gofile.io",
                                                "Authorization" to "Bearer $apiToken",
                                                "X-Website-Token" to websiteToken,
                                                "X-BL" to "en-US"
                                            )
                                        )
                                        if (contentResponse.isSuccessful) {
                                            val contentText = contentResponse.text
                                            Regex("\"link\"\\s*:\\s*\"([^\"]+)\"").findAll(contentText).forEach { match ->
                                                val link = match.groupValues[1]
                                                if (link.startsWith("http")) {
                                                    callback(
                                                        com.lagradost.cloudstream3.utils.newExtractorLink(
                                                            source = "Gofile",
                                                            name = "Gofile",
                                                            url = link,
                                                            type = if (link.contains(".m3u8")) com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 else com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
                                                        ) {
                                                            this.referer = "https://gofile.io/"
                                                            this.quality = com.lagradost.cloudstream3.utils.Qualities.Unknown.value
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    playerType == "m3u8" || playerType == "mp4" -> {
                        try {
                            val isM3u = playerType == "m3u8"
                            callback(
                                com.lagradost.cloudstream3.utils.newExtractorLink(
                                    source = "Direct Stream",
                                    name = "Direct Stream",
                                    url = cleanUrlEscaped,
                                    type = if (isM3u) com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 else com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = data
                                    this.quality = com.lagradost.cloudstream3.utils.Qualities.Unknown.value
                                }
                            )
                        } catch (_: Exception) {}
                    }
                    cleanUrlEscaped.contains("abyss") -> {
                        try {
                            AbyssExtractor().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "AbyssExtractor failed: ${e.message}")
                        }
                    }
                    cleanUrlEscaped.contains("streamplay") || cleanUrlEscaped.contains("gdplayer") -> {
                        try {
                            GDPlayerExtractor().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "GDPlayerExtractor failed: ${e.message}")
                        }
                    }
                    cleanUrlEscaped.contains("streamwish") -> {
                        try {
                            com.lagradost.cloudstream3.extractors.StreamWishExtractor().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "StreamWish failed: ${e.message}")
                        }
                    }
                    cleanUrlEscaped.contains("dood") -> {
                        try {
                            com.lagradost.cloudstream3.extractors.DoodLaExtractor().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "DoodLaExtractor failed: ${e.message}")
                        }
                    }
                    cleanUrlEscaped.contains("voe") -> {
                        try {
                            com.lagradost.cloudstream3.extractors.Voe().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "Voe failed: ${e.message}")
                        }
                    }
                    cleanUrlEscaped.contains("streamtape") -> {
                        try {
                            com.lagradost.cloudstream3.extractors.StreamTape().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "StreamTape failed: ${e.message}")
                        }
                    }
                    cleanUrlEscaped.contains("filemoon") -> {
                        try {
                            com.lagradost.cloudstream3.extractors.FileMoon().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "FileMoon failed: ${e.message}")
                        }
                    }
                    cleanUrlEscaped.contains("mp4upload") -> {
                        try {
                            com.lagradost.cloudstream3.extractors.Mp4Upload().getUrl(cleanUrlEscaped, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                            android.util.Log.e("FallbackExtractor", "Mp4Upload failed: ${e.message}")
                        }
                    }
                    else -> {
                        val isSameDomain = try {
                            val host1 = java.net.URL(cleanUrlEscaped).host.replace("www.", "")
                            val host2 = java.net.URL(mainUrl).host.replace("www.", "")
                            host1 == host2
                        } catch (_: Exception) { false }

                        if (isSameDomain && cleanUrlEscaped != data) {
                            try {
                                val subDoc = app.get(cleanUrlEscaped, referer = data).document
                                val subTargets = runStandardEngine(subDoc)
                                subTargets.forEach { subTarget ->
                                    if (subTarget != cleanUrlEscaped) {
                                        val subClean = subTarget.replace(92.toChar().toString(), "")
                                        val subType = getPlayerType(subClean)
                                        if (subType == "m3u8" || subType == "mp4") {
                                            val isM3u = subType == "m3u8"
                                            callback(
                                                com.lagradost.cloudstream3.utils.newExtractorLink(
                                                    source = "Direct Stream",
                                                    name = "Direct Stream",
                                                    url = subClean,
                                                    type = if (isM3u) com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 else com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
                                                ) {
                                                    this.referer = cleanUrlEscaped
                                                }
                                            )
                                        } else if (subClean.contains("abyss") || subClean.contains("streamplay") || subClean.contains("gdplayer") || subClean.contains("streamwish") || subClean.contains("dood") || subClean.contains("voe") || subClean.contains("streamtape") || subClean.contains("filemoon") || subClean.contains("mp4upload")) {
                                            when {
                                                subClean.contains("abyss") -> AbyssExtractor().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                                subClean.contains("streamplay") || subClean.contains("gdplayer") -> GDPlayerExtractor().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                                subClean.contains("streamwish") -> com.lagradost.cloudstream3.extractors.StreamWishExtractor().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                                subClean.contains("dood") -> com.lagradost.cloudstream3.extractors.DoodLaExtractor().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                                subClean.contains("voe") -> com.lagradost.cloudstream3.extractors.Voe().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                                subClean.contains("streamtape") -> com.lagradost.cloudstream3.extractors.StreamTape().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                                subClean.contains("filemoon") -> com.lagradost.cloudstream3.extractors.FileMoon().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                                subClean.contains("mp4upload") -> com.lagradost.cloudstream3.extractors.Mp4Upload().getUrl(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                            }
                                        } else {
                                            try {
                                                com.lagradost.cloudstream3.utils.loadExtractor(subClean, cleanUrlEscaped, subtitleCallback, callback)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }

                        if (!cleanUrlEscaped.contains("googletagmanager") && !cleanUrlEscaped.contains("facebook") && 
                            !cleanUrlEscaped.contains("googleads") && !cleanUrlEscaped.contains("analytics") && 
                            !cleanUrlEscaped.contains("histats") && !cleanUrlEscaped.contains("doubleclick") &&
                            !cleanUrlEscaped.contains("adskeeper")) {
                            try {
                                com.lagradost.cloudstream3.utils.loadExtractor(cleanUrlEscaped, data, subtitleCallback, callback)
                            } catch (e: Exception) {
                                android.util.Log.e("Extractor", "Standard loadExtractor failed for $cleanUrlEscaped: ${e.message}")
                            }
                        }
                    }
                }
            }
        }

        return true
    }
    
}

