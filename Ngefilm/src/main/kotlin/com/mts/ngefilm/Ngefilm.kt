package com.mts.ngefilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class NgefilmProvider : MainAPI() {
    override var mainUrl = "https://new39.ngefilm.site"
    override var name = "NgeFilm21"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    private val UA_BROWSER = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
    private val RPM_KEY = "6b69656d7469656e6d75613931316361" 
    private val RPM_IV = "313233343536373839306f6975797472"

    private fun cleanTitle(rawTitle: String?): String {
        if (rawTitle.isNullOrBlank()) return ""
        return rawTitle
            .replace(Regex("""(?i)^\s*(?:Nonton\s+(?:Film|Series|Movie)?|Streaming)\s*"""), "")
            .replace(Regex("""(?i)\s+(?:Sub\s*Indo|Subtitle\s*Indonesia|Gratis).*$"""), "")
            .trim()
    }

    private fun Element.getImageAttr(): String? {
        var url = this.attr("data-src").ifEmpty { this.attr("src") }
        if (url.isEmpty()) {
            val srcset = this.attr("srcset")
            if (srcset.isNotEmpty()) {
                url = srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull() ?: ""
            }
        }
        return if (url.isNotEmpty()) {
            httpsify(url).replace(Regex("""-\d+x\d+"""), "")
        } else null
    }

    private fun parseDateToEpoch(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        val clean = dateStr.trim()
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "dd MMMM yyyy",
            "dd MMM yyyy",
            "d MMMM yyyy",
            "d MMM yyyy"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale("id", "ID"))
                sdf.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
                val d = sdf.parse(clean)
                if (d != null) return d.time
            } catch (_: Exception) {}
            try {
                val sdf = SimpleDateFormat(pattern, Locale.ENGLISH)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val d = sdf.parse(clean)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
        return null
    }

    override val mainPage = mainPageOf(
        "" to "🎬 Filem & Siri Terbaru (Latest Releases)",
        "?s=&search=advanced&post_type=&index=&orderby=rating&genre=&movieyear=&country=&quality=" to "⭐ Rating Tertinggi (Top Rated)",
        "movies" to "🍿 Filem Pilihan Terkini (Movies)",
        "tvshows" to "📺 Siri TV & Drama Terkini (TV Shows)",
        "?s=&search=advanced&post_type=tv&index=&orderby=&genre=drama&movieyear=&country=korea&quality=" to "🇰🇷 Drama & Filem Korea (K-Drama)",
        "country/indonesia" to "🇮🇩 Filem & Siri Indonesia (Indo)",
        "country/usa" to "🇺🇸 Filem Barat & Hollywood (Western)",
        "country/malaysia" to "🇲🇾 Filem & Siri Malaysia (Malay)",
        "country/japan" to "🇯🇵 Siri & Filem Jepun (J-Drama)",
        "country/china" to "🇨🇳 Drama & Filem Mandarin (C-Drama)",
        "Genre/action" to "💥 Aksi & Pengembaraan (Action)",
        "Genre/horror" to "👻 Horor & Menyeramkan (Horror)",
        "Genre/comedy" to "😂 Komedi & Santai (Comedy)",
        "Genre/romance" to "💖 Cinta & Romantik (Romance)",
        "Genre/science-fiction" to "🚀 Fiksi Ilmiah & Magis (Sci-Fi)"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val urlPath = request.data
        val finalUrl = when {
            urlPath.isEmpty() -> if (page <= 1) "$mainUrl/" else "$mainUrl/page/$page/"
            urlPath.contains("?") -> {
                val split = urlPath.split("?")
                if (page <= 1) "$mainUrl/?${split[1]}" else "$mainUrl/page/$page/?${split[1]}"
            }
            else -> {
                val cleanPath = urlPath.trim().removePrefix("/").removeSuffix("/")
                if (page <= 1) "$mainUrl/$cleanPath/" else "$mainUrl/$cleanPath/page/$page/"
            }
        }

        val document = app.get(finalUrl, headers = mapOf("User-Agent" to UA_BROWSER)).document
        val items = document.select("article.item-infinite, article.item, article").mapNotNull { it.toSearchResult() }
        val distinctItems = items.distinctBy { it.url }
        return newHomePageResponse(request.name, distinctItems, hasNext = distinctItems.isNotEmpty())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleEl = this.selectFirst(".entry-title a") ?: this.selectFirst("h2 a, h3 a, a") ?: return null
        val rawTitle = titleEl.text().trim()
        val title = cleanTitle(rawTitle)
        if (title.isBlank() || title == "#") return null

        val href = titleEl.attr("href").ifEmpty { return null }
        val fullUrl = fixUrl(href)
        val qualityText = this.selectFirst(".gmr-quality-item, .quality")?.text()?.trim() ?: "HD"
        val poster = this.selectFirst(".content-thumbnail img, figure img, img")?.getImageAttr()

        val isTv = href.contains("/tv/") || href.contains("/series/")
        val type = if (isTv) TvType.TvSeries else TvType.Movie

        return if (isTv) {
            newTvSeriesSearchResponse(title, fullUrl, type) {
                this.posterUrl = poster
                addQuality(qualityText)
            }
        } else {
            newMovieSearchResponse(title, fullUrl, type) {
                this.posterUrl = poster
                addQuality(qualityText)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val searchUrl = "$mainUrl/?s=${query.trim().replace(" ", "+")}&post_type[]=post&post_type[]=tv"
            val doc = app.get(searchUrl, headers = mapOf("User-Agent" to UA_BROWSER)).document
            doc.select("article.item-infinite, article.item, article").mapNotNull { it.toSearchResult() }.distinctBy { it.url }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = mapOf("User-Agent" to UA_BROWSER)).document

        // If this is an episode page (/eps/), find parent series if possible
        var parentDoc = document
        var parentUrl = url
        if (url.contains("/eps/")) {
            val seriesLink = document.selectFirst(".gmr-listseries a[href*='/tv/']")?.attr("href")
                ?: document.selectFirst(".entry-content a[href*='/tv/']")?.attr("href")
                ?: document.selectFirst("meta[property='og:url']")?.attr("content")
            if (!seriesLink.isNullOrBlank() && seriesLink.contains("/tv/")) {
                runCatching {
                    parentDoc = app.get(fixUrl(seriesLink), headers = mapOf("User-Agent" to UA_BROWSER)).document
                    parentUrl = fixUrl(seriesLink)
                }
            }
        }

        val rawTitle = parentDoc.selectFirst("h1.entry-title")?.text()?.trim()
            ?: document.selectFirst("h1.entry-title")?.text()?.trim()
            ?: ""
        val title = cleanTitle(rawTitle)

        val poster = parentDoc.selectFirst(".gmr-movie-data figure img, .content-thumbnail img")?.getImageAttr()
            ?: document.selectFirst(".gmr-movie-data figure img, .content-thumbnail img")?.getImageAttr()
            ?: parentDoc.selectFirst("meta[property='og:image']")?.attr("content")
            ?: document.selectFirst("meta[property='og:image']")?.attr("content")

        val plotText = parentDoc.selectFirst("div.entry-content[itemprop='description'] p")?.text()?.trim()
            ?: parentDoc.selectFirst("div.entry-content p")?.text()?.trim()
            ?: document.selectFirst("meta[property='og:description']")?.attr("content")

        val yearText = parentDoc.selectFirst(".gmr-moviedata a[href*='year']")?.text()?.toIntOrNull()
            ?: Regex("""\b(19\d\d|20\d\d)\b""").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()

        val ratingText = parentDoc.selectFirst("[itemprop='ratingValue']")?.text()?.trim()
        val tagsList = parentDoc.select(".gmr-moviedata a[href*='genre']").map { it.text().trim() }
        val actorsList = parentDoc.select("[itemprop='actors'] a").map { it.text().trim() }
        val trailerUrl = parentDoc.selectFirst("a.gmr-trailer-popup")?.attr("href")
            ?: document.selectFirst("a.gmr-trailer-popup")?.attr("href")

        // Check if there is a series episode list
        val epElements = parentDoc.select(".gmr-listseries a").filter { it.attr("href").contains("/eps/") }.ifEmpty {
            document.select(".gmr-listseries a").filter { it.attr("href").contains("/eps/") }
        }

        val isSeries = epElements.isNotEmpty() || url.contains("/tv/") || url.contains("/eps/")
        val type = if (isSeries) TvType.TvSeries else TvType.Movie

        // Extract series/movie release date
        val releaseText = parentDoc.selectFirst(".gmr-moviedata:contains(Rilis:)")?.text()?.replace("Rilis:", "")?.trim()
            ?: parentDoc.selectFirst(".gmr-moviedata:contains(Diposting)")?.text()?.replace("Diposting pada:", "")?.trim()
            ?: parentDoc.selectFirst("time.entry-date")?.text()?.trim()
        val defaultEpoch = parseDateToEpoch(releaseText)

        if (isSeries) {
            val episodes = epElements.mapNotNull { el ->
                val epHref = el.attr("href")
                if (epHref.isBlank()) return@mapNotNull null
                val epTitleRaw = el.attr("title").removePrefix("Permalink ke ").trim()
                val cleanEpTitle = cleanTitle(epTitleRaw).ifBlank { el.text().trim() }
                val epNum = Regex("""(?:Episode|Eps|Ep)\s*(\d+)""", RegexOption.IGNORE_CASE).find(epTitleRaw)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""(\d+)""").find(el.text())?.groupValues?.get(1)?.toIntOrNull()
                    ?: 1

                val fullEpHref = fixUrl(epHref)

                newEpisode(fullEpHref) {
                    this.name = if (cleanEpTitle.startsWith("Episode", true)) cleanEpTitle else "Episode $epNum: $cleanEpTitle"
                    this.episode = epNum
                    this.season = 1
                    this.posterUrl = poster
                    this.date = defaultEpoch
                    this.description = if (!releaseText.isNullOrBlank()) "Rilis: $releaseText • Sub Indo" else "Sub Indo"
                }
            }

            return newTvSeriesLoadResponse(title, url, type, episodes) {
                this.posterUrl = poster
                this.plot = plotText
                this.year = yearText
                this.score = Score.from10(ratingText)
                this.tags = tagsList
                this.actors = actorsList.map { ActorData(Actor(it)) }
                if (!trailerUrl.isNullOrBlank()) this.trailers.add(TrailerData(trailerUrl, null, false))
            }
        } else {
            return newMovieLoadResponse(title, url, type, url) {
                this.posterUrl = poster
                this.plot = plotText
                this.year = yearText
                this.score = Score.from10(ratingText)
                this.tags = tagsList
                this.actors = actorsList.map { ActorData(Actor(it)) }
                if (!trailerUrl.isNullOrBlank()) this.trailers.add(TrailerData(trailerUrl, null, false))
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = mapOf("User-Agent" to UA_BROWSER)).document

        // Collect all player tab URLs
        val playerTabs = document.select(".muvipro-player-tabs a, ul#playeroptionsul li a, .gmr-player-nav a")
            .mapNotNull { it.attr("href") }
            .filter { it.isNotBlank() && !it.startsWith("javascript:") && it != "#" }
            .toMutableList()

        if (playerTabs.isEmpty()) playerTabs.add(data)

        coroutineScope {
            playerTabs.distinct().map { tabPath ->
                async {
                    try {
                        val fixedTabUrl = if (tabPath.startsWith("http")) tabPath else "$mainUrl$tabPath"
                        val tabDoc = app.get(fixedTabUrl, headers = mapOf("User-Agent" to UA_BROWSER)).document
                        val tabHtml = tabDoc.html()

                        // 1. Direct iframe links in the responsive player container
                        val iframes = tabDoc.select(".gmr-embed-responsive iframe, #pembed iframe, iframe")
                            .mapNotNull { it.attr("src") }
                            .filter { it.isNotBlank() && !it.contains("googletagmanager") && !it.contains("googleads") }

                        for (rawIframe in iframes) {
                            val iframeUrl = fixUrl(rawIframe)
                            when {
                                iframeUrl.contains("rpmlive.online") -> {
                                    val id = Regex("""rpmlive\.online.*?[#&?]id=([a-zA-Z0-9]+)|rpmlive\.online.*?#([a-zA-Z0-9]+)""").find(iframeUrl)
                                        ?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
                                    if (!id.isNullOrBlank()) {
                                        extractRpm(id, callback)
                                    }
                                }
                                iframeUrl.contains("abyssplayer.com") -> {
                                    AbyssplayerCom().getUrl(iframeUrl, fixedTabUrl, subtitleCallback, callback)
                                }
                                else -> {
                                    loadExtractor(iframeUrl, fixedTabUrl, subtitleCallback, callback)
                                }
                            }
                        }

                        // 2. Fallback regex in page content for any embedded player script or hidden embed
                        val rpmMatch = Regex("""rpmlive\.online.*?[#&?]id=([a-zA-Z0-9]+)|rpmlive\.online.*?#([a-zA-Z0-9]+)""").find(tabHtml)
                        rpmMatch?.let {
                            val id = it.groupValues[1].ifEmpty { it.groupValues[2] }
                            if (id.isNotBlank()) extractRpm(id, callback)
                        }

                        val abyssMatch = Regex("""https?://(?:player\.|play\.)?abyssplayer\.com/[a-zA-Z0-9_-]+""").findAll(tabHtml)
                        abyssMatch.forEach { m ->
                            AbyssplayerCom().getUrl(m.value, fixedTabUrl, subtitleCallback, callback)
                        }

                        // 3. Morencius / StreamWish regex fallback
                        Regex("""https?://morencius\.com/embed/[a-zA-Z0-9]+""").findAll(tabHtml).forEach { m ->
                            loadExtractor(m.value, fixedTabUrl, subtitleCallback, callback)
                        }

                        // 4. Hgcloud / Masukestin fallback
                        Regex("""https?://(?:hgcloud|hglink|vibuxer|masukestin)\.(?:to|com|net)/e/[a-zA-Z0-9]+""").findAll(tabHtml).forEach { m ->
                            extractMasukestin(m.value, callback)
                        }

                        // 5. Short.icu / krakenfiles / xshotcok
                        Regex("""https?://krakenfiles\.com/embed-video/[a-zA-Z0-9]+""").findAll(tabHtml).forEach { m ->
                            extractKrakenManual(m.value, callback)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }.awaitAll()
        }

        return true
    }

    private suspend fun extractRpm(id: String, callback: (ExtractorLink) -> Unit) {
        try {
            val domain = mainUrl.removePrefix("https://").removePrefix("http://").removeSuffix("/")
            val h = mapOf(
                "Host" to "playerngefilm21.rpmlive.online",
                "User-Agent" to UA_BROWSER,
                "Referer" to "https://playerngefilm21.rpmlive.online/",
                "Origin" to "https://playerngefilm21.rpmlive.online",
                "X-Requested-With" to "XMLHttpRequest"
            )
            val videoApi = "https://playerngefilm21.rpmlive.online/api/v1/video?id=$id&w=1920&h=1080&r=$domain"
            val encryptedRes = app.get(videoApi, headers = h).text
            val jsonStr = if (encryptedRes.trim().startsWith("{")) encryptedRes else decryptAES(encryptedRes)

            // Direct IP M3U8 source
            Regex("""source"\s*:\s*"([^"]+)""").find(jsonStr)?.groupValues?.get(1)?.let { rawLink ->
                val link = rawLink.replace("\\/", "/")
                callback.invoke(
                    newExtractorLink("RPM Live", "RPM Live (Direct 1080p)", link, ExtractorLinkType.M3U8) {
                        this.referer = "https://playerngefilm21.rpmlive.online/"
                        this.quality = Qualities.P1080.value
                    }
                )
            }

            // Cloudflare Native M3U8 source (Ultra-fast CDN)
            Regex("""cfNative"\s*:\s*"([^"]+)""").find(jsonStr)?.groupValues?.get(1)?.let { rawLink ->
                val link = rawLink.replace("\\/", "/")
                callback.invoke(
                    newExtractorLink("RPM Live", "RPM Live (Cloudflare CDN)", link, ExtractorLinkType.M3U8) {
                        this.referer = "https://playerngefilm21.rpmlive.online/"
                        this.quality = Qualities.P1080.value
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun extractKrakenManual(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            val text = app.get(url, headers = mapOf("User-Agent" to UA_BROWSER, "Referer" to mainUrl)).text
            val videoUrl = Regex("""<source[^>]+src=["'](https:[^"']+)["']""").find(text)?.groupValues?.get(1)
                ?: Regex("""src=["'](https:[^"']+/play/video/[^"']+)["']""").find(text)?.groupValues?.get(1)
            videoUrl?.let { clean ->
                callback.invoke(
                    newExtractorLink("Krakenfiles", "Krakenfiles", clean.replace("&amp;", "&").replace("\\", ""), ExtractorLinkType.VIDEO) {
                        this.referer = url
                        this.headers = mapOf("User-Agent" to UA_BROWSER)
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun extractMasukestin(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            val domain = "masukestin.com"
            val targetUrl = url.replace("hglink.to", domain)
                .replace("hglink.net", domain)
                .replace("hgcloud.to", domain)
                .replace("vibuxer.com", domain)

            val response = app.get(targetUrl, headers = mapOf(
                "User-Agent" to UA_BROWSER,
                "Referer" to "$mainUrl/",
                "Upgrade-Insecure-Requests" to "1"
            ))

            val doc = response.text
            val videoId = url.substringAfter("/e/").split('?', '&', '"', '\'').firstOrNull() ?: ""
            val packedRegex = Regex("""eval\(function\(p,a,c,k,e,d.*?\)\)""")
            val packedCode = packedRegex.find(doc)?.value

            if (packedCode != null) {
                val unpackedJs = Unpacker.unpack(packedCode)
                var linkM3u8 = Regex("""["']([^"']+\.m3u8[^"']*)["']""").find(unpackedJs)?.groupValues?.get(1)

                if (linkM3u8 == null) {
                    val hashMatch = Regex("""hash\s*:\s*["']([^"']+)["']""").find(unpackedJs)
                    val hash = hashMatch?.groupValues?.get(1)

                    if (hash != null) {
                        val apiUrl = "https://$domain/dl?op=view&file_code=$videoId&hash=$hash&embed=1&referer=hglink.to"
                        val apiRes = app.get(apiUrl, headers = mapOf(
                            "User-Agent" to UA_BROWSER,
                            "Referer" to targetUrl,
                            "X-Requested-With" to "XMLHttpRequest"
                        ), cookies = response.cookies).text

                        linkM3u8 = Regex("""["']([^"']+\.m3u8[^"']*)["']""").find(apiRes)?.groupValues?.get(1)
                    }
                }

                if (linkM3u8 != null) {
                    var cleanM3u8 = linkM3u8.replace("\\/", "/")
                    if (cleanM3u8.startsWith("/")) cleanM3u8 = "https://$domain$cleanM3u8"
                    callback.invoke(
                        newExtractorLink("Masukestin", "Masukestin Server", cleanM3u8, ExtractorLinkType.M3U8) {
                            this.headers = mapOf("User-Agent" to UA_BROWSER, "Referer" to "https://$domain/")
                        }
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    object Unpacker {
        fun unpack(packedJS: String): String {
            try {
                val startIdx = packedJS.indexOf("}('")
                if (startIdx == -1) return packedJS
                val argsString = packedJS.substring(startIdx + 3)
                val splitIdx = argsString.lastIndexOf("'.split('|')")
                if (splitIdx == -1) return packedJS
                val coreData = argsString.substring(0, splitIdx)
                val parts = coreData.split(",")
                if (parts.size < 4) return packedJS
                val dictRaw = parts.last().trim('\'', '"')
                val dictionary = dictRaw.split("|")
                val count = parts[parts.size - 2].toInt()
                val radix = parts[parts.size - 3].toInt()
                val payloadRaw = coreData.substring(0, coreData.lastIndexOf("," + radix))
                val payload = payloadRaw.trim('\'', '"')
                var decoded = payload

                fun encodeBase(n: Int, radix: Int): String {
                    val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                    var num = n
                    if (num == 0) return "0"
                    val sb = StringBuilder()
                    while (num > 0) {
                        sb.append(chars[num % radix])
                        num /= radix
                    }
                    return sb.reverse().toString()
                }

                for (i in count - 1 downTo 0) {
                    val token = encodeBase(i, radix)
                    val word = if (i < dictionary.size && dictionary[i].isNotEmpty()) dictionary[i] else token
                    decoded = decoded.replace(Regex("""\b$token\b"""), word)
                }
                return decoded.replace("\\", "")
            } catch (e: Exception) {
                return packedJS
            }
        }
    }

    private fun decryptAES(text: String): String {
        if (text.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(hexToBytes(RPM_KEY), "AES"), IvParameterSpec(hexToBytes(RPM_IV)))
            String(cipher.doFinal(hexToBytes(text.replace(Regex("[^0-9a-fA-F]"), ""))))
        } catch (e: Exception) { "" }
    }

    private fun hexToBytes(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
        }
        return data
    }
}
