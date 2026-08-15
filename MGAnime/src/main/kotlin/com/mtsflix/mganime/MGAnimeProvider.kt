package com.mtsflix.mganime

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.Jsoup
import org.json.JSONObject
import org.json.JSONArray
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

class MGAnimeProvider : MainAPI() {
    companion object {
        var context: android.content.Context? = null
    }
    override var mainUrl = "https://web.mgnime.com"
    override var name = "MGAnime"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime)

    override val mainPage = mainPageOf(
        "page/%d/" to "Rilisan Terbaru",
        "anime/page/%d/?order=popular" to "Trending Anime",
        "category/donghua/page/%d/" to "Donghua Series",
        "category/movie/page/%d/" to "Movie",
        "anime/page/%d/?status=&type=&order=" to "Filter Search"
    )

    private val cloudflareInterceptor by lazy { CloudflareKiller() }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (request.data.contains("%d")) {
            "$mainUrl/${request.data.format(page)}"
        } else {
            "$mainUrl/${request.data}"
        }

        val document = app.get(targetUrl, interceptor = cloudflareInterceptor).document
        val home = document.select("div.listupd article, div.bsx, article.bs, div.bs, .listupd .bsx, .animposx, article")
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
            title = this.selectFirst("div.title, div.tt, h2, h3, .entry-title, .data .title, .ttme")?.text()?.trim().orEmpty()
        }
        if (title.isBlank()) {
            title = a.text().trim()
        }
        if (title.isBlank()) return null

        val imgEl = this.selectFirst("img")
        val posterUrl = fixUrlNull(imgEl?.let { 
            val src = it.attr("src")
            val dataSrc = it.attr("data-src")
            val lazySrc = it.attr("data-lazy-src")
            val origSrc = it.attr("data-original")
            when {
                src.isNotBlank() && !src.contains("data:image") -> src
                dataSrc.isNotBlank() -> dataSrc
                lazySrc.isNotBlank() -> lazySrc
                origSrc.isNotBlank() -> origSrc
                else -> src
            }
        }) ?: fixUrlNull(this.selectFirst("meta[property=og:image]")?.attr("content"))

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 1..3) {
            val document = app.get("$mainUrl/page/$i/?s=$query", interceptor = cloudflareInterceptor).document
            val results = document.select("div.listupd article, div.bsx, article.bs, div.bs, .listupd .bsx, .animposx, article").mapNotNull { it.toSearchResult() }
            if (results.isEmpty()) break
            searchResponse.addAll(results)
        }
        return searchResponse.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val cleanUrl = fixUrl(url)
        var document = app.get(cleanUrl, interceptor = cloudflareInterceptor).document
        
        val seriesUrl = document.select("a").firstOrNull { 
            val txt = it.text()
            txt.contains("All Episodes", ignoreCase = true) ||
            txt.contains("Semua Episode", ignoreCase = true) ||
            txt.contains("Daftar Episode", ignoreCase = true)
        }?.attr("href")?.let { fixUrl(it) }

        val targetUrl = seriesUrl ?: cleanUrl
        if (!seriesUrl.isNullOrBlank()) {
            document = app.get(seriesUrl, interceptor = cloudflareInterceptor).document
        }

        val title = document.selectFirst("h1.entry-title, h1")?.text()?.trim().toString()
        val poster = document.selectFirst("img.wp-post-image, div.ime > img, .thumb img")
            ?.let { 
                val src = it.attr("src")
                val dataSrc = it.attr("data-src")
                if (src.isNotBlank() && !src.contains("data:image")) src else dataSrc
            }
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
        val description = document.selectFirst("div.entry-content, .synopse p, .entry-content p, .desc")?.text()?.trim()

        val genres = document.select(".genres a, .genre a, a[href*='/genre/'], a[href*='/genres/'], .gnr a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val episodeList = document.select(".eplister li, .eplist li, ul.clstyle li, .epl-box li")
        val hasPlayer = document.selectFirst("#pembed, .player-embed, #embed_holder, .embed-container") != null

        if (episodeList.isEmpty() && hasPlayer) {
            return newMovieLoadResponse(title, targetUrl, TvType.Anime, targetUrl) {
                this.posterUrl = fixUrlNull(poster)
                this.plot = description
                this.tags = genres
            }
        }

        val isMovie = document.selectFirst(".spe")?.text().orEmpty().contains("Movie", true) || title.contains("Movie", true)
        return if (isMovie) {
            val movieHref = document.selectFirst(".eplister li > a, .eplist li > a")?.attr("href")?.let { fixUrl(it) } ?: targetUrl
            newMovieLoadResponse(title, movieHref, TvType.Movie, movieHref) {
                this.posterUrl = fixUrlNull(poster)
                this.plot = description
                this.tags = genres
            }
        } else {
            val episodes = episodeList.map { ep ->
                val link = fixUrl(ep.selectFirst("a")?.attr("href").orEmpty())
                val epTitle = ep.selectFirst(".epl-title, .epl-num")?.text()?.trim().orEmpty()
                val epSub = ep.selectFirst(".epl-sub span, .epl-sub")?.text()?.trim().orEmpty()
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

            newTvSeriesLoadResponse(title, targetUrl, TvType.Anime, episodes) {
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
        val document = app.get(fixUrl(data), interceptor = cloudflareInterceptor).document
        val streamUrls = mutableSetOf<String>()

        // 1. Default Iframe Src
        val defaultIframeSrc = document.selectFirst("#pembed iframe, .player-embed iframe, #embed_holder iframe, .embed-container iframe")?.attr("src")
        if (!defaultIframeSrc.isNullOrBlank()) {
            val abs = fixUrl(defaultIframeSrc)
            streamUrls.add(abs)
        }

        // 2. Options / Mirrors / Servers
        document.select(".mobius option, select.mirror option, select option[value], .mob-mirror option[value], .mirroroption option, select#selectserver option").forEach { opt ->
            val value = opt.attr("value").trim()
            val dataPost = opt.attr("data-post").trim()
            val dataNume = opt.attr("data-nume").trim()
            val dataType = opt.attr("data-type").trim()

            // AJAX Player Handling
            if (dataPost.isNotBlank() && dataNume.isNotBlank()) {
                try {
                    val ajaxRes = app.post(
                        "$mainUrl/wp-admin/admin-ajax.php",
                        data = mapOf(
                            "action" to "player_ajax",
                            "post" to dataPost,
                            "nume" to dataNume,
                            "type" to dataType
                        ),
                        headers = mapOf(
                            "X-Requested-With" to "XMLHttpRequest"
                        ),
                        interceptor = cloudflareInterceptor
                    ).text
                    val iframeSrc = Jsoup.parse(ajaxRes).selectFirst("iframe[src], [src]")?.attr("src")
                    if (!iframeSrc.isNullOrBlank()) {
                        streamUrls.add(fixUrl(iframeSrc))
                    }
                } catch (_: Exception) {}
            }

            if (value.isNotBlank()) {
                when {
                    value.startsWith("http") -> streamUrls.add(value)
                    value.startsWith("//")   -> streamUrls.add("https:$value")
                    else -> try {
                        val decoded = base64Decode(value)
                        val sub = Jsoup.parse(decoded)
                        val src = sub.selectFirst("iframe[src], [src]")?.attr("src") ?: return@forEach
                        val abs = fixUrl(src)
                        streamUrls.add(abs)
                    } catch (_: Exception) {}
                }
            }
        }

        var count = 0
        streamUrls.forEach { streamUrl ->
            try {
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
}
