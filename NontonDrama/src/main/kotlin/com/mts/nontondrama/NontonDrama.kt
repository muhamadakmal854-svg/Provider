package com.mts.nontondrama

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class NontonDramaProvider : MainAPI() {
    override var mainUrl = "https://tv4.nontondrama.my"
    override var name = "NontonDrama"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.AsianDrama, TvType.Movie)

    val wpRedisInterceptor by lazy { CloudflareKiller() }

    override val mainPage = mainPageOf(
        "" to "✨ Pilihan Utama (Spotlight Series)",
        "populer/" to "🔥 Drama & Siri Terpopuler (Popular Series)",
        "series/ongoing/" to "⚡ Sedang Tayang (Ongoing Series)",
        "series/complete/" to "🏁 Siri Lengkap & Tamat (Complete Series)",
        "country/south-korea/" to "🇰🇷 Drama Korea Pilihan (K-Drama)",
        "country/china/" to "🇨🇳 Drama Mandarin & China (C-Drama)",
        "country/japan/" to "🇯🇵 Siri & Drama Jepun (J-Drama)",
        "series/asian/" to "🌏 Siri Asia Pilihan (Asian Series)",
        "series/west/" to "🇺🇸 Siri Barat & Hollywood (Western Series)",
        "genre/action/" to "💥 Aksi & Suspen (Action & Thriller)",
        "genre/romance/" to "💖 Romantik & Percintaan (Romance)",
        "genre/comedy/" to "😂 Komedi & Santai (Comedy)",
        "genre/fantasy/" to "🔮 Fantasi & Magis (Fantasy)",
        "genre/horror/" to "👻 Horor & Misteri (Horror & Mystery)",
        "genre/crime/" to "🔍 Penyiasatan & Jenayah (Crime)",
        "genre/sci-fi/" to "🚀 Fiksi Ilmiah (Sci-Fi)",
        "latest/" to "🆕 Episod Baru Diupload (Latest Update)",
        "year/2026/" to "📅 Siri Rilisan 2026 (Tahun 2026)",
        "rating/" to "⭐ Rating Tertinggi (Top Rated)"
    )

    private fun cleanTitle(rawTitle: String?): String {
        if (rawTitle.isNullOrBlank()) return ""
        return rawTitle
            .replace(Regex("""(?i)^\s*(?:Nonton\s+(?:series|film)?|Streaming|Lk21\s+Nonton)\s*"""), "")
            .replace(Regex("""(?i)\s+(?:streaming|sub\s*indo|subtitle\s*indonesia|gratis).*$"""), "")
            .trim()
    }

    private fun extractPoster(element: Element): String? {
        val pic = element.selectFirst("picture")
        val sourceWebp = pic?.selectFirst("source[type*='webp']")?.attr("srcset")
        val sourceJpg = pic?.selectFirst("source[type*='jpeg']")?.attr("srcset")
        val img = element.selectFirst("img")
        val imgSrc = img?.attr("src")?.ifBlank { null }
            ?: img?.attr("data-src")?.ifBlank { null }
            ?: img?.attr("data-srcset")?.ifBlank { null }
            ?: img?.attr("data-original")?.ifBlank { null }

        val candidate = sourceWebp ?: sourceJpg ?: imgSrc
        if (candidate.isNullOrBlank()) return null

        val highest = candidate.split(",")
            .map { it.trim().substringBefore(" ") }
            .filter { it.isNotBlank() && !it.endsWith(".svg", true) && !it.endsWith(".gif", true) }
            .lastOrNull() ?: candidate

        return fixUrlNull(highest)
    }

    private fun parseDateToEpoch(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        val clean = dateStr.trim()
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss.SSSSSS",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd",
            "dd MMM yyyy",
            "dd MMMM yyyy",
            "dd/MM/yyyy",
            "dd-MM-yyyy"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.ENGLISH)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val d = sdf.parse(clean)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
        return null
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val pageUrl = if (request.data.isBlank()) {
            if (page <= 1) "$mainUrl/" else "$mainUrl/page/$page/"
        } else {
            val cleanData = request.data.trim().removePrefix("/").removeSuffix("/")
            if (page <= 1) "$mainUrl/$cleanData/" else "$mainUrl/$cleanData/page/$page/"
        }

        val document = try {
            app.get(pageUrl, interceptor = wpRedisInterceptor).document
        } catch (_: Exception) {
            app.get(pageUrl).document
        }

        val items = mutableListOf<SearchResponse>()

        // 1. Featured Slider on Home (Spotlight)
        if (request.data.isBlank() && page <= 1) {
            document.select("ul#featured-slider li.slider article, ul.sliders li.slider article").forEach { el ->
                val a = el.selectFirst("figure a") ?: el.selectFirst("a") ?: return@forEach
                val href = a.attr("href") ?: return@forEach
                if (href.isBlank() || href == "#" || href.startsWith("javascript:")) return@forEach
                val rawTitle = el.selectFirst(".poster-title")?.text()
                    ?: a.attr("title")
                    ?: el.selectFirst("h3")?.text()
                    ?: el.selectFirst("img")?.attr("alt")
                    ?: ""
                val title = cleanTitle(rawTitle)
                if (title.isBlank() || title == "#") return@forEach

                val poster = extractPoster(el)
                val fullUrl = fixUrl(href)

                val card = newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries) {
                    this.posterUrl = poster
                }
                items.add(card)
            }
        }

        // 2. Main Grid Items
        val gridArticles = document.select("article, div.gmr-box-item, article.item-infinite, .search-item")
        gridArticles.forEach { el ->
            val a = el.selectFirst("figure a") ?: el.selectFirst("a") ?: return@forEach
            val href = a.attr("href") ?: return@forEach
            if (href.isBlank() || href == "#" || href.startsWith("javascript:")) return@forEach

            // Filter out navigation or external links
            if (href.contains("d21.team", true) || href.contains("layarkaca21.com", true) || href.contains("showcdnx.com", true)) {
                return@forEach
            }

            val rawTitle = el.selectFirst(".poster-title")?.text()
                ?: el.selectFirst("h3")?.text()
                ?: a.attr("title")
                ?: el.selectFirst("img")?.attr("alt")
                ?: ""
            val title = cleanTitle(rawTitle)
            if (title.isBlank() || title == "#") return@forEach

            val poster = extractPoster(el)
            val fullUrl = fixUrl(href)

            val card = newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries) {
                this.posterUrl = poster
            }
            items.add(card)
        }

        // Deduplicate items by URL, preferring items with poster
        val distinctItems = items.groupBy { it.url }.map { (_, list) ->
            list.firstOrNull { !it.posterUrl.isNullOrBlank() } ?: list.first()
        }

        return newHomePageResponse(request.name, distinctItems, hasNext = distinctItems.isNotEmpty())
    }

    data class SearchItem(
        val title: String?,
        val slug: String?,
        val type: String?
    )
    
    data class GudangVapeSearchResponse(
        val total: Int?,
        val results: List<SearchItem>?
    )

    override suspend fun search(query: String): List<SearchResponse> {
        // 1. Fast JSON search via gudangvape.com
        try {
            val response = app.get(
                "https://gudangvape.com/",
                params = mapOf("s" to query),
                headers = mapOf(
                    "Referer" to "$mainUrl/",
                    "Origin" to mainUrl
                )
            ).text
            val searchResults = tryParseJson<GudangVapeSearchResponse>(response)
            val results = searchResults?.results ?: emptyList()
            if (results.isNotEmpty()) {
                return results.mapNotNull { item: SearchItem ->
                    val slug = item.slug ?: return@mapNotNull null
                    val title = cleanTitle(item.title)
                    if (title.isBlank() || slug.isBlank()) return@mapNotNull null
                    val pageUrl = "$mainUrl/$slug"
                    val poster = "https://poster.showcdnx.com/wp-content/uploads/film-$slug-lk21.jpg"
                    newTvSeriesSearchResponse(title, pageUrl, TvType.TvSeries) {
                        this.posterUrl = poster
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NontonDrama", "Error in gudangvape search: ${e.message}")
        }

        // 2. Fallback search via HTML
        return try {
            val searchUrl = "$mainUrl/search/?s=${query.trim().replace(" ", "+")}"
            val doc = app.get(searchUrl, interceptor = wpRedisInterceptor).document
            val items = doc.select("article, .search-item, div.gmr-box-item").mapNotNull { el ->
                val a = el.selectFirst("figure a") ?: el.selectFirst("a") ?: return@mapNotNull null
                val href = a.attr("href") ?: return@mapNotNull null
                val rawTitle = el.selectFirst(".poster-title")?.text() ?: a.attr("title") ?: el.selectFirst("h3")?.text() ?: ""
                val title = cleanTitle(rawTitle)
                if (title.isBlank()) return@mapNotNull null
                val poster = extractPoster(el)
                newTvSeriesSearchResponse(title, fixUrl(href), TvType.TvSeries) {
                    this.posterUrl = poster
                }
            }
            items.distinctBy { it.url }
        } catch (e: Exception) {
            Log.e("NontonDrama", "Error in fallback search: ${e.message}")
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = try {
            app.get(url, interceptor = wpRedisInterceptor).document
        } catch (_: Exception) {
            app.get(url).document
        }

        // 1. Detect if this is an individual episode page and fetch parent series URL
        var parentSeriesDoc = document
        var parentSeriesUrl = url

        val partOfSeriesUrl = document.select("script[type='application/ld+json']").firstNotNullOfOrNull { script ->
            runCatching {
                val json = JSONObject(script.html())
                if (json.optString("@type") == "TVEpisode") {
                    json.optJSONObject("partOfSeries")?.optString("url")
                } else null
            }.getOrNull()
        }

        if (!partOfSeriesUrl.isNullOrBlank() && partOfSeriesUrl != url) {
            val fixedSeriesUrl = fixUrl(partOfSeriesUrl)
            runCatching {
                parentSeriesDoc = app.get(fixedSeriesUrl, interceptor = wpRedisInterceptor).document
                parentSeriesUrl = fixedSeriesUrl
            }
        }

        val rawTitle = parentSeriesDoc.selectFirst(".entry-title, h1.entry-title, h1")?.text()
            ?: document.selectFirst(".entry-title, h1.entry-title, h1")?.text()
            ?: ""
        val title = cleanTitle(rawTitle)

        val poster = extractPoster(parentSeriesDoc)
            ?: extractPoster(document)
            ?: parentSeriesDoc.selectFirst("meta[property='og:image']")?.attr("content")
            ?: document.selectFirst("meta[property='og:image']")?.attr("content")

        val plot = parentSeriesDoc.selectFirst("meta[property='og:description']")?.attr("content")
            ?: document.selectFirst("meta[property='og:description']")?.attr("content")
            ?: parentSeriesDoc.selectFirst(".synopsis, .entry-content p")?.text()?.trim()

        val year = parentSeriesDoc.selectFirst(".detail p:contains(Release)")?.text()
            ?.let { Regex("""\b(19\d\d|20\d\d)\b""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: Regex("""\b(19\d\d|20\d\d)\b""").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()

        val rating = parentSeriesDoc.selectFirst(".rating span[itemprop='ratingValue']")?.text()?.toRatingInt()

        // 2. Extract release date from episode or series page
        val dateText = document.selectFirst(".detail p:contains(Release)")?.text()?.replace("Release:", "")?.trim()
            ?: document.select("script[type='application/ld+json']").firstNotNullOfOrNull { script ->
                runCatching {
                    val json = JSONObject(script.html())
                    if (json.optString("@type") == "TVEpisode") {
                        json.optString("datePublished")
                    } else null
                }.getOrNull()
            }
        val defaultEpoch = parseDateToEpoch(dateText)

        // 3. Parse all episodes from script tag: {"1": [...], "2": [...]}
        val episodes = mutableListOf<Episode>()
        val scriptContent = parentSeriesDoc.select("script").firstOrNull { it.html().contains("episode_no") }?.html()
            ?: document.select("script").firstOrNull { it.html().contains("episode_no") }?.html()

        if (!scriptContent.isNullOrBlank()) {
            runCatching {
                val jsonMatch = Regex("""\{.*\"episode_no\".*\}""", RegexOption.DOT_MATCHES_ALL).find(scriptContent)?.value
                    ?: scriptContent.substringAfter("=").trim().substringBeforeLast(";")
                val jsonObject = JSONObject(jsonMatch)
                val seasonKeys = jsonObject.keys()

                while (seasonKeys.hasNext()) {
                    val sKey = seasonKeys.next()
                    val seasonNum = sKey.toIntOrNull() ?: 1
                    val arr = jsonObject.optJSONArray(sKey) ?: continue

                    for (i in 0 until arr.length()) {
                        val epObj = arr.optJSONObject(i) ?: continue
                        val epNum = epObj.optInt("episode_no", i + 1)
                        val epSlug = epObj.optString("slug", "")
                        val epTitleRaw = epObj.optString("title", "")
                        val cleanEpTitle = cleanTitle(epTitleRaw).ifBlank { "Episode $epNum" }

                        if (epSlug.isNotBlank()) {
                            val epUrl = "$mainUrl/$epSlug"
                            val ep = newEpisode(epUrl) {
                                this.name = "Episode $epNum: $cleanEpTitle"
                                this.episode = epNum
                                this.season = seasonNum
                                this.posterUrl = poster
                                this.date = defaultEpoch
                                this.description = if (!dateText.isNullOrBlank()) "Rilis: $dateText • Sub Indo" else "Sub Indo"
                            }
                            episodes.add(ep)
                        }
                    }
                }
            }.onFailure { e ->
                Log.e("NontonDrama", "Error parsing episodes script: ${e.message}")
            }
        }

        // 4. Fallback if no JSON script found
        if (episodes.isEmpty()) {
            val singleEp = newEpisode(url) {
                this.name = "Episode 1: $title"
                this.episode = 1
                this.season = 1
                this.posterUrl = poster
                this.date = defaultEpoch
                this.description = if (!dateText.isNullOrBlank()) "Rilis: $dateText • Sub Indo" else "Sub Indo"
            }
            episodes.add(singleEp)
        }

        return newTvSeriesLoadResponse(title, parentSeriesUrl, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.rating = rating
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val document = try {
                app.get(data, interceptor = wpRedisInterceptor).document
            } catch (_: Exception) {
                app.get(data).document
            }

            val linkElements = document.select("#player-list li a, #player-select option, .player-options a, select#player-select option")
            
            linkElements.forEach { element ->
                val embedUrl = element.attr("data-url")
                    .ifBlank { element.attr("href") }
                    .ifBlank { element.attr("value") }
                
                if (embedUrl.isNotBlank() && embedUrl != "#" && !embedUrl.startsWith("javascript:")) {
                    resolveAndExtract(embedUrl, data, subtitleCallback, callback)
                }
            }

            // Also check main-player iframe
            document.select("iframe#main-player, .main-player iframe, div.player-area iframe").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.isNotBlank() && src != "#") {
                    resolveAndExtract(src, data, subtitleCallback, callback)
                }
            }

            // Also scan download button (dadadidi.de)
            document.select("a[href*='dadadidi.de'], a.btn:contains(DOWNLOAD)").forEach { a ->
                val dlUrl = a.attr("href")
                if (dlUrl.isNotBlank() && dlUrl.startsWith("http")) {
                    runCatching {
                        val dlDoc = app.get(dlUrl, headers = mapOf("Referer" to data)).document
                        dlDoc.select("a[href]").forEach { dlLink ->
                            val href = dlLink.attr("href")
                            if (href.endsWith(".mp4", true) || href.endsWith(".m3u8", true)) {
                                callback.invoke(
                                    newExtractorLink(
                                        source = name,
                                        name = "$name Direct Video",
                                        url = href,
                                        type = if (href.contains(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                    )
                                )
                            }
                        }
                    }
                }
            }

            return true
        } catch (e: Exception) {
            Log.e("NontonDrama", "Error in loadLinks: ${e.message}")
            return false
        }
    }

    private suspend fun resolveAndExtract(
        rawUrl: String,
        pageUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
        val id = cleanUrl.substringAfterLast("/")

        when {
            // Turbovip (turbovidhls / emturbovid) -> Direct Master M3U8
            cleanUrl.contains("/turbovip/") -> {
                loadExtractor("https://turbovidhls.com/t/$id", pageUrl, subtitleCallback, callback)
                loadExtractor("https://emturbovid.com/t/$id", pageUrl, subtitleCallback, callback)
            }

            // Hydrax / Abyss -> AES-CTR Decrypted Sora Stream
            cleanUrl.contains("/hydrax/") -> {
                loadExtractor("https://abyssplayer.com/$id", "https://playeriframe.sbs/", subtitleCallback, callback)
                loadExtractor("https://abyss.to/$id", "https://playeriframe.sbs/", subtitleCallback, callback)
            }

            // Cast -> Gn1r5n / StreamWish (needs playeriframe.sbs referer)
            cleanUrl.contains("/cast/") -> {
                loadExtractor("https://gn1r5n.org/e/$id", "https://playeriframe.sbs/", subtitleCallback, callback)
            }

            // P2P -> Hownetwork
            cleanUrl.contains("/p2p/") -> {
                loadExtractor("https://cloud.hownetwork.xyz/video.php?id=$id", "https://playeriframe.sbs/", subtitleCallback, callback)
            }

            else -> {
                loadExtractor(cleanUrl, pageUrl, subtitleCallback, callback)
            }
        }
    }
}
