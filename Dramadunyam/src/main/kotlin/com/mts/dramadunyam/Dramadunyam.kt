package com.mts.dramadunyam

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.net.URLEncoder

class Dramadunyam : MainAPI() {

    override var mainUrl = "https://dramadunyam.com"
    override var name = "Dramadunyam"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.TvSeries)

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val MIRROR_URL = "https://dramadunyam.org"
    }

    private fun toMirror(url: String): String {
        return url.replace("https://dramadunyam.com", MIRROR_URL)
            .replace("http://dramadunyam.com", MIRROR_URL)
    }

    private suspend fun fetchDocument(url: String): Document? {
        val target = toMirror(url)
        return try {
            app.get(
                target,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$MIRROR_URL/"
                ),
                timeout = 15
            ).document
        } catch (_: Exception) {
            try {
                app.get(
                    url,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to "$mainUrl/"
                    ),
                    timeout = 15
                ).document
            } catch (_: Exception) {
                null
            }
        }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/" to "🔥 Terkini (Latest Series)",
        "$mainUrl/category/dramabox/" to "🎬 DramaBox",
        "$mainUrl/category/goodshort/" to "⭐ GoodShort",
        "$mainUrl/category/reelshort/" to "⚡ ReelShort",
        "$mainUrl/category/shortmax/" to "💎 ShortMax",
        "$mainUrl/category/dramawave/" to "🌊 DramaWave",
        "$mainUrl/category/flickreels/" to "🎞️ FlickReels",
        "$mainUrl/category/happyshort/" to "✨ HappyShort",
        "$mainUrl/category/flareflow/" to "🔥 FlareFlow",
        "$mainUrl/category/netshort/" to "🌐 NetShort",
        "$mainUrl/category/moboreels/" to "📱 MoboReels",
        "$mainUrl/tag/billionaire/" to "💰 Billionaire / CEO",
        "$mainUrl/tag/revenge/" to "⚔️ Revenge",
        "$mainUrl/tag/romance/" to "💖 Romance",
        "$mainUrl/tag/hidden-identity/" to "🎭 Hidden Identity",
        "$mainUrl/tag/contract-marriage/" to "💍 Contract Marriage",
        "$mainUrl/tag/mafia/" to "🕶️ Mafia",
        "$mainUrl/tag/werewolf/" to "🐺 Werewolf & Fantasy",
        "$mainUrl/tag/rebirth/" to "⏳ Rebirth & Time Travel"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val baseUrl = request.data.removeSuffix("/")
        val targetUrl = if (page <= 1) {
            baseUrl
        } else {
            if (baseUrl.contains("?")) {
                val parts = baseUrl.split("?", limit = 2)
                "${parts[0]}/page/$page/?${parts[1]}"
            } else {
                "$baseUrl/page/$page/"
            }
        }

        val doc = fetchDocument(targetUrl) ?: return newHomePageResponse(request.name, emptyList())
        val items = parseCards(doc)
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/?s=$encoded"
        val doc = fetchDocument(searchUrl) ?: return emptyList()
        return parseCards(doc)
    }

    private fun parseCards(doc: Document): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        val seenUrls = mutableSetOf<String>()

        val cards = doc.select("article.entry, article.post, li.entry-list-item, div.post")
        for (card in cards) {
            val a = card.selectFirst("h2.entry-title a, a.post-thumbnail, a[href]") ?: continue
            val href = a.attr("href").trim()
            if (href.isBlank() || href == "/" || href == mainUrl || href == "$mainUrl/" ||
                href == MIRROR_URL || href == "$MIRROR_URL/" ||
                href.contains("/category/") || href.contains("/tag/") || href.contains("/wp-content/") ||
                href.contains("#") || seenUrls.contains(href)
            ) continue

            val titleEl = card.selectFirst("h2.entry-title, .entry-title, h2, h3")
            val rawTitle = (titleEl?.text()?.trim() ?: a.text().trim()).ifBlank {
                card.selectFirst("img")?.attr("alt")?.trim() ?: ""
            }
            if (rawTitle.isBlank()) continue

            val img = card.selectFirst("img")
            val poster = img?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("data-lazy-src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("src")?.takeIf { it.isNotBlank() && !it.startsWith("data:image") }

            seenUrls.add(href)
            results.add(
                newTvSeriesSearchResponse(rawTitle, href, TvType.AsianDrama) {
                    this.posterUrl = poster
                }
            )
        }

        if (results.size < 5) {
            for (a in doc.select("a[href]")) {
                val href = a.attr("href").trim()
                if (href.isBlank() || href == "/" || href == mainUrl || href == "$mainUrl/" ||
                    href == MIRROR_URL || href == "$MIRROR_URL/" ||
                    href.contains("/category/") || href.contains("/tag/") || href.contains("/wp-content/") ||
                    href.contains("#") || href.contains("privacy") || href.contains("terms") ||
                    href.contains("dmca") || href.contains("data-collection") || seenUrls.contains(href)
                ) continue

                val img = a.selectFirst("img") ?: continue
                val poster = img.attr("data-src").takeIf { it.isNotBlank() }
                    ?: img.attr("data-lazy-src").takeIf { it.isNotBlank() }
                    ?: img.attr("src").takeIf { it.isNotBlank() && !it.startsWith("data:image") }

                val rawTitle = a.attr("title").takeIf { it.isNotBlank() }
                    ?: img.attr("alt").takeIf { it.isNotBlank() }
                    ?: a.text().trim().takeIf { it.isNotBlank() }
                    ?: continue

                seenUrls.add(href)
                results.add(
                    newTvSeriesSearchResponse(rawTitle, href, TvType.AsianDrama) {
                        this.posterUrl = poster
                    }
                )
            }
        }

        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = fetchDocument(url) ?: return null
        val html = doc.html()

        val title = doc.selectFirst("h1.entry-title, .asdi-ov-series, h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property='og:title']")?.attr("content")?.removeSuffix(" | Dramadunyam")?.trim()
            ?: "Short Drama"

        val poster = doc.selectFirst("img.asdi-poster")?.attr("data-src")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("img.asdi-poster")?.attr("src")?.takeIf { it.isNotBlank() && !it.startsWith("data:image") }
            ?: doc.selectFirst("meta[property='og:image']")?.attr("content")?.takeIf { it.isNotBlank() }

        val plot = doc.selectFirst("meta[property='og:description']")?.attr("content")?.trim()
            ?: doc.selectFirst(".entry-content p, .asdi-desc, .description")?.text()?.trim()

        val tags = doc.select("a[href*='/tag/'], a[href*='/category/']")
            .map { it.text().trim() }
            .filter { it.isNotBlank() && !it.contains("series", ignoreCase = true) }

        val episodesList = mutableListOf<Episode>()

        val codeMatch = Regex("""\{"post":\d+,"code":"([^"]+)".*?"episodes":(\[.*?\])(?:,"|\})""").find(html)
        if (codeMatch != null) {
            val code = codeMatch.groupValues[1]
            val epsJsonStr = codeMatch.groupValues[2]
            try {
                val epsArray = JSONArray(epsJsonStr)
                for (i in 0 until epsArray.length()) {
                    val epObj = epsArray.optJSONObject(i) ?: continue
                    val epNum = epObj.optInt("n", i + 1)
                    val rawEpTitle = epObj.optString("t", "Episode $epNum")
                    val epTitle = rawEpTitle.split("–")[0].trim().ifBlank { "Episode $epNum" }
                    val linkData = "$code|$epNum|$url"

                    episodesList.add(
                        newEpisode(linkData) {
                            this.name = epTitle
                            this.episode = epNum
                            this.season = 1
                            this.posterUrl = poster
                        }
                    )
                }
            } catch (_: Exception) {}
        }

        if (episodesList.isEmpty()) {
            val epCountText = doc.selectFirst(".asdi-eps-count")?.text()?.trim()
            val count = Regex("""(\d+)""").find(epCountText ?: "")?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val codeFallback = Regex("\"code\":\"(dp-[a-zA-Z0-9]+)\"").find(html)?.groupValues?.get(1) ?: ""
            for (epNum in 1..count) {
                val linkData = "$codeFallback|$epNum|$url"
                episodesList.add(
                    newEpisode(linkData) {
                        this.name = "Episode $epNum"
                        this.episode = epNum
                        this.season = 1
                        this.posterUrl = poster
                    }
                )
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodesList) {
            this.posterUrl = poster
            this.plot = plot
            this.tags = tags.distinct()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parts = data.split("|")
        val code: String
        val ep: Int
        val pageUrl: String

        if (parts.size >= 3) {
            code = parts[0].trim()
            ep = parts[1].trim().toIntOrNull() ?: 1
            pageUrl = parts[2].trim()
        } else if (data.startsWith("http")) {
            pageUrl = data
            val doc = fetchDocument(pageUrl)
            code = Regex("\"code\":\"(dp-[a-zA-Z0-9]+)\"").find(doc?.html() ?: "")?.groupValues?.get(1) ?: ""
            ep = 1
        } else {
            return false
        }

        if (code.isBlank()) return false

        val streamApiUrl = "$MIRROR_URL/wp-json/asdi/v1/stream?code=$code&ep=$ep"
        val resText = try {
            app.get(
                streamApiUrl,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to toMirror(pageUrl),
                    "Accept" to "application/json, */*"
                ),
                timeout = 15
            ).text
        } catch (_: Exception) {
            return false
        }

        val json = try {
            JSONObject(resText)
        } catch (_: Exception) {
            return false
        }

        var foundAny = false
        val mainStreamUrl = json.optString("url", "").trim()
        val mainType = json.optString("type", "").lowercase().trim()

        fun emitLink(rawUrl: String, typeStr: String, label: String) {
            if (rawUrl.isBlank()) return
            val isHls = typeStr == "hls" || rawUrl.contains(".m3u8")
            if (isHls) {
                generateM3u8(
                    source = name,
                    streamUrl = rawUrl,
                    referer = "$MIRROR_URL/"
                ).forEach { link ->
                    callback.invoke(link)
                    foundAny = true
                }
            } else {
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name $label (MP4)",
                        url = rawUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "$MIRROR_URL/"
                        this.quality = Qualities.P1080.value
                    }
                )
                foundAny = true
            }
        }

        if (mainStreamUrl.isNotBlank()) {
            emitLink(mainStreamUrl, mainType, "VIP")
        }

        val sources = json.optJSONArray("sources")
        if (sources != null) {
            for (i in 0 until sources.length()) {
                val sObj = sources.optJSONObject(i) ?: continue
                val sUrl = sObj.optString("url", "").trim()
                if (sUrl.isNotBlank() && sUrl != mainStreamUrl) {
                    val sType = sObj.optString("type", "").lowercase().trim()
                    emitLink(sUrl, sType, "Server ${i + 2}")
                }
            }
        }

        return foundAny
    }
}
