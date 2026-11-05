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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    private fun formatFileSize(size: Long?): String {
        if (size == null || size <= 0L) return ""
        val mb = size.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1000.0) {
            String.format("%.1f GB", mb / 1024.0)
        } else {
            String.format("%.0f MB", mb)
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val offset = (page - 1) * 20
        val url = if (page > 1) {
            "${request.data}&offset=$offset"
        } else {
            request.data
        }

        val jsonText = try {
            app.get(url, headers = getAjaxHeaders(), timeout = 10).text
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
            app.get(searchUrl, headers = getAjaxHeaders(), timeout = 10).text
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
            // Jalankan kedua-dua permintaan API serentak secara selari (Parallel Async) untuk pantas
            val postUrl = "$mainUrl/wp-admin/admin-ajax.php?action=stream_get_post&post_id=$postId"
            val filesUrl = "$mainUrl/wp-admin/admin-ajax.php?action=stream_post_files&post_id=$postId&limit=50"

            val (postText, filesText) = coroutineScope {
                val postDeferred = async {
                    try {
                        app.get(postUrl, headers = getAjaxHeaders(), timeout = 8).text
                    } catch (_: Exception) {
                        ""
                    }
                }
                val filesDeferred = async {
                    try {
                        app.get(filesUrl, headers = getAjaxHeaders(), timeout = 8).text
                    } catch (_: Exception) {
                        ""
                    }
                }
                Pair(postDeferred.await(), filesDeferred.await())
            }

            val singlePost = try { JSONObject(postText).optJSONObject("data") } catch (_: Exception) { null }
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

                // Simpan semua fail pilihan resolusi dalam passObj
                val filesListJson = JSONArray()
                for (idx in 0 until filesArr.length()) {
                    val fItem = filesArr.optJSONObject(idx) ?: continue
                    val sc = fItem.optString("short_code", "")
                    if (sc.isNotBlank()) {
                        filesListJson.put(JSONObject().apply {
                            put("shortCode", sc)
                            put("title", fItem.optString("title", fTitle))
                            put("fileSize", fItem.optLong("file_size", 0L))
                            put("ddlUrl", fItem.optString("ddl_url", ""))
                        })
                    }
                }

                val passObj = JSONObject().apply {
                    put("shortCode", shortCode)
                    put("title", fTitle)
                    if (fSize != null) put("fileSize", fSize)
                    put("playUrl", if (playUrl.isNotBlank()) playUrl else url)
                    if (ddlUrl.isNotBlank()) put("ddlUrl", ddlUrl)
                    if (filesListJson.length() > 0) put("files", filesListJson)
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

    private fun isCompanionInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.pencarimovie.server", 0) != null
        } catch (_: Exception) {
            false
        }
    }

    private fun triggerCompanionServiceNonBlocking() {
        if (!isCompanionInstalled()) return
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
        } catch (_: Exception) {}
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
        var filesArr: JSONArray? = null

        try {
            val json = JSONObject(data)
            shortCode = json.optString("shortCode", "")
            title = json.optString("title", "Stream")
            if (json.has("fileSize")) {
                fileSize = json.optLong("fileSize")
            }
            ddlUrl = json.optString("ddlUrl", "")
            filesArr = json.optJSONArray("files")
        } catch (_: Exception) {
            shortCode = Regex("""/link/([a-zA-Z0-9]+)""").find(data)?.groupValues?.getOrNull(1) ?: ""
        }

        if (shortCode.isBlank()) {
            shortCode = Regex("""/link/([a-zA-Z0-9]+)""").find(data)?.groupValues?.getOrNull(1) ?: ""
        }

        var linkFound = false

        // Ambil URL pelayan penstriman daripada SharedPreferences (Lalai: http://127.0.0.1:8088)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val configuredServer = prefs.getString(PREF_PM_SERVER_URL, DEFAULT_PM_SERVER_URL)
            ?.trimEnd('/') ?: DEFAULT_PM_SERVER_URL

        // Mulakan companion service secara tak sekat (Non-blocking background intent - 0ms delay)
        val isLocalhost = configuredServer.contains("127.0.0.1") || configuredServer.contains("localhost")
        if (isLocalhost) {
            triggerCompanionServiceNonBlocking()
        }

        // 1. Jika terdapat beberapa fail resolusi (cth: 1080p, 720p), keluarkan kesemuanya serta-merta
        if (filesArr != null && filesArr.length() > 0) {
            for (i in 0 until filesArr.length()) {
                val fObj = filesArr.optJSONObject(i) ?: continue
                val fSc = fObj.optString("shortCode", "")
                if (fSc.isBlank()) continue
                val fTitle = fObj.optString("title", title)
                val fSize = fObj.optLong("fileSize", 0L).takeIf { it > 0 }
                val fDdl = fObj.optString("ddlUrl", "")
                val encodedName = URLEncoder.encode(fTitle, "UTF-8")

                val sizeText = formatFileSize(fSize)
                val label = if (sizeText.isNotBlank()) "PencariMovie ($sizeText)" else "PencariMovie ($fTitle)"

                val streamUrl = "$configuredServer/api/download?short_code=$fSc&file_size=${fSize ?: ""}&name=$encodedName"
                callback(
                    newExtractorLink(
                        source = name,
                        name = label,
                        url = streamUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = if (fTitle.contains("1080", ignoreCase = true)) Qualities.P1080.value else Qualities.P720.value
                    }
                )
                linkFound = true

                if (fDdl.isNotBlank()) {
                    callback(
                        newExtractorLink(
                            source = "$name Fast DDL",
                            name = "Direct DDL ($sizeText)",
                            url = fDdl,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = "$mainUrl/"
                            this.quality = Qualities.P720.value
                        }
                    )
                }
            }
        } else if (shortCode.isNotBlank()) {
            // 2. Jika 1 fail tunggal, keluarkan pautan strim terus serta-merta tanpa sebarang lengah masa
            val encodedName = URLEncoder.encode(title, "UTF-8")
            val sizeText = formatFileSize(fileSize)
            val label = if (sizeText.isNotBlank()) "PencariMovie Server ($sizeText)" else "PencariMovie Server (Port 8088)"
            val serverStreamUrl = "$configuredServer/api/download?short_code=$shortCode&file_size=${fileSize ?: ""}&name=$encodedName"

            callback(
                newExtractorLink(
                    source = name,
                    name = label,
                    url = serverStreamUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.P1080.value
                }
            )
            linkFound = true

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
        }

        return linkFound
    }
}
