package com.mts.kuronime

import com.lagradost.cloudstream3.*

import com.lagradost.cloudstream3.utils.*

import org.jsoup.Jsoup

import org.jsoup.nodes.Element

import javax.crypto.Cipher

import javax.crypto.spec.SecretKeySpec

import javax.crypto.spec.IvParameterSpec

import java.security.MessageDigest

class KuronimeProvider : MainAPI() {



    override var mainUrl        = "https://kuronime.sbs"

    override var name           = "Kuronime"

    override var lang           = "id"

    override val hasMainPage    = true

    override val supportedTypes = setOf(TvType.TvSeries, TvType.Anime, TvType.OVA)

    override val mainPage = mainPageOf(

        "" to "Terbaru",
        "anime?list" to "Anime Lists",
        "movies" to "Movies",
        "ongoing-anime" to "Ongoing",
        "popular-anime" to "Popular"

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

    private suspend fun scrapeList(pageUrl: String): List<SearchResponse> {

        val doc = app.get(pageUrl, headers = mapOf(

            "Referer" to mainUrl,

            "Accept"  to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"

        )).document

        return doc.select(".listupd .bsx, .listupd .bs, .bsx, .bs, article.bs, article, .animpost, article.animpost, .animepost, article.animepost, article.item, .film-poster, .item-anime, .epbox, .out-thumb, .milist, .post-item, .hentry").mapNotNull {

            val a     = (if (it.tagName() == "a") it else it.selectFirst("a")) ?: return@mapNotNull null

            val href  = a.attr("href").let { h -> if (h.startsWith("http")) h else "$mainUrl$h" }

            val img   = it.selectFirst("img") ?: it.selectFirst("[data-src], [data-lazy-src], [data-original]")

            val title = it.selectFirst(".tt, .ttl, h2, .bigor .tt, .mdl-animepost .info .name, .film-name, h3")

                ?.text()?.trim()

                ?: a.attr("title").trim().ifEmpty { img?.attr("alt")?.trim() ?: "" }.ifEmpty { img?.attr("title")?.trim() ?: "" }.ifEmpty { a.text().trim() }

            if (title.isBlank()) return@mapNotNull null

            var src   = img?.posterUrl() ?: ""

            if (src.isEmpty()) {

                src = it.posterUrl()

            }

            if (src.isEmpty()) {

                var foundBg = ""

                it.select("[style*=background], [style*=url]").forEach { el ->

                    val url = el.posterUrl()

                    if (url.isNotEmpty()) {

                        foundBg = url

                        return@forEach

                    }

                }

                src = foundBg

            }

            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = src }

        }.distinctBy { it.url }

    }

    

