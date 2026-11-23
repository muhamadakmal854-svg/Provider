package com.mts.dramadunyam

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.net.URLEncoder

class Dramadunyam : MainAPI() {

    override var mainUrl = "https://dramadunyam.com"
    override var name = "Dramadunyam"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.TvSeries)

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        @Volatile
        private var cachedBilet: String? = null
        private var lastBiletFetchTime = 0L
    }

    private suspend fun getBilet(): String? {
        val now = System.currentTimeMillis()
        if (cachedBilet != null && (now - lastBiletFetchTime < 3600000L)) {
            return cachedBilet
        }
        return try {
            val res = app.get(
                "$mainUrl/api/config",
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/id"
                ),
                timeout = 15
            )
            val setCookie = res.headers["set-cookie"] ?: res.headers["Set-Cookie"]
            val m = Regex("""(dd_bilet=[^;\r\n]+)""").find(setCookie.orEmpty())
            val bilet = m?.groupValues?.getOrNull(1)
            if (bilet != null) {
                cachedBilet = bilet
                lastBiletFetchTime = now
            }
            cachedBilet
        } catch (_: Exception) {
            cachedBilet
        }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/api/series?page=1&limit=20&lang=id" to "Terkini (Semua)",
        "$mainUrl/api/series?platform=FreeReels&page=1&limit=20&lang=id" to "FreeReels",
        "$mainUrl/api/series?platform=DramaBox&page=1&limit=20&lang=id" to "DramaBox",
        "$mainUrl/api/series?platform=ShortMax&page=1&limit=20&lang=id" to "ShortMax",
        "$mainUrl/api/series?platform=ReelShort&page=1&limit=20&lang=id" to "ReelShort",
        "$mainUrl/api/series?tag=balas-dendam&page=1&limit=20&lang=id" to "Balas Dendam (Revenge)",
        "$mainUrl/api/series?tag=romantis&page=1&limit=20&lang=id" to "Romantis (Romance)",
        "$mainUrl/api/series?tag=bisnis&page=1&limit=20&lang=id" to "Bisnis (Business)",
        "$mainUrl/api/series?tag=pengkhianatan&page=1&limit=20&lang=id" to "Pengkhianatan (Betrayal)",
        "$mainUrl/api/series?tag=kota&page=1&limit=20&lang=id" to "Kehidupan Kota (Urban)",
        "$mainUrl/api/series?tag=keluarga&page=1&limit=20&lang=id" to "Keluarga (Family)"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val targetUrl = if (page > 1) {
            request.data.replace(Regex("""page=\d+"""), "page=$page")
        } else {
            request.data
        }

        val bilet = getBilet()
        val headers = mutableMapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$mainUrl/id",
            "Accept" to "application/json"
        )
        if (bilet != null) {
            headers["Cookie"] = bilet
        }

        val jsonText = try {
            app.get(targetUrl, headers = headers, timeout = 15).text
        } catch (_: Exception) {
            return null
        }

        val json = try {
            JSONObject(jsonText)
        } catch (_: Exception) {
            return null
        }

        val dataArr = json.optJSONArray("data") ?: return null
        if (dataArr.length() == 0) return null

        val items = mutableListOf<SearchResponse>()
        for (i in 0 until dataArr.length()) {
            val obj = dataArr.optJSONObject(i) ?: continue
            val title = obj.optString("title", "").trim()
            val slug = obj.optString("slug", "").trim()
            if (title.isBlank() || slug.isBlank()) continue

            val cover = obj.optString("cover", "").trim().ifBlank { null }
            val postUrl = "$mainUrl/id/serial/$slug"

            items.add(newTvSeriesSearchResponse(title, postUrl, TvType.AsianDrama) {
                this.posterUrl = cover
            })
        }

        return if (items.isNotEmpty()) {
            newHomePageResponse(request.name, items, hasNext = items.size >= 20)
        } else {
            null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        // Jika carian adalah URL terus ke drama
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val slugMatch = Regex("""/serial/([^/?#]+)""").find(trimmed)
            if (slugMatch != null) {
                val slug = slugMatch.groupValues[1]
                val postUrl = "$mainUrl/id/serial/$slug"
                val displayTitle = slug.replace('-', ' ').split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                return listOf(newTvSeriesSearchResponse(displayTitle, postUrl, TvType.AsianDrama))
            }
        }

        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val searchUrl = "$mainUrl/api/search?q=$encoded&lang=id&page=1&limit=30"

        val bilet = getBilet()
        val headers = mutableMapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$mainUrl/id",
            "Accept" to "application/json"
        )
        if (bilet != null) {
            headers["Cookie"] = bilet
        }

        val jsonText = try {
            app.get(searchUrl, headers = headers, timeout = 15).text
        } catch (_: Exception) {
            return emptyList()
        }

        val json = try {
            JSONObject(jsonText)
        } catch (_: Exception) {
            return emptyList()
        }

        val dataArr = json.optJSONArray("data") ?: return emptyList()
        val results = mutableListOf<SearchResponse>()
        for (i in 0 until dataArr.length()) {
            val obj = dataArr.optJSONObject(i) ?: continue
            val title = obj.optString("title", "").trim()
            val slug = obj.optString("slug", "").trim()
            if (title.isBlank() || slug.isBlank()) continue

            val cover = obj.optString("cover", "").trim().ifBlank { null }
            val postUrl = "$mainUrl/id/serial/$slug"

            results.add(newTvSeriesSearchResponse(title, postUrl, TvType.AsianDrama) {
                this.posterUrl = cover
            })
        }

        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val cleanUrl = if (url.startsWith("http")) url else "$mainUrl$url"
        val bilet = getBilet()
        val headers = mutableMapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$mainUrl/id"
        )
        if (bilet != null) {
            headers["Cookie"] = bilet
        }

        val doc = try {
            app.get(cleanUrl, headers = headers, timeout = 15).document
        } catch (_: Exception) {
            return null
        }

        val rawTitle = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property='og:title']")?.attr("content")?.trim()
            ?: "Drama"

        val cleanTitle = rawTitle
            .replace(Regex("""\s*Sub Indo.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*Dubbing.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*Full Episode.*""", RegexOption.IGNORE_CASE), "")
            .trim()

        val plot = doc.selectFirst("meta[property='og:description']")?.attr("content")?.trim()
            ?: doc.selectFirst("meta[name='description']")?.attr("content")?.trim()

        val poster = doc.selectFirst("meta[property='og:image']")?.attr("content")?.trim()
            ?: doc.selectFirst("img[src*='covers']")?.attr("src")?.trim()

        // Anggarkan jumlah episod
        val cntMatch = Regex("""\((\d+)\s*Episode\)""", RegexOption.IGNORE_CASE).find(rawTitle)
            ?: Regex("""(\d+)\s*episode""", RegexOption.IGNORE_CASE).find(plot.orEmpty())
        val estimatedTotal = cntMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 30

        // Kumpul semua pautan episod yang disenaraikan
        val epLinks = doc.select("a[href*='/episode-']")
        val epMap = mutableMapOf<Int, String>()
        for (a in epLinks) {
            val href = a.attr("href")
            val epNum = Regex("""/episode-(\d+)""").find(href)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (epNum != null) {
                epMap[epNum] = fixUrl(href)
            }
        }

        val totalEpisodes = maxOf(estimatedTotal, epMap.keys.maxOrNull() ?: 1)
        val episodes = mutableListOf<Episode>()
        val baseSerialUrl = cleanUrl.replace(Regex("""/episode-\d+.*"""), "").trimEnd('/')

        for (ep in 1..totalEpisodes) {
            val epUrl = epMap[ep] ?: "$baseSerialUrl/episode-$ep"
            episodes.add(
                newEpisode(epUrl) {
                    this.name = "Episode $ep"
                    this.episode = ep
                    this.posterUrl = poster
                }
            )
        }

        return newTvSeriesLoadResponse(cleanTitle, cleanUrl, TvType.AsianDrama, episodes) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val epUrl = if (data.startsWith("http")) data else "$mainUrl$data"
        val bilet = getBilet()
        val headers = mutableMapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$mainUrl/"
        )
        if (bilet != null) {
            headers["Cookie"] = bilet
        }

        val epRes = try {
            app.get(epUrl, headers = headers, timeout = 15)
        } catch (_: Exception) {
            return false
        }

        // Kemas kini bilet jika ada cookie baharu dalam respons
        val setCookie = epRes.headers["set-cookie"] ?: epRes.headers["Set-Cookie"]
        val newBilet = Regex("""(dd_bilet=[^;\r\n]+)""").find(setCookie.orEmpty())?.groupValues?.getOrNull(1)
        if (newBilet != null) {
            cachedBilet = newBilet
            lastBiletFetchTime = System.currentTimeMillis()
        }
        val activeBilet = newBilet ?: bilet

        val html = epRes.text
        val hlsRelative = Regex("""<video[^>]+src=["']([^"']+)["']""").find(html)?.groupValues?.getOrNull(1)
            ?: Regex("""(/hls/\d+/\d+/playlist\.m3u8)""").find(html)?.groupValues?.getOrNull(1)
            ?: return false

        val fullHlsUrl = if (hlsRelative.startsWith("http")) hlsRelative else "$mainUrl$hlsRelative"
        val hlsHeaders = mutableMapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to epUrl
        )
        if (activeBilet != null) {
            hlsHeaders["Cookie"] = activeBilet
        }

        // Dapatkan pautan aliran terus melalui 302 redirect
        val redirectRes = try {
            app.get(fullHlsUrl, headers = hlsHeaders, allowRedirects = false, timeout = 15)
        } catch (_: Exception) {
            null
        }

        val location = redirectRes?.headers?.get("location") ?: redirectRes?.headers?.get("Location")
        val streamUrl = if (!location.isNullOrBlank()) {
            if (location.startsWith("http")) location else "$mainUrl$location"
        } else {
            fullHlsUrl
        }

        var foundAny = false

        // 1. Pecahkan kualiti M3U8 (1080p, 720p, 480p, dll.) secara automatik
        try {
            generateM3u8(
                source = name,
                streamUrl = streamUrl,
                referer = "$mainUrl/"
            ).forEach { link ->
                callback.invoke(link)
                foundAny = true
            }
        } catch (_: Exception) {}

        // 2. Sandaran pautan M3U8 terus jika penjanaan senarai kualiti gagal
        if (!foundAny) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "$name (HLS Auto)",
                    url = streamUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.P1080.value
                }
            )
            foundAny = true
        }

        return foundAny
    }
}
