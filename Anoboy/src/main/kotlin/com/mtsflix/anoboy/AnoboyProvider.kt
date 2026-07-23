package com.mtsflix.anoboy

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import java.net.URI
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class Anoboy : MainAPI() {
    override var mainUrl = "https://ww1.anoboy.boo"
    override var name = "AnoBoy"
    override val hasMainPage = true
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Anime, TvType.AnimeMovie, TvType.OVA
    )

    companion object {
        fun getType(t: String?): TvType {
            if (t == null) return TvType.Anime
            return when {
                t.contains("Tv", true) -> TvType.Anime
                t.contains("Movie", true) -> TvType.AnimeMovie
                t.contains("OVA", true) -> TvType.OVA
                t.contains("Special", true) -> TvType.OVA
                else -> TvType.Anime
            }
        }

        fun getStatus(t: String?): ShowStatus {
            if (t == null) return ShowStatus.Completed
            return when {
                t.contains("Ongoing", true) -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override val mainPage = mainPageOf(
        "" to "New Update",
        "category/anime" to "Latest Added",
        "category/live-action-movie" to "Live Action",
        "category/anime-movie" to "Movie",
        "category/donghua" to "Donghua"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = if (page == 1) {
            "$mainUrl/${request.data.replace("page/%d/", "")}"
        } else {
            "$mainUrl/${request.data.format(page)}"
        }.replace("//", "/").replace(":/", "://")

        val document = app.get(pageUrl).document
        val items = document.select("article.has-post-thumbnail, article.item, article.item-infinite, div.poster")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val linkElement = selectFirst("a[href][title]") ?: selectFirst("a[href]") ?: return null
        val href = fixUrl(linkElement.attr("href"))
        val title = (linkElement.attr("title").ifBlank { selectFirst("h2, h3, .entry-title")?.text() ?: text() })
            .removePrefix("Permalink to: ")
            .substringBefore("Season")
            .substringBefore("Episode")
            .substringBefore("(")
            .trim()

        if (title.isBlank() || href.isBlank()) return null

        val posterUrl = selectFirst("img")?.fixPoster()?.let { fixUrl(it) }
        val isTv = href.contains("/serial-tv/", true) || href.contains("/series/", true) || href.contains("/tv/", true)

        return if (isTv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article.has-post-thumbnail, article.item, article.item-infinite, div.poster")
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val rawTitle = document.selectFirst("h1.entry-title, h1, div.mvic-desc h3")?.text()?.trim().orEmpty()
        val title = rawTitle
            .substringBefore("Season")
            .substringBefore("Episode")
            .substringBefore("(")
            .trim()

        val poster = document.selectFirst("figure.pull-left img, .mvic-thumb img, .poster img, .entry-content img")
            .fixPoster()
            ?.let { fixUrl(it) }

        val description = document.selectFirst("div[itemprop=description] p, div.desc p.f-desc, div.entry-content p, .synops p")
            ?.text()
            ?.trim()

        val tags = document.select("strong:contains(Genre) ~ a, .gmr-moviedata strong:contains(Genre) ~ a, a[rel=tag]").map { it.text() }.filter { it.isNotBlank() }

        val year = document.selectFirst("div.gmr-moviedata strong:contains(Year:) ~ a, .date, [itemprop=dateCreated]")
            ?.text()
            ?.filter { it.isDigit() }
            ?.let { if (it.length >= 4) it.substring(0, 4).toIntOrNull() else null }

        val rating = document.selectFirst("span[itemprop=ratingValue], .rating strong")
            ?.text()
            ?.replace("Rating", "")
            ?.trim()
            ?.toDoubleOrNull()

        val actors = document.select("div.gmr-moviedata span[itemprop=actors] a, .actors a")
            .map { it.text() }
            .filter { it.isNotBlank() }

        val seasonBlocks = document.select("div.gmr-season-block")
        val allEpisodes = mutableListOf<Episode>()

        seasonBlocks.forEach { block ->
            val seasonTitle = block.selectFirst("h3.season-title")?.text()?.trim()
            val seasonNumber = Regex("(\\d+)").find(seasonTitle ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1

            val eps = block.select("div.gmr-season-episodes a")
                .filter { a ->
                    val t = a.text().lowercase()
                    !t.contains("view all") && !t.contains("batch")
                }
                .mapIndexedNotNull { index, epLink ->
                    val hrefEp = epLink.attr("href").takeIf { it.isNotBlank() }?.let { fixUrl(it) } ?: return@mapIndexedNotNull null
                    val name = epLink.text().trim()
                    val episodeNum = Regex("E(p|ps)?(\\d+)", RegexOption.IGNORE_CASE).find(name)?.groupValues?.getOrNull(2)?.toIntOrNull() ?: (index + 1)

                    newEpisode(hrefEp) {
                        this.name = name
                        this.season = seasonNumber
                        this.episode = episodeNum
                    }
                }
            allEpisodes.addAll(eps)
        }

        val isTv = allEpisodes.isNotEmpty() || url.contains("/serial-tv/", true) || url.contains("/series/", true)

        return if (isTv) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, allEpisodes.sortedWith(compareBy({ it.season }, { it.episode }))) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
                this.year = year
                if (rating != null) addScore(rating.toString(), 10)
                addActors(actors)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
                this.year = year
                addActors(actors)
                if (rating != null) addScore(rating.toString(), 10)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val multiPrefix = "multi:"
        val isMulti = data.startsWith(multiPrefix)
        val requestData = if (isMulti) data.removePrefix(multiPrefix).substringBefore("||").trim() else data
        val requestReferer = if (isMulti && data.contains("||")) data.substringAfter("||").trim() else mainUrl

        val document = try {
            if (requestData.isBlank()) null else app.get(requestData, referer = requestReferer).document
        } catch (_: Exception) {
            null
        }

        val seedUrls = mutableListOf<String>()
        val discoveredUrls = LinkedHashSet<String>()
        val queuedUrls = ArrayDeque<String>()
        val crawledUrls = HashSet<String>()

        fun queueUrl(raw: String?, base: String) {
            val resolved = resolveUrl(raw, base) ?: return
            seedUrls.add(resolved)
            if (discoveredUrls.add(resolved)) queuedUrls.add(resolved)
        }

        fun isDirectResolvableUrl(url: String): Boolean {
            val lower = url.lowercase()
            return lower.contains("blogger.com/video.g") ||
                lower.contains("blogger.googleusercontent.com") ||
                lower.contains("/uploads/adsbatch") ||
                lower.contains("/uploads/acbatch") ||
                lower.contains("/uploads/yupbatch") ||
                lower.contains("/uploads/stream/embed.php") ||
                lower.contains("yourupload.com/embed/") ||
                lower.contains("yourupload.com/watch/") ||
                lower.endsWith(".mp4") ||
                lower.endsWith(".m3u8")
        }

        fun extractFromDoc(baseUrl: String, doc: org.jsoup.nodes.Document) {
            doc.select("iframe#mediaplayer, iframe#videoembed, div.player-embed iframe, iframe[src], iframe[data-src], iframe[data-litespeed-src]")
                .forEach { queueUrl(it.getIframeAttr(), baseUrl) }

            doc.select("a[href*=\"yourupload.com/embed/\"], a[href*=\"yourupload.com/watch/\"], a[href*=\"www.yourupload.com/embed/\"], a[href*=\"www.yourupload.com/watch/\"]")
                .forEach { queueUrl(it.attr("href"), baseUrl) }

            doc.select(
                "a[href*=\"/uploads/stream/embed.php\"], " +
                    "a[href*=\"/uploads/acbatch.php\"], " +
                    "a[href*=\"/uploads/adsbatch\"], " +
                    "a[href*=\"/uploads/yupbatch\"], " +
                    "a[href*=\"blogger.com/video.g\"], " +
                    "a[href*=\"blogger.googleusercontent.com\"]"
            ).forEach { queueUrl(it.attr("href"), baseUrl) }

            doc.select("#fplay a#allmiror[data-video], #fplay a[data-video], a#allmiror[data-video], a[data-video], [data-video]")
                .forEach { anchor ->
                    queueUrl(anchor.attr("data-video"), baseUrl)
                    queueUrl(anchor.attr("href"), baseUrl)
                }

            doc.select("[data-embed], [data-iframe], [data-url], [data-src]")
                .forEach { el ->
                    queueUrl(el.attr("data-embed"), baseUrl)
                    queueUrl(el.attr("data-iframe"), baseUrl)
                    queueUrl(el.attr("data-url"), baseUrl)
                    queueUrl(el.attr("data-src"), baseUrl)
                }

            doc.select("div.download a.udl[href], div.download a[href], div.dlbox li span.e a[href]")
                .forEach { queueUrl(it.attr("href"), baseUrl) }

            val bloggerRegex = Regex("""https?://(?:www\.)?blogger\.com/video\.g\?[^"'<\s]+""", RegexOption.IGNORE_CASE)
            val batchRegex = Regex("""/uploads/(?:adsbatch[^"'\s]+|yupbatch[^"'\s]+|acbatch[^"'\s]+|stream/embed\.php\?[^"'\s]+)""", RegexOption.IGNORE_CASE)
            val yourUploadRegex = Regex("""https?://(?:www\.)?yourupload\.com/(?:embed|watch)/[^"'<\s]+""", RegexOption.IGNORE_CASE)
            doc.select("script").forEach { script ->
                val scriptData = script.data()
                bloggerRegex.findAll(scriptData).forEach { match ->
                    queueUrl(match.value, baseUrl)
                }
                batchRegex.findAll(scriptData).forEach { match ->
                    queueUrl(match.value, baseUrl)
                }
                yourUploadRegex.findAll(scriptData).forEach { match ->
                    queueUrl(match.value, baseUrl)
                }
            }
        }

        fun shouldCrawl(url: String): Boolean {
            val lower = url.lowercase()
            if (lower.contains("blogger.com/video.g")) return false
            if (lower.endsWith(".mp4") || lower.endsWith(".m3u8")) return false
            return lower.contains("anoboy.boo") ||
                lower.contains("/uploads/") ||
                lower.contains("adsbatch") ||
                lower.contains("yupbatch")
        }

        if (!isMulti && isDirectResolvableUrl(requestData)) {
            queueUrl(requestData, requestReferer)
        }

        if (document != null) {
            extractFromDoc(requestData, document)
        }
        if (isMulti) {
            requestData.removePrefix(multiPrefix)
                .split("||")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { queueUrl(it, mainUrl) }
        }

        var safety = 0
        while (queuedUrls.isNotEmpty() && safety++ < 120) {
            val next = queuedUrls.removeFirst()
            if (!shouldCrawl(next) || !crawledUrls.add(next)) continue
            try {
                val nestedDoc = app.get(next, referer = requestReferer).document
                extractFromDoc(next, nestedDoc)
            } catch (_: Exception) {
            }
        }

        if (discoveredUrls.isEmpty() && document != null) {
            val mirrorOptions = document.select("select.mirror option[value]:not([disabled])")
            for (opt in mirrorOptions) {
                val base64 = opt.attr("value")
                if (base64.isBlank()) continue
                try {
                    val decodedHtml = base64Decode(base64.replace("\\s".toRegex(), ""))
                    Jsoup.parse(decodedHtml).selectFirst("iframe")?.getIframeAttr()?.let { iframe ->
                        queueUrl(iframe, requestReferer)
                    }
                } catch (_: Exception) {
                }
            }
        }

        val bloggerLinks = discoveredUrls.filter {
            it.contains("blogger.com/video.g", true) ||
                it.contains("blogger.googleusercontent.com", true)
        }

        val fallbackLinks = discoveredUrls.filterNot {
            it.contains("blogger.com/video.g", true) ||
                it.contains("blogger.googleusercontent.com", true)
        }

        var foundLinks = 0
        val callbackWrapper: (ExtractorLink) -> Unit = { link ->
            foundLinks++
            callback(link)
        }
        val bloggerExtractor = BloggerExtractor()

        fun decodeUnicodeEscapes(input: String): String {
            val unicodeRegex = Regex("""\\u([0-9a-fA-F]{4})""")
            var output = input
            repeat(2) {
                output = unicodeRegex.replace(output) { match ->
                    match.groupValues[1].toInt(16).toChar().toString()
                }
            }
            output = output.replace(92.toChar().toString() + "/", "/")
            output = output.replace("\\=", "=")
            output = output.replace("\\&", "&")
            output = output.replace("\\\\", "\\")
            output = output.replace("\\\"", "\"")
            return output
        }

        fun normalizeVideoUrl(input: String): String {
            return decodeUnicodeEscapes(input)
                .replace("\\u003d", "=")
                .replace("\\u0026", "&")
                .replace("\\u002F", "/")
                .replace(92.toChar().toString() + "/", "/")
                .replace("\\", "")
        }

        fun itagToQuality(itag: Int?): Int {
            return when (itag) {
                18 -> Qualities.P360.value
                22 -> Qualities.P720.value
                37 -> Qualities.P1080.value
                59 -> Qualities.P480.value
                43 -> Qualities.P360.value
                36 -> Qualities.P240.value
                17 -> Qualities.P144.value
                137 -> Qualities.P1080.value
                136 -> Qualities.P720.value
                135 -> Qualities.P480.value
                134 -> Qualities.P360.value
                133 -> Qualities.P240.value
                160 -> Qualities.P144.value
                else -> Qualities.Unknown.value
            }
        }

        suspend fun emitBloggerDirectLinks(bloggerUrl: String, referer: String?): Boolean {
            val googleVideoReferer = referer ?: "$mainUrl/"
            val fixedUrl = if (bloggerUrl.startsWith("//")) "https:$bloggerUrl" else bloggerUrl
            if (fixedUrl.contains("blogger.googleusercontent.com", true)) {
                callbackWrapper(
                    newExtractorLink("Blogger", "Blogger", fixedUrl, if (fixedUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                        this.referer = referer ?: fixedUrl
                    }
                )
                return true
            }

            val token = Regex("[?&]token=([^&]+)")
                .find(fixedUrl)
                ?.groupValues
                ?.getOrNull(1)
                ?: return false

            val page = runCatching {
                app.get(
                    fixedUrl,
                    referer = referer ?: "$mainUrl/",
                    headers = mapOf(
                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                        "User-Agent" to USER_AGENT
                    )
                )
            }.getOrNull() ?: return false
            val html = page.text
            val cookies = page.cookies

            val fSid = Regex("FdrFJe\":\"(-?\\d+)\"")
                .find(html)
                ?.groupValues
                ?.getOrNull(1)
                ?: ""
            val bl = Regex("cfb2h\":\"([^\"]+)\"")
                .find(html)
                ?.groupValues
                ?.getOrNull(1)
                ?: return false
            val hl = Regex("lang=\"([^\"]+)\"")
                .find(html)
                ?.groupValues
                ?.getOrNull(1)
                ?.ifBlank { null }
                ?: "en-US"
            val reqId = (10000..99999).random()
            val rpcId = "WcwnYd"
            val payload = """[[["$rpcId","[\"$token\",\"\",0]",null,"generic"]]]"""
            val apiUrl = "https://www.blogger.com/_/BloggerVideoPlayerUi/data/batchexecute" +
                "?rpcids=$rpcId&source-path=%2Fvideo.g&f.sid=$fSid&bl=$bl&hl=$hl&_reqid=$reqId&rt=c"

            val response = runCatching {
                app.post(
                    apiUrl,
                    data = mapOf("f.req" to payload),
                    referer = fixedUrl,
                    cookies = cookies,
                    headers = mapOf(
                        "Origin" to "https://www.blogger.com",
                        "Accept" to "*/*",
                        "Content-Type" to "application/x-www-form-urlencoded;charset=UTF-8",
                        "X-Same-Domain" to "1",
                        "User-Agent" to USER_AGENT
                    )
                ).text
            }.getOrNull() ?: return false

            val directUrls = Regex("""https://[^\s"']+""")
                .findAll(decodeUnicodeEscapes(response))
                .map { it.value }
                .plus(
                    Regex("""https://[^\s"']+""")
                        .findAll(response)
                        .map { it.value }
                )
                .map { normalizeVideoUrl(it) }
                .filter {
                    it.contains("googlevideo.com/videoplayback") ||
                        it.contains("blogger.googleusercontent.com")
                }
                .distinct()
                .toList()

            directUrls.forEach { videoUrl ->
                val itag = Regex("[?&]itag=(\\d+)")
                    .find(videoUrl)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                val directReferer = if (videoUrl.contains("googlevideo.com/", true)) {
                    googleVideoReferer
                } else {
                    fixedUrl
                }
                callbackWrapper(
                    newExtractorLink("Blogger", "Blogger", videoUrl, if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                        this.referer = directReferer
                        this.headers = mapOf(
                            "Referer" to directReferer,
                            "User-Agent" to USER_AGENT,
                            "Accept" to "*/*"
                        )
                        this.quality = itagToQuality(itag)
                    }
                )
            }

            return directUrls.isNotEmpty()
        }

        suspend fun resolveLegacyMirrorPage(pageUrl: String): Boolean {
            val lower = pageUrl.lowercase()
            val isLegacyMirrorPage = lower.contains("/uploads/adsbatch") ||
                lower.contains("/uploads/acbatch") ||
                lower.contains("/uploads/yupbatch") ||
                lower.contains("/uploads/stream/embed.php")
            if (!isLegacyMirrorPage) return false

            val doc = runCatching {
                app.get(pageUrl, referer = requestReferer).document
            }.getOrNull() ?: return false

            val resolvedCandidates = linkedSetOf<String>()
            fun addCandidate(raw: String?) {
                val resolved = resolveUrl(raw, pageUrl) ?: return
                resolvedCandidates.add(resolved)
            }

            doc.select("iframe[src], iframe[data-src], iframe[data-litespeed-src]")
                .forEach { addCandidate(it.getIframeAttr()) }
            doc.select("a[href], [data-video], [data-src], [data-url], [data-iframe], [data-embed]")
                .forEach { el ->
                    addCandidate(el.attr("href"))
                    addCandidate(el.attr("data-video"))
                    addCandidate(el.attr("data-src"))
                    addCandidate(el.attr("data-url"))
                    addCandidate(el.attr("data-iframe"))
                    addCandidate(el.attr("data-embed"))
                }

            val bloggerRegex = Regex("""https?://(?:www\.)?blogger\.com/video\.g\?[^"'<\s]+""", RegexOption.IGNORE_CASE)
            val fileRegex = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
            doc.select("script").forEach { script ->
                val scriptData = script.data()
                bloggerRegex.findAll(scriptData).forEach { addCandidate(it.value) }
                fileRegex.findAll(scriptData).forEach { addCandidate(it.value) }
            }

            var resolvedAny = false
            resolvedCandidates.forEach { candidate ->
                when {
                    candidate.contains("blogger.com/video.g", true) ||
                        candidate.contains("blogger.googleusercontent.com", true) -> {
                        if (emitBloggerDirectLinks(candidate, pageUrl)) resolvedAny = true
                    }

                    candidate != pageUrl -> {
                        loadExtractor(candidate, pageUrl, subtitleCallback, callbackWrapper)
                    }
                }
            }

            return resolvedAny
        }

        seedUrls
            .distinct()
            .filter {
                val lower = it.lowercase()
                lower.contains("/uploads/adsbatch") ||
                    lower.contains("/uploads/acbatch") ||
                    lower.contains("/uploads/yupbatch") ||
                    lower.contains("/uploads/stream/embed.php")
            }
            .forEach { resolveLegacyMirrorPage(it) }

        bloggerLinks.distinct().forEach { link ->
            if (!emitBloggerDirectLinks(link, requestReferer)) {
                bloggerExtractor.getUrl(link, requestReferer, subtitleCallback, callbackWrapper)
            }
        }
        fallbackLinks.distinct().forEach { link ->
            val resolvedLegacy = resolveLegacyMirrorPage(link)
            if (!resolvedLegacy) {
                loadExtractor(link, requestReferer, subtitleCallback, callbackWrapper)
            }
        }

        return true
    }

    private fun Element.getImageAttr(): String {
        val result = when {
            hasAttr("data-src") -> attr("abs:data-src")
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
            else -> attr("abs:src")
        }
        return if (result.isBlank()) attr("src").substringBefore(" ") else result
    }

    private fun Element?.getIframeAttr(): String? {
        if (this == null) return null
        val candidates = listOf(
            attr("data-litespeed-src"),
            attr("data-lazy-src"),
            attr("data-src"),
            attr("data-video"),
            attr("data-embed"),
            attr("data-url"),
            attr("data-iframe"),
            attr("src")
        )
        return candidates.firstOrNull { it.isNotBlank() && !it.equals("about:blank", true) && !it.startsWith("javascript", true) }
    }

    private fun resolveUrl(url: String?, base: String): String? {
        if (url.isNullOrBlank()) return null
        val cleanUrl = url.trim()
        return try {
            URI(base).resolve(cleanUrl).toString()
        } catch (_: Exception) {
            fixUrl(cleanUrl)
        }
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

    private fun Element?.fixPoster(): String? {
        if (this == null) return null
        val src = this.attr("data-src").ifBlank { this.attr("data-lazy-src").ifBlank { this.attr("src") } }
        return src.takeIf { it.isNotBlank() }
    }

}
