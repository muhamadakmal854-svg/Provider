package com.mts.sarangfilm21

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

open class Sarangfilm21 : MainAPI() {
    override var mainUrl = "http://154.203.167.18"
    override var name = "Sarangfilm21"
    override var lang = "id"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    // Bypass Cloudflare / Turnstile / Captcha
    val wpRedisInterceptor by lazy { CloudflareKiller() }

    companion object {
        private const val UA_BROWSER =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val PLAYSOBAT_KEY = "96fb393f57087e9333cc067bf4aa378e"
    }

    // Tampilan Homepage Gaya Netflix dengan 25 Kategori Tematik Lengkap & Disahkan Status 200
    override val mainPage = mainPageOf(
        "" to "✨ Pilihan Utama (Spotlight)",
        "tv/" to "📺 Serial TV Terbaru (New TV Series)",
        "drama-korea/" to "🇰🇷 Drama Korea Terhangat (K-Drama)",
        "drama-china/" to "🇨🇳 Drama Mandarin Populer (C-Drama)",
        "drama-jepang/" to "🇯🇵 Drama & Anime Jepang (J-Drama)",
        "drama-thailand/" to "🇹🇭 Drama Thailand (Thai-Drama)",
        "drama-india/" to "🇮🇳 Sinema Bollywood & Drama India",
        "west-series/" to "🇺🇸 Siri Barat & Hollywood (West Series)",
        "country/indonesia/" to "🇮🇩 Film Bioskop Indonesia",
        "country/usa/" to "🇺🇸 Film Box Office USA",
        "action/" to "💥 Aksi & Laga Menegangkan (Action)",
        "film-horror-terbaru/" to "👻 Horor & Menyeramkan (Horror)",
        "comedy/" to "😂 Komedi Menggelitik (Comedy)",
        "adventure/" to "🗺️ Petualangan Epik (Adventure)",
        "crime/" to "🕵️ Kriminal & Penyelidikan (Crime)",
        "drama/" to "🎭 Kisah Hidup & Emosi (Drama)",
        "fantasy/" to "🔮 Fantasi & Keajaiban (Fantasy)",
        "mystery/" to "🔍 Misteri & Teka-Teki (Mystery)",
        "romance/" to "🌸 Romantis & Percintaan (Romance)",
        "science-fiction/" to "🚀 Fiksi Sains & Masa Depan (Sci-Fi)",
        "thriller/" to "🔪 Thriller Mendebarkan (Thriller)",
        "animation/" to "🎨 Animasi & Kartun (Animation)",
        "family/" to "👨‍👩‍👧‍👦 Tontonan Bersama Keluarga (Family)",
        "year/2026/" to "📅 Rilisan Terkini 2026",
        "year/2025/" to "📅 Rilisan Populer 2025"
    )

    private fun cleanTitle(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(Regex("""(?i)^\s*(?:Nonton\s+(?:Film|Series|Serial)?|Streaming|Permalink\s+to:?|Permalink\s+ke)\s*"""), "")
            .replace(Regex("""(?i)\s+(?:Sub\s*Indo|Subtitle\s*Indonesia|Bioskopkeren|Lk21|Layarkaca21|Rebahin|Idlix).*$"""), "")
            .trim()
    }

