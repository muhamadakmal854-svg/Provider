package com.mts.dotdrama

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import android.util.Log
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import org.json.JSONObject
import org.json.JSONArray

class DotDrama : MainAPI() {
    override var mainUrl = "https://jrjp.vividshort.com"
    override var name = "DotDrama"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AsianDrama,
        TvType.TvSeries,
        TvType.Movie
    )

    companion object {
        private const val ANDROID_ID = "d88f4e2b10a93c7136f32810a0129c91"
        private const val PKG_NAME = "com.vividshort.drama.vivid"
        private const val APP_VERSION = "1.9.1"
        private const val TMDB_API_KEY = "b030404650f279792a8d3287232358e3"

        fun parseDateMillis(timestamp: Long): Long {
            return if (timestamp > 0L) timestamp else System.currentTimeMillis()
        }
    }

    private fun getHeaders(): Map<String, String> {
        val ts = System.currentTimeMillis().toString()
        val uuid = java.util.UUID.randomUUID().toString()

        return mapOf(
            "User-Agent" to "okhttp/4.12.0",
            "Accept" to "application/json",
            "X-Ameal-Vage" to APP_VERSION,
            "X-Pestab" to PKG_NAME,
            "X-Dstre-Tfin" to "ANDROID",
            "X-Cconsi-Vage" to "V2",
            "X-Ppool" to "PANGLE",
            "X-Rstre-Ilook" to uuid,
            "X-Dide-Ilook" to ANDROID_ID,
            "X-Dstre-Ilook" to ANDROID_ID,
            "X-Thote" to ts,
            "tlbootxqjrmxk" to "V2",
            "xannmddtlmlcba" to "Pixel 7",
            "vvdyrllngkkfki" to "Google",
            "wrctseguov" to "",
            "xqwnegmvwrok" to "",
            "oapgxxkf" to "",
            "xltpbr" to ""
        )
    }

    private suspend fun fetchAndDecrypt(url: String): String? {
        return try {
            val response = app.get(url, headers = getHeaders(), timeout = 25)
            val headerKlow = response.headers["X-Ecurve-Klow"] ?: response.headers["x-ecurve-klow"]
            val rawBody = response.text
            if (rawBody.isBlank()) return null
            DotDramaCipher.decrypt(rawBody, headerKlow)
        } catch (e: Exception) {
            Log.e("DotDrama", "fetchAndDecrypt error for $url: ${e.message}")
            null
        }
    }

    override val mainPage = mainPageOf(
        "api/snast/pspre?pdirec=1&pglas=20&lweek=&gwork=true" to "🔥 Sedang Tren (Trending Now)",
        "api/snast/pspre?pdirec=1&pglas=20&lweek=id&gwork=true" to "🇮🇩 Drama Bahasa Indonesia (Sub/Dub Indo)",
        "api/snast/pspre?pdirec=2&pglas=20&lweek=id&gwork=true" to "⚡ Rilisan Terbaru Indo (Latest Updates)",
        "api/snast/pspre?pdirec=1&pglas=20&lweek=en&gwork=true" to "🌸 Drama Romantis & CEO Pilihan",
        "api/snast/pspre?pdirec=2&pglas=20&lweek=en&gwork=true" to "⚔️ Balas Dendam & Konflik Keluarga",
        "api/snast/pspre?pdirec=3&pglas=20&lweek=en&gwork=true" to "🐺 Werewolf & Fantasi Gelap (Supernatural)",
        "api/snast/pspre?pdirec=4&pglas=20&lweek=en&gwork=true" to "👑 Miliarder & Pewaris Rahasia (Billionaire)",
        "api/snast/pspre?pdirec=5&pglas=20&lweek=en&gwork=true" to "💍 Pernikahan Kontrak (Contract Marriage)",
        "api/snast/pspre?pdirec=6&pglas=20&lweek=en&gwork=true" to "💥 Aksi & Mafia (Action & Underworld)",
        "api/snast/pspre?pdirec=1&pglas=20&lweek=es&gwork=true" to "🇪🇸 Drama Pilihan Bahasa Spanyol (Spanish Hits)",
        "api/snast/pspre?pdirec=1&pglas=20&lweek=pt&gwork=true" to "🇧🇷 Drama Pilihan Bahasa Portugis (Portuguese)",
        "api/snast/pspre?pdirec=7&pglas=20&lweek=en&gwork=true" to "🌟 Pilihan Populer Global (Global Picks)"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val basePath = request.data.trim().removePrefix("/")
        val pageUrl = if (basePath.contains("pdirec=") && page > 1) {
            val curPage = Regex("""pdirec=(\d+)""").find(basePath)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val nextP = curPage + page - 1
            "$mainUrl/" + basePath.replace(Regex("""pdirec=\d+"""), "pdirec=$nextP")
        } else {
            "$mainUrl/$basePath"
        }

        val home = ArrayList<SearchResponse>()
        try {
            val decrypted = fetchAndDecrypt(pageUrl) ?: return newHomePageResponse(request.name, home)
            val json = JSONObject(decrypted)
            val dgiv = json.optJSONObject("dgiv") ?: return newHomePageResponse(request.name, home)
            val lint = dgiv.optJSONArray("lint") ?: JSONArray()

            for (i in 0 until lint.length()) {
                val item = lint.getJSONObject(i)
                val dcup = item.optString("dcup").trim()
                if (dcup.isEmpty()) continue

                val title = item.optString("nseri").trim()
                if (title.isEmpty()) continue

                val poster = item.optString("pday").trim().takeIf { it.isNotEmpty() }
                val totalEp = item.optInt("ewood", 1)

                val dataUrl = "$mainUrl/api/snast/gdrink?dcup=$dcup&grush=true"

                if (totalEp <= 1) {
                    home.add(newMovieSearchResponse(title, dataUrl, TvType.Movie) {
                        this.posterUrl = poster
                    })
                } else {
                    home.add(newTvSeriesSearchResponse(title, dataUrl, TvType.AsianDrama) {
                        this.posterUrl = poster
                    })
                }
            }
        } catch (e: Exception) {
            Log.e("DotDrama", "getMainPage error [${request.name}]: ${e.message}")
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val results = ArrayList<SearchResponse>()
        val seenIds = HashSet<String>()
        val cleanQuery = query.trim().lowercase()

        try {
            val searchLangs = listOf("id", "en", "")
            for (langCode in searchLangs) {
                val url = "$mainUrl/api/snast/pspre?pdirec=1&pglas=100&lweek=$langCode&gwork=true"
                val decrypted = fetchAndDecrypt(url) ?: continue
                val json = JSONObject(decrypted)
                val dgiv = json.optJSONObject("dgiv") ?: continue
                val lint = dgiv.optJSONArray("lint") ?: continue

                for (i in 0 until lint.length()) {
                    val item = lint.getJSONObject(i)
                    val dcup = item.optString("dcup").trim()
                    if (dcup.isEmpty() || seenIds.contains(dcup)) continue

                    val title = item.optString("nseri").trim()
                    val plot = item.optString("dwill").trim()

                    if (cleanQuery.isEmpty() ||
                        title.lowercase().contains(cleanQuery) ||
                        plot.lowercase().contains(cleanQuery)
                    ) {
                        seenIds.add(dcup)
                        val poster = item.optString("pday").trim().takeIf { it.isNotEmpty() }
                        val totalEp = item.optInt("ewood", 1)
                        val dataUrl = "$mainUrl/api/snast/gdrink?dcup=$dcup&grush=true"

                        if (totalEp <= 1) {
                            results.add(newMovieSearchResponse(title, dataUrl, TvType.Movie) {
                                this.posterUrl = poster
                            })
                        } else {
                            results.add(newTvSeriesSearchResponse(title, dataUrl, TvType.AsianDrama) {
                                this.posterUrl = poster
                            })
                        }
                    }
                }
                if (results.size >= 30) break
            }
        } catch (e: Exception) {
            Log.e("DotDrama", "search error: ${e.message}")
        }

        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val dcup = Regex("""dcup=([0-9]+)""").find(url)?.groupValues?.get(1)
            ?: url.trim().substringAfterLast("/").substringBefore("?")

        val dramaApiUrl = "$mainUrl/api/snast/gdrink?dcup=$dcup&grush=true"
        val decrypted = fetchAndDecrypt(dramaApiUrl) ?: throw ErrorLoadingException("Gagal memuat data drama")

        val json = JSONObject(decrypted)
        val dgiv = json.optJSONObject("dgiv") ?: throw ErrorLoadingException("Data drama kosong")
        val bswitc = dgiv.optJSONObject("bswitc") ?: JSONObject()

        val title = bswitc.optString("nseri").trim().ifEmpty { "Dot Drama $dcup" }
        val plot = bswitc.optString("dwill").trim()
        val poster = bswitc.optString("pday").trim().takeIf { it.isNotEmpty() }
        val totalEp = bswitc.optInt("ewood", 1)
        val rawTime = bswitc.optLong("csme", System.currentTimeMillis())
        val releaseDateMillis = parseDateMillis(rawTime)

        val yearMatch = Regex("""\b(20\d\d)\b""").find(title)
        val year = yearMatch?.value?.toIntOrNull()

        val tags = ArrayList<String>()
        val sstra = bswitc.optJSONArray("sstra")
        if (sstra != null) {
            for (i in 0 until sstra.length()) {
                val t = sstra.optString(i)
                if (t.isNotEmpty()) tags.add(t)
            }
        }
        val langStr = bswitc.optString("lweek")
        if (langStr.isNotEmpty()) tags.add(langStr.uppercase())
        val spai = bswitc.optString("spai")
        if (spai.isNotEmpty()) tags.add(spai)

        val ebeer = dgiv.optJSONArray("ebeer") ?: JSONArray()
        val isMovie = totalEp <= 1 && ebeer.length() <= 1

        // Look for trailer in funi, episode 1, or TMDB search
        var trailerUrl: String? = null
        val funi = bswitc.optJSONArray("funi")
        if (funi != null && funi.length() > 0) {
            val funiObj = funi.getJSONObject(0)
            trailerUrl = funiObj.optString("Mopp").trim().takeIf { it.isNotEmpty() }
        }

        // Secondary trailer lookup via TMDB
        if (trailerUrl.isNullOrBlank()) {
            try {
                val cleanTitle = title.replace(Regex("""\(\s*\d{4}\s*\)"""), "").trim()
                val tmdbSearchUrl = "https://api.themoviedb.org/3/search/tv?query=${URLEncoder.encode(cleanTitle, "UTF-8")}&api_key=$TMDB_API_KEY"
                val sRes = app.get(tmdbSearchUrl, timeout = 10).text
                val sJson = JSONObject(sRes)
                val sArr = sJson.optJSONArray("results")
                if (sArr != null && sArr.length() > 0) {
                    val tmdbId = sArr.getJSONObject(0).opt("id")?.toString()
                    if (tmdbId != null) {
                        val vidUrl = "https://api.themoviedb.org/3/tv/$tmdbId/videos?api_key=$TMDB_API_KEY"
                        val vidRes = app.get(vidUrl, timeout = 10).text
                        val vidArr = JSONObject(vidRes).optJSONArray("results")
                        if (vidArr != null) {
                            for (v in 0 until vidArr.length()) {
                                val vObj = vidArr.getJSONObject(v)
                                if (vObj.optString("site").equals("YouTube", ignoreCase = true)) {
                                    val key = vObj.optString("key")
                                    if (key.isNotEmpty()) {
                                        trailerUrl = "https://www.youtube.com/watch?v=$key"
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DotDrama", "TMDB trailer search error: ${e.message}")
            }
        }

        // Tertiary trailer fallback: Episode 1 direct stream preview
        if (trailerUrl.isNullOrBlank() && ebeer.length() > 0) {
            val firstEp = ebeer.getJSONObject(0)
            val pphys = firstEp.optJSONArray("pphys")
            if (pphys != null && pphys.length() > 0) {
                trailerUrl = pphys.getJSONObject(0).optString("Mopp").trim().takeIf { it.isNotEmpty() }
            }
        }

        if (isMovie) {
            val movieDataUrl = if (ebeer.length() > 0) {
                val epObj = ebeer.getJSONObject(0)
                JSONObject().apply {
                    put("dcup", dcup)
                    put("ewheel", 1)
                    put("pphys", epObj.optJSONArray("pphys") ?: JSONArray())
                }.toString()
            } else {
                dramaApiUrl
            }

            return newMovieLoadResponse(title, url, TvType.Movie, data = movieDataUrl) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                if (!trailerUrl.isNullOrBlank()) {
                    addTrailer(trailerUrl)
                }
            }
        } else {
            val episodes = ArrayList<Episode>()

            for (i in 0 until ebeer.length()) {
                val epObj = ebeer.getJSONObject(i)
                val epNum = epObj.optInt("ewheel", i + 1)
                val pphys = epObj.optJSONArray("pphys") ?: JSONArray()

                // Encapsulate episode streams in JSON for instantaneous playback without extra HTTP calls
                val epPayload = JSONObject().apply {
                    put("dcup", dcup)
                    put("ewheel", epNum)
                    put("pphys", pphys)
                }.toString()

                episodes.add(newEpisode(epPayload) {
                    this.name = "Episode $epNum"
                    this.episode = epNum
                    this.season = 1
                    this.posterUrl = poster
                    this.description = plot
                    this.date = releaseDateMillis
                })
            }

            // Ensure episodes are sorted by episode number ascending
            episodes.sortBy { it.episode ?: 0 }

            return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                if (!trailerUrl.isNullOrBlank()) {
                    addTrailer(trailerUrl)
                }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var found = false

        try {
            val pphysArray = if (data.trim().startsWith("{")) {
                val json = JSONObject(data)
                json.optJSONArray("pphys") ?: JSONArray()
            } else {
                val decrypted = fetchAndDecrypt(data)
                if (decrypted != null) {
                    val json = JSONObject(decrypted)
                    val dgiv = json.optJSONObject("dgiv")
                    val ebeer = dgiv?.optJSONArray("ebeer")
                    if (ebeer != null && ebeer.length() > 0) {
                        ebeer.getJSONObject(0).optJSONArray("pphys") ?: JSONArray()
                    } else {
                        JSONArray()
                    }
                } else {
                    JSONArray()
                }
            }

            for (i in 0 until pphysArray.length()) {
                val pphy = pphysArray.getJSONObject(i)
                val mopp = pphy.optString("Mopp").trim()
                val bcold = pphy.optString("Bcold").trim()
                val qualityStr = pphy.optString("Dbag").trim().ifEmpty { "720P" }

                val qualityInt = when {
                    qualityStr.contains("1080", ignoreCase = true) -> Qualities.P1080.value
                    qualityStr.contains("720", ignoreCase = true) -> Qualities.P720.value
                    qualityStr.contains("540", ignoreCase = true) -> Qualities.P480.value
                    qualityStr.contains("480", ignoreCase = true) -> Qualities.P480.value
                    qualityStr.contains("360", ignoreCase = true) -> Qualities.P360.value
                    else -> Qualities.Unknown.value
                }

                // Server 1: Direct CDN Playback URL (Mopp)
                if (mopp.isNotEmpty() && mopp.startsWith("http")) {
                    callback(
                        newExtractorLink(
                            source = name,
                            name = "$name - $qualityStr HD",
                            url = mopp,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = "$mainUrl/"
                            this.headers = mapOf(
                                "Referer" to "$mainUrl/",
                                "User-Agent" to "okhttp/4.12.0"
                            )
                            this.quality = qualityInt
                        }
                    )
                    found = true
                }

                // Server 2: Backup CDN Stream (Bcold) if distinct from Mopp
                if (bcold.isNotEmpty() && bcold.startsWith("http") && bcold != mopp) {
                    callback(
                        newExtractorLink(
                            source = name,
                            name = "$name (Backup) - $qualityStr",
                            url = bcold,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = "$mainUrl/"
                            this.headers = mapOf(
                                "Referer" to "$mainUrl/",
                                "User-Agent" to "okhttp/4.12.0"
                            )
                            this.quality = qualityInt
                        }
                    )
                    found = true
                }
            }
        } catch (e: Exception) {
            Log.e("DotDrama", "loadLinks error: ${e.message}")
        }

        return found
    }
}
