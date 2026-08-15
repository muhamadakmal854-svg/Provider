package com.mtsflix.animekhor

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.json.JSONObject
import org.json.JSONArray
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

class AnimeKhorProvider : MainAPI() {
    companion object {
        var context: android.content.Context? = null
    }
    override var mainUrl = "https://animekhor.org"
    override var name = "AnimeKhor"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime)

    override val mainPage = mainPageOf(
        "page/%d/" to "Rilisan Terbaru",
        "donghua-series/page/%d/" to "Donghua Series",
        "comic-series/page/%d/" to "Comic Series",
        "a-z-lists/page/%d/" to "A-Z Lists",
        "anime/page/%d/?status=&type=&order=" to "Filter Search"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (request.data.contains("%d")) {
            "$mainUrl/${request.data.format(page)}"
        } else {
            "$mainUrl/${request.data}"
        }

        val document = app.get(targetUrl).document
        val home = document.select("div.listupd article, div.bsx, article.bs, div.bs, .listupd .bsx, .item, article")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = home.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = this.selectFirst("a") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        var title = a.attr("title").trim()
        if (title.isBlank()) {
            title = this.selectFirst("div.title, div.tt, h2, h3, .entry-title")?.text()?.trim().orEmpty()
        }
        if (title.isBlank()) {
            title = a.text().trim()
        }
        if (title.isBlank()) return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 1..3) {
            val document = app.get("$mainUrl/page/$i/?s=$query").document
            val results = document.select("div.listupd article, .item, article").mapNotNull { it.toSearchResult() }
            if (results.isEmpty()) break
            searchResponse.addAll(results)
        }
        return searchResponse.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val cleanUrl = fixUrl(url)
        val document = app.get(cleanUrl).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim().toString()
        val poster = document.selectFirst("img.wp-post-image, div.ime > img, .thumb img")
            ?.attr("src")?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
        val description = document.selectFirst("div.entry-content, .synopse p, .entry-content p")?.text()?.trim()

        val genres = document.select(".genres a, .genre a, a[href*='/genres/'], a[href*='/genre/'], .gnr a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val episodeList = document.select(".eplister li, .eplist li, ul.clstyle li")
        val hasPlayer = document.selectFirst("#pembed, .player-embed, #embed_holder") != null

        if (episodeList.isEmpty() && hasPlayer) {
            return newMovieLoadResponse(title, cleanUrl, TvType.Anime, cleanUrl) {
                this.posterUrl = fixUrlNull(poster)
                this.plot = description
                this.tags = genres
            }
        }

        val isMovie = document.selectFirst(".spe")?.text().orEmpty().contains("Movie", true) || title.contains("Movie", true)
        return if (isMovie) {
            val movieHref = document.selectFirst(".eplister li > a")?.attr("href")?.let { fixUrl(it) } ?: cleanUrl
            newMovieLoadResponse(title, movieHref, TvType.Movie, movieHref) {
                this.posterUrl = fixUrlNull(poster)
                this.plot = description
                this.tags = genres
            }
        } else {
            val episodes = episodeList.map { ep ->
                val link = fixUrl(ep.selectFirst("a")?.attr("href").orEmpty())
                val epTitle = ep.selectFirst(".epl-title")?.text()?.trim().orEmpty()
                val epSub = ep.selectFirst(".epl-sub span")?.text()?.trim().orEmpty()
                val epDate = ep.selectFirst(".epl-date")?.text()?.trim().orEmpty()
                val cleanTitle = epTitle
                    .replace(Regex("""Subtitle\s*Indonesia""", RegexOption.IGNORE_CASE), "")
                    .trim()
                val epName = if (cleanTitle.isNotBlank()) "$cleanTitle $epSub".trim() else "Episode"
                val desc = if (epDate.isNotEmpty()) "Rilis: $epDate" else null
                newEpisode(link) {
                    this.name = epName
                    this.posterUrl = fixUrlNull(poster)
                    this.description = desc
                }
            }.reversed()

            newTvSeriesLoadResponse(title, cleanUrl, TvType.Anime, episodes) {
                this.posterUrl = fixUrlNull(poster)
                this.plot = description
                this.tags = genres
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(fixUrl(data)).document
        val streamUrls = mutableSetOf<String>()

        val defaultIframeSrc = document.selectFirst("#pembed iframe, .player-embed iframe, #embed_holder iframe")?.attr("src")
        if (!defaultIframeSrc.isNullOrBlank()) {
            val abs = when {
                defaultIframeSrc.startsWith("http") -> defaultIframeSrc
                defaultIframeSrc.startsWith("//")   -> "https:$defaultIframeSrc"
                defaultIframeSrc.startsWith("/")    -> "$mainUrl$defaultIframeSrc"
                else -> null
            }
            if (abs != null) streamUrls.add(abs)
        }

        document.select(".mobius option, select.mirror option, select option[value], .mob-mirror option[value]").forEach { opt ->
            val value = opt.attr("value").trim()
            if (value.isBlank()) return@forEach
            when {
                value.startsWith("http") -> streamUrls.add(value)
                value.startsWith("//")   -> streamUrls.add("https:$value")
                else -> try {
                    val decoded = base64Decode(value)
                    val sub = Jsoup.parse(decoded)
                    val src = sub.selectFirst("iframe[src], [src]")?.attr("src") ?: return@forEach
                    val abs = when {
                        src.startsWith("http") -> src
                        src.startsWith("//")   -> "https:$src"
                        src.startsWith("/")    -> "$mainUrl$src"
                        else -> return@forEach
                    }
                    streamUrls.add(abs)
                } catch (_: Exception) {}
            }
        }

        var count = 0
        streamUrls.forEach { streamUrl ->
            try {
                // 1. Dailymotion
                val isDailymotion = streamUrl.contains("dailymotion.com") || streamUrl.contains("xid0t.html")
                val dmVideoId = if (isDailymotion) {
                    when {
                        streamUrl.contains("video=") -> streamUrl.substringAfter("video=").substringBefore("&").substringBefore("#").trim()
                        streamUrl.contains("/embed/video/") -> streamUrl.substringAfter("/embed/video/").substringBefore("?").substringBefore("/").trim()
                        streamUrl.contains("dailymotion.com/video/") -> streamUrl.substringAfter("/video/").substringBefore("?").substringBefore("/").trim()
                        else -> null
                    }
                } else null

                if (!dmVideoId.isNullOrBlank()) {
                    val metaUrl = "https://www.dailymotion.com/player/metadata/video/$dmVideoId?locale=en_US"
                    val metaResponse = app.get(
                        metaUrl,
                        headers = mapOf(
                            "Origin" to "https://www.dailymotion.com",
                            "Referer" to "https://www.dailymotion.com/",
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                        )
                    ).text

                    val metaJson = JSONObject(metaResponse)
                    val qualities = metaJson.optJSONObject("qualities") ?: JSONObject()
                    val qualityKeys = qualities.keys()
                    while (qualityKeys.hasNext()) {
                        val quality = qualityKeys.next()
                        val arr = qualities.optJSONArray(quality) ?: continue
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            val qStreamUrl = item.optString("url").trim()
                            val mimeType = item.optString("type")
                            if (qStreamUrl.isNotBlank() && (
                                    qStreamUrl.contains(".m3u8") ||
                                    mimeType.contains("mpegURL", true) ||
                                    mimeType.contains("x-mpegURL", true)
                                )) {
                                callback.invoke(
                                    newExtractorLink(
                                        source = "AnimeKhor Dailymotion",
                                        name = "Dailymotion ($quality)",
                                        url = qStreamUrl,
                                        type = ExtractorLinkType.M3U8
                                    ) {
                                        referer = "https://www.dailymotion.com/"
                                        this.quality = when (quality.lowercase()) {
                                            "1080" -> Qualities.P1080.value
                                            "720" -> Qualities.P720.value
                                            "480" -> Qualities.P480.value
                                            "360" -> Qualities.P360.value
                                            "240" -> Qualities.P240.value
                                            else -> Qualities.Unknown.value
                                        }
                                    }
                                )
                                count++
                            }
                        }
                    }
                    return@forEach
                }

                // 2. AbyssPlayer Custom AES-CTR Decryption
                if (streamUrl.contains("abyssplayer.com/")) {
                    val streamDoc = app.get(
                        streamUrl,
                        referer = data,
                        headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                        )
                    ).text

                    val b64Match = Regex("""const\s+datas\s*=\s*"([^"]+)"""").find(streamDoc)
                    val b64Val = b64Match?.groupValues?.getOrNull(1)
                    if (!b64Val.isNullOrBlank()) {
                        val decodedBytes = base64DecodeBytes(b64Val)
                        val rawStr = String(decodedBytes, Charsets.ISO_8859_1)

                        val slugRegex = Regex("""\"slug\"\s*:\s*\"([^\"]+)\"""")
                        val userIdRegex = Regex("""\"user_id\"\s*:\s*(\d+)""")
                        val md5IdRegex = Regex("""\"md5_id\"\s*:\s*(\d+)""")
                        val mediaRegex = Regex("""\"media\"\s*:\s*\"([^\"]+)\"""")

                        val slug = slugRegex.find(rawStr)?.groupValues?.getOrNull(1)
                        val userId = userIdRegex.find(rawStr)?.groupValues?.getOrNull(1)
                        val md5Id = md5IdRegex.find(rawStr)?.groupValues?.getOrNull(1)
                        val mediaStr = mediaRegex.find(rawStr)?.groupValues?.getOrNull(1)

                        if (slug != null && userId != null && md5Id != null && mediaStr != null) {
                            val decodedMedia = decodeJsString(mediaStr)
                            val ciphertext = ByteArray(decodedMedia.length)
                            for (i in decodedMedia.indices) {
                                ciphertext[i] = decodedMedia[i].code.toByte()
                            }

                            val keyStr = "$userId:$slug:$md5Id"
                            val md5Hex = md5(keyStr)
                            val keyBytes = md5Hex.toByteArray(Charsets.UTF_8)
                            val ivBytes = keyBytes.sliceArray(0 until 16)

                            val decryptedBytes = decryptAesCtr(ciphertext, keyBytes, ivBytes)
                            val decText = String(decryptedBytes, Charsets.UTF_8)

                            val sourcesObj = JSONObject(decText)
                            val mp4Obj = sourcesObj.optJSONObject("mp4") ?: JSONObject()
                            val sourcesArr = mp4Obj.optJSONArray("sources") ?: JSONArray()
                            for (i in 0 until sourcesArr.length()) {
                                val srcObj = sourcesArr.getJSONObject(i)
                                val label = srcObj.optString("label", "Unknown")
                                val path = srcObj.optString("path")
                                val serverUrl = srcObj.optString("url")
                                val status = srcObj.optBoolean("status", false)
                                if (status && !path.isNullOrBlank() && !serverUrl.isNullOrBlank()) {
                                    val videoUrl = "$serverUrl/$path"
                                    callback.invoke(
                                        newExtractorLink(
                                            source = "AnimeKhor Abyss",
                                            name = "AbyssPlayer ($label)",
                                            url = videoUrl,
                                            type = ExtractorLinkType.VIDEO
                                        ) {
                                            referer = "https://player.abyssplayer.com/"
                                            this.quality = when (label.replace("p", "").trim()) {
                                                "1080" -> Qualities.P1080.value
                                                "720" -> Qualities.P720.value
                                                "480" -> Qualities.P480.value
                                                "360" -> Qualities.P360.value
                                                else -> Qualities.Unknown.value
                                            }
                                        }
                                    )
                                    count++
                                }
                            }
                        }
                    }
                    return@forEach
                }

                // 3. upns.live (CloudPlayer) Custom AES-CBC Decryption
                if (streamUrl.contains("upns.live/")) {
                    val idMatch = Regex("""#(.*)$""").find(streamUrl)
                    val id = idMatch?.groupValues?.getOrNull(1)?.trim()
                    if (!id.isNullOrBlank()) {
                        // First trigger the info API to register IP session on the backend
                        app.get(
                            "https://animekhor.upns.live/api/v1/info?id=$id",
                            headers = mapOf(
                                "Referer" to "https://animekhor.upns.live/",
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                            )
                        )

                        // Next fetch the video API
                        val apiResponse = app.get(
                            "https://animekhor.upns.live/api/v1/video?id=$id&w=1920&h=1080&r=animekhor.org",
                            headers = mapOf(
                                "Referer" to "https://animekhor.upns.live/",
                                "Origin" to "https://animekhor.upns.live",
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                            )
                        ).text

                        val hexData = apiResponse.trim()
                        if (hexData.isNotEmpty() && !hexData.contains("message")) {
                            val ciphertext = hexStringToByteArray(hexData)
                            val keyBytes = "kiemtienmua911ca".toByteArray(Charsets.UTF_8)
                            val ivBytes = "1234567890oiuytr".toByteArray(Charsets.UTF_8)

                            val decryptedBytes = decryptAesCbc(ciphertext, keyBytes, ivBytes)
                            val decText = String(decryptedBytes, Charsets.UTF_8)

                            val sourcesObj = JSONObject(decText)
                            val mp4Obj = sourcesObj.optJSONObject("mp4") ?: JSONObject()
                            val sourcesArr = mp4Obj.optJSONArray("sources") ?: JSONArray()
                            for (i in 0 until sourcesArr.length()) {
                                val srcObj = sourcesArr.getJSONObject(i)
                                val label = srcObj.optString("label", "Unknown")
                                val path = srcObj.optString("path")
                                val serverUrl = srcObj.optString("url")
                                val status = srcObj.optBoolean("status", false)
                                if (status && !path.isNullOrBlank() && !serverUrl.isNullOrBlank()) {
                                    val videoUrl = "$serverUrl/$path"
                                    callback.invoke(
                                        newExtractorLink(
                                            source = "CloudPlayer",
                                            name = "CloudPlayer ($label)",
                                            url = videoUrl,
                                            type = ExtractorLinkType.VIDEO
                                        ) {
                                            referer = "https://animekhor.upns.live/"
                                            this.quality = when (label.replace("p", "").trim()) {
                                                "1080" -> Qualities.P1080.value
                                                "720" -> Qualities.P720.value
                                                "480" -> Qualities.P480.value
                                                "360" -> Qualities.P360.value
                                                else -> Qualities.Unknown.value
                                            }
                                        }
                                    )
                                    count++
                                }
                            }
                        }
                    }
                    return@forEach
                }

                // 4. Default Fallbacks
                loadExtractor(streamUrl, data, subtitleCallback, callback)
                count++
            } catch (_: Exception) {}
        }

        return count > 0
    }

    private fun base64Decode(encoded: String): String {
        return try {
            String(java.util.Base64.getDecoder().decode(encoded.trim()), Charsets.UTF_8)
        } catch (_: Exception) {
            try {
                String(android.util.Base64.decode(encoded.trim(), android.util.Base64.DEFAULT), Charsets.UTF_8)
            } catch (_: Exception) {
                encoded
            }
        }
    }

    private fun base64DecodeBytes(encoded: String): ByteArray {
        return try {
            java.util.Base64.getDecoder().decode(encoded.trim())
        } catch (_: Exception) {
            try {
                android.util.Base64.decode(encoded.trim(), android.util.Base64.DEFAULT)
            } catch (_: Exception) {
                encoded.toByteArray(Charsets.UTF_8)
            }
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun decryptAesCtr(ciphertext: ByteArray, keyBytes: ByteArray, ivBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(ivBytes)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(ciphertext)
    }

    private fun decryptAesCbc(ciphertext: ByteArray, keyBytes: ByteArray, ivBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(ivBytes)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(ciphertext)
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun decodeJsString(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                val nextC = s[i + 1]
                if (nextC == 'u' && i + 5 < s.length) {
                    val hexStr = s.substring(i + 2, i + 6)
                    try {
                        sb.append(hexStr.toInt(16).toChar())
                        i += 6
                        continue
                    } catch (_: NumberFormatException) {
                        sb.append('\\')
                        i += 1
                        continue
                    }
                } else if (nextC == '"' || nextC == '\\' || nextC == '/') {
                    sb.append(nextC)
                    i += 2
                    continue
                } else if (nextC == 'n') {
                    sb.append('\n')
                    i += 2
                    continue
                } else if (nextC == 'r') {
                    sb.append('\r')
                    i += 2
                    continue
                } else if (nextC == 't') {
                    sb.append('\t')
                    i += 2
                    continue
                }
            }
            sb.append(s[i])
            i++
        }
        return sb.toString()
    }
}
