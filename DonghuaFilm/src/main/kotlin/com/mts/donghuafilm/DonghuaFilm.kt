package com.mts.donghuafilm

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder

class DonghuaFilm : MainAPI() {
    override var mainUrl = "https://donghuafilm.com"
    override var name = "DonghuaFilm"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    private val defaultHeaders = mapOf(
        "User-Agent" to USER_AGENT,
        "Referer" to "$mainUrl/"
    )

    private fun toAbsoluteUrl(url: String): String {
        val clean = url.trim()
        if (clean.isBlank()) return ""
        return when {
            clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true) -> clean
            clean.startsWith("//") -> "https:$clean"
            clean.startsWith("/") -> "$mainUrl$clean"
            else -> "$mainUrl/$clean"
        }
    }

    private fun getPosterUrl(element: Element?): String? {
        if (element == null) return null
        val img = if (element.tagName().equals("img", true) || element.tagName().equals("source", true)) {
            element
        } else {
            element.selectFirst("img, picture source, [style*='background'], [style*='url']") ?: element
        }

        for (attr in listOf("data-src", "data-lazy-src", "data-original", "data-cfsrc", "srcset", "data-srcset", "src")) {
            var v = img.attr(attr).trim()
            if (v.isNotBlank() && !v.startsWith("data:image", true) && !v.startsWith("data:text", true)) {
                if (attr.contains("srcset")) {
                    v = v.substringBefore(" ").substringBefore(",").trim()
                }
                if (v.isNotBlank() && !v.startsWith("data:", true)) {
                    return toAbsoluteUrl(v)
                }
            }
        }

        val style = img.attr("style").ifBlank { element.attr("style") }
        if (style.isNotBlank()) {
            val bgMatch = Regex("""url\(['"]?(.*?)['"]?\)""").find(style)
            if (bgMatch != null) {
                val bgUrl = bgMatch.groupValues[1].trim()
                if (bgUrl.isNotBlank() && !bgUrl.startsWith("data:", true)) {
                    return toAbsoluteUrl(bgUrl)
                }
            }
        }
        return null
    }

    override val mainPage = mainPageOf(
        "" to "Rilisan Terbaru",
        "popular" to "Populer Hari Ini",
        "recommendation" to "Rekomendasi",
        "segera-tayang" to "Segera Tayang",
        "az-list" to "Daftar Donghua (A-Z)",
        "?s=&status=ongoing" to "Donghua Ongoing",
        "?s=&status=completed" to "Donghua Tamat",
        "?s=&type=movie" to "Donghua Movie"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path = request.data.trim()
        val items = mutableListOf<SearchResponse>()

        when {
            // 1. Rilisan Terbaru
            path.isEmpty() -> {
                val url = if (page > 1) "$mainUrl/page/$page/" else "$mainUrl/"
                val doc = app.get(url, headers = defaultHeaders).document
                val box = if (page == 1) {
                    doc.select(".bixbox").firstOrNull { bix ->
                        bix.select("h2, h3, .releases").text().contains("Latest", true)
                    } ?: doc.selectFirst(".bixbox")
                } else null
                val elements = box?.select(".listupd .bsx, .bsx, article.bs, .item")
                    ?: doc.select(".listupd .bsx, .bsx, article.bs, .item")
                items.addAll(elements.mapNotNull { toSearchResult(it) })
            }

            // 2. Populer Hari Ini
            path == "popular" -> {
                if (page == 1) {
                    val doc = app.get("$mainUrl/", headers = defaultHeaders).document
                    val box = doc.select(".bixbox").firstOrNull { bix ->
                        bix.select("h2, h3, .releases").text().contains("Popular", true)
                    }
                    val elements = box?.select(".bsx, article.bs, .item") ?: emptyList()
                    items.addAll(elements.mapNotNull { toSearchResult(it) })
                }
            }

            // 3. Rekomendasi
            path == "recommendation" -> {
                if (page == 1) {
                    val doc = app.get("$mainUrl/", headers = defaultHeaders).document
                    val box = doc.select(".bixbox").firstOrNull { bix ->
                        bix.select("h2, h3, .releases").text().contains("Recommendation", true)
                    }
                    val elements = box?.select(".bsx, article.bs, .item") ?: emptyList()
                    items.addAll(elements.mapNotNull { toSearchResult(it) })
                }
            }

            // 4. Segera Tayang
            path == "segera-tayang" -> {
                val url = if (page > 1) "$mainUrl/segera-tayang/page/$page/" else "$mainUrl/segera-tayang/"
                try {
                    val doc = app.get(url, headers = defaultHeaders).document
                    val elements = doc.select(".listupd .bsx, .bsx, article.bs, .item")
                    items.addAll(elements.mapNotNull { toSearchResult(it) })
                } catch (_: Exception) {}
            }

            // 5. Daftar A-Z
            path == "az-list" -> {
                val url = if (page > 1) "$mainUrl/az-list/page/$page/" else "$mainUrl/az-list/"
                try {
                    val doc = app.get(url, headers = defaultHeaders).document
                    val elements = doc.select(".listupd .bsx, .bsx, article.bs, .item")
                    items.addAll(elements.mapNotNull { toSearchResult(it) })
                } catch (_: Exception) {}
            }

            // 6. Filter queries (?s=&status=ongoing, ?s=&status=completed, ?s=&type=movie)
            path.startsWith("?") -> {
                val url = if (page > 1) "$mainUrl/page/$page/$path" else "$mainUrl/$path"
                try {
                    val doc = app.get(url, headers = defaultHeaders).document
                    val elements = doc.select(".listupd .bsx, .bsx, article.bs, .item")
                    items.addAll(elements.mapNotNull { toSearchResult(it) })
                } catch (_: Exception) {}
            }
        }

        return newHomePageResponse(request.name, items.distinctBy { it.url }, hasNext = items.isNotEmpty())
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        return try {
            val a = element.selectFirst("a[href]") ?: return null
            val href = toAbsoluteUrl(a.attr("href"))
            if (href.isBlank() || href == "$mainUrl/" || href.contains("/category/") || href.contains("/genres/")) return null

            val img = element.selectFirst("img") ?: a.selectFirst("img")
            var rawTitle = element.selectFirst(".tt, .title, h2, h3, .entry-title")?.text()?.trim().orEmpty()
            if (rawTitle.isBlank()) {
                rawTitle = a.attr("title").trim()
            }
            if (rawTitle.isBlank()) {
                rawTitle = img?.attr("title")?.trim().orEmpty().ifBlank { img?.attr("alt")?.trim().orEmpty() }
            }
            if (rawTitle.isBlank()) {
                rawTitle = a.text().trim()
            }
            rawTitle = rawTitle.lines().firstOrNull()?.trim() ?: ""
            if (rawTitle.isBlank()) return null

            val title = rawTitle.replace(Regex("""(?i)\s*(?:Episode|Eps)\s*\d+.*"""), "")
                .replace(Regex("""(?i)\s*(?:Subtitle\s*Indonesia|Sub\s*Indo).*"""), "")
                .trim()
                .ifBlank { rawTitle }

            val poster = getPosterUrl(img ?: element)
            val isMovie = href.contains("movie", true) || element.selectFirst(".typez")?.text()?.contains("Movie", true) == true
            val type = if (isMovie) TvType.AnimeMovie else TvType.Anime

            val epText = element.selectFirst(".ep, .bt .ep, .epx")?.text()?.trim()
            val epNum = Regex("""\d+""").find(epText ?: "")?.value?.toIntOrNull()

            newAnimeSearchResponse(title, href, type) {
                this.posterUrl = poster
                this.posterHeaders = defaultHeaders
                if (epNum != null) {
                    addDubStatus(false, epNum)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = URLEncoder.encode(query, "UTF-8")
        val doc = app.get("$mainUrl/?s=$q", headers = defaultHeaders).document
        return doc.select(".listupd .bsx, .bsx, article.bs, .item").mapNotNull {
            toSearchResult(it)
        }.distinctBy { it.url }
    }

    private suspend fun resolveEpisodeUrlFromSlug(slug: String): String? {
        val cleanSlug = slug.trim().trim('/')
        if (cleanSlug.isBlank()) return null

        // Fast search backwards from newest sitemap to oldest
        for (i in 4 downTo 1) {
            try {
                val sitemapUrl = "$mainUrl/wp-sitemap-posts-post-$i.xml"
                val xml = app.get(sitemapUrl, headers = defaultHeaders).text
                val matches = Regex("""<loc>(https://donghuafilm\.com/[^<]*$cleanSlug[^<]*)</loc>""").findAll(xml).toList()
                if (matches.isNotEmpty()) {
                    return matches.last().groupValues[1]
                }
            } catch (_: Exception) {}
        }

        // Try simplified slug keywords
        val keyword = cleanSlug.split("-").firstOrNull { it.length > 3 }
        if (!keyword.isNullOrBlank()) {
            for (i in 4 downTo 1) {
                try {
                    val sitemapUrl = "$mainUrl/wp-sitemap-posts-post-$i.xml"
                    val xml = app.get(sitemapUrl, headers = defaultHeaders).text
                    val matches = Regex("""<loc>(https://donghuafilm\.com/[^<]*$keyword[^<]*)</loc>""").findAll(xml).toList()
                    if (matches.isNotEmpty()) {
                        return matches.last().groupValues[1]
                    }
                } catch (_: Exception) {}
            }
        }
        return null
    }

    override suspend fun load(url: String): LoadResponse? {
        var targetUrl = url
        if (targetUrl.contains("/anime/")) {
            val slug = targetUrl.substringAfter("/anime/").substringBefore("/")
            val resolved = resolveEpisodeUrlFromSlug(slug)
            if (!resolved.isNullOrBlank()) {
                targetUrl = resolved
            }
        }

        val doc = app.get(targetUrl, headers = defaultHeaders).document
        val rawTitle = doc.selectFirst("h1.entry-title, .entry-title, h1")?.text()?.trim().orEmpty()
        val cleanTitle = rawTitle.replace(Regex("""(?i)(?:Episode|Eps)\s*\d+.*"""), "")
            .replace(Regex("""(?i)Subtitle\s*Indonesia|Sub\s*Indo"""), "")
            .trim()
            .ifBlank { rawTitle }

        val poster = getPosterUrl(doc.selectFirst(".thumb img, .ts-post-image, .infox img"))
        val desc = doc.selectFirst(".entry-content, .desc, .sinopsis")?.text()?.trim()

        val episodeElements = doc.select("div.episodelist li, ul.episodelist li, ul.clstyle li")
        val episodes = episodeElements.mapNotNull { li ->
            val a = li.selectFirst("a[href]") ?: return@mapNotNull null
            val href = toAbsoluteUrl(a.attr("href"))
            val epText = li.text().trim()
            val epNum = Regex("""(?i)(?:Episode|Eps)\s*0*(\d+)""").find(epText)?.groupValues?.get(1)?.toIntOrNull()
            newEpisode(href) {
                this.name = a.attr("title").ifBlank { epText.substringBefore(" -").trim() }
                this.episode = epNum
            }
        }.distinctBy { it.data }

        val isMovie = episodes.isEmpty() || targetUrl.contains("movie", true)
        val type = if (isMovie) TvType.AnimeMovie else TvType.Anime

        val epList = if (episodes.isEmpty()) {
            listOf(
                newEpisode(targetUrl) {
                    this.name = cleanTitle
                    this.episode = 1
                }
            )
        } else {
            episodes
        }

        return newAnimeLoadResponse(cleanTitle, targetUrl, type) {
            this.posterUrl = poster
            this.plot = desc
            addEpisodes(DubStatus.Subbed, epList)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = defaultHeaders).document
        val candidateUrls = mutableSetOf<String>()

        // 1. Iframes in page
        doc.select("iframe").forEach { ifr ->
            listOf("src", "data-src", "data-lazy-src", "data-original").forEach { attr ->
                val v = ifr.attr(attr).trim()
                if (v.isNotBlank() && !v.startsWith("data:", true)) {
                    candidateUrls.add(toAbsoluteUrl(v))
                }
            }
        }

        // 2. Select options (raw URLs or Base64 encoded iframes/videos)
        doc.select("select.mirror option, select option, [data-post]").forEach { opt ->
            val valAttr = opt.attr("value").trim()
            if (valAttr.startsWith("http://", true) || valAttr.startsWith("https://", true) || valAttr.startsWith("//")) {
                candidateUrls.add(toAbsoluteUrl(valAttr))
            } else if (valAttr.length > 15) {
                try {
                    val decoded = String(Base64.decode(valAttr, Base64.DEFAULT), Charsets.UTF_8)
                    val iframeDoc = Jsoup.parse(decoded)
                    iframeDoc.select("iframe[src], video[src], source[src]").forEach { el ->
                        val src = el.attr("src").trim()
                        if (src.isNotBlank()) candidateUrls.add(toAbsoluteUrl(src))
                    }
                    Regex("""src=["']([^"']+)["']""").findAll(decoded).forEach { m ->
                        val src = m.groupValues[1].trim()
                        if (src.isNotBlank()) candidateUrls.add(toAbsoluteUrl(src))
                    }
                } catch (_: Exception) {}
            }
        }

        var foundAny = false
        candidateUrls.forEach { cand ->
            val u = cand.trim()
            if (u.isBlank() || u == "about:blank") return@forEach

            // 1. ZeroStorage
            val zsMatch = Regex("""zerostorage\.net/(?:embed|api/files|files)/([a-zA-Z0-9\-]+)""").find(u)
            if (zsMatch != null) {
                val vid = zsMatch.groupValues[1]
                val streamUrl = "https://zerostorage.net/api/files/$vid/stream"
                callback(
                    newExtractorLink(
                        name,
                        "ZeroStorage [1080p]",
                        streamUrl,
                        ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://zerostorage.net/embed/$vid"
                        this.quality = Qualities.P1080.value
                    }
                )
                foundAny = true
                return@forEach
            }

            // 2. PixelDrain
            val pdMatch = Regex("""pixeldrain\.com/(?:u|api/file)/([a-zA-Z0-9]+)""").find(u)
            if (pdMatch != null) {
                val vid = pdMatch.groupValues[1]
                val streamUrl = "https://pixeldrain.com/api/file/$vid"
                callback(
                    newExtractorLink(
                        name,
                        "PixelDrain [1080p]",
                        streamUrl,
                        ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://pixeldrain.com/"
                        this.quality = Qualities.P1080.value
                    }
                )
                foundAny = true
                return@forEach
            }

            // 3. DailyMotion
            if (u.contains("dailymotion.com", true) || u.contains("dai.ly", true)) {
                val dmMatch = Regex("""(?:video=|/embed/video/|/video/)([A-Za-z0-9]+)""").find(u)
                if (dmMatch != null) {
                    val vid = dmMatch.groupValues[1]
                    try {
                        val metaJson = app.get(
                            "https://www.dailymotion.com/player/metadata/video/$vid",
                            headers = mapOf("Referer" to u, "User-Agent" to USER_AGENT)
                        ).text
                        Regex(""""url"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""").findAll(metaJson).forEach { m ->
                            val m3u8Url = m.groupValues[1].replace("\\/", "/").replace("\\u0026", "&")
                            if (m3u8Url.contains(".m3u8", true)) {
                                M3u8Helper.generateM3u8(name, m3u8Url, u).forEach { link ->
                                    callback(link)
                                    foundAny = true
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // 4. Dropbox
            if (u.contains("dropbox.com", true)) {
                val directDb = if (u.contains("dl=0")) u.replace("dl=0", "dl=1") else if (!u.contains("dl=1")) {
                    if (u.contains("?")) "$u&dl=1" else "$u?dl=1"
                } else u
                callback(
                    newExtractorLink(
                        name,
                        "Dropbox",
                        directDb,
                        ExtractorLinkType.VIDEO
                    ) {
                        this.referer = u
                        this.quality = Qualities.P1080.value
                    }
                )
                foundAny = true
            }

            // 5. Standard extractors fallback
            try {
                loadExtractor(u, mainUrl, subtitleCallback) { link ->
                    callback(link)
                    foundAny = true
                }
            } catch (_: Exception) {}
        }

        return foundAny
    }
}
