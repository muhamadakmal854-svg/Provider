package com.mts.kisskh

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import android.util.Log
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import org.json.JSONObject
import org.json.JSONArray

class Kisskh : MainAPI() {
    override var mainUrl = "https://kisskh.do"
    override var name = "Kisskh"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie,
        TvType.AsianDrama,
        TvType.Anime
    )

    companion object {
        private const val TMDB_API_KEY = "b030404650f279792a8d3287232358e3"
        private const val VIDEO_GUID = "62f176f3bb1b5b8e70e39932ad34a0c7"
        private const val SUB_GUID = "VgV52sWhwvBSf8BsM3BRY9weWiiCbtGp"

        fun parseDateMillis(dateStr: String?): Long? {
            if (dateStr.isNullOrBlank()) return null
            return try {
                val clean = dateStr.trim().substringBefore("T")
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                format.parse(clean)?.time
            } catch (e: Exception) {
                null
            }
        }
    }

    override val mainPage = mainPageOf(
        "api/DramaList/MostView?ispc=false&c=1" to "🔥 Sedang Tren (Trending)",
        "api/DramaList/TopRating?ispc=false" to "⭐ Rating Tertinggi (Top Rated)",
        "api/DramaList/List?type=0&sub=0&country=0&status=0&order=1" to "⚡ Rilisan Terbaru (Latest Updates)",
        "api/DramaList/List?type=1&sub=0&country=2&status=0&order=1" to "🌸 Drama Korea Pilihan (K-Drama)",
        "api/DramaList/List?type=1&sub=0&country=1&status=0&order=1" to "🏮 Drama China Populer (C-Drama)",
        "api/DramaList/List?type=1&sub=0&country=3&status=0&order=1" to "🇯🇵 Sinema Jepang (Japanese Drama)",
        "api/DramaList/List?type=1&sub=0&country=4&status=0&order=1" to "🇹🇭 Lakorn & Drama Thailand",
        "api/DramaList/List?type=2&sub=0&country=0&status=0&order=1" to "🍿 Film Bioskop Asia (Movies)",
        "api/DramaList/Animate?ispc=false" to "⚔️ Anime Populer (Anime Hub)",
        "api/DramaList/List?type=1&sub=0&country=0&status=2&order=1" to "🏆 Serial TV Tamat (Completed)",
        "api/DramaList/List?type=1&sub=0&country=0&status=1&order=1" to "🎬 Sedang Tayang (Ongoing)",
        "api/DramaList/List?type=0&sub=0&country=0&status=0&order=2" to "🌟 Pilihan Favorit Penonton"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val basePath = request.data.trim().removePrefix("/")
        val pageUrl = if (basePath.contains("api/DramaList/List")) {
            "$mainUrl/$basePath&page=$page&pageSize=30"
        } else {
            "$mainUrl/$basePath"
        }

        val home = ArrayList<SearchResponse>()
        try {
            val response = app.get(
                pageUrl,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/"
                ),
                timeout = 25
            ).text

            val jsonArray = try {
                val json = JSONObject(response)
                json.optJSONArray("data") ?: JSONArray()
            } catch (e: Exception) {
                try {
                    JSONArray(response)
                } catch (e2: Exception) {
                    JSONArray()
                }
            }

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val id = item.optLong("id")
                if (id <= 0L) continue

                val title = item.optString("title").trim()
                if (title.isEmpty()) continue

                val poster = item.optString("thumbnail").let {
                    if (it.isNotEmpty()) fixUrl(it) else null
                }
                val epCount = item.optInt("episodesCount", 1)
                val isMovie = request.data.contains("type=2") || epCount <= 1

                val dataUrl = "$mainUrl/api/DramaList/Drama/$id"

                if (isMovie) {
                    home.add(newMovieSearchResponse(title, dataUrl, TvType.Movie) {
                        this.posterUrl = poster
                    })
                } else {
                    home.add(newTvSeriesSearchResponse(title, dataUrl, TvType.TvSeries) {
                        this.posterUrl = poster
                    })
                }
            }
        } catch (e: Exception) {
            Log.e("Kisskh", "getMainPage error for [${request.name}]: ${e.message}")
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val results = ArrayList<SearchResponse>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$mainUrl/api/DramaList/Search?q=$encodedQuery&type=0"
            val response = app.get(
                searchUrl,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/"
                ),
                timeout = 20
            ).text

            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val id = item.optLong("id")
                if (id <= 0L) continue

                val title = item.optString("title").trim()
                if (title.isEmpty()) continue

                val poster = item.optString("thumbnail").let {
                    if (it.isNotEmpty()) fixUrl(it) else null
                }
                val epCount = item.optInt("episodesCount", 1)
                val dataUrl = "$mainUrl/api/DramaList/Drama/$id"

                if (epCount <= 1) {
                    results.add(newMovieSearchResponse(title, dataUrl, TvType.Movie) {
                        this.posterUrl = poster
                    })
                } else {
                    results.add(newTvSeriesSearchResponse(title, dataUrl, TvType.TvSeries) {
                        this.posterUrl = poster
                    })
                }
            }
        } catch (e: Exception) {
            Log.e("Kisskh", "search error: ${e.message}")
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val dramaId = Regex("""(?:Drama/|id=)(\d+)""").find(url)?.groupValues?.get(1)
            ?: url.trim().removeSuffix("/").substringAfterLast("/")

        val dramaApi = "$mainUrl/api/DramaList/Drama/$dramaId"
        val response = app.get(
            dramaApi,
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "$mainUrl/"
            ),
            timeout = 25
        ).text

        val drama = JSONObject(response)
        val title = drama.optString("title").trim()
        val description = drama.optString("description").trim()
        val releaseDate = drama.optString("releaseDate")
        val country = drama.optString("country")
        val status = drama.optString("status")
        val rawType = drama.optString("type")
        val rawTrailer = drama.optString("trailer")
        val poster = drama.optString("thumbnail").let {
            if (it.isNotEmpty()) fixUrl(it) else null
        }

        val yearMatch = Regex("""\b(19\d\d|20\d\d)\b""").find(title)
            ?: Regex("""\b(19\d\d|20\d\d)\b""").find(releaseDate)
        val year = yearMatch?.value?.toIntOrNull()

        val cleanTitle = title.replace(Regex("""\(\s*\d{4}\s*\)"""), "").trim()

        val episodesArray = drama.optJSONArray("episodes") ?: JSONArray()
        val isMovie = rawType.equals("Movie", ignoreCase = true) && episodesArray.length() <= 1

        // TMDB lookup for official trailer & enriched episode metadata
        var trailerUrl: String? = if (rawTrailer.contains("youtube.com") || rawTrailer.contains("youtu.be")) {
            rawTrailer
        } else null

        var tmdbId: String? = null
        val tmdbEpisodesMap = HashMap<Int, JSONObject>()

        try {
            val searchType = if (isMovie) "movie" else "tv"
            val tmdbSearchUrl = "https://api.themoviedb.org/3/search/$searchType?query=${URLEncoder.encode(cleanTitle, "UTF-8")}&api_key=$TMDB_API_KEY"
            val searchRes = app.get(tmdbSearchUrl, timeout = 10).text
            val searchJson = JSONObject(searchRes)
            val resultsArr = searchJson.optJSONArray("results")
            if (resultsArr != null && resultsArr.length() > 0) {
                val firstResult = resultsArr.getJSONObject(0)
                tmdbId = firstResult.opt("id")?.toString()

                // Fetch trailer if not already available
                if (trailerUrl == null && tmdbId != null) {
                    val vidApi = "https://api.themoviedb.org/3/$searchType/$tmdbId/videos?api_key=$TMDB_API_KEY"
                    val vidRes = app.get(vidApi, timeout = 10).text
                    val vidJson = JSONObject(vidRes)
                    val vidsArr = vidJson.optJSONArray("results")
                    if (vidsArr != null) {
                        for (v in 0 until vidsArr.length()) {
                            val vObj = vidsArr.getJSONObject(v)
                            val site = vObj.optString("site")
                            val key = vObj.optString("key")
                            val vType = vObj.optString("type")
                            if (site.equals("YouTube", ignoreCase = true) && key.isNotEmpty()) {
                                trailerUrl = "https://www.youtube.com/watch?v=$key"
                                if (vType.equals("Trailer", ignoreCase = true)) break
                            }
                        }
                    }
                }

                // If TV series, fetch Season 1 episode details (names, release dates, still thumbnails, overview)
                if (!isMovie && tmdbId != null) {
                    val seasonApi = "https://api.themoviedb.org/3/tv/$tmdbId/season/1?api_key=$TMDB_API_KEY"
                    val sRes = app.get(seasonApi, timeout = 10).text
                    val sJson = JSONObject(sRes)
                    val epsArr = sJson.optJSONArray("episodes")
                    if (epsArr != null) {
                        for (e in 0 until epsArr.length()) {
                            val epObj = epsArr.getJSONObject(e)
                            val epNum = epObj.optInt("episode_number")
                            if (epNum > 0) {
                                tmdbEpisodesMap[epNum] = epObj
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Kisskh", "TMDB enrichment error: ${e.message}")
        }

        val tags = ArrayList<String>()
        if (country.isNotEmpty()) tags.add(country)
        if (status.isNotEmpty()) tags.add(status)
        if (rawType.isNotEmpty()) tags.add(rawType)

        if (isMovie) {
            val movieEpId = if (episodesArray.length() > 0) episodesArray.getJSONObject(0).optLong("id") else dramaId.toLongOrNull() ?: 0L
            val movieDataUrl = "$mainUrl/api/DramaList/Episode/$movieEpId?dramaId=$dramaId"

            return newMovieLoadResponse(title, url, TvType.Movie, data = movieDataUrl) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                if (!trailerUrl.isNullOrBlank()) {
                    addTrailer(trailerUrl)
                }
            }
        } else {
            val episodes = ArrayList<Episode>()

            // Iterate through episodes in ascending order
            val epList = ArrayList<JSONObject>()
            for (i in 0 until episodesArray.length()) {
                epList.add(episodesArray.getJSONObject(i))
            }
            epList.sortBy { it.optDouble("number", 0.0) }

            for (epObj in epList) {
                val epId = epObj.optLong("id")
                val epNumDouble = epObj.optDouble("number", 0.0)
                val epNumInt = epNumDouble.toInt()

                val dataUrl = "$mainUrl/api/DramaList/Episode/$epId?dramaId=$dramaId"

                val tmdbEp = tmdbEpisodesMap[epNumInt]
                val epTitle = tmdbEp?.optString("name")?.takeIf { it.isNotEmpty() }
                    ?: if (epNumDouble % 1.0 == 0.0) "Episode $epNumInt" else "Episode $epNumDouble"

                val epOverview = tmdbEp?.optString("overview")?.takeIf { it.isNotEmpty() }
                val epAirDate = tmdbEp?.optString("air_date")?.let { parseDateMillis(it) }
                    ?: parseDateMillis(releaseDate)

                val epStill = tmdbEp?.optString("still_path")?.let {
                    if (it.isNotEmpty()) "https://image.tmdb.org/t/p/w342$it" else null
                } ?: poster

                episodes.add(newEpisode(dataUrl) {
                    this.name = epTitle
                    this.season = 1
                    this.episode = if (epNumInt > 0) epNumInt else 1
                    this.posterUrl = epStill
                    if (epAirDate != null) {
                        this.date = epAirDate
                    }
                    if (!epOverview.isNullOrBlank()) {
                        this.description = epOverview
                    }
                })
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
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
            val epId = Regex("""Episode/(\d+)""").find(data)?.groupValues?.get(1)?.toLongOrNull()
                ?: return false

            // 1. Generate video kkey and fetch video stream links
            val videoKkey = KisskhCipher.generateKkey(epId, VIDEO_GUID)
            val videoApi = "$mainUrl/api/DramaList/Episode/$epId.png?kkey=$videoKkey"

            val videoRes = app.get(
                videoApi,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/"
                ),
                timeout = 25
            ).text

            val videoJson = JSONObject(videoRes)
            val mainVideoUrl = videoJson.optString("Video").trim()
            val thirdPartyUrl = videoJson.optString("ThirdParty").trim()

            // 1.1 Direct HLS M3U8 Stream
            if (mainVideoUrl.isNotEmpty()) {
                generateM3u8(
                    source = name,
                    streamUrl = mainVideoUrl,
                    referer = "$mainUrl/",
                    headers = mapOf("Referer" to "$mainUrl/", "User-Agent" to USER_AGENT)
                ).forEach { link ->
                    found = true
                    callback(link)
                }

                if (!found) {
                    callback(
                        newExtractorLink(
                            name = name,
                            source = "$name Server (HLS)",
                            url = mainVideoUrl,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = "$mainUrl/"
                            this.headers = mapOf("Referer" to "$mainUrl/", "User-Agent" to USER_AGENT)
                        }
                    )
                    found = true
                }
            }

            // 1.2 Third-Party Provider Embeds
            if (thirdPartyUrl.isNotEmpty()) {
                try {
                    if (loadExtractor(thirdPartyUrl, "$mainUrl/", subtitleCallback, callback)) {
                        found = true
                    }
                } catch (e: Exception) {
                    Log.e("Kisskh", "Third party extractor error: ${e.message}")
                }
            }

            // 2. Generate subtitle kkey and fetch multi-language subtitles
            try {
                val subKkey = KisskhCipher.generateKkey(epId, SUB_GUID)
                val subApi = "$mainUrl/api/Sub/$epId?kkey=$subKkey"

                val subRes = app.get(
                    subApi,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to "$mainUrl/"
                    ),
                    timeout = 15
                ).text

                val subArray = JSONArray(subRes)
                for (i in 0 until subArray.length()) {
                    val subObj = subArray.getJSONObject(i)
                    val subSrc = subObj.optString("src").trim()
                    val subLabel = subObj.optString("label").trim()
                    if (subSrc.isEmpty()) continue

                    val cleanPath = subSrc.substringBefore("?").substringBefore("#")
                    val ext = cleanPath.substringAfterLast(".", "").lowercase()

                    val finalSubUrl = if (ext != "srt") {
                        try {
                            val rawSub = app.get(
                                subSrc,
                                headers = mapOf("Referer" to "$mainUrl/", "User-Agent" to USER_AGENT),
                                timeout = 15
                            ).text
                            val cleanSrt = KisskhSubDecryptor.decryptSubtitle(rawSub, ext)
                            val subKey = "${epId}_${i}_${subLabel.replace(Regex("[^a-zA-Z0-9]"), "")}"
                            KisskhSubServer.addSubtitle(subKey, cleanSrt)
                        } catch (e: Exception) {
                            Log.e("Kisskh", "Error decrypting subtitle [$subSrc]: ${e.message}")
                            subSrc
                        }
                    } else {
                        subSrc
                    }

                    subtitleCallback(
                        SubtitleFile(
                            subLabel.ifEmpty { "Subtitle" },
                            finalSubUrl
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("Kisskh", "Subtitle extraction error: ${e.message}")
            }

        } catch (e: Exception) {
            Log.e("Kisskh", "loadLinks error: ${e.message}")
        }

        return found
    }
}
