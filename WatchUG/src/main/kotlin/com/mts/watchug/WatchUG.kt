package com.mts.watchug

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class WatchUG : MainAPI() {
    override var mainUrl = "https://moviespro.watch"
    override var name = "WatchUG"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val hasQuickSearch = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    companion object {
        private const val tmdbAPI = "https://api.themoviedb.org/3"
        private const val apiKey = "7ac6de5ca5060c7504e05da7b218a30c"
        private const val fallbackApiKey = "5c0f02b237bd8226ef5ffa3a86dfdcd5"
        private const val moviesApiKey = "3a67e8866ae1d2bb9e81fe7f73315a56eb3bdf5e3e755c7554c8be6910aa6b13"
        private const val vidrockAPI = "https://vidrock.net"
        private const val vidlinkAPI = "https://vidlink.pro"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

        fun getImageUrl(link: String?): String? {
            if (link.isNullOrBlank()) return null
            return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w500$link" else link
        }

        fun getOriImageUrl(link: String?): String? {
            if (link.isNullOrBlank()) return null
            return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original$link" else link
        }

        fun getBackdropUrl(link: String?): String? {
            if (link.isNullOrBlank()) return null
            return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w1280$link" else link
        }

        fun base64UrlEncode(input: ByteArray): String {
            return base64Encode(input)
                .replace("+", "-")
                .replace("/", "_")
                .replace("=", "")
        }
    }

    override val mainPage = mainPageOf(
        "$tmdbAPI/trending/all/day?api_key=$apiKey" to "🔥 Spotlight & Pilihan Khas (Trending Hari Ini)",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=213" to "🍿 Netflix Originals & Pilihan Utama",
        "$tmdbAPI/trending/movie/week?api_key=$apiKey" to "⚡ Filem Blockbuster & Terhangat",
        "$tmdbAPI/movie/now_playing?api_key=$apiKey" to "🎬 Filem Terkini di Pawagam",
        "$tmdbAPI/tv/popular?api_key=$apiKey" to "🏆 Siri TV Paling Popular",
        "$tmdbAPI/movie/top_rated?api_key=$apiKey" to "⭐ Filem Penilaian Tertinggi (Top IMDb)",
        "$tmdbAPI/tv/top_rated?api_key=$apiKey" to "📺 Siri TV Penilaian Tertinggi",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_genres=28" to "💥 Aksi & Pengembaraan (Action & Adventure)",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_genres=878" to "🔮 Fiksyen Sains & Fantasi (Sci-Fi & Fantasy)",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_genres=27" to "👻 Seram & Suspen (Horror)",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_genres=35" to "😂 Komedi Blockbuster (Comedy)",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_genres=16" to "🎨 Filem & Siri Animasi (Animation)"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if (request.data.contains("?")) {
            "${request.data}&page=$page"
        } else {
            "${request.data}?page=$page"
        }

        val res = try {
            app.get(url).parsedSafe<Results>()
        } catch (_: Exception) {
            val fallbackUrl = url.replace(apiKey, fallbackApiKey)
            app.get(fallbackUrl).parsedSafe<Results>()
        } ?: throw ErrorLoadingException("Gagal memuatkan data daripada pelayan WatchUG")

        val list = res.results?.mapNotNull { media ->
            media.toSearchResponse()
        } ?: emptyList()

        return newHomePageResponse(request.name, list)
    }

    private fun Media.toSearchResponse(): SearchResponse? {
        val titleName = title ?: name ?: originalTitle ?: originalName ?: return null
        val id = id ?: return null
        val isMovie = mediaType == "movie" || (mediaType == null && title != null)
        val tvType = if (isMovie) TvType.Movie else TvType.TvSeries
        val poster = getImageUrl(posterPath)
        val mediaTypeStr = if (isMovie) "movie" else "tv"
        val dataPayload = WatchUGData(id = id, type = mediaTypeStr).toJson()

        return if (tvType == TvType.Movie) {
            newMovieSearchResponse(titleName, dataPayload, TvType.Movie) {
                this.posterUrl = poster
                this.score = Score.from10(voteAverage?.toString())
            }
        } else {
            newTvSeriesSearchResponse(titleName, dataPayload, TvType.TvSeries) {
                this.posterUrl = poster
                this.score = Score.from10(voteAverage?.toString())
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$tmdbAPI/search/multi?api_key=$apiKey&query=$encodedQuery"
        val res = try {
            app.get(url).parsedSafe<Results>()
        } catch (_: Exception) {
            app.get(url.replace(apiKey, fallbackApiKey)).parsedSafe<Results>()
        } ?: return emptyList()

        return res.results?.mapNotNull { media ->
            if (media.mediaType == "person") return@mapNotNull null
            media.toSearchResponse()
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val data: WatchUGData = try {
            parseJson<WatchUGData>(url)
        } catch (_: Exception) {
            if (url.startsWith("http")) {
                // Cuba ekstrak daripada URL moviespro.watch atau ID TMDB
                try {
                    val html = app.get(url).text
                    val tmdbMatch = Regex("""['"]id['"]\s*:\s*['"]?(\d+)['"]?""").find(html)?.groupValues?.get(1)?.toIntOrNull()
                        ?: Regex("""['"]tvid['"]\s*:\s*['"]?(\d+)['"]?""").find(html)?.groupValues?.get(1)?.toIntOrNull()
                    val isTv = html.contains("Episodes") || url.contains("/tv") || url.contains("/series")
                    if (tmdbMatch != null) {
                        WatchUGData(tmdbMatch, if (isTv) "tv" else "movie")
                    } else {
                        val slug = url.removeSuffix("/").substringAfterLast("/")
                        val cleanQuery = slug.replace("-", " ")
                        val searchRes = search(cleanQuery).firstOrNull()
                        if (searchRes != null) {
                            parseJson<WatchUGData>(searchRes.url)
                        } else null
                    }
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        } ?: throw ErrorLoadingException("Format data tidak sah: $url")

        val id = data.id ?: throw ErrorLoadingException("ID TMDB tidak dijumpai")
        val type = if (data.type == "movie") TvType.Movie else TvType.TvSeries

        val append = "credits,external_ids,keywords,videos,recommendations"
        val tmdbUrl = if (type == TvType.Movie) {
            "$tmdbAPI/movie/$id?api_key=$apiKey&append_to_response=$append"
        } else {
            "$tmdbAPI/tv/$id?api_key=$apiKey&append_to_response=$append"
        }

        val res = try {
            app.get(tmdbUrl).parsedSafe<MediaDetail>()
        } catch (_: Exception) {
            app.get(tmdbUrl.replace(apiKey, fallbackApiKey)).parsedSafe<MediaDetail>()
        } ?: throw ErrorLoadingException("Gagal memuatkan butiran TMDB")

        val title = res.title ?: res.name ?: res.originalTitle ?: res.originalName ?: "WatchUG"
        val poster = getImageUrl(res.posterPath)
        val bgPoster = getBackdropUrl(res.backdropPath) ?: getOriImageUrl(res.backdropPath)
        val releaseDate = res.releaseDate ?: res.firstAirDate
        val year = releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
        val score = Score.from10(res.voteAverage?.toString())
        val genres = res.genres?.mapNotNull { it.name }
        val plot = res.overview
        val trailer = res.videos?.results?.firstOrNull { it.key != null }?.let {
            "https://www.youtube.com/watch?v=${it.key}"
        }

        val actors = res.credits?.cast?.take(12)?.mapNotNull { cast ->
            ActorData(
                Actor(cast.name ?: cast.originalName ?: return@mapNotNull null, getImageUrl(cast.profilePath)),
                roleString = cast.character
            )
        }

        val recommendations = res.recommendations?.results?.mapNotNull { it.toSearchResponse() }

        if (type == TvType.TvSeries) {
            val episodes = res.seasons?.filter { (it.seasonNumber ?: 0) > 0 }?.mapNotNull { season ->
                val sNum = season.seasonNumber ?: return@mapNotNull null
                val seasonUrl = "$tmdbAPI/tv/$id/season/$sNum?api_key=$apiKey"
                val seasonRes = try {
                    app.get(seasonUrl).parsedSafe<MediaDetailEpisodes>()
                } catch (_: Exception) {
                    app.get(seasonUrl.replace(apiKey, fallbackApiKey)).parsedSafe<MediaDetailEpisodes>()
                }

                seasonRes?.episodes?.mapNotNull { eps ->
                    val epNum = eps.episodeNumber ?: return@mapNotNull null
                    val epTitle = eps.name.takeIf { !it.isNullOrBlank() } ?: "Episod $epNum"
                    val epStill = getOriImageUrl(eps.stillPath) ?: getImageUrl(eps.stillPath) ?: poster
                    val airDateStr = eps.airDate

                    val parsedEpoch = try {
                        if (!airDateStr.isNullOrBlank()) {
                            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(airDateStr)?.time
                        } else null
                    } catch (_: Exception) { null }

                    newEpisode(
                        WatchUGLinkData(
                            id = id,
                            imdbId = res.external_ids?.imdb_id,
                            type = "tv",
                            season = sNum,
                            episode = epNum,
                            title = title
                        ).toJson()
                    ) {
                        this.name = epTitle
                        this.season = sNum
                        this.episode = epNum
                        this.posterUrl = epStill
                        this.description = eps.overview
                        this.score = Score.from10(eps.voteAverage?.toString())
                        if (parsedEpoch != null) {
                            this.date = parsedEpoch
                        }
                    }.apply {
                        this.addDate(airDateStr)
                    }
                }
            }?.flatten() ?: emptyList()

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = plot
                this.tags = genres
                this.score = score
                this.showStatus = when (res.status) {
                    "Returning Series" -> ShowStatus.Ongoing
                    else -> ShowStatus.Completed
                }
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer)
                addTMDbId(id.toString())
                addImdbId(res.external_ids?.imdb_id)
            }
        } else {
            return newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                WatchUGLinkData(
                    id = id,
                    imdbId = res.external_ids?.imdb_id,
                    type = "movie",
                    title = title,
                    year = year
                ).toJson()
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = plot
                this.tags = genres
                this.score = score
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer)
                addTMDbId(id.toString())
                addImdbId(res.external_ids?.imdb_id)
            }
        }
    }

    private suspend fun fetchImdbId(id: Int, isMovie: Boolean): String? {
        val type = if (isMovie) "movie" else "tv"
        return try {
            app.get("$tmdbAPI/$type/$id/external_ids?api_key=$apiKey")
                .parsedSafe<ExternalIds>()?.imdb_id
        } catch (_: Exception) {
            try {
                app.get("$tmdbAPI/$type/$id/external_ids?api_key=$fallbackApiKey")
                    .parsedSafe<ExternalIds>()?.imdb_id
            } catch (_: Exception) { null }
        }
    }

    private suspend fun fetchSubtitles(
        imdbId: String?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        if (imdbId.isNullOrBlank()) return
        val cleanImdb = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
        val numericImdb = cleanImdb.removePrefix("tt")

        // 1. Stremio OpenSubtitles v3 (Menghasilkan fail .srt UTF-8 secara terus)
        try {
            val stremioUrl = if (season != null && episode != null) {
                "https://opensubtitles-v3.strem.io/subtitles/series/$cleanImdb:$season:$episode.json"
            } else {
                "https://opensubtitles-v3.strem.io/subtitles/movie/$cleanImdb.json"
            }
            val res = app.get(stremioUrl, timeout = 10L).parsedSafe<StremioSubResponse>()
            res?.subtitles?.forEach { sub ->
                val subUrl = sub.url ?: return@forEach
                val langCode = sub.lang?.lowercase() ?: ""
                val label = when (langCode) {
                    "eng", "en" -> "English"
                    "ind", "id" -> "Indonesian"
                    "may", "ms", "zsm" -> "Malay"
                    else -> sub.lang ?: "Subtitle"
                }
                if (langCode in listOf("eng", "en", "ind", "id", "may", "ms", "zsm")) {
                    subtitleCallback(newSubtitleFile(label, subUrl))
                }
            }
        } catch (_: Throwable) {}

        // 2. OpenSubtitles REST API dengan carian bahasa terarah (Malay, Indonesian, English)
        val targetLangs = listOf("may" to "Malay", "ind" to "Indonesian", "eng" to "English")
        targetLangs.amap { (langCode, langName) ->
            try {
                val osUrl = if (season != null && episode != null) {
                    "https://rest.opensubtitles.org/search/episode-$episode/imdbid-$numericImdb/season-$season/sublanguageid-$langCode"
                } else {
                    "https://rest.opensubtitles.org/search/imdbid-$numericImdb/sublanguageid-$langCode"
                }
                val osRes = app.get(osUrl, headers = mapOf("User-Agent" to "VLSub 0.10.2"), timeout = 10L).text
                val parsed = tryParseJson<List<OpenSubItem>>(osRes)
                parsed?.take(3)?.forEach { item ->
                    val fileId = item.IDSubtitleFile
                        ?: Regex("""file/(\d+)\.gz""").find(item.SubDownloadLink ?: "")?.groupValues?.get(1)
                    val subUrl = if (fileId != null) {
                        "https://subs5.strem.io/en/download/subencoding-stremio-utf8/src-api/file/$fileId"
                    } else {
                        item.SubDownloadLink
                    }
                    if (!subUrl.isNullOrBlank()) {
                        subtitleCallback(newSubtitleFile(langName, subUrl))
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linkData = try {
            parseJson<WatchUGLinkData>(data)
        } catch (_: Throwable) {
            null
        }

        val tmdbId: Int
        val isMovie: Boolean
        val season: Int?
        val episode: Int?
        var imdbId: String? = null

        if (linkData != null && linkData.id != null) {
            tmdbId = linkData.id
            isMovie = linkData.type == "movie"
            season = linkData.season
            episode = linkData.episode
            imdbId = linkData.imdbId
        } else {
            val isTv = data.contains("/tv") || data.contains("/serie") || data.contains("season") || data.contains("episode")
            isMovie = !isTv
            val idMatch = Regex("""(?:movie|tv|serie)[/-](\d+)""", RegexOption.IGNORE_CASE).find(data)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""(\d{3,8})""").find(data)?.groupValues?.get(1)?.toIntOrNull()
                ?: return false
            tmdbId = idMatch
            if (isTv) {
                season = Regex("""(?:season[/-]|s=?)(\d+)""", RegexOption.IGNORE_CASE).find(data)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                episode = Regex("""(?:episode[/-]|e=?)(\d+)""", RegexOption.IGNORE_CASE).find(data)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            } else {
                season = null
                episode = null
            }
        }

        if (imdbId.isNullOrBlank()) {
            imdbId = fetchImdbId(tmdbId, isMovie)
        }

        listOf(
            // Ekstraksi Sari Kata: English, Indonesian, Malay
            suspend {
                fetchSubtitles(imdbId, season, episode, subtitleCallback)
            },
            // Pelayan 1: VidSrcMe / VidSrc
            suspend {
                invokeVidSrc(tmdbId, imdbId, isMovie, season, episode, subtitleCallback, callback)
            },
            // Pelayan 2: MoviesAPI (Vidora 1080p FHD HLS)
            suspend {
                invokeMoviesAPI(tmdbId, isMovie, season, episode, subtitleCallback, callback)
            },
            // Pelayan 3: VidCore / Rigel (1080p FHD HLS)
            suspend {
                invokeVidCore(tmdbId, isMovie, season, episode, subtitleCallback, callback)
            },
            // Pelayan 4: Vidrock (AES-CBC encrypted)
            suspend {
                invokeVidrock(tmdbId, isMovie, season, episode, subtitleCallback, callback)
            },
            // Pelayan 5: VidLink
            suspend {
                invokeVidlink(tmdbId, season, episode, subtitleCallback, callback)
            },
            // Pelayan 6: 2Embed & StreamWish Multi-Quality
            suspend {
                invoke2Embed(tmdbId, imdbId, isMovie, season, episode, subtitleCallback, callback)
            },
            // Pelayan 7: SuperEmbed / MultiEmbed
            suspend {
                invokeSuperEmbed(tmdbId, isMovie, season, episode, subtitleCallback, callback)
            },
            // Pelayan 8: AutoEmbed
            suspend {
                invokeAutoEmbed(tmdbId, isMovie, season, episode, subtitleCallback, callback)
            }
        ).amap { action ->
            try {
                action.invoke()
            } catch (_: Throwable) {}
        }

        return true
    }

    private suspend fun invokeVidSrc(
        tmdbId: Int,
        imdbId: String?,
        isMovie: Boolean,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val targetImdb = imdbId ?: return
        val urls = if (isMovie) {
            listOf(
                "https://vidsrcme.su/embed/movie/$targetImdb",
                "https://vidsrc.me/embed/movie/$targetImdb",
                "https://vidsrc.cc/v2/embed/movie/$tmdbId"
            )
        } else {
            val s = season ?: 1
            val e = episode ?: 1
            listOf(
                "https://vidsrcme.su/embed/tv?tmdb=$tmdbId&season=$s&episode=$e",
                "https://vidsrc.me/embed/tv?tmdb=$tmdbId&season=$s&episode=$e",
                "https://vidsrc.cc/v2/embed/tv/$tmdbId/$s/$e"
            )
        }

        urls.amap { url ->
            try {
                loadExtractor(url, subtitleCallback, callback)
            } catch (_: Throwable) {}
        }
    }

    private suspend fun invokeMoviesAPI(
        tmdbId: Int,
        isMovie: Boolean,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val endpoint = if (isMovie) {
            "https://moviesapi.to/api/vidora/v1/movie/$tmdbId?key=$moviesApiKey"
        } else {
            "https://moviesapi.to/api/vidora/v1/tv/$tmdbId/${season ?: 1}/${episode ?: 1}?key=$moviesApiKey"
        }

        val jsonStr = app.get(
            endpoint,
            headers = mapOf(
                "Referer" to "https://moviesapi.to/",
                "User-Agent" to USER_AGENT
            )
        ).text

        val resp = tryParseJson<VidoraResponse>(jsonStr) ?: return
        val masterUrl = resp.source ?: return

        generateM3u8(
            name,
            masterUrl,
            "https://moviesapi.to/"
        ).forEach { link ->
            callback(link)
        }

        resp.tracks?.forEach { track ->
            val trackUrl = track.file ?: return@forEach
            val trackLabel = track.label ?: "English"
            subtitleCallback(newSubtitleFile(trackLabel, trackUrl))
        }
    }

    private suspend fun invokeVidCore(
        tmdbId: Int,
        isMovie: Boolean,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val endpoint = if (isMovie) {
            "https://movish.to/player-sources/rigel/movie/$tmdbId"
        } else {
            "https://movish.to/player-sources/rigel/tv/$tmdbId/${season ?: 1}/${episode ?: 1}"
        }

        val res = app.get(
            endpoint,
            headers = mapOf(
                "Referer" to "https://watchhub.work/",
                "User-Agent" to USER_AGENT
            )
        ).parsedSafe<VidCoreResponse>() ?: return

        res.sources?.forEach { src ->
            val srcUrl = src.url ?: return@forEach
            if (srcUrl.contains(".m3u8")) {
                generateM3u8(
                    "${this.name} VidCore",
                    srcUrl,
                    "https://movish.to/"
                ).forEach { link ->
                    callback(link)
                }
            } else {
                callback(
                    newExtractorLink(
                        "${this.name} VidCore",
                        "${this.name} VidCore (${src.quality ?: "1080p"})",
                        srcUrl,
                        ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://movish.to/"
                        this.quality = Qualities.P1080.value
                    }
                )
            }
        }
    }

    private suspend fun invokeVidrock(
        tmdbId: Int,
        isMovie: Boolean,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val payload = if (isMovie) {
            VidrockPayload(tmdb_id = tmdbId, type = "movie")
        } else {
            VidrockPayload(tmdb_id = tmdbId, type = "tv", season = season ?: 1, episode = episode ?: 1)
        }

        val jsonBody = payload.toJson()
        val encResult = encryptVidrockPayload(jsonBody) ?: return

        val requestBody = "{\"data\":\"$encResult\"}".toRequestBody("application/json".toMediaTypeOrNull())
        val res = app.post(
            "$vidrockAPI/api/source",
            requestBody = requestBody,
            headers = mapOf(
                "Origin" to vidrockAPI,
                "Referer" to "$vidrockAPI/",
                "User-Agent" to USER_AGENT
            )
        ).text

        val respObj = tryParseJson<VidrockResponse>(res) ?: return
        val rawData = respObj.data ?: return
        val decJson = decryptVidrockPayload(rawData) ?: return
        val sourceObj = tryParseJson<VidrockSourceData>(decJson) ?: return

        sourceObj.sources?.forEach { src ->
            val streamUrl = src.file ?: return@forEach
            if (streamUrl.contains(".m3u8")) {
                generateM3u8(
                    "${this.name} Vidrock",
                    streamUrl,
                    "$vidrockAPI/"
                ).forEach { link ->
                    callback(link)
                }
            } else {
                callback(
                    newExtractorLink(
                        "${this.name} Vidrock",
                        "${this.name} Vidrock 1080p",
                        streamUrl,
                        ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "$vidrockAPI/"
                        this.quality = Qualities.P1080.value
                    }
                )
            }
        }

        sourceObj.tracks?.forEach { trk ->
            val trkUrl = trk.file ?: return@forEach
            val trkLabel = trk.label ?: "English"
            subtitleCallback(newSubtitleFile(trkLabel, trkUrl))
        }
    }

    private suspend fun invokeVidlink(
        tmdbId: Int,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season != null && episode != null) {
            "$vidlinkAPI/tv/$tmdbId/$season/$episode"
        } else {
            "$vidlinkAPI/movie/$tmdbId"
        }
        loadExtractor(url, subtitleCallback, callback)
    }

    private suspend fun invoke2Embed(
        tmdbId: Int,
        imdbId: String?,
        isMovie: Boolean,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = if (isMovie) {
            "https://www.2embed.cc/embed/$tmdbId"
        } else {
            "https://www.2embed.cc/embedtv/$tmdbId&s=${season ?: 1}&e=${episode ?: 1}"
        }

        try {
            loadExtractor(embedUrl, subtitleCallback, callback)
        } catch (_: Throwable) {}

        if (!imdbId.isNullOrBlank()) {
            try {
                loadExtractor("https://www.2embed.skin/embed/$imdbId", subtitleCallback, callback)
            } catch (_: Throwable) {}
        }
    }

    private suspend fun invokeSuperEmbed(
        tmdbId: Int,
        isMovie: Boolean,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val superUrl = if (isMovie) {
            "https://multiembed.mov/?video_id=$tmdbId&tmdb=1"
        } else {
            "https://multiembed.mov/?video_id=$tmdbId&tmdb=1&s=${season ?: 1}&e=${episode ?: 1}"
        }

        try {
            loadExtractor(superUrl, subtitleCallback, callback)
        } catch (_: Throwable) {}
    }

    private suspend fun invokeAutoEmbed(
        tmdbId: Int,
        isMovie: Boolean,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val autoUrl = if (isMovie) {
            "https://player.autoembed.cc/embed/movie/$tmdbId"
        } else {
            "https://player.autoembed.cc/embed/tv/$tmdbId/${season ?: 1}/${episode ?: 1}"
        }

        try {
            loadExtractor(autoUrl, subtitleCallback, callback)
        } catch (_: Throwable) {}
    }

    private fun encryptVidrockPayload(text: String): String? {
        return try {
            val keyBytes = "x7k9mPqT2rWvY8zA5bC3nF6hJ2lK4mN9".toByteArray(Charsets.UTF_8)
            val ivBytes = ByteArray(16) { 0 }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
            val encrypted = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
            base64UrlEncode(encrypted)
        } catch (_: Exception) {
            null
        }
    }

    private fun decryptVidrockPayload(base64Text: String): String? {
        return try {
            val keyBytes = "x7k9mPqT2rWvY8zA5bC3nF6hJ2lK4mN9".toByteArray(Charsets.UTF_8)
            val ivBytes = ByteArray(16) { 0 }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
            val padded = when (base64Text.length % 4) {
                2 -> "$base64Text=="
                3 -> "$base64Text="
                else -> base64Text
            }.replace("-", "+").replace("_", "/")
            val decodedBytes = base64Decode(padded)
            String(cipher.doFinal(decodedBytes), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    data class WatchUGData(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("type") val type: String? = null
    )

    data class WatchUGLinkData(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("imdbId") val imdbId: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("episode") val episode: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("year") val year: Int? = null
    )

    data class StremioSubResponse(
        @JsonProperty("subtitles") val subtitles: List<StremioSubItem>? = null
    )

    data class StremioSubItem(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("lang") val lang: String? = null,
        @JsonProperty("subtitleFileName") val subtitleFileName: String? = null
    )

    data class OpenSubItem(
        @JsonProperty("IDSubtitleFile") val IDSubtitleFile: String? = null,
        @JsonProperty("SubDownloadLink") val SubDownloadLink: String? = null,
        @JsonProperty("SubFileName") val SubFileName: String? = null,
        @JsonProperty("LanguageName") val LanguageName: String? = null,
        @JsonProperty("SubLanguageID") val SubLanguageID: String? = null
    )

    data class VidoraResponse(
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("source") val source: String? = null,
        @JsonProperty("tracks") val tracks: List<VidoraTrack>? = null
    )

    data class VidoraTrack(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("kind") val kind: String? = null
    )

    data class VidCoreResponse(
        @JsonProperty("sources") val sources: List<VidCoreSource>? = null
    )

    data class VidCoreSource(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("quality") val quality: String? = null
    )

    data class VidrockPayload(
        @JsonProperty("tmdb_id") val tmdb_id: Int,
        @JsonProperty("type") val type: String,
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("episode") val episode: Int? = null
    )

    data class VidrockResponse(
        @JsonProperty("data") val data: String? = null
    )

    data class VidrockSourceData(
        @JsonProperty("sources") val sources: List<VidrockSourceFile>? = null,
        @JsonProperty("tracks") val tracks: List<VidrockTrack>? = null
    )

    data class VidrockSourceFile(
        @JsonProperty("file") val file: String? = null
    )

    data class VidrockTrack(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null
    )

    data class Results(
        @JsonProperty("results") val results: List<Media>? = null
    )

    data class Media(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("original_title") val originalTitle: String? = null,
        @JsonProperty("original_name") val originalName: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("media_type") val mediaType: String? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null
    )

    data class MediaDetail(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("original_title") val originalTitle: String? = null,
        @JsonProperty("original_name") val originalName: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
        @JsonProperty("genres") val genres: List<Genre>? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("seasons") val seasons: List<Season>? = null,
        @JsonProperty("credits") val credits: Credits? = null,
        @JsonProperty("external_ids") val external_ids: ExternalIds? = null,
        @JsonProperty("videos") val videos: VideoResults? = null,
        @JsonProperty("recommendations") val recommendations: Results? = null
    )

    data class Genre(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null
    )

    data class Season(
        @JsonProperty("season_number") val seasonNumber: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("episode_count") val episodeCount: Int? = null
    )

    data class MediaDetailEpisodes(
        @JsonProperty("episodes") val episodes: List<EpisodeItem>? = null
    )

    data class EpisodeItem(
        @JsonProperty("episode_number") val episodeNumber: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("still_path") val stillPath: String? = null,
        @JsonProperty("air_date") val airDate: String? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null
    )

    data class Credits(
        @JsonProperty("cast") val cast: List<Cast>? = null
    )

    data class Cast(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("original_name") val originalName: String? = null,
        @JsonProperty("character") val character: String? = null,
        @JsonProperty("profile_path") val profilePath: String? = null
    )

    data class ExternalIds(
        @JsonProperty("imdb_id") val imdb_id: String? = null
    )

    data class VideoResults(
        @JsonProperty("results") val results: List<VideoItem>? = null
    )

    data class VideoItem(
        @JsonProperty("key") val key: String? = null,
        @JsonProperty("site") val site: String? = null,
        @JsonProperty("type") val type: String? = null
    )
}