    private fun fixItemUrl(url: String): String {
        var clean = url.trim()
        if (clean.startsWith("//")) return "http:$clean"
        if (clean.startsWith("/")) return "$mainUrl$clean"
        clean = clean.replace("https://sarangfilm.diy", mainUrl)
                     .replace("http://sarangfilm.diy", mainUrl)
        return clean
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = (if (this.tagName() == "a") this else this.selectFirst("a")) ?: return null
        val href = fixItemUrl(a.attr("href"))
        if (href.isBlank() || href == "$mainUrl/" || href.contains("/category/") || href.contains("/genre/")) return null

        val img = this.selectFirst("img")
        var title = cleanTitle(this.selectFirst(".entry-title, h2, h3, .title")?.text())
        if (title.isBlank()) {
            title = cleanTitle(a.attr("title").ifBlank { img?.attr("alt") })
        }
        if (title.isBlank()) return null

        var poster = img?.attr("src")?.ifBlank { null }
            ?: img?.attr("data-src")?.ifBlank { null }
            ?: img?.attr("data-original")?.ifBlank { null }
            ?: img?.attr("data-litespeed-src")?.ifBlank { null }
            ?: ""

        val srcset = img?.attr("srcset") ?: ""
        if (srcset.isNotBlank()) {
            val best = srcset.split(",").map { it.trim().substringBefore(" ") }.lastOrNull { it.isNotBlank() }
            if (!best.isNullOrBlank()) poster = best
        }
        if (poster.startsWith("//")) poster = "http:$poster"
        poster = poster.replace(Regex("""-\d+x\d+(\.[a-zA-Z]+)$"""), "$1")

        val isTv = href.contains("/tv/") || href.contains("/series/") || href.contains("/drama") ||
                   title.contains("Season", ignoreCase = true) || title.contains("Series", ignoreCase = true)

        return if (isTv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster.ifBlank { null }
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster.ifBlank { null }
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path = request.data.trim().removePrefix("/").removeSuffix("/")
        val pageUrl = if (page <= 1) {
            if (path.isEmpty()) "$mainUrl/" else "$mainUrl/$path/"
        } else {
            if (path.isEmpty()) "$mainUrl/page/$page/" else "$mainUrl/$path/page/$page/"
        }

        val doc = try {
            app.get(
                pageUrl,
                interceptor = wpRedisInterceptor,
                headers = mapOf("Referer" to "$mainUrl/", "User-Agent" to UA_BROWSER),
                timeout = 25
            ).document
        } catch (_: Exception) {
            app.get(
                pageUrl,
                headers = mapOf("Referer" to "$mainUrl/", "User-Agent" to UA_BROWSER),
                timeout = 25
            ).document
        }

        val items = doc.select("article, .gmr-item-modulepost, .gmr-item-archivepost, .item").mapNotNull {
            it.toSearchResult()
        }.distinctBy { it.url }

        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=${query.trim().replace(" ", "+")}"
        val doc = try {
            app.get(
                searchUrl,
                interceptor = wpRedisInterceptor,
                headers = mapOf("Referer" to "$mainUrl/", "User-Agent" to UA_BROWSER),
                timeout = 25
            ).document
        } catch (_: Exception) {
            app.get(
                searchUrl,
                headers = mapOf("Referer" to "$mainUrl/", "User-Agent" to UA_BROWSER),
                timeout = 25
            ).document
        }

        return doc.select("article, .gmr-item-modulepost, .gmr-item-archivepost, .item").mapNotNull {
            it.toSearchResult()
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val fixedUrl = fixItemUrl(url)
        val doc = try {
            app.get(
                fixedUrl,
                interceptor = wpRedisInterceptor,
                headers = mapOf("Referer" to "$mainUrl/", "User-Agent" to UA_BROWSER),
                timeout = 25
            ).document
        } catch (_: Exception) {
            app.get(
                fixedUrl,
                headers = mapOf("Referer" to "$mainUrl/", "User-Agent" to UA_BROWSER),
                timeout = 25
            ).document
        }

        val rawTitle = doc.selectFirst("h1.entry-title, .entry-title")?.text()?.trim() ?: return null
        val title = cleanTitle(rawTitle)

        val img = doc.selectFirst(".gmr-movie-data img, .thumb img, .film-poster img, .entry-thumb img")
        var rawPoster = img?.attr("src")?.ifBlank { null }
            ?: img?.attr("data-src")?.ifBlank { null }
            ?: img?.attr("data-original")?.ifBlank { null }
            ?: ""
        val srcset = img?.attr("srcset") ?: ""
        if (srcset.isNotBlank()) {
            val best = srcset.split(",").map { it.trim().substringBefore(" ") }.lastOrNull { it.isNotBlank() }
            if (!best.isNullOrBlank()) rawPoster = best
        }
        if (rawPoster.startsWith("//")) rawPoster = "http:$rawPoster"
        rawPoster = rawPoster.replace(Regex("""-\d+x\d+(\.[a-zA-Z]+)$"""), "$1")

        val plot = doc.selectFirst(".entry-content p, [itemprop='description']")?.text()?.trim()
            ?: doc.selectFirst("meta[property='og:description']")?.attr("content")?.trim()

        val yearText = doc.select(".gmr-moviedata, .content-moviedata").firstOrNull { it.text().contains("Year", true) }?.text()
            ?: doc.selectFirst("time[datetime], .year")?.text()
        val year = Regex("""\b(19\d\d|20\d\d)\b""").find(yearText ?: "")?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""\b(19\d\d|20\d\d)\b""").find(title)?.groupValues?.get(1)?.toIntOrNull()

        val ratingText = doc.selectFirst(".gmr-rating-item, [itemprop='ratingValue']")?.text()
            ?.replace(Regex("""[^\d.]"""), "")?.trim()

        val tags = doc.select(".gmr-movie-on a, a[rel='category tag'], .genxed a").map { it.text().trim() }.filter { it.isNotBlank() }

        val actors = doc.select(".gmr-moviedata").firstOrNull { it.text().contains("Cast", true) }
            ?.select("a")?.map { ActorData(Actor(it.text().trim())) } ?: emptyList()

        val epLinks = doc.select(".gmr-listseries a, a[href*='/eps/'], a.button-shadow")
            .filter { !it.hasClass("gmr-all-serie") && !it.attr("href").contains("/tv/") }
            .distinctBy { it.attr("href") }

        val isTv = fixedUrl.contains("/tv/") || epLinks.isNotEmpty()

        if (isTv && epLinks.isNotEmpty()) {
            val episodes = coroutineScope {
                epLinks.map { a ->
                    async {
                        val epHref = fixItemUrl(a.attr("href"))
                        val rawEpTitle = a.attr("title").ifBlank { a.text() }
                        val cleanEpTitle = cleanTitle(rawEpTitle)

                        val seasonNum = Regex("""(?i)season-(\d+)""").find(epHref)?.groupValues?.get(1)?.toIntOrNull()
                            ?: Regex("""(?i)S(\d+)""").find(a.text())?.groupValues?.get(1)?.toIntOrNull() ?: 1
                        val epNum = Regex("""(?i)episode-(\d+)""").find(epHref)?.groupValues?.get(1)?.toIntOrNull()
                            ?: Regex("""(?i)Eps(\d+)""").find(a.text())?.groupValues?.get(1)?.toIntOrNull() ?: 1

                        var epDate: Long? = null
                        var epDesc: String? = null
                        var epPoster: String? = rawPoster

                        val epDoc = runCatching {
                            app.get(
                                epHref,
                                interceptor = wpRedisInterceptor,
                                headers = mapOf("Referer" to fixedUrl, "User-Agent" to UA_BROWSER),
                                timeout = 8
                            ).document
                        }.getOrNull()

                        if (epDoc != null) {
                            val timeEl = epDoc.selectFirst("time[itemprop='datePublished'], time[datetime], time.published, time[itemprop='dateCreated']")
                            val rawDate = timeEl?.attr("datetime")?.ifBlank { timeEl.text().trim() }
                            if (!rawDate.isNullOrBlank()) {
                                val dateIso = rawDate.substringBefore("T")
                                epDate = runCatching {
                                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                                    sdf.parse(dateIso)?.time
                                }.getOrNull() ?: runCatching {
                                    val sdf = SimpleDateFormat("d MMMM yyyy", Locale("id", "ID"))
                                    sdf.parse(rawDate)?.time
                                }.getOrNull()

                                epDesc = "Rilis: ${dateIso.ifBlank { rawDate }} • Sub Indo"
                            }

                            val descMeta = epDoc.selectFirst("meta[property='og:description'], meta[name='description']")?.attr("content")
                            if (!descMeta.isNullOrBlank()) {
                                epDesc = if (epDesc != null) "$epDesc\n$descMeta" else descMeta
                            }

                            val epImg = epDoc.selectFirst(".gmr-movie-data img, .entry-content img, .attachment-post-thumbnail, .poster img")
                            val src = epImg?.attr("src")?.ifBlank { epImg.attr("data-src") }
                            if (!src.isNullOrBlank()) {
                                val fullSrc = if (src.startsWith("//")) "http:$src" else src
                                epPoster = fullSrc.replace(Regex("""-\d+x\d+(\.[a-zA-Z]+)$"""), "$1")
                            }
                        }

                        val epName = if (cleanEpTitle.isNotBlank() && !cleanEpTitle.startsWith("S1 Eps", ignoreCase = true) && !cleanEpTitle.startsWith("Eps", ignoreCase = true)) {
                            cleanEpTitle
                        } else {
                            "Episode $epNum"
                        }

                        newEpisode(epHref) {
                            this.name = epName
                            this.season = seasonNum
                            this.episode = epNum
                            this.posterUrl = epPoster
                            this.date = epDate
                            this.description = epDesc ?: "Rilis: Terkini • Sub Indo"
                        }
                    }
                }.awaitAll()
            }

            return newTvSeriesLoadResponse(title, fixedUrl, TvType.TvSeries, episodes) {
                this.posterUrl = rawPoster
                this.plot = plot
                this.year = year
                this.tags = tags
                if (!ratingText.isNullOrBlank()) this.score = Score.from10(ratingText)
                this.actors = actors
            }
        }

        return newMovieLoadResponse(title, fixedUrl, TvType.Movie, fixedUrl) {
            this.posterUrl = rawPoster
            this.plot = plot
            this.year = year
            this.tags = tags
            if (!ratingText.isNullOrBlank()) this.score = Score.from10(ratingText)
            this.actors = actors
        }
    }

    private fun decryptPlaysobatPayload(payloadJsonStr: String): Map<String, String>? {
        return try {
            val payloadObj = JSONObject(payloadJsonStr)
            val ivB64 = payloadObj.getString("iv")
            val dataB64 = payloadObj.getString("data")

            val keyBytes = PLAYSOBAT_KEY.toByteArray(Charsets.UTF_8)
            val ivBytes = Base64.decode(ivB64, Base64.DEFAULT)
            val cipherBytes = Base64.decode(dataB64, Base64.DEFAULT)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(ivBytes)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

            val decryptedBytes = cipher.doFinal(cipherBytes)
            val decryptedStr = String(decryptedBytes, Charsets.UTF_8)
            val resultObj = JSONObject(decryptedStr)
            val map = mutableMapOf<String, String>()
            val keys = resultObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = resultObj.getString(k)
            }
            map
        } catch (e: Exception) {
            Log.e("Sarangfilm21", "Playsobat decrypt error: ${e.message}")
            null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCdn: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var found = false
        val extractedUrls = mutableSetOf<String>()

        suspend fun handleDirectVideoUrl(url: String, serverName: String) {
            val clean = url.trim()
            if (clean.isBlank() || extractedUrls.contains(clean)) return
            extractedUrls.add(clean)

            if (clean.contains(".m3u8", ignoreCase = true)) {
                runCatching {
                    generateM3u8(serverName, clean, "$mainUrl/").forEach {
                        callback(it)
                        found = true
                    }
                }
                callback(
                    newExtractorLink(
                        source = serverName,
                        name = "$serverName HLS",
                        url = clean,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.Unknown.value
                    }
                )
                found = true
            } else if (clean.contains(".mp4", ignoreCase = true)) {
                callback(
                    newExtractorLink(
                        source = serverName,
                        name = "$serverName MP4",
                        url = clean,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.Unknown.value
                    }
                )
                found = true
            }
        }

        suspend fun processExtractorUrl(rawUrl: String, pageReferer: String) {
            var cleanUrl = rawUrl.trim()
            if (cleanUrl.startsWith("//")) cleanUrl = "http:$cleanUrl"
            if (cleanUrl.isBlank() || extractedUrls.contains(cleanUrl) || cleanUrl.startsWith("about:blank", true)) return
            extractedUrls.add(cleanUrl)

            when {
                cleanUrl.contains("abyssplayer.com", true) || cleanUrl.contains("abysscdn.com", true) || cleanUrl.contains("sssrr.org", true) -> {
                    runCatching {
                        AbyssExtractor().getUrl(cleanUrl, pageReferer, subtitleCallback, callback)
                        found = true
                    }
                }
                cleanUrl.contains("streamwish", true) || cleanUrl.contains("hglink.to", true) || cleanUrl.contains("asnwish", true) -> {
                    runCatching {
                        SarangStreamWishExtractor().getUrl(cleanUrl, pageReferer, subtitleCallback, callback)
                        found = true
                    }
                    runCatching {
                        loadExtractor(cleanUrl, pageReferer, subtitleCallback, callback)
                        found = true
                    }
                }
                cleanUrl.contains("vidhide", true) || cleanUrl.contains("dintezuvio.com", true) || cleanUrl.contains("mevidhides.xyz", true) -> {
                    runCatching {
                        VidHideExtractor().getUrl(cleanUrl, pageReferer, subtitleCallback, callback)
                        found = true
                    }
                    runCatching {
                        loadExtractor(cleanUrl, pageReferer, subtitleCallback, callback)
                        found = true
                    }
                }
                else -> {
                    runCatching {
                        loadExtractor(cleanUrl, pageReferer, subtitleCallback, callback)
                        found = true
                    }
                    handleDirectVideoUrl(cleanUrl, "Direct Player")
                }
            }
        }

        suspend fun processPlaysobatEmbed(embedUrl: String, pageReferer: String) {
            try {
                val embedHtml = app.get(
                    embedUrl,
                    headers = mapOf("Referer" to pageReferer, "User-Agent" to UA_BROWSER),
                    timeout = 15
                ).text

                // Cari window.payload
                val payloadMatch = Regex("""window\.payload\s*=\s*["'](\{.*?\})["'];""").find(embedHtml)
                    ?: Regex("""window\.payload\s*=\s*"(\{.*?\})";""").find(embedHtml)

                if (payloadMatch != null) {
                    val rawPayloadJson = payloadMatch.groupValues[1]
                        .replace("\\\"", "\"")
                        .replace("\\/", "/")

                    val servers = decryptPlaysobatPayload(rawPayloadJson)
                    if (!servers.isNullOrEmpty()) {
                        for ((serverKey, serverUrl) in servers) {
                            var targetUrl = serverUrl.trim()
                            if (targetUrl.isBlank()) continue

                            // Transformasi pelayan playsobat mengikut logik rasmi
                            when (serverKey.uppercase()) {
                                "HYDRAX" -> {
                                    targetUrl = targetUrl.replace(".ink", ".icu")
                                        .replace("abysscdn.com", "abyssplayer.com")
                                    processExtractorUrl(targetUrl, embedUrl)
                                }
                                "VIDHIDE" -> {
                                    val id = targetUrl.substringAfterLast("/").substringBefore("?")
                                    val dintez = "https://dintezuvio.com/embed/$id"
                                    val vidhidepro = "https://vidhidepro.com/v/$id"
                                    processExtractorUrl(dintez, embedUrl)
                                    processExtractorUrl(vidhidepro, embedUrl)
                                }
                                "STREAMWISH" -> {
                                    val id = targetUrl.substringAfterLast("/").substringBefore("?")
                                    val hglink = "https://hglink.to/e/$id"
                                    processExtractorUrl(hglink, embedUrl)
                                }
                                "TURBOVIP" -> {
                                    val id = targetUrl.substringAfterLast("/").substringBefore("?")
                                    val turbo = "https://turbovidhls.com/t/$id"
                                    processExtractorUrl(turbo, embedUrl)
                                }
                                else -> {
                                    processExtractorUrl(targetUrl, embedUrl)
                                }
                            }
                        }
                    }
                }

                // Fallback: cari sebarang iframe di dalam laman playsobat
                val embedDoc = Jsoup.parse(embedHtml)
                embedDoc.select("iframe").forEach { ifr ->
                    val src = ifr.attr("src").ifBlank { ifr.attr("data-src") }
                    if (src.isNotBlank() && !src.startsWith("about:blank")) {
                        processExtractorUrl(src, embedUrl)
                    }
                }
            } catch (e: Exception) {
                Log.e("Sarangfilm21", "Error processing playsobat embed: ${e.message}")
            }
        }

        try {
            val fixedData = fixItemUrl(data)
            val doc = try {
                app.get(
                    fixedData,
                    interceptor = wpRedisInterceptor,
                    headers = mapOf("Referer" to "$mainUrl/", "User-Agent" to UA_BROWSER),
                    timeout = 25
                ).document
            } catch (_: Exception) {
                app.get(
                    fixedData,
                    headers = mapOf("Referer" to "$mainUrl/", "User-Agent" to UA_BROWSER),
                    timeout = 25
                ).document
            }

            // 1. Kumpul semua iframe di laman utama
            val iframes = doc.select("iframe").mapNotNull {
                val s = it.attr("src").ifBlank {
                    it.attr("data-src").ifBlank {
                        it.attr("data-litespeed-src")
                    }
                }
                if (s.isNotBlank() && !s.startsWith("about:blank") && !s.startsWith("javascript")) s else null
            }.distinct()

            for (ifr in iframes) {
                if (ifr.contains("playsobat.xyz", true)) {
                    processPlaysobatEmbed(ifr, fixedData)
                } else {
                    processExtractorUrl(ifr, fixedData)
                }
            }

            // 2. Tab pelayan video muvipro / player-nav
            doc.select(".muvipro-player-tabs a, ul.nav-tabs a, #loadplayers a, a[href*='player='], .gmr-player-nav a").forEach { a ->
                val href = a.attr("href")
                if (href.isNotBlank() && !href.startsWith("#") && href != "javascript:void(0)" && href != fixedData) {
                    try {
                        val tabUrl = fixItemUrl(href)
                        val tabDoc = app.get(
                            tabUrl,
                            interceptor = wpRedisInterceptor,
                            headers = mapOf("Referer" to fixedData, "User-Agent" to UA_BROWSER),
                            timeout = 10
                        ).document

                        tabDoc.select("iframe").forEach { ifr ->
                            val s = ifr.attr("src").ifBlank { ifr.attr("data-src") }
                            if (s.isNotBlank() && !s.startsWith("about:blank")) {
                                if (s.contains("playsobat.xyz", true)) {
                                    processPlaysobatEmbed(s, tabUrl)
                                } else {
                                    processExtractorUrl(s, tabUrl)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // 3. Regex sandaran untuk pautan media langsung m3u8 / mp4
            val pageText = doc.html()
            val directStreams = Regex("""https?://[^\s"'\]+\.(?:m3u8|mp4)[^\s"'\]*""").findAll(pageText).map { it.value }.toList()
            for (streamUrl in directStreams) {
                handleDirectVideoUrl(streamUrl, "Direct Stream")
            }

        } catch (e: Exception) {
            Log.e("Sarangfilm21", "Error in loadLinks: ${e.message}")
        }

        return found
    }
}

// Alias class untuk mengekalkan keserasian penuh
class Sarangfilm21Provider : Sarangfilm21()
