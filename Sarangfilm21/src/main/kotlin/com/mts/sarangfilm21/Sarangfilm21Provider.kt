package com.mts.sarangfilm21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest

class Sarangfilm21Provider : MainAPI() {

    override var mainUrl        = "https://sarangfilm.asia"
    override var name           = "SARANGFILM21"
    override var lang           = "id"
    override val hasMainPage    = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Anime, TvType.OVA, TvType.Movie)

    override val mainPage = mainPageOf(
        "" to "Home",
        "category/trending" to "FILM TRENDING",
        "tv" to "FILM SERIES"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path = request.data
        if (path.isEmpty()) {
            val doc = app.get(mainUrl, headers = mapOf("Referer" to mainUrl, "User-Agent" to USER_AGENT)).document
            val homePageList = mutableListOf<HomePageList>()
            
            // Section 1: FILM TRENDING
            val trendingDoc = doc.selectFirst(".home-widget:has(h3:contains(FILM TRENDING))")
            if (trendingDoc != null) {
                val items = trendingDoc.select(".gmr-item-modulepost").mapNotNull { it.toSearchResult() }
                if (items.isNotEmpty()) homePageList.add(HomePageList("FILM TRENDING", items, isHorizontalImages = false))
            }
            
            // Section 2: FILM SERIES
            val seriesDoc = doc.selectFirst(".home-widget:has(h3:contains(FILM SERIES))")
            if (seriesDoc != null) {
                val items = seriesDoc.select(".gmr-item-modulepost").mapNotNull { it.toSearchResult() }
                if (items.isNotEmpty()) homePageList.add(HomePageList("FILM SERIES", items, isHorizontalImages = false))
            }
            
            // Section 3: Rekomendasi Lain Sarangfilm21
            val recDoc = doc.selectFirst(".home-widget:has(h3:contains(Rekomendasi Lain))")
            if (recDoc != null) {
                val items = recDoc.select(".gmr-item-modulepost").mapNotNull { it.toSearchResult() }
                if (items.isNotEmpty()) homePageList.add(HomePageList("Rekomendasi Lain Sarangfilm21", items, isHorizontalImages = false))
            }
            
            // Section 4: UPDATE TERBARU SARANGFILM21
            val terbaruDoc = doc.selectFirst(".home-widget:has(h3:contains(UPDATE TERBARU))") ?: doc.selectFirst("#gmr-main, .gmr-main")
            if (terbaruDoc != null) {
                val items = terbaruDoc.select("article, .gmr-item-modulepost, .gmr-item-archivepost").mapNotNull { it.toSearchResult() }
                if (items.isNotEmpty()) homePageList.add(HomePageList("UPDATE TERBARU SARANGFILM21", items, isHorizontalImages = false))
            }
            
            // Section 5: Film/Serial Popular
            val popularDoc = doc.selectFirst(".idmuvi-mostview-widget")
            if (popularDoc != null) {
                val items = popularDoc.select("li, .gmr-item-modulepost, .gmr-mostview-item").mapNotNull { it.toSearchResult() }
                if (items.isNotEmpty()) homePageList.add(HomePageList("Film/Serial Popular", items, isHorizontalImages = false))
            }
            
            return newHomePageResponse(homePageList, hasNext = false)
        } else {
            val cleanPath = path.removePrefix("/").removeSuffix("/")
            val pageUrl = if (path.startsWith("http")) {
                path + if (page > 1) "page/$page/" else ""
            } else {
                val parts = cleanPath.split("?")
                val basePath = parts[0].removeSuffix("/")
                val query = if (parts.size > 1) "?" + parts[1] else ""
                val pagedPath = if (page > 1) "$basePath/page/$page/" else "$basePath/"
                "$mainUrl/$pagedPath$query"
            }
            val items = scrapeList(pageUrl)
            return newHomePageResponse(
                list = HomePageList(
                    name = request.name,
                    list = items,
                    isHorizontalImages = false
                ),
                hasNext = items.isNotEmpty()
            )
        }
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

    private fun Element.toSearchResult(): SearchResponse? {
        val a     = (if (this.tagName() == "a") this else this.selectFirst("a")) ?: return null
        val href  = a.attr("href").let { h -> if (h.startsWith("http")) h else "$mainUrl$h" }
        val img   = this.selectFirst("img") ?: this.selectFirst("[data-src], [data-lazy-src], [data-original]")
        val title = this.selectFirst(".tt, .ttl, h2, .bigor .tt, .mdl-animepost .info .name, .film-name, h3")
            ?.text()?.trim()
            ?: a.attr("title").trim().ifEmpty { img?.attr("alt")?.trim() ?: "" }.ifEmpty { img?.attr("title")?.trim() ?: "" }.ifEmpty { a.text().trim() }
        if (title.isBlank()) return null
        var src   = img?.posterUrl() ?: ""
        if (src.isEmpty()) {
            src = this.posterUrl()
        }
        if (src.isEmpty()) {
            var foundBg = ""
            this.select("[style*=background], [style*=url]").forEach { el ->
                val url = el.posterUrl()
                if (url.isNotEmpty()) {
                    foundBg = url
                    return@forEach
                }
            }
            src = foundBg
        }
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = src }
    }

    private suspend fun scrapeList(pageUrl: String): List<SearchResponse> {
        val doc = app.get(pageUrl, headers = mapOf(
            "Referer" to mainUrl,
            "User-Agent" to USER_AGENT,
            "Accept"  to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        )).document
        return doc.select(".gmr-item-modulepost, .gmr-item-archivepost, .gmr-item-module, .gmr-item-archive, .gmr-item, .listupd .bsx, .listupd .bs, .bsx, .bs, article.bs, .animpost, article.animpost, .animepost, article.animepost, article.item, .film-poster, .item-anime, .epbox, .out-thumb, .milist, .post-item, .hentry").mapNotNull {
            it.toSearchResult()
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("Referer" to mainUrl, "User-Agent" to USER_AGENT)).document
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
            if (epUrl.isNotBlank()) newEpisode(epUrl) { this.name = epTitle } else null
        }.reversed()
        return if (eps.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, eps) {
                this.posterUrl = poster; this.plot = plot; this.tags = genres
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster; this.plot = plot; this.tags = genres
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = mapOf("Referer" to mainUrl, "User-Agent" to USER_AGENT)).document
        val targets = mutableListOf<String>()

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

        // 1. Direct video/source elements
        doc.select("source[src], video source[src], video[src]").forEach { el ->
            val src = el.attr("src").trim()
            val finalUrl = fixUrl(src)
            if (finalUrl.isNotEmpty()) targets.add(finalUrl)
        }

        // 2. Direct iframes
        doc.select("iframe[src], iframe[data-src], iframe[data-litespeed-src], iframe[data-lazy-src], iframe.metaframe").forEach { iframe ->
            val src = iframe.attr("src")
                .ifEmpty { iframe.attr("data-src") }
                .ifEmpty { iframe.attr("data-litespeed-src") }
                .ifEmpty { iframe.attr("data-lazy-src") }
                .trim()
            val finalUrl = fixUrl(src)
            if (finalUrl.isNotEmpty()) targets.add(finalUrl)
        }

        // 3. Option elements / Dropdowns
        doc.select("select option, .mirror option, .server option, select.mirror option, select.server option, .mobius option").forEach { el ->
            listOf("value", "data-src", "data-link", "data-embed", "data-video", "data-url", "data-id").forEach { attr ->
                val v = el.attr(attr).trim()
                val finalUrl = fixUrl(v)
                if (finalUrl.isNotEmpty()) targets.add(finalUrl)
            }
        }

        // 4. Clickable elements, links, buttons
        doc.select("a, button, li, div, span, .opt-sp, .opt-single, .mirror-item, div#downloadb li, div.download li").forEach { el ->
            val href = el.attr("href").trim()
            if (href.isNotBlank() && !href.startsWith("#") && !href.contains("javascript", true)) {
                val finalUrl = fixUrl(href)
                if (finalUrl.isNotEmpty()) targets.add(finalUrl)
            }
            listOf("data-src", "data-link", "data-embed", "data-video", "data-id", "data-url", "data-content").forEach { attr ->
                val v = el.attr(attr).trim()
                val finalUrl = fixUrl(v)
                if (finalUrl.isNotEmpty() && !v.contains("data:image")) {
                    targets.add(finalUrl)
                }
            }
        }

        // 5. Muvipro dynamic Ajax server loaders (Server 1 to 5)
        val postDiv = doc.selectFirst("#muvipro_player_content_id")
        val postId = postDiv?.attr("data-id") ?: ""
        if (postId.isNotEmpty()) {
            listOf("p1", "p2", "p3", "p4", "p5").forEach { tab ->
                try {
                    val ajaxResponse = app.post(
                        "https://sarangfilm.asia/wp-admin/admin-ajax.php",
                        data = mapOf(
                            "action" to "muvipro_player_content",
                            "tab" to tab,
                            "post_id" to postId
                        ),
                        headers = mapOf(
                            "User-Agent" to USER_AGENT,
                            "Referer" to data,
                            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
                        )
                    ).text
                    val subDoc = Jsoup.parse(ajaxResponse)
                    val iframe = subDoc.selectFirst("iframe")
                    val src = iframe?.attr("src")?.ifEmpty { iframe.attr("data-src") } ?: ""
                    if (src.isNotEmpty()) {
                        val finalUrl = fixUrl(src)
                        if (finalUrl.isNotEmpty()) targets.add(finalUrl)
                    }
                } catch (_: Exception) {}
            }
        }

        // 6. Process all targets
        targets.distinct().forEach { raw ->
            val finalUrl = raw.trim()
            if (finalUrl.startsWith("http")) {
                val cleanUrl = finalUrl.replace(92.toChar().toString(), "")
                val isStreamWish = listOf("streamwish", "wish", "hglink", "hgcloud", "gendeng", "fkupon", "desacinta", "layarotaku", "layarwibu", "nekonime", "layarecchi", "subsource", "doimg", "anchurl", "certaker", "listeamed", "bigwarp", "cloudatacdn", "push-sdk", "gradehg", "hgplus", "streamplay", "awish", "wishembed", "fastdl", "p2pstream", "morencius", "embedpyrox").any { cleanUrl.contains(it, true) }
                val isDood = listOf("dood", "dsvplay", "doodcdn", "vide0", "ds2play", "ds2video", "doodstream", "doodla").any { cleanUrl.contains(it, true) }
                val isVoe = cleanUrl.contains("voe.sx", true) || cleanUrl.contains("voe", true)
                val isStreamtape = cleanUrl.contains("streamtape", true)
                val isFilemoon = cleanUrl.contains("filemoon", true)
                val isMp4Upload = cleanUrl.contains("mp4upload", true)
                val isAbyss = listOf("abyssplayer.com", "abyss.to", "abysscdn.com", "iamcdn.net", "sssrr").any { cleanUrl.contains(it, true) }
                
                when {
                    isAbyss -> {
                        try {
                            AbyssExtractor().getUrl(cleanUrl, data, subtitleCallback, callback)
                        } catch (_: Exception) {}
                    }
                    isStreamWish -> {
                        try {
                            com.lagradost.cloudstream3.extractors.StreamWishExtractor().getUrl(cleanUrl, data, subtitleCallback, callback)
                        } catch (_: Exception) {}
                    }
                    isDood -> {
                        try {
                            com.lagradost.cloudstream3.extractors.DoodLaExtractor().getUrl(cleanUrl, data, subtitleCallback, callback)
                        } catch (_: Exception) {}
                    }
                    isVoe -> {
                        try {
                            com.lagradost.cloudstream3.extractors.Voe().getUrl(cleanUrl, data, subtitleCallback, callback)
                        } catch (_: Exception) {}
                    }
                    isStreamtape -> {
                        try {
                            com.lagradost.cloudstream3.extractors.StreamTape().getUrl(cleanUrl, data, subtitleCallback, callback)
                        } catch (_: Exception) {}
                    }
                    isFilemoon -> {
                        try {
                            com.lagradost.cloudstream3.extractors.FileMoon().getUrl(cleanUrl, data, subtitleCallback, callback)
                        } catch (_: Exception) {}
                    }
                    isMp4Upload -> {
                        try {
                            com.lagradost.cloudstream3.extractors.Mp4Upload().getUrl(cleanUrl, data, subtitleCallback, callback)
                        } catch (_: Exception) {}
                    }
                    else -> {
                        try {
                            loadExtractor(cleanUrl, data, subtitleCallback, callback)
                        } catch (_: Exception) {}
                    }
                }
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
            val base64Str = Regex("const datas\s*=\s*"([^"]+)"").find(pageHtml)?.groupValues?.get(1) ?: return
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
