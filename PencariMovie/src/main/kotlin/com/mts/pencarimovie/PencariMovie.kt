package com.mts.pencarimovie

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.net.URLEncoder

data class PmPost(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("excerpt") val excerpt: String? = null,
    @JsonProperty("content") val content: String? = null,
    @JsonProperty("thumbnail_url") val thumbnailUrl: String? = null,
    @JsonProperty("permalink") val permalink: String? = null,
    @JsonProperty("date") val date: String? = null,
    @JsonProperty("categories") val categories: List<String>? = null,
    @JsonProperty("tags") val tags: List<String>? = null
)

data class PmPostsResponse(
    @JsonProperty("success") val success: Boolean? = null,
    @JsonProperty("data") val data: List<PmPost>? = null
)

data class PmFile(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("caption") val caption: String? = null,
    @JsonProperty("short_code") val shortCode: String? = null,
    @JsonProperty("file_type") val fileType: String? = null,
    @JsonProperty("mime") val mime: String? = null,
    @JsonProperty("file_size") val fileSize: Long? = null,
    @JsonProperty("extension") val extension: String? = null,
    @JsonProperty("season_num") val seasonNum: Int? = null,
    @JsonProperty("episode_num") val episodeNum: Int? = null,
    @JsonProperty("quality_score") val qualityScore: Int? = null,
    @JsonProperty("added_date") val addedDate: String? = null,
    @JsonProperty("play_url") val playUrl: String? = null,
    @JsonProperty("ddl_url") val ddlUrl: String? = null,
    @JsonProperty("thumbnail_url") val thumbnailUrl: String? = null
)

data class PmFilesData(
    @JsonProperty("files") val files: List<PmFile>? = null,
    @JsonProperty("total_found") val totalFound: Int? = null
)

data class PmFilesResponse(
    @JsonProperty("success") val success: Boolean? = null,
    @JsonProperty("data") val data: PmFilesData? = null
)

data class PmMediaPass(
    val shortCode: String,
    val title: String,
    val fileSize: Long? = null,
    val playUrl: String? = null,
    val ddlUrl: String? = null
)

class PencariMovie : MainAPI() {
    override var mainUrl = "https://flixtorbot.pencarimovie.com"
    override var name = "PencariMovie"
    override val hasMainPage = true
    override var lang = "ms"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    private fun getAjaxHeaders(): Map<String, String> {
        return mapOf(
            "User-Agent" to USER_AGENT,
            "X-Requested-With" to "XMLHttpRequest",
            "Referer" to "$mainUrl/"
        )
    }

    override val mainPage = mainPageOf(
        "action=stream_posts&limit=20" to "🔥 Terkini & Hangat",
        "action=stream_posts&category=malay&limit=20" to "🇲🇾 Filem & Drama Melayu",
        "action=stream_posts&category=indonesian&limit=20" to "🇮🇩 Filem & Drama Indonesia",
        "action=stream_posts&category=korean&limit=20" to "🇰🇷 K-Drama & Korea",
        "action=stream_posts&category=action&limit=20" to "💥 Aksi (Action)",
        "action=stream_posts&category=animation&limit=20" to "🎨 Animasi & Kartun",
        "action=stream_posts&category=comedy&limit=20" to "😂 Komedi (Comedy)",
        "action=stream_posts&category=horror&limit=20" to "👻 Seram (Horror)",
        "action=stream_posts&category=sci-fi&limit=20" to "🚀 Sains Fiksyen (Sci-Fi)",
        "action=stream_posts&category=drama&limit=20" to "🎭 Drama & Romantik"
    )