    override suspend fun load(url: String): LoadResponse? {

        val doc = app.get(url, headers = mapOf("Referer" to mainUrl)).document

        var currentDoc = doc

        var targetUrl = url

        // Parent redirection logic for episode pages

        val isEpisodePage = url.contains("/eps/") || url.contains("/episode/") || (!url.contains("/anime/") && !url.contains("/series/") && !url.contains("/tvshows/") && !url.contains("/tv/"))

        if (isEpisodePage) {

            val parentLink = doc.select("a[href]").map { it.attr("href") }.firstOrNull { href ->

                val h = href.lowercase()

                h.contains("/tv/") || h.contains("/anime/") || h.contains("/series/") || h.contains("/tvshows/")

            }

            if (!parentLink.isNullOrBlank()) {

                val resolved = if (parentLink.startsWith("http")) parentLink else {

                    val base = mainUrl.removeSuffix("/")

                    if (parentLink.startsWith("/")) "$base$parentLink" else "$base/$parentLink"

                }

                try {

                    val parentDoc = app.get(resolved, headers = mapOf("Referer" to url)).document

                    val newTitle = parentDoc.selectFirst("h1.entry-title, .thumb img, .film-poster img, .animposx .entry-title")

                    if (newTitle != null) {

                        currentDoc = parentDoc

                        targetUrl = resolved

                    }

                } catch (_: Exception) {}

            }

        }

        val title  = currentDoc.selectFirst("h1.entry-title, .thumb img, .film-poster img, .animposx .entry-title")?.let {

            if (it.tagName() == "img") it.attr("alt").trim() else it.text().trim()

        }?.trim() ?: return null

        val poster = currentDoc.selectFirst(".thumb img, .seriesthumb img, .film-poster img, .entry-thumb img, .cover img, img.wp-post-image, .poster img")

            ?.let { img ->

                listOf("data-src","data-lazy-src","data-lazy","data-cfsrc","data-original","src")

                    .map { img.attr(it) }

                    .firstOrNull { it.isNotBlank() && it.startsWith("http") }

            }

        val plot   = currentDoc.selectFirst(".entry-content p, .synp .deskripsi, [itemprop=description], .film-description p")

            ?.text()?.trim()

        val genres = currentDoc.select(".genxed a, .genre-info a, .info-content .spe a[href*=genre], .film-genres a")

            .map { it.text() }

        val eps = currentDoc.select(

            ".eplister ul li a, .episodelist ul li a, .clps li a, .ep-list li a, " +

            "#daftarepisode li a, #daftarepisode a, .epcheck li a, [id*=episode] li a, [id*=episode] a, " +

            ".gmr-listseries a, .list-table a"

        ).mapNotNull { a ->

            val epUrl = a.attr("href")

            val epTitle = a.selectFirst(".epl-title, .epl-num, span")?.text()?.trim()

                ?: a.text().trim()

            if (epUrl.isNotBlank() && 

                !epUrl.contains("/tv/") && 

                !epUrl.contains("/series/") && 

                !epUrl.contains("/anime/") && 

                epUrl.substringBefore("?") != targetUrl.substringBefore("?")) {

                newEpisode(epUrl) { 

                    this.name = epTitle

                    this.episode = epTitle.filter { it.isDigit() }.toIntOrNull()

                }

            } else null

        }

        val finalEps = eps.distinctBy { it.data }.sortedBy { it.episode ?: 0 }

        return if (finalEps.isNotEmpty()) {

            newTvSeriesLoadResponse(title, targetUrl, TvType.TvSeries, finalEps) {

                this.posterUrl = poster; this.plot = plot; this.tags = genres

            }

        } else {

            newMovieLoadResponse(title, targetUrl, TvType.Movie, targetUrl) {

                this.posterUrl = poster; this.plot = plot; this.tags = genres

            }

        }

    }

    
    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun decryptCryptoJS(encryptedJson: String, password: String): String {
        try {
            val obj = org.json.JSONObject(encryptedJson)
            val ciphertext = android.util.Base64.decode(obj.getString("ct"), android.util.Base64.DEFAULT)
            val salt = hexToBytes(obj.getString("s"))
            val passBytes = password.toByteArray(Charsets.UTF_8)
            
            val md = java.security.MessageDigest.getInstance("MD5")
            val derived = ByteArray(48)
            var prev = ByteArray(0)
            var count = 0
            while (count < 48) {
                md.reset()
                md.update(prev)
                md.update(passBytes)
                md.update(salt)
                prev = md.digest()
                val limit = minOf(prev.size, 48 - count)
                System.arraycopy(prev, 0, derived, count, limit)
                count += limit
            }
            
            val key = derived.copyOfRange(0, 32)
            val iv = if (obj.has("iv")) hexToBytes(obj.getString("iv")) else derived.copyOfRange(32, 48)
            
            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.IvParameterSpec(iv))
            return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            return ""
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = mapOf("Referer" to mainUrl)).document
        val html = doc.html()
        
        val tokenRegex = """var\s+_0xa100d42aa\s*=\s*['"]([^'"]+)['"]""".toRegex()
        val tokenMatch = tokenRegex.find(html)
        val token = tokenMatch?.groupValues?.get(1) ?: return false
        
        val responseStr = app.post(
            "https://animeku.org/api/v9/sources",
            json = mapOf("id" to token),
            headers = mapOf(
                "Referer" to data,
                "Origin" to "https://kuronime.sbs",
                "Content-Type" to "application/json"
            )
        ).text
        
        val res = org.json.JSONObject(responseStr)
        val key = "3&!Z0M,VIZ;dZW=="
        
        if (res.has("src") && !res.isNull("src")) {
            try {
                val encSrc = res.getString("src")
                val decodedSrc = String(android.util.Base64.decode(encSrc, android.util.Base64.DEFAULT), Charsets.UTF_8)
                val decryptedSrc = decryptCryptoJS(decodedSrc, key)
                if (decryptedSrc.isNotBlank()) {
                    val srcObj = org.json.JSONObject(decryptedSrc)
                    val directUrl = srcObj.getString("src")
                    callback(
                        newExtractorLink(
                            "VIP PLAYER 1",
                            "VIP PLAYER 1 1080p",
                            directUrl,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = "https://player.animeku.org/"
                            this.quality = getQualityFromName("1080p")
                        }
                    )
                }
            } catch (_: Exception) {}
        }
        
