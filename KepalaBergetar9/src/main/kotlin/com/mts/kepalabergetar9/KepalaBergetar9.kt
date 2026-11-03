package com.mts.kepalabergetar9

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLEncoder

class KepalaBergetar9 : MainAPI() {
    override var mainUrl = "https://kepalabergetar9.net"
    override var name = "KepalaBergetar9"
    override val hasMainPage = true
    override var lang = "ms"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "$mainUrl/" to "⚡ Episod Terkini (Hari Ini)",
        "$mainUrl/astro-ria/" to "🎬 Drama Astro Ria",
        "$mainUrl/tv3-episod/" to "📺 Drama TV3",
        "$mainUrl/tv1/" to "📡 Drama TV1",
        "$mainUrl/tv2-episod/" to "✨ Drama TV2",
        "$mainUrl/tv9-episod/" to "🌟 Drama TV9",
        "$mainUrl/ntv7/" to "🎥 Drama NTV7",
        "$mainUrl/astro-citra/" to "🍿 Astro Citra"
    )

    private fun cleanTitle(raw: String): String {
        return raw
            .replace(Regex("""(?i)\s*-\s*Kepala\s*Bergetar.*"""), "")
            .replace(Regex("""(?i)\s*(?:Tonton\s*Drama\s*Video|Tonton\s*Drama|Live\s*Episod|Episod|Episode)\s*\d+.*"""), "")
            .replace(Regex("""(?i)\b(?:TV1|TV2|TV3|TV9|NTV7|Astro\s*Ria|Astro\s*Citra|Drama|Live|Full|Online)\b"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val a = element.selectFirst(".post-box-title a, h3 a, h2 a, .entry-title a, a[rel='bookmark']")
            ?: element.selectFirst("a[href*='-episod-']")
            ?: element.selectFirst("a[href]")
            ?: return null

        val href = fixUrlNull(a.attr("href")) ?: return null
        if (href.isBlank() || href == "$mainUrl/" || href.contains("/category/")) return null

        val rawTitle = a.text().trim()
        if (rawTitle.isBlank()) return null

        val clean = cleanTitle(rawTitle).ifBlank { rawTitle }
        val img = element.selectFirst(".post-thumbnail img, img")
        val poster = img?.attr("data-src")?.ifBlank { null }
            ?: img?.attr("src")?.ifBlank { null }
            ?: img?.attr("data-lazy-src")?.ifBlank { null }

        return newTvSeriesSearchResponse(clean, href, TvType.TvSeries) {
            this.posterUrl = poster?.let { fixUrl(it) }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val targetUrl = if (page <= 1) {
            request.data
        } else {
            val base = request.data.trimEnd('/')
            "$base/page/$page/"
        }

        val doc = app.get(targetUrl, headers = mapOf("User-Agent" to USER_AGENT)).document
        val items = doc.select(".recent-item, .post-item, article, li.post-item, .cat-box-content .tie_video").mapNotNull {
            toSearchResult(it)
        }.distinctBy { it.url }

        return if (items.isNotEmpty()) {
            newHomePageResponse(request.name, items)
        } else {
            null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/?s=$encoded"
        val doc = app.get(searchUrl, headers = mapOf("User-Agent" to USER_AGENT)).document

        return doc.select(".recent-item, .post-item, article, .cat-box-content .tie_video, .search-results article").mapNotNull {
            toSearchResult(it)
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val fullUrl = fixUrl(url)
        val doc = app.get(fullUrl, headers = mapOf("User-Agent" to USER_AGENT)).document

        val rawTitle = doc.selectFirst("h1.entry-title, h1, .post-title, title")?.text()?.trim() ?: "Drama"
        val clean = cleanTitle(rawTitle).ifBlank { rawTitle }

        val poster = doc.selectFirst(".post-thumbnail img, .entry-content img, meta[property='og:image']")?.let { el ->
            el.attr("content").ifBlank { el.attr("src").ifBlank { el.attr("data-src") } }
        }?.let { fixUrlNull(it) }

        val plot = doc.selectFirst(".entry-content p, .entry p")?.text()?.trim()

        // Extract episodes from episode navigation (.episodes-grid a, .episodes a, a[href*="-episod-"])
        val episodeButtons = doc.select(".episodes-grid a, .episodes a, a[href*='-episod-']")
            .filter { el ->
                val h = el.attr("href")
                h.isNotBlank() && h != fullUrl && h != "$mainUrl/"
            }

        val episodes = mutableListOf<Episode>()

        if (episodeButtons.isNotEmpty()) {
            episodeButtons.forEach { btn ->
                val epHref = fixUrl(btn.attr("href"))
                val epText = btn.text().trim()
                val epNum = epText.filter { it.isDigit() }.toIntOrNull()
                    ?: Regex("""(?i)(?:episod|episode)-(\d+)""").find(epHref)?.groupValues?.getOrNull(1)?.toIntOrNull()

                episodes.add(
                    newEpisode(epHref) {
                        this.name = if (epNum != null) "Episod $epNum" else epText.ifBlank { "Episod" }
                        this.episode = epNum
                        this.posterUrl = poster
                    }
                )
            }
        }

        // Also add current page as an episode if not present
        val currentEpNum = Regex("""(?i)(?:Episod|Episode)\s*(\d+)""").find(rawTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("""(?i)(?:episod|episode)-(\d+)""").find(fullUrl)?.groupValues?.getOrNull(1)?.toIntOrNull()

        if (episodes.none { it.data == fullUrl }) {
            episodes.add(
                newEpisode(fullUrl) {
                    this.name = if (currentEpNum != null) "Episod $currentEpNum" else "Episod"
                    this.episode = currentEpNum
                    this.posterUrl = poster
                }
            )
        }

        val sortedEpisodes = episodes.distinctBy { it.data }.sortedBy { it.episode ?: 0 }

        return newTvSeriesLoadResponse(clean, fullUrl, TvType.TvSeries, sortedEpisodes) {
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
        val fullUrl = fixUrl(data)
        val doc = app.get(fullUrl, headers = mapOf("User-Agent" to USER_AGENT)).document

        val serverUrls = mutableSetOf<String>()

        // 1. All iframes in page
        doc.select("iframe").forEach { ifr ->
            val src = ifr.attr("src").ifBlank { ifr.attr("data-src") }
            if (src.isNotBlank()) {
                serverUrls.add(fixUrl(src))
            }
        }

        // 2. All links inside entry-content or server selector
        doc.select(".entry-content a[href], .players a[href], .server a[href]").forEach { a ->
            val href = a.attr("href").trim()
            if (href.isNotBlank() && anyStreamingKeyword(href)) {
                serverUrls.add(fixUrl(href))
            }
        }

        // 3. Script tags with embed URLs
        val html = doc.html()
        Regex("""https?://(?:www\.)?(?:vkspeed\.com|douvideo\.me\.uk|filemoon\.[a-z]+|embedwish\.com|streamwish\.[a-z]+|dailymotion\.com|dood\.[a-z]+|mixdrop\.[a-z]+)/[^\s"\'<>]+""").findAll(html).forEach { m ->
            serverUrls.add(m.value)
        }

        // Load all extracted servers
        var foundAny = false
        for (server in serverUrls) {
            try {
                if (server.contains("vkspeed.com", ignoreCase = true)) {
                    VkspeedExtractor().getUrl(server, fullUrl, subtitleCallback, callback)
                    foundAny = true
                } else if (server.contains("douvideo.me.uk", ignoreCase = true)) {
                    DouvideoExtractor().getUrl(server, fullUrl, subtitleCallback, callback)
                    foundAny = true
                } else {
                    val loaded = loadExtractor(server, fullUrl, subtitleCallback, callback)
                    if (loaded) foundAny = true
                }
            } catch (_: Throwable) {}
        }

        return foundAny
    }

    private fun anyStreamingKeyword(url: String): Boolean {
        val lower = url.lowercase()
        return listOf("vkspeed", "douvideo", "filemoon", "embedwish", "streamwish", "dailymotion", "dood", "mixdrop", "vidguard").any {
            lower.contains(it)
        }
    }
}
