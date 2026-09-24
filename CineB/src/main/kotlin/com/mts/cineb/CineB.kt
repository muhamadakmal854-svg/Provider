package com.mts.cineb

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
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class CineB : MainAPI() {
    override var mainUrl = "https://cineb.vg"
    override var name = "CineB"
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
        private const val apiKey = "5c0f02b237bd8226ef5ffa3a86dfdcd5"
        private const val fallbackApiKey = "7ac6de5ca5060c7504e05da7b218a30c"
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
        "$tmdbAPI/trending/all/day?api_key=$apiKey" to "🔥 Spotlight & Pilihan Utama (Trending Hari Ini)",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=213" to "🍿 Netflix Originals & Siri Terhangat",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_watch_providers=8&watch_region=US" to "🍿 Netflix: Koleksi Filem Pilihan",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=2739" to "🏰 Disney+ Originals & Universe",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_watch_providers=337&watch_region=US" to "🏰 Disney+: Filem Blockbuster & Animasi",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=49%7C3186" to "⚡ HBO & Max Exclusives",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_watch_providers=1899&watch_region=US" to "⚡ HBO Max: Filem Pawagam Pilihan",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=453" to "🟢 Hulu Exclusives & Siri Pilihan",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=2552" to "🍎 Apple TV+ Originals",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_watch_providers=350&watch_region=US" to "🍎 Apple TV+: Filem Eksklusif",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=1024" to "📦 Amazon Prime Video Exclusives",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_watch_providers=9&watch_region=US" to "📦 Amazon Prime: Koleksi Filem Terhangat",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=4330" to "⭐ Paramount+ Originals",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=3353" to "🦚 Peacock Originals & Shows",
        "$tmdbAPI/movie/now_playing?api_key=$apiKey" to "🎬 Filem Terkini di Pawagam (Now Playing)",
        "$tmdbAPI/trending/movie/week?api_key=$apiKey" to "⚡ Filem Blockbuster & Terhangat Minggu Ini",
        "$tmdbAPI/tv/popular?api_key=$apiKey" to "🏆 Siri TV Paling Popular",
        "$tmdbAPI/movie/top_rated?api_key=$apiKey" to "⭐ Filem Penilaian Tertinggi (Top IMDb)",
        "$tmdbAPI/tv/top_rated?api_key=$apiKey" to "📺 Siri TV Penilaian Tertinggi",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_genres=28" to "💥 Aksi & Pengembaraan (Action & Adventure)",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_genres=878" to "🔮 Fiksyen Sains & Fantasi (Sci-Fi & Fantasy)",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_genres=27" to "👻 Seram & Suspen (Horror)",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_genres=35" to "😂 Komedi Blockbuster (Comedy)",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_genres=16" to "🎨 Animasi & Siri Kartun Pilihan"
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
            null
        } ?: try {
            app.get(url.replace(apiKey, fallbackApiKey)).parsedSafe<Results>()
        } catch (_: Exception) {
            null
        } ?: throw ErrorLoadingException("Gagal memuatkan data dari pelayan CineB")

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
        val dataPayload = CineBData(id = id, type = mediaTypeStr).toJson()

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
        val data: CineBData = try {
            parseJson<CineBData>(url)
        } catch (_: Exception) {
            if (url.startsWith("http") || url.startsWith("tt")) {
                try {
                    val cleanId = if (url.contains("/")) url.substringAfterLast("/").substringBefore("?") else url
                    if (cleanId.startsWith("tt")) {
                        // Carian IMDb ID terus melalui TMDB find
                        val findRes = app.get("$tmdbAPI/find/$cleanId?api_key=$apiKey&external_source=imdb_id").text
                        val parsed = tryParseJson<Map<String, List<Media>>>(findRes)
                        val movie = parsed?.get("movie_results")?.firstOrNull()
                        val tv = parsed?.get("tv_results")?.firstOrNull()
                        if (movie?.id != null) {
                            CineBData(movie.id, "movie")
                        } else if (tv?.id != null) {
                            CineBData(tv.id, "tv")
                        } else null
                    } else {
                        val numId = cleanId.toIntOrNull()
                        if (numId != null) {
                            val isTv = url.contains("/tv") || url.contains("/series")
                            CineBData(numId, if (isTv) "tv" else "movie")
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

        val title = res.title ?: res.name ?: res.originalTitle ?: res.originalName ?: "Stremio"
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
                        CineBLinkData(
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
                CineBLinkData(
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
            val res = app.get(stremioUrl, timeout = 10L).parsedSafe<CineBSubResponse>()
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
                    subtitleCallback(SubtitleFile(label, subUrl))
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
                        subtitleCallback(SubtitleFile(langName, subUrl))
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
            parseJson<CineBLinkData>(data)
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
            // Pelayan 1: MoviesAPI (Vidora Ultra-Fast 1080p FHD HLS)
            suspend {
                invokeMoviesAPI(tmdbId, isMovie, season, episode, subtitleCallback, callback)
            },
            // Pelayan 2: VidCore / Rigel (Stremio Server 2 - 1080p FHD HLS)
            suspend {
                invokeVidCore(tmdbId, isMovie, season, episode, subtitleCallback, callback)
            },
            // Pelayan 3: Vidrock (AES-CBC encrypted HLS)
            suspend {
                invokeVidrock(tmdbId, isMovie, season, episode, subtitleCallback, callback)
            },
            // Pelayan 4: VidLink
            suspend {
                invokeVidlink(tmdbId, season, episode, subtitleCallback, callback)
            },
            // Pelayan 5: 2Embed & StreamWish Multi-Quality
            suspend {
                invoke2Embed(tmdbId, imdbId, isMovie, season, episode, subtitleCallback, callback)
            },
            // Pelayan 6: SuperEmbed / MultiEmbed
            suspend {
                invokeSuperEmbed(tmdbId, isMovie, season, episode, subtitleCallback, callback)
            },
            // Pelayan 7: AutoEmbed
            suspend {
                invokeAutoEmbed(tmdbId, isMovie, season, episode, subtitleCallback, callback)
            },
            // Pelayan 8: Vidsrc.to / Vidsrc.in
            suspend {
                invokeVidsrcTo(tmdbId, isMovie, season, episode, subtitleCallback, callback)
            },
            // Pelayan 9: Stremio Native Vsrc / VidSrcMe
            suspend {
                invokeCineBNative(tmdbId, season, episode, subtitleCallback, callback)
            }
        ).amap { call ->
            try {
                call.invoke()
            } catch (_: Throwable) {
            }
        }

        return true
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
            "https://moviesapi.to/api/vidora/v1/movie/$tmdbId"
        } else {
            "https://moviesapi.to/api/vidora/v1/tv/$tmdbId/$season/$episode"
        }

        val res = try {
            app.get(
                endpoint,
                headers = mapOf(
                    "x-player-key" to moviesApiKey,
                    "Referer" to "https://moviesapi.to/",
                    "Origin" to "https://moviesapi.to",
                    "User-Agent" to USER_AGENT
                ),
                timeout = 10
            ).parsedSafe<VidoraResponse>()
        } catch (_: Throwable) {
            null
        } ?: return

        res.sources?.forEach { src ->
            val m3u8Url = src.url ?: return@forEach
            val serverName = "CineB - Server 1 (MoviesAPI)"
            val streamHeaders = mapOf(
                "Referer" to "https://moviesapi.to/",
                "Origin" to "https://moviesapi.to",
                "User-Agent" to USER_AGENT
            )

            callback.invoke(
                newExtractorLink(
                    serverName,
                    "MoviesAPI [1080p FHD]",
                    m3u8Url,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = "https://moviesapi.to/"
                    this.quality = Qualities.P1080.value
                    this.headers = streamHeaders
                }
            )

            try {
                generateM3u8(
                    serverName,
                    m3u8Url,
                    referer = "https://moviesapi.to/",
                    headers = streamHeaders
                ).forEach(callback)
            } catch (_: Throwable) {
            }

            src.tracks?.forEach { track ->
                val trackUrl = track.file ?: return@forEach
                val lang = track.label ?: "English"
                subtitleCallback.invoke(
                    SubtitleFile(lang, trackUrl)
                )
            }
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
        val path = if (isMovie) "movie/$tmdbId" else "tv/$tmdbId/$season/$episode"
        val primaryUrl = "https://movish.to/player-sources/rigel/$path"
        val fallbackUrl = "https://box-prox.jeannefrankli-n2-7-2-0-5.workers.dev/https://movish.to/player-sources/rigel/$path"

        val res = try {
            app.get(
                primaryUrl,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "https://vidcore.net/"
                ),
                timeout = 10
            ).parsedSafe<VidCoreResponse>()
        } catch (_: Throwable) {
            try {
                app.get(
                    fallbackUrl,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to "https://vidcore.net/"
                    ),
                    timeout = 10
                ).parsedSafe<VidCoreResponse>()
            } catch (_: Throwable) {
                null
            }
        } ?: return

        res.streams?.forEach { stream ->
            val m3u8Url = stream.url ?: return@forEach
            val label = stream.label ?: "Rigel"
            val qualityStr = stream.quality ?: "1080p"
            val serverName = "CineB - Server 2 (VidCore [$label])"

            val streamHeaders = mapOf(
                "Referer" to "https://vidcore.net/",
                "Origin" to "https://vidcore.net",
                "User-Agent" to USER_AGENT
            )

            callback.invoke(
                newExtractorLink(
                    serverName,
                    "$label [$qualityStr]",
                    m3u8Url,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = "https://vidcore.net/"
                    this.quality = Qualities.P1080.value
                    this.headers = streamHeaders
                }
            )

            try {
                generateM3u8(
                    serverName,
                    m3u8Url,
                    referer = "https://vidcore.net/",
                    headers = streamHeaders
                ).forEach(callback)
            } catch (_: Throwable) {
            }
        }

        res.subtitles?.forEach { sub ->
            val subUrl = sub.file ?: return@forEach
            val subLabel = sub.label ?: "English"
            subtitleCallback.invoke(
                SubtitleFile(subLabel, subUrl)
            )
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
        val type = if (isMovie) "movie" else "tv"
        val url = "$vidrockAPI/$type/$tmdbId${if (isMovie) "" else "/$season/$episode"}"
        val encrypted = encryptVidrock(tmdbId, type, season, episode)

        val sources = try {
            app.get(
                "$vidrockAPI/api/$type/$encrypted",
                headers = mapOf(
                    "Referer" to url,
                    "User-Agent" to USER_AGENT
                )
            ).parsedSafe<LinkedHashMap<String, HashMap<String, String>>>()
        } catch (_: Throwable) {
            null
        } ?: return

        sources.forEach { source ->
            val streamUrl = source.value["url"] ?: return@forEach
            val sourceName = source.key.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            val serverName = "CineB - Server 3 (Vidrock [$sourceName])"

            callback.invoke(
                newExtractorLink(
                    serverName,
                    serverName,
                    streamUrl,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = "$vidrockAPI/"
                    this.headers = mapOf(
                        "Origin" to vidrockAPI,
                        "Referer" to "$vidrockAPI/",
                        "User-Agent" to USER_AGENT
                    )
                }
            )

            try {
                generateM3u8(
                    serverName,
                    streamUrl,
                    referer = "$vidrockAPI/",
                    headers = mapOf("Origin" to vidrockAPI, "Referer" to "$vidrockAPI/")
                ).forEach(callback)
            } catch (_: Throwable) {
            }
        }

        // Subtitles for Vidrock
        try {
            val subUrl = "https://sub.vdrk.site/$type/$tmdbId${if (isMovie) "" else "/$season/$episode"}"
            val res = app.get(subUrl, headers = mapOf("Referer" to "$vidrockAPI/")).text
            tryParseJson<ArrayList<VidrockSubtitle>>(res)?.forEach { subtitle ->
                subtitleCallback.invoke(
                    SubtitleFile(
                        subtitle.label?.replace(Regex("\\d"), "")?.replace(Regex("\\s+Hi"), "")?.trim() ?: return@forEach,
                        subtitle.file ?: return@forEach
                    )
                )
            }
        } catch (_: Throwable) {
        }
    }

    private fun encryptVidrock(r: Int, e: String, t: Int?, n: Int?): String {
        val s = if (e == "tv") "${r}_${t}_${n}" else r.toString()
        val ww = "x7k9mPqT2rWvY8zA5bC3nF6hJ2lK4mN9"
        val keyBytes = ww.toByteArray(Charsets.UTF_8)
        val ivBytes = ww.substring(0, 16).toByteArray(Charsets.UTF_8)

        val secretKey = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(ivBytes)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        val encrypted = cipher.doFinal(s.toByteArray(Charsets.UTF_8))
        return base64UrlEncode(encrypted)
    }

    private suspend fun invokeVidlink(
        tmdbId: Int,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) {
            "$vidlinkAPI/movie/$tmdbId"
        } else {
            "$vidlinkAPI/tv/$tmdbId/$season/$episode"
        }

        try {
            loadExtractor(url, "https://cineb.vg/", subtitleCallback, callback)
        } catch (_: Throwable) {
        }
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
        val urls = mutableListOf<String>()
        if (isMovie) {
            urls.add("https://www.2embed.cc/embed/$tmdbId")
            if (!imdbId.isNullOrBlank()) {
                urls.add("https://www.2embed.cc/embed/$imdbId")
            }
        } else {
            urls.add("https://www.2embed.cc/embedtv/$tmdbId&s=$season&e=$episode")
            if (!imdbId.isNullOrBlank()) {
                urls.add("https://www.2embed.cc/embedtv/$imdbId&s=$season&e=$episode")
            }
        }

        urls.forEach { pageUrl ->
            try {
                val doc = app.get(pageUrl, headers = mapOf("User-Agent" to USER_AGENT)).document
                val iframe = doc.selectFirst("iframe")?.attr("src") ?: return@forEach
                val streamWishUrl = if (iframe.startsWith("//")) "https:$iframe" else iframe

                if (streamWishUrl.contains("streamwish") || streamWishUrl.contains("embed")) {
                    loadExtractor(streamWishUrl, pageUrl, subtitleCallback, callback)
                }
            } catch (_: Throwable) {
            }
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
        val url = if (isMovie) {
            "https://multiembed.mov/?video_id=$tmdbId&tmdb=1"
        } else {
            "https://multiembed.mov/?video_id=$tmdbId&tmdb=1&s=$season&e=$episode"
        }

        try {
            val doc = app.get(url, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).document
            doc.select("iframe[src*='http']").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.startsWith("http") && !src.contains("multiembed.mov")) {
                    loadExtractor(src, url, subtitleCallback, callback)
                }
            }
        } catch (_: Throwable) {
        }
    }

    private suspend fun invokeAutoEmbed(
        tmdbId: Int,
        isMovie: Boolean,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (isMovie) {
            "https://player.autoembed.cc/embed/movie/$tmdbId"
        } else {
            "https://player.autoembed.cc/embed/tv/$tmdbId/$season/$episode"
        }

        try {
            val doc = app.get(url, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).document
            doc.select("iframe[src*='http']").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.startsWith("http") && !src.contains("autoembed.cc")) {
                    loadExtractor(src, url, subtitleCallback, callback)
                }
            }
        } catch (_: Throwable) {
        }
    }

    private suspend fun invokeVidsrcTo(
        tmdbId: Int,
        isMovie: Boolean,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (isMovie) {
            "https://vidsrc.to/embed/movie/$tmdbId"
        } else {
            "https://vidsrc.to/embed/tv/$tmdbId/$season/$episode"
        }

        try {
            val doc = app.get(url, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/")).document
            doc.select("iframe[src*='http']").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.startsWith("http") && !src.contains("vidsrc.to")) {
                    loadExtractor(src, url, subtitleCallback, callback)
                }
            }
        } catch (_: Throwable) {
        }
    }

    private suspend fun invokeCineBNative(
        tmdbId: Int,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val urls = if (season == null) {
            listOf(
                "https://vidsrcme.su/embed/movie/$tmdbId",
                "https://vsrc.su/embed/movie/$tmdbId"
            )
        } else {
            listOf(
                "https://vidsrcme.su/embed/tv/$tmdbId/$season/$episode",
                "https://vsrc.su/embed/tv/$tmdbId/$season/$episode"
            )
        }

        urls.forEach { embedUrl ->
            try {
                loadExtractor(embedUrl, "https://cineb.vg/", subtitleCallback, callback)
            } catch (_: Throwable) {
            }
        }
    }

    data class CineBData(
        val id: Int? = null,
        val type: String? = null
    )

    data class CineBLinkData(
        val id: Int? = null,
        val imdbId: String? = null,
        val type: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val title: String? = null,
        val year: Int? = null
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
        @JsonProperty("vote_average") val voteAverage: Any? = null
    )

    data class Results(
        @JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
        @JsonProperty("page") val page: Int? = null,
        @JsonProperty("total_pages") val totalPages: Int? = null
    )

    data class Genres(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null
    )

    data class Seasons(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("season_number") val seasonNumber: Int? = null,
        @JsonProperty("air_date") val airDate: String? = null
    )

    data class Cast(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("original_name") val originalName: String? = null,
        @JsonProperty("character") val character: String? = null,
        @JsonProperty("profile_path") val profilePath: String? = null
    )

    data class Credits(
        @JsonProperty("cast") val cast: ArrayList<Cast>? = arrayListOf()
    )

    data class Trailers(
        @JsonProperty("key") val key: String? = null
    )

    data class ResultsTrailer(
        @JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf()
    )

    data class ExternalIds(
        @JsonProperty("imdb_id") val imdb_id: String? = null,
        @JsonProperty("tvdb_id") val tvdb_id: Int? = null
    )

    data class Episodes(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("air_date") val airDate: String? = null,
        @JsonProperty("still_path") val stillPath: String? = null,
        @JsonProperty("vote_average") val voteAverage: Any? = null,
        @JsonProperty("episode_number") val episodeNumber: Int? = null,
        @JsonProperty("season_number") val seasonNumber: Int? = null
    )

    data class MediaDetailEpisodes(
        @JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf()
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
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("vote_average") val voteAverage: Any? = null,
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("genres") val genres: ArrayList<Genres>? = arrayListOf(),
        @JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
        @JsonProperty("videos") val videos: ResultsTrailer? = null,
        @JsonProperty("external_ids") val external_ids: ExternalIds? = null,
        @JsonProperty("credits") val credits: Credits? = null,
        @JsonProperty("recommendations") val recommendations: Results? = null
    )

    data class VidoraTrack(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("kind") val kind: String? = null
    )

    data class VidoraSource(
        @JsonProperty("file_code") val fileCode: String? = null,
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("source") val source: String? = null,
        @JsonProperty("tracks") val tracks: List<VidoraTrack>? = null
    )

    data class VidoraResponse(
        @JsonProperty("result") val result: Boolean? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("sources") val sources: List<VidoraSource>? = null
    )

    data class VidCoreStream(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("quality") val quality: String? = null
    )

    data class VidCoreSubtitle(
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("file") val file: String? = null
    )

    data class VidCoreResponse(
        @JsonProperty("source") val source: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("streams") val streams: List<VidCoreStream>? = null,
        @JsonProperty("subtitles") val subtitles: List<VidCoreSubtitle>? = null,
        @JsonProperty("success") val success: Boolean? = null
    )

    data class VidrockSubtitle(
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("file") val file: String? = null
    )

    data class CineBSubResponse(
        @JsonProperty("subtitles") val subtitles: List<CineBSubItem>? = null
    )

    data class CineBSubItem(
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
}
