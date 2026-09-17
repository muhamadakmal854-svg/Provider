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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linkData = parseJson<SFlixLinkData>(data)
        val id = linkData.id ?: return false
        val isMovie = linkData.type == "movie"
        val season = linkData.season
        val episode = linkData.episode

        listOf(
            // Server 1: MoviesAPI (Vidora Ultra-Fast 1080p FHD HLS)
            suspend {
                invokeMoviesAPI(id, isMovie, season, episode, subtitleCallback, callback)
            },
            // Server 2: 2Embed / StreamWish
            suspend {
                invoke2Embed(id, isMovie, season, episode, subtitleCallback, callback)
            },
            // Server 3: SuperEmbed / MultiEmbed
            suspend {
                invokeSuperEmbed(id, isMovie, season, episode, subtitleCallback, callback)
            },
            // Server 4: VidLink
            suspend {
                invokeVidLink(id, isMovie, season, episode, subtitleCallback, callback)
            },
            // Server 5: VixSrc
            suspend {
                invokeVixSrc(id, isMovie, season, episode, subtitleCallback, callback)
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

        val res = app.get(
            endpoint,
            headers = mapOf(
                "x-player-key" to moviesApiKey,
                "Referer" to "https://moviesapi.to/",
                "Origin" to "https://moviesapi.to"
            )
        ).parsedSafe<VidoraResponse>() ?: return

        res.sources?.forEach { src ->
            val m3u8Url = src.url ?: return@forEach
            generateM3u8(
                "SFlix - MoviesAPI",
                m3u8Url,
                "https://moviesapi.to/"
            ).forEach(callback)

            src.tracks?.forEach { track ->
                val trackUrl = track.file ?: return@forEach
                val lang = track.label ?: "Sub"
                subtitleCallback.invoke(
                    SubtitleFile(
                        lang,
                        trackUrl
                    )
                )
            }
        }
    }

    private suspend fun invoke2Embed(
        tmdbId: Int,
        isMovie: Boolean,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (isMovie) {
            "https://www.2embed.cc/embed/$tmdbId"
        } else {
            "https://www.2embed.cc/embedtv/$tmdbId&s=$season&e=$episode"
        }

        val doc = app.get(url, referer = "$mainUrl/").document
        doc.select("iframe[src]").forEach { iframe ->
            val src = fixUrl(iframe.attr("src"))
            if (src.contains("stream") || src.contains("embed") || src.contains("swish") || src.contains("play")) {
                loadExtractor(src, url, subtitleCallback, callback)
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

        val doc = app.get(url, referer = "$mainUrl/").document
        doc.select("iframe[src], a[href*='embed'], button[data-src]").forEach { el ->
            val src = fixUrl(el.attr("src").ifBlank { el.attr("href") }.ifBlank { el.attr("data-src") })
            if (src.startsWith("http")) {
                loadExtractor(src, url, subtitleCallback, callback)
            }
        }
    }

    private suspend fun invokeVidLink(
        tmdbId: Int,
        isMovie: Boolean,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (isMovie) {
            "https://vidlink.pro/movie/$tmdbId"
        } else {
            "https://vidlink.pro/tv/$tmdbId/$season/$episode"
        }

        val text = app.get(url, referer = "$mainUrl/").text
        val m3u8Matches = Regex("""https?://[^\\s"']+\\.m3u8[^\\s"']*""").findAll(text)
        m3u8Matches.forEach { match ->
            generateM3u8(
                "SFlix - VidLink",
                match.value,
                "https://vidlink.pro/"
            ).forEach(callback)
        }
    }

    private suspend fun invokeVixSrc(
        tmdbId: Int,
        isMovie: Boolean,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (isMovie) {
            "https://vixsrc.to/movie/$tmdbId"
        } else {
            "https://vixsrc.to/tv/$tmdbId/$season/$episode"
        }

        val doc = app.get(url, referer = "$mainUrl/").document
        doc.select("iframe[src]").forEach { iframe ->
            val src = fixUrl(iframe.attr("src"))
            loadExtractor(src, url, subtitleCallback, callback)
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
