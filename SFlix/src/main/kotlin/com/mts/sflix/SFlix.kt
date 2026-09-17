package com.mts.sflix

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import java.text.SimpleDateFormat
import java.util.Locale

class SFlix : MainAPI() {
    override var mainUrl = "https://ssflix.pro"
    override var name = "SFlix"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val hasQuickSearch = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )

    companion object {
        private const val tmdbAPI = "https://api.themoviedb.org/3"
        private const val apiKey = "31eb6ae13f030d2e334cdd978cfc72b7"
        private const val moviesApiKey = "3a67e8866ae1d2bb9e81fe7f73315a56eb3bdf5e3e755c7554c8be6910aa6b13"
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
            return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w780$link" else link
        }
    }

    override val mainPage = mainPageOf(
        "$tmdbAPI/trending/all/day?api_key=$apiKey" to "🔥 Trending Hari Ini (Spotlight)",
        "$tmdbAPI/trending/all/week?api_key=$apiKey" to "⚡ Trending Minggu Ini",
        "$tmdbAPI/movie/now_playing?api_key=$apiKey" to "🎬 Filem Terkini di Pawagam",
        "$tmdbAPI/tv/popular?api_key=$apiKey" to "🏆 Siri TV Paling Popular",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=213" to "🍿 Netflix Originals & Pilihan Utama",
        "$tmdbAPI/movie/top_rated?api_key=$apiKey" to "⭐ Filem Penilaian Tertinggi",
        "$tmdbAPI/tv/top_rated?api_key=$apiKey" to "📺 Siri TV Penilaian Tertinggi",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_genres=28" to "💥 Aksi & Pengembaraan",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_genres=35" to "😂 Komedi Blockbuster",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_genres=10765" to "🔮 Fiksyen Sains & Fantasi",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_original_language=ko" to "🇰🇷 K-Drama Pilihan",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_genres=16" to "✨ Animasi & Filem Anime"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val isHorizontal = request.name.contains("Trending", ignoreCase = true) ||
                request.name.contains("Spotlight", ignoreCase = true) ||
                request.name.contains("Netflix", ignoreCase = true) ||
                request.name.contains("Siri TV", ignoreCase = true) ||
                request.name.contains("Aksi", ignoreCase = true) ||
                request.name.contains("Animasi", ignoreCase = true)

        val defaultType = if (request.data.contains("/movie")) "movie" else "tv"
        val res = app.get("${request.data}&page=$page").parsedSafe<Results>()
            ?: throw ErrorLoadingException("Gagal memuatkan data dari pelayan")

        val list = res.results?.mapNotNull { media ->
            media.toSearchResponse(defaultType, isHorizontal)
        } ?: emptyList()

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = list,
                isHorizontalImages = isHorizontal
            ),
            hasNext = (res.page ?: page) < (res.totalPages ?: 1)
        )
    }

    private fun Media.toSearchResponse(defaultType: String? = null, isHorizontal: Boolean = false): SearchResponse? {
        val titleName = title ?: name ?: originalTitle ?: originalName ?: return null
        val mediaTypeStr = mediaType ?: defaultType ?: if (name != null) "tv" else "movie"
        val tvType = if (mediaTypeStr == "movie") TvType.Movie else TvType.TvSeries

        val poster = if (isHorizontal) {
            getBackdropUrl(backdropPath) ?: getImageUrl(posterPath)
        } else {
            getImageUrl(posterPath) ?: getBackdropUrl(backdropPath)
        }

        val dataPayload = SFlixData(id = id, type = mediaTypeStr).toJson()

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

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val res = app.get("$tmdbAPI/search/multi?api_key=$apiKey&language=en-US&query=$encoded&page=1")
            .parsedSafe<Results>() ?: return null
        return res.results?.mapNotNull { media ->
            media.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val data: SFlixData = try {
            parseJson<SFlixData>(url)
        } catch (_: Exception) {
            if (url.contains("/movie/")) {
                val id = url.substringAfterLast("/").substringAfterLast("-").toIntOrNull()
                SFlixData(id, "movie")
            } else if (url.contains("/serie/") || url.contains("/tv/")) {
                val id = url.substringAfterLast("/").substringAfterLast("-").toIntOrNull()
                SFlixData(id, "tv")
            } else {
                null
            }
        } ?: throw ErrorLoadingException("Format data tidak sah: $url")

        val id = data.id ?: throw ErrorLoadingException("ID tidak dijumpai")
        val type = if (data.type == "movie") TvType.Movie else TvType.TvSeries

        val append = "credits,external_ids,keywords,videos,recommendations"
        val tmdbUrl = if (type == TvType.Movie) {
            "$tmdbAPI/movie/$id?api_key=$apiKey&append_to_response=$append"
        } else {
            "$tmdbAPI/tv/$id?api_key=$apiKey&append_to_response=$append"
        }

        val res = app.get(tmdbUrl).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Gagal memuatkan butiran TMDB")

        val title = res.title ?: res.name ?: res.originalTitle ?: res.originalName ?: "SFlix"
        val poster = getImageUrl(res.posterPath)
        val bgPoster = getOriImageUrl(res.backdropPath)
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
                val seasonRes = app.get("$tmdbAPI/tv/$id/season/$sNum?api_key=$apiKey")
                    .parsedSafe<MediaDetailEpisodes>()

                seasonRes?.episodes?.mapNotNull { eps ->
                    val epNum = eps.episodeNumber ?: return@mapNotNull null
                    val epTitle = eps.name.takeIf { !it.isNullOrBlank() } ?: "Episod $epNum"
                    val epStill = getImageUrl(eps.stillPath) ?: poster
                    val airDateStr = eps.airDate

                    val parsedEpoch = try {
                        if (!airDateStr.isNullOrBlank()) {
                            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(airDateStr)?.time
                        } else null
                    } catch (_: Exception) { null }

                    newEpisode(
                        SFlixLinkData(
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
                SFlixLinkData(
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
        val typeStr = if (isMovie) "movie" else "tv"
        return try {
            app.get("$tmdbAPI/$typeStr/$id/external_ids?api_key=$apiKey").parsedSafe<ExternalIds>()?.imdb_id
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linkData = try {
            parseJson<SFlixLinkData>(data)
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
            // Pelayan 1: MoviesAPI (Vidora Ultra-Fast 1080p FHD HLS)
            suspend {
                invokeMoviesAPI(tmdbId, isMovie, season, episode, subtitleCallback, callback)
            },
            // Pelayan 2: SFlix Native Player (vidsrc-embed.ru / vs_src.php)
            suspend {
                invokeSFlixNative(tmdbId, isMovie, season, episode, subtitleCallback, callback)
            },
            // Pelayan 3: 2Embed & StreamWish Multi-Quality (1080p, 720p, 480p)
            suspend {
                invoke2Embed(tmdbId, imdbId, isMovie, season, episode, subtitleCallback, callback)
            },
            // Pelayan 4: MultiEmbed / SuperEmbed
            suspend {
                invokeSuperEmbed(tmdbId, isMovie, season, episode, subtitleCallback, callback)
            },
            // Pelayan 5: AutoEmbed
            suspend {
                invokeAutoEmbed(tmdbId, isMovie, season, episode, subtitleCallback, callback)
            },
            // Pelayan 6: Vidsrc.to / Vidsrc.in
            suspend {
                invokeVidsrcTo(tmdbId, isMovie, season, episode, subtitleCallback, callback)
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
            val serverName = "SFlix - Server 1 (MoviesAPI)"
            val streamHeaders = mapOf(
                "Referer" to "https://moviesapi.to/",
                "Origin" to "https://moviesapi.to",
                "User-Agent" to USER_AGENT
            )

            // Pancarkan terus ExtractorLink FHD 1080p dengan pengepala lengkap
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

            // Pancarkan sub-resolusi dari m3u8 playlist jika tersedia
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

    private suspend fun invokeSFlixNative(
        tmdbId: Int,
        isMovie: Boolean,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val apiUrl = if (isMovie) {
            "https://vidsrc-embed.ru/vs_src.php?type=movie&id=$tmdbId"
        } else {
            "https://vidsrc-embed.ru/vs_src.php?type=tv&id=$tmdbId&s=$season&e=$episode"
        }

        try {
            val res = app.get(
                apiUrl,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "https://vidsrc-embed.ru/"
                ),
                timeout = 8
            ).parsedSafe<Map<String, String>>()

            val src = res?.get("src")
            if (!src.isNullOrBlank()) {
                loadExtractor(src, "https://vidsrc-embed.ru/", subtitleCallback, callback)
            }
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
                val doc = app.get(
                    pageUrl,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to "$mainUrl/"
                    ),
                    timeout = 8
                ).document

                doc.select("iframe[src]").forEach { iframe ->
                    val src = fixUrl(iframe.attr("src"))
                    if (src.contains("swish")) {
                        val fileCode = src.substringAfter("id=").substringBefore("&")
                        if (fileCode.isNotBlank()) {
                            invokeStreamWish(fileCode, subtitleCallback, callback)
                        }
                    } else if (src.startsWith("http") && !src.contains("2embed.cc")) {
                        loadExtractor(src, pageUrl, subtitleCallback, callback)
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private suspend fun invokeStreamWish(
        fileCode: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        listOf(
            "https://2vcdn.skin/e/$fileCode",
            "https://streamwish.to/e/$fileCode",
            "https://wishembed.pro/e/$fileCode"
        ).forEach { embedUrl ->
            try {
                val html = app.get(
                    embedUrl,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to "https://streamsrcs.2embed.cc/"
                    ),
                    timeout = 8
                ).text

                val unpacked = getAndUnpack(html)

                // Ekstrak direct m3u8 stream
                val m3u8Matches = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").findAll(unpacked)
                m3u8Matches.forEach { match ->
                    val streamUrl = match.value
                    callback.invoke(
                        newExtractorLink(
                            "SFlix - StreamWish",
                            "StreamWish (Multi Quality)",
                            streamUrl,
                            ExtractorLinkType.M3U8
                        ) {
                            this.referer = "https://2vcdn.skin/"
                            this.headers = mapOf("Referer" to "https://2vcdn.skin/")
                        }
                    )
                }

                // Ekstrak fail sarikata VTT
                val vttMatches = Regex("""https?://[^\s"'<>]+\.vtt[^\s"'<>]*""").findAll(unpacked)
                vttMatches.forEach { vtt ->
                    val vttUrl = vtt.value
                    val label = when {
                        vttUrl.contains("eng", ignoreCase = true) -> "English"
                        vttUrl.contains("fre", ignoreCase = true) -> "French"
                        vttUrl.contains("spa", ignoreCase = true) -> "Spanish"
                        else -> "Subtitle"
                    }
                    subtitleCallback.invoke(
                        SubtitleFile(label, vttUrl)
                    )
                }

                loadExtractor(embedUrl, "https://streamsrcs.2embed.cc/", subtitleCallback, callback)
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
        try {
            val url = if (isMovie) {
                "https://multiembed.mov/?video_id=$tmdbId&tmdb=1"
            } else {
                "https://multiembed.mov/?video_id=$tmdbId&tmdb=1&s=$season&e=$episode"
            }

            val doc = app.get(
                url,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/"
                ),
                timeout = 8
            ).document

            doc.select("iframe[src], a[href*='embed'], button[data-src]").forEach { el ->
                val src = fixUrl(el.attr("src").ifBlank { el.attr("href") }.ifBlank { el.attr("data-src") })
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
            "https://player.autoembed.co/embed/movie/$tmdbId"
        } else {
            "https://player.autoembed.co/embed/tv/$tmdbId/$season/$episode"
        }

        try {
            val doc = app.get(
                url,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "https://autoembed.co/"
                ),
                timeout = 8
            ).document

            doc.select("iframe[src]").forEach { iframe ->
                val src = fixUrl(iframe.attr("src"))
                if (src.startsWith("http") && !src.contains("autoembed.co")) {
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
            val doc = app.get(
                url,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/"
                ),
                timeout = 8
            ).document

            doc.select("iframe[src]").forEach { iframe ->
                val src = fixUrl(iframe.attr("src"))
                if (src.startsWith("http")) {
                    loadExtractor(src, url, subtitleCallback, callback)
                }
            }
        } catch (_: Throwable) {
        }
    }

    data class SFlixData(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("type") val type: String? = null
    )

    data class SFlixLinkData(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("imdbId") val imdbId: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("episode") val episode: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("year") val year: Int? = null
    )

    data class Media(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("original_title") val originalTitle: String? = null,
        @JsonProperty("original_name") val originalName: String? = null,
        @JsonProperty("media_type") val mediaType: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("vote_average") val voteAverage: Any? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("overview") val overview: String? = null
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
}
