package com.mts.pencarimovie

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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

        val json = try {
            JSONObject(jsonText)
        } catch (_: Exception) {
            return null
        }

        val dataArr = json.optJSONArray("data") ?: return null
        if (dataArr.length() == 0) return null

        val items = mutableListOf<SearchResponse>()
        for (i in 0 until dataArr.length()) {
            val post = dataArr.optJSONObject(i) ?: continue
            val id = post.optLong("id", -1L)
            if (id <= 0) continue
            val rawTitle = post.optString("title", "")
            if (rawTitle.isBlank()) continue
            val title = cleanTitle(rawTitle).ifBlank { rawTitle }

            var poster = post.optString("thumbnail_url", "").trim()
            if (poster.isBlank()) {
                val content = post.optString("content", "")
                val imgMatch = Regex("""<img[^>]+src=["']([^"']+)["']""").find(content)
                if (imgMatch != null) {
                    poster = imgMatch.groupValues[1].trim()
                }
            }
            val posterUrl = if (poster.isNotBlank()) poster else null
            val postUrl = "$mainUrl/?p=$id&pm_id=$id"

            val isSeries = rawTitle.contains("TvSeries", ignoreCase = true) ||
                           rawTitle.contains("Series", ignoreCase = true) ||
                           post.optJSONArray("categories")?.let { cats ->
                               (0 until cats.length()).any { cats.optString(it).contains("series", ignoreCase = true) }
                           } == true

            val type = if (isSeries) TvType.TvSeries else TvType.Movie

            if (isSeries) {
                items.add(newTvSeriesSearchResponse(title, postUrl, type) {
                    this.posterUrl = posterUrl
                })
            } else {
                items.add(newMovieSearchResponse(title, postUrl, type) {
                    this.posterUrl = posterUrl
                })
            }
        }

        return if (items.isNotEmpty()) {
            newHomePageResponse(request.name, items)
        } else {
            null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/wp-admin/admin-ajax.php?action=stream_search&search=$encoded&limit=25"

        val jsonText = try {
            app.get(searchUrl, headers = getAjaxHeaders(), timeout = 15).text
        } catch (_: Exception) {
            return emptyList()
        }

        val json = try {
            JSONObject(jsonText)
        } catch (_: Exception) {
            return emptyList()
        }

        val dataArr = json.optJSONArray("data") ?: return emptyList()
        val items = mutableListOf<SearchResponse>()
        for (i in 0 until dataArr.length()) {
            val post = dataArr.optJSONObject(i) ?: continue
            val id = post.optLong("id", -1L)
            if (id <= 0) continue
            val rawTitle = post.optString("title", "")
            if (rawTitle.isBlank()) continue
            val title = cleanTitle(rawTitle).ifBlank { rawTitle }

            var poster = post.optString("thumbnail_url", "").trim()
            if (poster.isBlank()) {
                val content = post.optString("content", "")
                val imgMatch = Regex("""<img[^>]+src=["']([^"']+)["']""").find(content)
                if (imgMatch != null) {
                    poster = imgMatch.groupValues[1].trim()
                }
            }
            val posterUrl = if (poster.isNotBlank()) poster else null
            val postUrl = "$mainUrl/?p=$id&pm_id=$id"

            val isSeries = rawTitle.contains("TvSeries", ignoreCase = true) ||
                           rawTitle.contains("Series", ignoreCase = true) ||
                           post.optJSONArray("categories")?.let { cats ->
                               (0 until cats.length()).any { cats.optString(it).contains("series", ignoreCase = true) }
                           } == true

            val type = if (isSeries) TvType.TvSeries else TvType.Movie

            if (isSeries) {
                items.add(newTvSeriesSearchResponse(title, postUrl, type) {
                    this.posterUrl = posterUrl
                })
            } else {
                items.add(newMovieSearchResponse(title, postUrl, type) {
                    this.posterUrl = posterUrl
                })
            }
        }

        return items
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
            val singlePost = try { JSONObject(postText).optJSONObject("data") } catch (_: Exception) { null }

            // 2. Ambil senarai fail berkaitan melalui stream_post_files
            val filesUrl = "$mainUrl/wp-admin/admin-ajax.php?action=stream_post_files&post_id=$postId&limit=50"
            val filesText = try {
                app.get(filesUrl, headers = getAjaxHeaders(), timeout = 20).text
            } catch (_: Exception) {
                ""
            }

            val filesArr = try {
                JSONObject(filesText).optJSONObject("data")?.optJSONArray("files") ?: JSONArray()
            } catch (_: Exception) {
                JSONArray()
            }

            val rawTitle = singlePost?.optString("title", "")?.ifBlank { null }
                ?: (if (filesArr.length() > 0) filesArr.optJSONObject(0)?.optString("title", "") else null)
                ?: "Movie"
            val title = cleanTitle(rawTitle).ifBlank { rawTitle }

            var poster = singlePost?.optString("thumbnail_url", "")?.trim().orEmpty()
            if (poster.isBlank() && singlePost != null) {
                val content = singlePost.optString("content", "")
                val imgMatch = Regex("""<img[^>]+src=["']([^"']+)["']""").find(content)
                if (imgMatch != null) {
                    poster = imgMatch.groupValues[1].trim()
                }
            }
            if (poster.isBlank() && filesArr.length() > 0) {
                poster = filesArr.optJSONObject(0)?.optString("thumbnail_url", "")?.trim().orEmpty()
            }
            val posterUrl = if (poster.isNotBlank()) poster else null

            val synopsis = singlePost?.optString("excerpt", "")?.replace(Regex("""<[^>]*>"""), "")?.trim()?.ifBlank { null }
                ?: singlePost?.optString("content", "")?.replace(Regex("""<[^>]*>"""), "")?.trim()?.ifBlank { null }

            val year = Regex("""\b(19\d{2}|20\d{2})\b""").find(rawTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()

            var hasMultipleEpisodes = false
            for (idx in 0 until filesArr.length()) {
                val fObj = filesArr.optJSONObject(idx) ?: continue
                if (fObj.optInt("episode_num", 0) > 0 || fObj.optInt("season_num", 0) > 0) {
                    hasMultipleEpisodes = true
                    break
                }
            }
            val isSeries = rawTitle.contains("TvSeries", ignoreCase = true) ||
                           rawTitle.contains("Series", ignoreCase = true) ||
                           hasMultipleEpisodes

            if (!isSeries || filesArr.length() <= 1) {
                val f = if (filesArr.length() > 0) filesArr.optJSONObject(0) else null
                val shortCode = f?.optString("short_code", "").orEmpty()
                val fTitle = f?.optString("title", title) ?: title
                val fSize = f?.optLong("file_size", 0L)?.takeIf { it > 0 }
                val playUrl = f?.optString("play_url", "").orEmpty()
                val ddlUrl = f?.optString("ddl_url", "").orEmpty()

                val passObj = JSONObject().apply {
                    put("shortCode", shortCode)
                    put("title", fTitle)
                    if (fSize != null) put("fileSize", fSize)
                    put("playUrl", if (playUrl.isNotBlank()) playUrl else url)
                    if (ddlUrl.isNotBlank()) put("ddlUrl", ddlUrl)
                }

                return newMovieLoadResponse(title, url, TvType.Movie, passObj.toString()) {
                    this.posterUrl = posterUrl
                    this.plot = synopsis
                    this.year = year
                }
            } else {
                val episodes = mutableListOf<Episode>()
                for (idx in 0 until filesArr.length()) {
                    val file = filesArr.optJSONObject(idx) ?: continue
                    val sc = file.optString("short_code", "")
                    val epRaw = file.optInt("episode_num", 0)
                    val sRaw = file.optInt("season_num", 0)
                    val epNum = if (epRaw > 0) epRaw else (idx + 1)
                    val sNum = if (sRaw > 0) sRaw else 1
                    val epTitle = file.optString("title", "").ifBlank { "Episod $epNum" }
                    val fSize = file.optLong("file_size", 0L).takeIf { it > 0 }
                    val pUrl = file.optString("play_url", "")
                    val dUrl = file.optString("ddl_url", "")
                    val fThumb = file.optString("thumbnail_url", "").ifBlank { poster }

                    val epPassObj = JSONObject().apply {
                        put("shortCode", sc)
                        put("title", epTitle)
                        if (fSize != null) put("fileSize", fSize)
                        if (pUrl.isNotBlank()) put("playUrl", pUrl)
                        if (dUrl.isNotBlank()) put("ddlUrl", dUrl)
                    }

                    episodes.add(
                        newEpisode(epPassObj.toString()) {
                            this.name = epTitle
                            this.episode = epNum
                            this.season = sNum
                            this.posterUrl = if (fThumb.isNotBlank()) fThumb else posterUrl
                        }
                    )
                }
                val sortedEpisodes = episodes.sortedWith(compareBy({ it.season }, { it.episode }))

                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, sortedEpisodes) {
                    this.posterUrl = posterUrl
                    this.plot = synopsis
                    this.year = year
                }
            }
        }

        // Fallback untuk URL terus link / short_code
        val shortCodeMatch = Regex("""/link/([a-zA-Z0-9]+)""").find(url)?.groupValues?.getOrNull(1)
        if (!shortCodeMatch.isNullOrBlank()) {
            val passObj = JSONObject().apply {
                put("shortCode", shortCodeMatch)
                put("title", "Video Play")
                put("playUrl", url)
            }

            return newMovieLoadResponse("Video Play", url, TvType.Movie, passObj.toString())
        }

        return null
    }

    private fun pingServer(serverUrl: String): Boolean {
        return try {
            val pingUrl = "$serverUrl/api/version"
            val conn = URL(pingUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 1200
            conn.readTimeout = 1200
            conn.requestMethod = "GET"
            conn.connect()
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private fun isCompanionInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.pencarimovie.server", 0) != null
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureCompanionServerStarted(configuredServer: String): Boolean {
        if (pingServer(configuredServer)) return true

        val isLocalhost = configuredServer.contains("127.0.0.1") || configuredServer.contains("localhost")
        if (isLocalhost && isCompanionInstalled()) {
            try {
                val intent = Intent("com.pencarimovie.server.action.START").apply {
                    setPackage("com.pencarimovie.server")
                    component = ComponentName("com.pencarimovie.server", "com.pencarimovie.server.ServerService")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }

                for (i in 1..6) {
                    Thread.sleep(500)
                    if (pingServer(configuredServer)) {
                        return true
                    }
                }
            } catch (_: Exception) {}
        }

        return pingServer(configuredServer)
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
        var shortCode = ""
        var title = "Stream"
        var fileSize: Long? = null
        var ddlUrl = ""

        try {
            val json = JSONObject(data)
            shortCode = json.optString("shortCode", "")
            title = json.optString("title", "Stream")
            if (json.has("fileSize")) {
                fileSize = json.optLong("fileSize")
            }
            ddlUrl = json.optString("ddlUrl", "")
        } catch (_: Exception) {
            shortCode = Regex("""/link/([a-zA-Z0-9]+)""").find(data)?.groupValues?.getOrNull(1) ?: ""
        }

        if (shortCode.isBlank()) {
            shortCode = Regex("""/link/([a-zA-Z0-9]+)""").find(data)?.groupValues?.getOrNull(1) ?: ""
        }

        var linkFound = false

        if (shortCode.isNotBlank()) {
            val encodedName = URLEncoder.encode(title, "UTF-8")

            // Ambil URL pelayan daripada SharedPreferences (Lalai: http://127.0.0.1:8088)
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val configuredServer = prefs.getString(PREF_PM_SERVER_URL, DEFAULT_PM_SERVER_URL)
                ?.trimEnd('/') ?: DEFAULT_PM_SERVER_URL
            val tgCookies = prefs.getString(PREF_PM_TG_COOKIES, null).orEmpty()

            // Semak status pelayan penstriman & auto-start companion jika dipasang
            val isServerActive = ensureCompanionServerStarted(configuredServer)

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
            if (ddlUrl.isNotBlank()) {
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
