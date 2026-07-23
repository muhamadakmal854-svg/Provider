package com.mts.layarotaku

import com.lagradost.cloudstream3.*

import com.lagradost.cloudstream3.utils.*

import org.jsoup.Jsoup

import org.jsoup.nodes.Element

import javax.crypto.Cipher

import javax.crypto.spec.SecretKeySpec

import javax.crypto.spec.IvParameterSpec

import java.security.MessageDigest

class LayarOtakuProvider : MainAPI() {



    override var mainUrl        = "https://www.xml-acronym-demystifier.org"

    override var name           = "LayarOtaku"

    override var lang           = "id"

    override val hasMainPage    = true

    override val supportedTypes = setOf(TvType.Anime)

    override val mainPage = mainPageOf(

        "top-10" to "Top 10 Anime Terpopuler Hari Ini",
        "rilisan" to "Rilisan Terbaru",
        "populer" to "Anime Populer"

    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        val pageUrl = request.data

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
        if (pageUrl == "top-10" || pageUrl == "rilisan" || pageUrl == "populer") {
            val doc = app.get(mainUrl, headers = mapOf("Referer" to mainUrl)).document
            val results = mutableListOf<SearchResponse>()
            when (pageUrl) {
                "top-10" -> {
                    doc.select("div.listupd.popularslider article.bs").forEach { element ->
                        val a = element.selectFirst("a") ?: return@forEach
                        val title = a.selectFirst(".tt h2")?.text()?.trim() ?: a.attr("title").trim()
                        val url = fixUrl(a.attr("href"))
                        val poster = a.selectFirst("img")?.posterUrl() ?: ""
                        results.add(newAnimeSearchResponse(title, url, TvType.Anime) {
                            this.posterUrl = poster
                        })
                    }
                }
                "rilisan" -> {
                    doc.select("div.listupd.normal article").forEach { element ->
                        val a = element.selectFirst("a.tip") ?: element.selectFirst("a") ?: return@forEach
                        val title = a.attr("title").trim().ifBlank { element.selectFirst("h2")?.text()?.trim() ?: "" }
                        val url = fixUrl(a.attr("href"))
                        val poster = a.selectFirst("img")?.posterUrl() ?: ""
                        results.add(newAnimeSearchResponse(title, url, TvType.Anime) {
                            this.posterUrl = poster
                        })
                    }
                }
                "populer" -> {
                    doc.select("div#wpop-items div.serieslist li").forEach { element ->
                        val a = element.selectFirst("a.series") ?: return@forEach
                        val title = a.text().trim().ifBlank { element.selectFirst("h4 a")?.text()?.trim() ?: "" }
                        val url = fixUrl(a.attr("href"))
                        val poster = a.selectFirst("img")?.posterUrl() ?: ""
                        results.add(newAnimeSearchResponse(title, url, TvType.Anime) {
                            this.posterUrl = poster
                        })
                    }
                }
            }
            return results
        }

        val doc = app.get(pageUrl, headers = mapOf(
            "Referer" to mainUrl,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        )).document
        return doc.select(".listupd .bsx, .listupd .bs, .bsx, .bs, article.bs, article, .card, div.card, article.item, .item, .movie-item, .post-item, div.module-item, div.ml-item, .box-item, .post, .entry, .film-poster-ahref").mapNotNull {
            val a = (if (it.tagName() == "a") it else it.selectFirst("a")) ?: return@mapNotNull null
            val href = a.attr("href").let { h -> if (h.startsWith("http")) h else "$mainUrl$h" }
            val title = a.attr("title").trim().ifBlank {
                a.selectFirst("h2, h3, h4")?.text()?.trim() ?: it.selectFirst(".title, .entry-title")?.text()?.trim() ?: a.text().trim()
            }
            if (title.isBlank()) return@mapNotNull null
            val poster = it.selectFirst("img")?.posterUrl() ?: ""
            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = poster
            }
        }.distinctBy { it.url }
    }
    

    

    override suspend fun load(url: String): LoadResponse? {

        val doc = app.get(url, headers = mapOf("Referer" to mainUrl)).document

        var currentDoc = doc

        var targetUrl = url

        // Parent redirection logic for episode pages

        val isEpisodePage = !url.contains("/anime/") && !url.contains("/series/") && !url.contains("/tvshows/") && !url.contains("/movies/")

        if (isEpisodePage) {

            val parentLink = doc.select("a[href]").map { it.attr("href") }.firstOrNull { href ->

                val h = href.lowercase()

                (h.contains("/anime/") && !h.endsWith("/anime/") && !h.endsWith("/anime")) ||

                (h.contains("/series/") && !h.endsWith("/series/") && !h.endsWith("/series")) ||

                (h.contains("/tvshows/") && !h.endsWith("/tvshows/") && !h.endsWith("/tvshows"))

            }

            if (!parentLink.isNullOrBlank()) {

                val resolved = if (parentLink.startsWith("http")) parentLink else {

                    val base = mainUrl.removeSuffix("/")

                    if (parentLink.startsWith("/")) "$base$parentLink" else "$base/$parentLink"

                }

                try {

                    val parentDoc = app.get(resolved, headers = mapOf("Referer" to url)).document

                    val newTitle = parentDoc.selectFirst(".sheader .data h1, h1.entry-title, .data h1, h1, .heading-name, .film-name")

                    if (newTitle != null) {

                        currentDoc = parentDoc

                        targetUrl = resolved

                    }

                } catch (_: Exception) {}

            }

        }

        val title = currentDoc.selectFirst(

            ".sheader .data h1, h1.entry-title, .data h1, h1, .heading-name, .film-name"

        )?.text()?.trim() ?: return null

        val poster = currentDoc.selectFirst(

            ".poster img, .sheader .poster img, .film-poster img, [class*=poster] img, " +

            ".entry-thumbnail img, .thumb img, img.wp-post-image, .cover img"

        )?.let { img ->

            listOf("data-src", "data-lazy-src", "data-lazy", "data-cfsrc", "src")

                .map { img.attr(it) }

                .firstOrNull { it.isNotBlank() && it.startsWith("http") }

        }

        val plot = currentDoc.selectFirst(

            ".description p, .wp-content p, .entry-content p, [itemprop=description], " +

            ".film-description, .synops p, .overview"

        )?.text()?.trim()

        val year = currentDoc.selectFirst(

            ".date, .extra .year, [itemprop=dateCreated], .film-stats span, [class*=year]"

        )?.text()?.filter { it.isDigit() }?.let {

            if (it.length >= 4) it.substring(0, 4).toIntOrNull() else null

        }

        val genres = currentDoc.select(

            ".sgeneros a, .genres a, .genre a, .film-genres a, [class*=genre] a, .categories a"

        ).map { it.text() }.filter { it.isNotBlank() }

        val isTv = targetUrl.contains("/tvshows/") || targetUrl.contains("/series/") ||

                   targetUrl.contains("/tv/") || targetUrl.contains("/season/") ||

                   targetUrl.contains("/anime/") ||

                   currentDoc.select(

                       ".episodes-list li, .episodios li, #seasons .se-c, " +

                       ".eplister li, .episodelist li, .clps li, #episodes li, " +

                       "#daftarepisode li, #daftarepisode, .epcheck li"

                   ).isNotEmpty()

        return if (isTv) {

            val eps = currentDoc.select(

                ".episodes-list li a, .episodios li a, #episodes .episodiotitle a, " +

                ".eplister ul li a, .episodelist ul li a, .ep-list li a, .clps li a, " +

                "[class*=episode-list] li a, [class*=episode] a[href], " +

                "#daftarepisode li a, #daftarepisode a, .epcheck li a, [id*=episode] li a, [id*=episode] a"

            ).mapIndexed { i, a ->

                newEpisode(fixUrl(a.attr("href"))) {

                    this.name = a.selectFirst(".epl-title, .epl-num, span, .episode-title")

                        ?.text()?.trim() ?: a.text().trim()

                    this.episode = i + 1

                }

            }.filter { it.data.isNotBlank() }.distinctBy { it.data }

            newTvSeriesLoadResponse(title, targetUrl, TvType.TvSeries, eps) {

                this.posterUrl = poster; this.plot = plot; this.year = year; this.tags = genres

            }

        } else {

            newMovieLoadResponse(title, targetUrl, TvType.Movie, targetUrl) {

                this.posterUrl = poster; this.plot = plot; this.year = year; this.tags = genres

            }

        }

    }

    
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = mapOf("Referer" to mainUrl)).document
        
        doc.select("button[data-value]").forEach { button ->
            val name = button.text().trim()
            val base64Value = button.attr("data-value")
            if (base64Value.isNotBlank()) {
                try {
                    val decodedHtml = String(android.util.Base64.decode(base64Value, android.util.Base64.DEFAULT), Charsets.UTF_8)
                    val srcRegex = """src="([^"]+)"""".toRegex()
                    val match = srcRegex.find(decodedHtml)
                    val iframeSrc = match?.groupValues?.get(1)
                    if (!iframeSrc.isNullOrBlank()) {
                        if (name.equals("Turbovid", ignoreCase = true)) {
                            val directUrl = if (iframeSrc.contains("?")) "$iframeSrc&hlsok=1" else "$iframeSrc?hlsok=1"
                            callback(
                                newExtractorLink(
                                    name,
                                    name,
                                    directUrl,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.referer = "https://hls-sniper.layarwibu.com/"
                                }
                            )
                        } else {
                            val playerMarker = "/player/"
                            if (iframeSrc.contains(playerMarker)) {
                                val b64PartEnc = iframeSrc.substringAfter(playerMarker)
                                val b64Part = java.net.URLDecoder.decode(b64PartEnc, "UTF-8")
                                val decodedUrl = String(android.util.Base64.decode(b64Part, android.util.Base64.DEFAULT), Charsets.UTF_8)
                                if (decodedUrl.startsWith("http")) {
                                    callback(
                                        newExtractorLink(
                                            name,
                                            name,
                                            decodedUrl,
                                            type = ExtractorLinkType.M3U8
                                        ) {
                                            this.referer = "https://hls-sniper.layarwibu.com/"
                                        }
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
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

