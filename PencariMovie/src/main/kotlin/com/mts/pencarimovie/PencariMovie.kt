package com.mts.pencarimovie

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.net.HttpURLConnection
import java.net.URL
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

data class PmSinglePostResponse(
    @JsonProperty("success") val success: Boolean? = null,
    @JsonProperty("data") val data: PmPost? = null
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

class PencariMovie(val context: Context) : MainAPI() {
    override var mainUrl = "https://flixtorbot.pencarimovie.com"
    override var name = "PencariMovie"
    override val hasMainPage = true
    override var lang = "ms"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private var lastToastTime = 0L
    }

    private fun getAjaxHeaders(): Map<String, String> {
        return mapOf(
            "User-Agent" to USER_AGENT,
            "X-Requested-With" to "XMLHttpRequest",
            "Referer" to "$mainUrl/"
        )
    }

    override val mainPage = mainPageOf(
        "$mainUrl/wp-admin/admin-ajax.php?action=stream_posts&limit=20" to "Terkini",
        "$mainUrl/wp-admin/admin-ajax.php?action=stream_posts&category=action&limit=20" to "Aksi (Action)",
        "$mainUrl/wp-admin/admin-ajax.php?action=stream_posts&category=animation&limit=20" to "Animasi (Animation)",
        "$mainUrl/wp-admin/admin-ajax.php?action=stream_posts&category=comedy&limit=20" to "Komedi (Comedy)",
        "$mainUrl/wp-admin/admin-ajax.php?action=stream_posts&category=drama&limit=20" to "Drama",
        "$mainUrl/wp-admin/admin-ajax.php?action=stream_posts&category=horror&limit=20" to "Seram (Horror)",
        "$mainUrl/wp-admin/admin-ajax.php?action=stream_posts&category=sci-fi&limit=20" to "Sains Fiksyen (Sci-Fi)",
        "$mainUrl/wp-admin/admin-ajax.php?action=stream_posts&category=romance&limit=20" to "Romantik (Romance)",
        "$mainUrl/wp-admin/admin-ajax.php?action=stream_posts&category=fantasy&limit=20" to "Fantasi (Fantasy)",
        "$mainUrl/wp-admin/admin-ajax.php?action=stream_posts&category=adventure&limit=20" to "Pengembaraan (Adventure)",
        "$mainUrl/wp-admin/admin-ajax.php?action=stream_posts&category=crime&limit=20" to "Jenayah (Crime)"
    )

    private fun cleanTitle(title: String?): String {
        if (title.isNullOrBlank()) return ""
        return title
            .replace(Regex("""\s*\[.*?\]"""), "")
            .replace(Regex("""\s*\(.*?\)"""), "")
            .replace(Regex("""\s*•\s*(?:Movie|TvSeries|Series)""", RegexOption.IGNORE_CASE), "")
            .replace("Download", "", ignoreCase = true)
            .replace("Streaming", "", ignoreCase = true)
            .replace("Nonton", "", ignoreCase = true)
            .replace("Subtitle Indonesia", "", ignoreCase = true)
            .replace("Malay Subtitle", "", ignoreCase = true)
            .replace("Malay Sub", "", ignoreCase = true)
            .replace("Eng Sub", "", ignoreCase = true)
            .trim()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val offset = (page - 1) * 20
        val url = if (page > 1) {
            "${request.data}&offset=$offset"
        } else {
            request.data
        }

        val jsonText = try {
            app.get(url, headers = getAjaxHeaders(), timeout = 15).text
        } catch (_: Exception) {
            return null
        }

        val res = tryParseJson<PmPostsResponse>(jsonText)
        val posts = res?.data ?: emptyList()
        if (posts.isEmpty()) return null

        val items = posts.mapNotNull { post ->
            val id = post.id ?: return@mapNotNull null
            val rawTitle = post.title ?: return@mapNotNull null
            val title = cleanTitle(rawTitle).ifBlank { rawTitle }
            val poster = post.thumbnailUrl
            val postUrl = "$mainUrl/?p=$id&pm_id=$id"

            val isSeries = rawTitle.contains("TvSeries", ignoreCase = true) ||
                           rawTitle.contains("Series", ignoreCase = true) ||
                           post.categories?.any { it.contains("series", ignoreCase = true) } == true

            val type = if (isSeries) TvType.TvSeries else TvType.Movie

            newAnimeSearchResponse(title, postUrl, type) {
                this.posterUrl = poster
            }
        }

        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/wp-admin/admin-ajax.php?action=stream_search&search=$encoded&limit=25"

        val jsonText = try {
            app.get(searchUrl, headers = getAjaxHeaders(), timeout = 15).text
        } catch (_: Exception) {
            return emptyList()
        }

        val res = tryParseJson<PmPostsResponse>(jsonText)
        val posts = res?.data ?: emptyList()

        return posts.mapNotNull { post ->
            val id = post.id ?: return@mapNotNull null
            val rawTitle = post.title ?: return@mapNotNull null
            val title = cleanTitle(rawTitle).ifBlank { rawTitle }
            val poster = post.thumbnailUrl
            val postUrl = "$mainUrl/?p=$id&pm_id=$id"

            val isSeries = rawTitle.contains("TvSeries", ignoreCase = true) ||
                           rawTitle.contains("Series", ignoreCase = true) ||
                           post.categories?.any { it.contains("series", ignoreCase = true) } == true

            val type = if (isSeries) TvType.TvSeries else TvType.Movie

            newAnimeSearchResponse(title, postUrl, type) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val postIdMatch = Regex("""[?&]pm_id=(\d+)""").find(url) ?: Regex("""[?&]p=(\d+)""").find(url)
        val postId = postIdMatch?.groupValues?.getOrNull(1)

        if (!postId.isNullOrBlank()) {
            // 1. Ambil maklumat pos lengkap melalui stream_get_post
            val postUrl = "$mainUrl/wp-admin/admin-ajax.php?action=stream_get_post&post_id=$postId"
            val postText = try {
                app.get(postUrl, headers = getAjaxHeaders(), timeout = 15).text
            } catch (_: Exception) {
                ""
            }
            val singlePost = tryParseJson<PmSinglePostResponse>(postText)?.data

            // 2. Ambil senarai fail berkaitan melalui stream_post_files
            val filesUrl = "$mainUrl/wp-admin/admin-ajax.php?action=stream_post_files&post_id=$postId&limit=50"
            val filesText = try {
                app.get(filesUrl, headers = getAjaxHeaders(), timeout = 20).text
            } catch (_: Exception) {
                ""
            }

            val filesData = tryParseJson<PmFilesResponse>(filesText)?.data?.files ?: emptyList()

            val rawTitle = singlePost?.title ?: filesData.firstOrNull()?.title ?: "Movie"
            val title = cleanTitle(rawTitle).ifBlank { rawTitle }

            val poster = singlePost?.thumbnailUrl ?: filesData.firstOrNull()?.thumbnailUrl
            val synopsis = singlePost?.excerpt?.replace(Regex("""<[^>]*>"""), "")?.trim()
                ?: singlePost?.content?.replace(Regex("""<[^>]*>"""), "")?.trim()

            val year = Regex("""\b(19\d{2}|20\d{2})\b""").find(rawTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()

            val hasMultipleEpisodes = filesData.size > 1 && filesData.any { (it.episodeNum ?: 0) > 0 || (it.seasonNum ?: 0) > 0 }
            val isSeries = rawTitle.contains("TvSeries", ignoreCase = true) ||
                           rawTitle.contains("Series", ignoreCase = true) ||
                           hasMultipleEpisodes

            if (!isSeries || filesData.isEmpty()) {
                val f = filesData.firstOrNull()
                val passData = if (f != null && !f.shortCode.isNullOrBlank()) {
                    PmMediaPass(
                        shortCode = f.shortCode,
                        title = f.title ?: title,
                        fileSize = f.fileSize,
                        playUrl = f.playUrl,
                        ddlUrl = f.ddlUrl
                    ).toJson()
                } else {
                    PmMediaPass(
                        shortCode = "",
                        title = title,
                        fileSize = null,
                        playUrl = url,
                        ddlUrl = null
                    ).toJson()
                }

                return newMovieLoadResponse(title, url, TvType.Movie, passData) {
                    this.posterUrl = poster
                    this.plot = synopsis
                    this.year = year
                }
            } else {
                val episodes = filesData.mapIndexed { idx, file ->
                    val sc = file.shortCode ?: ""
                    val epNum = if ((file.episodeNum ?: 0) > 0) file.episodeNum else (idx + 1)
                    val sNum = if ((file.seasonNum ?: 0) > 0) file.seasonNum else 1
                    val epTitle = file.title?.ifBlank { "Episod $epNum" } ?: "Episod $epNum"

                    val epData = PmMediaPass(
                        shortCode = sc,
                        title = epTitle,
                        fileSize = file.fileSize,
                        playUrl = file.playUrl,
                        ddlUrl = file.ddlUrl
                    ).toJson()

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
            val passData = PmMediaPass(
                shortCode = shortCodeMatch,
                title = "Video Play",
                playUrl = url
            ).toJson()

            return newMovieLoadResponse("Video Play", url, TvType.Movie, passData)
        }

        return null
    }

    private fun showServerGuideToast() {
        val now = System.currentTimeMillis()
        if (now - lastToastTime > 15000) {
            lastToastTime = now
            Handler(Looper.getMainLooper()).post {
                try {
                    Toast.makeText(
                        context,
                        "PencariMovie: Pelayan port 8088 belum dikesan. Pasang companion app di telegra.my/apk atau tetapkan IP di Tetapan Extension.",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (_: Exception) {}
            }
        }
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

            // Ambil URL pelayan daripada SharedPreferences (Lalai: http://127.0.0.1:8088)
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val configuredServer = prefs.getString(PREF_PM_SERVER_URL, DEFAULT_PM_SERVER_URL)
                ?.trimEnd('/') ?: DEFAULT_PM_SERVER_URL
            val tgCookies = prefs.getString(PREF_PM_TG_COOKIES, null).orEmpty()

            // Semak status pelayan penstriman
            var isServerActive = false
            try {
                val pingUrl = "$configuredServer/api/version"
                val conn = URL(pingUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 1500
                conn.readTimeout = 1500
                conn.requestMethod = "GET"
                conn.connect()
                if (conn.responseCode in 200..299) {
                    isServerActive = true
                }
                conn.disconnect()
            } catch (_: Exception) {}

            if (!isServerActive) {
                showServerGuideToast()
            }

            // Pelayan 1: Konfigurasi Pelayan PencariMovie (Local Daemon / LAN IP / Tunnel)
            val serverStreamUrl = "$configuredServer/api/download?short_code=$shortCode&file_size=${fileSize ?: ""}&name=$encodedName"
            val labelStatus = if (isServerActive) "PencariMovie Server (Aktif)" else "PencariMovie Server (Port 8088)"
            callback(
                newExtractorLink(
                    source = name,
                    name = labelStatus,
                    url = serverStreamUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.P1080.value
                }
            )
            linkFound = true

            // Pelayan 2: Jika DDL URL tersedia secara langsung daripada data API
            val ddlUrl = mediaPass?.ddlUrl
            if (!ddlUrl.isNullOrBlank()) {
                callback(
                    newExtractorLink(
                        source = "$name Fast DDL",
                        name = "PencariMovie Direct DDL",
                        url = ddlUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.P720.value
                    }
                )
            }

            // Pelayan 3: Halaman pautan /link/{shortCode} scraping dengan kuki sesi WordPress / Telegram
            try {
                val linkPageUrl = "$mainUrl/link/$shortCode"
                val headers = mutableMapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/"
                )
                if (tgCookies.isNotBlank()) {
                    headers["Cookie"] = tgCookies
                }

                val doc = app.get(linkPageUrl, headers = headers, timeout = 8).document

                // Cari pautan muat turun atau video langsung
                doc.select("a.download-btn, a[href*='api/download'], a[href*='download'], video source, video").forEach { el ->
                    val href = el.attr("href").ifBlank { el.attr("src") }
                    if (href.isNotBlank() && !href.contains("127.0.0.1") && !href.contains("localhost") && !href.startsWith("tg:")) {
                        val fixed = fixUrl(href)
                        callback(
                            newExtractorLink(
                                source = "$name Direct",
                                name = "PencariMovie Direct Stream",
                                url = fixed,
                                type = if (fixed.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                this.referer = "$mainUrl/"
                            }
                        )
                        linkFound = true
                    }
                }
            } catch (_: Exception) {}
        }

        return linkFound
    }
}