        if (res.has("src_sd") && !res.isNull("src_sd")) {
            try {
                val encSrcSd = res.getString("src_sd")
                val decodedSrcSd = String(android.util.Base64.decode(encSrcSd, android.util.Base64.DEFAULT), Charsets.UTF_8)
                val decryptedSrcSd = decryptCryptoJS(decodedSrcSd, key)
                if (decryptedSrcSd.isNotBlank()) {
                    val srcObj = org.json.JSONObject(decryptedSrcSd)
                    val directUrl = srcObj.getString("src")
                    callback(
                        newExtractorLink(
                            "VIP PLAYER 1",
                            "VIP PLAYER 1 480p",
                            directUrl,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = "https://player.animeku.org/"
                            this.quality = getQualityFromName("480p")
                        }
                    )
                }
            } catch (_: Exception) {}
        }
        
        if (res.has("blog") && !res.isNull("blog")) {
            try {
                val blogId = res.getString("blog")
                val player2Url = "https://blog.animeku.org/player2.php?id=$blogId"
                val player2Html = app.get(
                    player2Url,
                    headers = mapOf(
                        "Referer" to data,
                        "Origin" to "https://kuronime.sbs"
                    )
                ).text
                
                val arrRegex = """_ada02020i230i2\s*=\s*'([^']+)'""".toRegex()
                val arrMatch = arrRegex.find(player2Html)
                if (arrMatch != null) {
                    val arrStr = arrMatch.groupValues[1]
                    val parts = arrStr.split(',').filter { it.isNotBlank() }.reversed()
                    val joined = parts.joinToString("")
                    val decryptedLokalan = String(android.util.Base64.decode(joined, android.util.Base64.DEFAULT), Charsets.UTF_8)
                    val bloggerArray = org.json.JSONArray(decryptedLokalan)
                    for (j in 0 until bloggerArray.length()) {
                        val bObj = bloggerArray.getJSONObject(j)
                        val fileUrl = bObj.getString("file")
                        val label = bObj.getString("label")
                        val quality = if (label == "HD") getQualityFromName("720p") else getQualityFromName("360p")
                        callback(
                            newExtractorLink(
                                "LOKALAN",
                                "LOKALAN $label",
                                fileUrl,
                                type = ExtractorLinkType.VIDEO
                            ) {
                                this.quality = quality
                            }
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        
        if (res.has("mirror") && !res.isNull("mirror")) {
            try {
                val encMirror = res.getString("mirror")
                val decodedMirror = String(android.util.Base64.decode(encMirror, android.util.Base64.DEFAULT), Charsets.UTF_8)
                val decryptedMirror = decryptCryptoJS(decodedMirror, key)
                if (decryptedMirror.isNotBlank()) {
                    val mirrorObj = org.json.JSONObject(decryptedMirror)
                    if (mirrorObj.has("embed")) {
                        val embedObj = mirrorObj.getJSONObject("embed")
                        val qualities = embedObj.keys()
                        while (qualities.hasNext()) {
                            val q = qualities.next()
                            val servers = embedObj.getJSONObject(q)
                            val serverNames = servers.keys()
                            while (serverNames.hasNext()) {
                                val s = serverNames.next()
                                if (!servers.isNull(s)) {
                                    val embedUrl = servers.getString(s)
                                    if (embedUrl.isNotBlank()) {
                                        loadExtractor(embedUrl, data, subtitleCallback, callback)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        
        if (res.has("filelions") && !res.isNull("filelions")) {
            try {
                val flLink = res.getString("filelions")
                if (flLink.isNotBlank()) {
                    loadExtractor(flLink, data, subtitleCallback, callback)
                }
            } catch (_: Exception) {}
        }
        
        return true
    }
    

}

class AbyssExtractor : ExtractorApi() {

    override var name = "Abyss"

    override var mainUrl = "https://abyssplayer.com"

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

            val domainsObj = if (mp4.has("domains")) mp4.optJSONObject("domains") else if (mediaJson.has("domains")) mediaJson.optJSONObject("domains") else null

            val domainsArr = if (mp4.has("domains")) mp4.optJSONArray("domains") else if (mediaJson.has("domains")) mediaJson.optJSONArray("domains") else null

            for (i in 0 until sources.length()) {

                val src = sources.getJSONObject(i)

                val size = src.getLong("size")

                val resId = src.getInt("res_id")

                val label = src.getString("label")

                val sub = src.getString("sub")

                var domain = ""

                if (domainsObj != null) {

                    domain = domainsObj.optString(sub, "")

                } else if (domainsArr != null) {

                    for (j in 0 until domainsArr.length()) {

                        val dStr = domainsArr.getString(j)

                        if (dStr.startsWith(sub)) {

                            domain = dStr

                            break

                        }

                    }

                }

                if (domain.isBlank()) {

                    domain = "$sub.sssrr.org"

                }

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

                val cleanPath = b64Twice.replace("=", "").replace("\n", "").replace("\r", "")

                val finalStreamUrl = "https://$domain/sora/$size/$cleanPath"

                callback(

                    newExtractorLink(

                        source = name,

                        name = "$name - $label",

                        url = finalStreamUrl,

                        type = ExtractorLinkType.VIDEO

                    ) {

                        this.referer = cleanUrl

                        this.quality = when (label.lowercase()) {

                            "360p" -> Qualities.P360.value

                            "480p" -> Qualities.P480.value

                            "720p" -> Qualities.P720.value

                            "1080p" -> Qualities.P1080.value

                            else -> Qualities.Unknown.value

                        }

                    }

                )

            }

        } catch (e: Exception) {

            e.printStackTrace()

        }

    }

}

class SeekplayerVip : ExtractorApi() {

    override var name = "SeekPlayer"

    override var mainUrl = "https://drakorku.seekplayer.vip"

    override val requiresReferer = true

    private fun decryptAesCbc(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {

        val spec = javax.crypto.spec.SecretKeySpec(key, "AES")

        val parameterSpec = javax.crypto.spec.IvParameterSpec(iv)

        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding")

        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, spec, parameterSpec)

        val decrypted = cipher.doFinal(ciphertext)

        if (decrypted.isEmpty()) return decrypted

        val pad = decrypted[decrypted.size - 1].toInt()

        if (pad in 1..16) {

            return decrypted.copyOfRange(0, decrypted.size - pad)

        }

        return decrypted

    }

    override suspend fun getUrl(

        url: String,

        referer: String?,

        subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,

        callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit

    ) {

        try {

            val id = if (url.contains("#")) {

                url.substringAfter("#").substringBefore("?").substringBefore("&")

            } else if (url.contains("id=")) {

                url.substringAfter("id=").substringBefore("&")

            } else {

                url.substringAfter("/video/").substringBefore("?").substringBefore("&")

            }

            if (id.isEmpty()) return

            val domain = try { java.net.URL(url).host } catch (_: Exception) { "drakorku.seekplayer.vip" }

            val apiUrl = "https://$domain/api/v1/video?id=$id"

            

            val response = app.get(apiUrl, headers = mapOf("Referer" to url)).text

            val encBytes = response.trim().chunked(2).map { it.toInt(16).toByte() }.toByteArray()

            val key = "kiemtienmua911ca".toByteArray(Charsets.UTF_8)

            val iv = "1234567890oiuytr".toByteArray(Charsets.UTF_8)

            val decryptedBytes = decryptAesCbc(encBytes, key, iv)

            val decryptedStr = String(decryptedBytes, Charsets.UTF_8)

            val json = org.json.JSONObject(decryptedStr)

            val title = if (json.has("title")) json.getString("title") else "SeekPlayer"

            

            if (json.has("source")) {

                val sourceUrl = json.getString("source")

                if (sourceUrl.isNotBlank()) {

                    callback(

                        newExtractorLink(

                            source = name,

                            name = "$name - $title",

                            url = sourceUrl,

                            type = ExtractorLinkType.M3U8

                        ) {

                            this.referer = "https://$domain/"

                        }

                    )

                }

            }

            if (json.has("cfNative")) {

                val cfUrl = json.getString("cfNative")

                if (cfUrl.isNotBlank()) {

                    callback(

                        newExtractorLink(

                            source = name,

                            name = "$name - $title (Cloudflare)",

                            url = cfUrl,

                            type = ExtractorLinkType.M3U8

                        ) {

                            this.referer = "https://$domain/"

                        }

                    )

                }

            }

        } catch (e: Exception) {

            e.printStackTrace()

        }

    }

}

class TamilEmbed : ExtractorApi() {

    override var name = "TamilEmbed"

    override var mainUrl = "https://tamilembed.lol"

    override val requiresReferer = true

    override suspend fun getUrl(

        url: String,

        referer: String?,

        subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,

        callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit

    ) {

        try {

            val doc = app.get(url, headers = mapOf("Referer" to (referer ?: "")), timeout = 15).document

            val bloggerIfr = doc.selectFirst("iframe[src*=blogger.com]")

            val bloggerUrl = bloggerIfr?.attr("src")?.trim()

            if (!bloggerUrl.isNullOrBlank()) {

                val cleanUrl = if (bloggerUrl.startsWith("//")) "https:$bloggerUrl" else bloggerUrl

                com.lagradost.cloudstream3.utils.loadExtractor(cleanUrl, url, subtitleCallback, callback)

            }

        } catch (e: Exception) {

            e.printStackTrace()

        }

    }

}