    private fun cleanTitle(raw: String): String {
        return raw
            .replace(Regex("""(?i)\s*[•·\-|]\s*(?:Movie|TvSeries|Series|Film)\b.*"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val targetUrl = "$mainUrl/wp-admin/admin-ajax.php?${request.data}&page=$page"
        val response = try {
            app.get(targetUrl, headers = getAjaxHeaders(), timeout = 15).text
        } catch (_: Exception) {
            return null
        }

        val postList: List<PmPost> = tryParseJson<PmPostsResponse>(response)?.data
            ?: tryParseJson<List<PmPost>>(response)
            ?: emptyList()

        if (postList.isEmpty()) return null

        val searchResponses = postList.mapNotNull { post ->
            val rawTitle = post.title?.trim() ?: return@mapNotNull null
            val postId = post.id ?: return@mapNotNull null
            val clean = cleanTitle(rawTitle).ifBlank { rawTitle }
            val isMovie = rawTitle.contains("Movie", ignoreCase = true)
            val link = post.permalink ?: "$mainUrl/?p=$postId"
            val safeUrl = "$mainUrl/?pm_post_id=$postId&type=${if (isMovie) "movie" else "tv"}&url=${URLEncoder.encode(link, "UTF-8")}"

            if (isMovie) {
                newMovieSearchResponse(clean, safeUrl, TvType.Movie) {
                    this.posterUrl = post.thumbnailUrl
                }
            } else {
                newTvSeriesSearchResponse(clean, safeUrl, TvType.TvSeries) {
                    this.posterUrl = post.thumbnailUrl
                }
            }
        }

        return newHomePageResponse(request.name, searchResponses)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val results = mutableListOf<SearchResponse>()

        // 1. Carian pos katalog filem/drama
        try {
            val res = app.get("$mainUrl/wp-admin/admin-ajax.php?action=stream_search&search=$q&limit=30", headers = getAjaxHeaders(), timeout = 15).text
            val posts = tryParseJson<PmPostsResponse>(res)?.data ?: tryParseJson<List<PmPost>>(res) ?: emptyList()
            posts.forEach { post ->
                val rawTitle = post.title?.trim() ?: return@forEach
                val postId = post.id ?: return@forEach
                val clean = cleanTitle(rawTitle).ifBlank { rawTitle }
                val isMovie = rawTitle.contains("Movie", ignoreCase = true)
                val link = post.permalink ?: "$mainUrl/?p=$postId"
                val safeUrl = "$mainUrl/?pm_post_id=$postId&type=${if (isMovie) "movie" else "tv"}&url=${URLEncoder.encode(link, "UTF-8")}"

                if (isMovie) {
                    results.add(newMovieSearchResponse(clean, safeUrl, TvType.Movie) {
                        this.posterUrl = post.thumbnailUrl
                    })
                } else {
                    results.add(newTvSeriesSearchResponse(clean, safeUrl, TvType.TvSeries) {
                        this.posterUrl = post.thumbnailUrl
                    })
                }
            }
        } catch (_: Exception) {}

        // 2. Carian terus fail Telegram jika katalog kosong atau padanan fail khusus
        if (results.isEmpty()) {
            try {
                val res = app.get("$mainUrl/wp-admin/admin-ajax.php?action=stream_search_files&search=$q&limit=30", headers = getAjaxHeaders(), timeout = 15).text
                val files = tryParseJson<PmFilesResponse>(res)?.data?.files ?: emptyList()
                files.forEach { file ->
                    val sc = file.shortCode ?: return@forEach
                    val title = file.title?.trim() ?: sc
                    val isMovie = (file.seasonNum ?: 0) == 0 && (file.episodeNum ?: 0) == 0
                    val fileUrl = "$mainUrl/link/$sc"

                    if (isMovie) {
                        results.add(newMovieSearchResponse(title, fileUrl, TvType.Movie) {
                            this.posterUrl = file.thumbnailUrl
                        })
                    } else {
                        results.add(newTvSeriesSearchResponse(title, fileUrl, TvType.TvSeries) {
                            this.posterUrl = file.thumbnailUrl
                        })
                    }
                }
            } catch (_: Exception) {}
        }

        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val postId = Regex("""pm_post_id=(\d+)""").find(url)?.groupValues?.getOrNull(1)

        if (!postId.isNullOrBlank()) {
            // Mengambil maklumat fail media dan episod dari endpoint AJAX
            val filesUrl = "$mainUrl/wp-admin/admin-ajax.php?action=stream_post_files&post_id=$postId&limit=60"
            val filesText = try {
                app.get(filesUrl, headers = getAjaxHeaders(), timeout = 20).text
            } catch (_: Exception) {
                ""
            }

            val filesData = tryParseJson<PmFilesResponse>(filesText)?.data?.files ?: emptyList()

            // Dapatkan maklumat pos jika ada permalink
            val originalUrl = Regex("""url=([^&]+)""").find(url)?.groupValues?.getOrNull(1)?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            } ?: "$mainUrl/?p=$postId"

            val doc = try {
                app.get(originalUrl, headers = mapOf("User-Agent" to USER_AGENT), timeout = 10).document
            } catch (_: Exception) {
                null
            }

            val rawTitle = doc?.selectFirst("h1.entry-title, h1")?.text()?.trim()
                ?: filesData.firstOrNull()?.title
                ?: "Movie"
            val title = cleanTitle(rawTitle).ifBlank { rawTitle }

            val poster = doc?.selectFirst("meta[property='og:image']")?.attr("content")
                ?: doc?.selectFirst(".wp-post-image, .attachment-post-thumbnail, img")?.attr("src")
                ?: filesData.firstOrNull()?.thumbnailUrl

            val synopsis = doc?.selectFirst(".entry-content p, .entry-content")?.text()?.trim()
            val year = Regex("""\b(19\d{2}|20\d{2})\b""").find(rawTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()

            val hasMultipleEpisodes = filesData.size > 1 && filesData.any { (it.episodeNum ?: 0) > 0 || (it.seasonNum ?: 0) > 0 }
            val isMovie = !hasMultipleEpisodes && (url.contains("type=movie", ignoreCase = true) || filesData.size <= 1)

            if (isMovie) {
                val f = filesData.firstOrNull()
                val passData = if (f != null && !f.shortCode.isNullOrBlank()) {
                    toJson(PmMediaPass(
                        shortCode = f.shortCode,
                        title = f.title ?: title,
                        fileSize = f.fileSize,
                        playUrl = f.playUrl,
                        ddlUrl = f.ddlUrl
                    ))
                } else {
                    toJson(PmMediaPass(
                        shortCode = "",
                        title = title,
                        fileSize = null,
                        playUrl = originalUrl,
                        ddlUrl = null
                    ))
                }

                return newMovieLoadResponse(title, url, TvType.Movie, passData) {
                    this.posterUrl = poster
                    this.plot = synopsis
                    this.year = year
                }
            } else {
                val episodes = filesData.mapIndexed { idx, file ->
                    val sc = file.shortCode ?: ""
                    val epTitle = file.title?.ifBlank { "Episod ${idx + 1}" } ?: "Episod ${idx + 1}"
                    val epNum = file.episodeNum ?: (idx + 1)
                    val sNum = file.seasonNum ?: 1
                    val epData = toJson(PmMediaPass(
                        shortCode = sc,
                        title = epTitle,
                        fileSize = file.fileSize,
                        playUrl = file.playUrl,
                        ddlUrl = file.ddlUrl
                    ))

                    newEpisode(epData) {
                        this.name = epTitle
                        this.episode = epNum
                        this.season = sNum
                        this.posterUrl = file.thumbnailUrl ?: poster
                    }
                }.sortedWith(compareBy({ it.season }, { it.episode }))

                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.plot = synopsis
                    this.year = year
                }
            }
        }

        // Fallback untuk URL terus link / short_code
        val shortCodeMatch = Regex("""/link/([a-zA-Z0-9]+)""").find(url)?.groupValues?.getOrNull(1)
        if (!shortCodeMatch.isNullOrBlank()) {
            val passData = toJson(PmMediaPass(
                shortCode = shortCodeMatch,
                title = "Video Play",
                playUrl = url
            ))

            return newMovieLoadResponse("Video Play", url, TvType.Movie, passData)
        }

        return null
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mediaPass = tryParseJson<PmMediaPass>(data)
        val shortCode = mediaPass?.shortCode
            ?: Regex("""/link/([a-zA-Z0-9]+)""").find(data)?.groupValues?.getOrNull(1)
            ?: ""
        val title = mediaPass?.title ?: "Stream"
        val fileSize = mediaPass?.fileSize

        var linkFound = false

        if (shortCode.isNotBlank()) {
            val encodedName = URLEncoder.encode(title, "UTF-8")

            // Pelayan 1: Local Resolver PencariMovie (Port 8088 / telegra.my local daemon)
            val localUrl = "http://127.0.0.1:8088/api/download?short_code=$shortCode&file_size=${fileSize ?: ""}&name=$encodedName"
            callback(
                ExtractorLink(
                    source = name,
                    name = "PencariMovie Server (Port 8088)",
                    url = localUrl,
                    referer = "$mainUrl/",
                    quality = Qualities.P1080.value,
                    isM3u8 = false
                )
            )
            linkFound = true

            // Pelayan 2: Fallback Web Gateway Direct DDL (telegra.my)
            val fallbackUrl = "https://telegra.my/api/download?short_code=$shortCode"
            callback(
                ExtractorLink(
                    source = name,
                    name = "PencariMovie Web Fast Gateway",
                    url = fallbackUrl,
                    referer = "$mainUrl/",
                    quality = Qualities.P720.value,
                    isM3u8 = false
                )
            )

            // Pelayan 3: Halaman pautan /link/{shortCode} scraping untuk pelayan video alternatif
            try {
                val linkPageUrl = "$mainUrl/link/$shortCode"
                val doc = app.get(linkPageUrl, headers = mapOf("User-Agent" to USER_AGENT), timeout = 8).document

                // Cari pautan muat turun atau video langsung
                doc.select("a.download-btn, a[href*='api/download'], a[href*='download'], video source, video").forEach { el ->
                    val href = el.attr("href").ifBlank { el.attr("src") }
                    if (href.isNotBlank() && !href.contains("127.0.0.1") && !href.contains("localhost") && !href.startsWith("tg:")) {
                        val fixed = fixUrl(href)
                        callback(
                            ExtractorLink(
                                source = "$name Direct",
                                name = "PencariMovie Direct Stream",
                                url = fixed,
                                referer = "$mainUrl/",
                                quality = Qualities.Unknown.value,
                                isM3u8 = fixed.contains(".m3u8")
                            )
                        )
                        linkFound = true
                    }
                }
            } catch (_: Exception) {}
        }

        return linkFound
    }
}
