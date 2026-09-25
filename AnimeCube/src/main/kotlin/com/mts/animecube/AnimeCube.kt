package com.mts.animecube

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
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class AnimeCube : MainAPI() {
    override var mainUrl = "https://animecube.live"
    override var name = "AnimeCube"
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

        fun decryptAesGcm(keyBytes: ByteArray, ivBytes: ByteArray, cipherBytes: ByteArray): String {
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val spec = GCMParameterSpec(128, ivBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec)
            val plain = cipher.doFinal(cipherBytes)
            return String(plain, Charsets.UTF_8)
        }

        fun sha256(input: String): ByteArray {
            return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        }

        fun randomHex(bytesCount: Int = 16): String {
            val bytes = Random.nextBytes(bytesCount)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    data class AnimeCubeItem(
        val slug: String,
        val title: String,
        val cover: String?,
        val rating: Double?,
        val status: String?,
        val genres: List<String>,
        val hasUpcoming: Boolean = false
    )

    private var cachedAnimeList: List<AnimeCubeItem>? = null
    private var lastFetchTime = 0L

    private suspend fun fetchAnimeCubeList(): List<AnimeCubeItem> {
        val now = System.currentTimeMillis()
        cachedAnimeList?.let {
            if (now - lastFetchTime < 300_000) return it
        }

        val html = try {
            app.get(mainUrl, headers = mapOf("User-Agent" to USER_AGENT)).text
        } catch (_: Exception) {
            return cachedAnimeList ?: emptyList()
        }

        val pushRegex = Regex("""self\.__next_f\.push\(\[1,\s*"(.*?)"\]\)""")
        val sb = StringBuilder()
        pushRegex.findAll(html).forEach {
            val unescaped = it.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
            sb.append(unescaped)
        }
        val fullData = sb.toString()

        val list = mutableListOf<AnimeCubeItem>()
        var pos = 0
        while (true) {
            val idx = fullData.indexOf("{\"aliases\":", pos)
            if (idx == -1) break
            var depth = 0
            var end = -1
            for (i in idx until fullData.length) {
                if (fullData[i] == '{') depth++
                else if (fullData[i] == '}') {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
            if (end != -1) {
                val snippet = fullData.substring(idx, end + 1)
                tryParseJson<AnimeCubeCard>(snippet)?.let { card ->
                    val s = card.slug
                    val t = card.title
                    if (!s.isNullOrBlank() && !t.isNullOrBlank()) {
                        list.add(
                            AnimeCubeItem(
                                slug = s,
                                title = t.trim(),
                                cover = card.coverImage,
                                rating = card.rating,
                                status = card.status,
                                genres = card.genres ?: emptyList(),
                                hasUpcoming = card.status?.contains("upcoming", true) == true
                            )
                        )
                    }
                }
                pos = end + 1
            } else {
                break
            }
        }

        if (list.isNotEmpty()) {
            cachedAnimeList = list
            lastFetchTime = now
        }
        return list
    }

    override val mainPage = mainPageOf(
        "$mainUrl/?category=for-you" to "💖 For You (Pilihan Utama AnimeCube)",
        "$mainUrl/?category=weekly-schedule" to "📺 Weekly Schedule (Sedang Bersiaran / Ongoing)",
        "$mainUrl/?category=most-popular" to "🏆 Most Popular (Paling Popular)",
        "$mainUrl/?category=future-releases" to "🚀 Future Releases (Keluaran Masa Depan / Akan Datang)",
        "$mainUrl/?category=season-completed" to "🏁 Season Completed (Musim Telah Selesai)",
        "$mainUrl/?category=completed" to "🎖️ Completed (Tamat Sepenuhnya)",
        "$mainUrl/?category=xianxia" to "⚡ Cultivation & Xianxia (Kultivasi & Dunia Abadi)",
        "$mainUrl/?category=action" to "💥 Action & Martial Arts (Aksi & Seni Mempertahankan Diri)",
        "$mainUrl/?category=fantasy" to "🔮 Fantasy & Xuanhuan (Fantasi Mistik)",
        "$mainUrl/?category=scifi" to "🚀 Sci-Fi & Futuristic (Sains Fiksyen)",
        "$mainUrl/?category=movies" to "🎬 Donghua Movies (Koleksi Filem Animasi)",
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
        "$tmdbAPI/tv/top_rated?api_key=$apiKey" to "📺 Siri TV Penilaian Tertinggi"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        if (request.data.startsWith(mainUrl)) {
            val list = fetchAnimeCubeList()
            val category = request.data.substringAfter("category=", "")
            val filtered = when (category) {
                "for-you" -> list.filter { (it.rating ?: 0.0) >= 9.4 }
                "weekly-schedule" -> list.filter { it.status == "ongoing" }
                "future-releases" -> list.filter { it.hasUpcoming || (it.status?.contains("upcoming", true) == true) }
                "most-popular" -> list.sortedByDescending { it.rating ?: 0.0 }
                "season-completed" -> list.filter { it.status == "season-completed" }
                "completed" -> list.filter { it.status == "completed" }
                "xianxia" -> list.filter { it.genres.any { g -> g in listOf("Xianxia", "Cultivation", "Xuanhuan") } }
                "action" -> list.filter { it.genres.any { g -> g in listOf("Action", "Martial Arts", "Action Epic") } }
                "fantasy" -> list.filter { it.genres.any { g -> g in listOf("Fantasy", "Supernatural", "Supernatural Fantasy", "Magic") } }
                "scifi" -> list.filter { it.genres.any { g -> g.contains("Sci-Fi", true) } }
                "movies" -> list.filter { it.title.contains("Movie", true) || it.slug.contains("movie", true) }
                else -> list
            }

            val searchResponses = filtered.map { item ->
                newTvSeriesSearchResponse(
                    item.title.trim(),
                    "$mainUrl/anime/${item.slug}",
                    TvType.Anime
                ) {
                    this.posterUrl = item.cover
                    this.score = Score.from10(item.rating?.toString())
                }
            }
            return newHomePageResponse(request.name, searchResponses)
        }

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
        } ?: throw ErrorLoadingException("Ralat memuatkan halaman utama")

        val searchResponses = res.results?.mapNotNull { it.toSearchResponse() } ?: emptyList()
        return newHomePageResponse(request.name, searchResponses)
    }

    private fun Media.toSearchResponse(): SearchResponse? {
        val title = this.title ?: this.name ?: this.originalTitle ?: this.originalName ?: return null
        val url = if (this.mediaType == "tv" || this.name != null) {
            "$tmdbAPI/tv/${this.id}?api_key=$apiKey"
        } else {
            "$tmdbAPI/movie/${this.id}?api_key=$apiKey"
        }
        val poster = getImageUrl(this.posterPath)
        val type = if (this.mediaType == "tv" || this.name != null) TvType.TvSeries else TvType.Movie

        return if (type == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, url, type) {
                this.posterUrl = poster
                this.score = Score.from10(this@toSearchResponse.voteAverage?.toString())
            }
        } else {
            newMovieSearchResponse(title, url, type) {
                this.posterUrl = poster
                this.score = Score.from10(this@toSearchResponse.voteAverage?.toString())
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        val cleanQ = query.trim().lowercase()

        // 1. Search AnimeCube native list
        try {
            val animeList = fetchAnimeCubeList()
            animeList.filter {
                it.title.lowercase().contains(cleanQ) || it.slug.lowercase().contains(cleanQ)
            }.forEach { item ->
                results.add(
                    newTvSeriesSearchResponse(
                        item.title.trim(),
                        "$mainUrl/anime/${item.slug}",
                        TvType.Anime
                    ) {
                        this.posterUrl = item.cover
                        this.score = Score.from10(item.rating?.toString())
                    }
                )
            }
        } catch (_: Exception) {
        }

        // 2. Search TMDB fallback
        try {
            val encodedQuery = URLEncoder.encode(query, "utf-8")
            val url = "$tmdbAPI/search/multi?api_key=$apiKey&query=$encodedQuery"
            val res = try {
                app.get(url).parsedSafe<Results>()
            } catch (_: Exception) {
                app.get(url.replace(apiKey, fallbackApiKey)).parsedSafe<Results>()
            }
            res?.results?.mapNotNull { it.toSearchResponse() }?.let { results.addAll(it) }
        } catch (_: Exception) {
        }

        return results
    }

    override suspend fun load(url: String): LoadResponse {
        if (url.startsWith("$mainUrl/anime/")) {
            val slug = url.substringAfter("$mainUrl/anime/").substringBefore("?").substringBefore("/")
            val detailHtml = app.get("$mainUrl/anime/$slug", headers = mapOf("User-Agent" to USER_AGENT)).text

            val pushRegex = Regex("""self\.__next_f\.push\(\[1,\s*"(.*?)"\]\)""")
            val sb = StringBuilder()
            pushRegex.findAll(detailHtml).forEach {
                val unescaped = it.groupValues[1]
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                sb.append(unescaped)
            }
            val fullData = sb.toString()

            var title: String = slug.replace("-", " ")
            var plot: String? = null
            var cover: String? = null
            var rating: Double? = null

            val titleIdx = fullData.indexOf("\"title\":\"")
            if (titleIdx != -1) {
                val startT = titleIdx + 9
                val endT = fullData.indexOf("\"", startT)
                if (endT != -1) title = fullData.substring(startT, endT).trim()
            }
            val descIdx = fullData.indexOf("\"description\":\"")
            if (descIdx != -1) {
                val startD = descIdx + 15
                val endD = fullData.indexOf("\"", startD)
                if (endD != -1) plot = fullData.substring(startD, endD)
            }
            val coverIdx = fullData.indexOf("\"coverImage\":\"")
            if (coverIdx != -1) {
                val startC = coverIdx + 14
                val endC = fullData.indexOf("\"", startC)
                if (endC != -1) cover = fullData.substring(startC, endC)
            }
            val ratingIdx = fullData.indexOf("\"rating\":")
            if (ratingIdx != -1) {
                val startR = ratingIdx + 9
                val endR = fullData.indexOfAny(charArrayOf(',', '}'), startR)
                if (endR != -1) rating = fullData.substring(startR, endR).trim().toDoubleOrNull()
            }

            // Parse seasons and episodes
            val episodes = mutableListOf<Episode>()
            var pos = 0
            while (true) {
                val idx = fullData.indexOf("{\"id\":\"tab-", pos)
                if (idx == -1) break
                var depth = 0
                var end = -1
                for (i in idx until fullData.length) {
                    if (fullData[i] == '{') depth++
                    else if (fullData[i] == '}') {
                        depth--
                        if (depth == 0) {
                            end = i
                            break
                        }
                    }
                }
                if (end != -1) {
                    val snippet = fullData.substring(idx, end + 1)
                    tryParseJson<AnimeCubeSeasonTab>(snippet)?.let { tab ->
                        val tabId = tab.id ?: "tab-1"
                        val sNum = tab.number ?: 1
                        tab.episodes?.forEach { ep ->
                            val epId = ep.id ?: "$slug-$tabId-ep-${ep.number ?: 1}"
                            val epNum = ep.number ?: 1
                            val epNumDisp = ep.numberDisplay ?: "$epNum"
                            val epTitle = if (!ep.title.isNullOrBlank()) ep.title else "Episod $epNumDisp"
                            val epDesc = ep.description?.takeIf { it.isNotBlank() } ?: plot
                            val parsedEpoch = try {
                                if (!ep.publishedAt.isNullOrBlank()) {
                                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).parse(ep.publishedAt)?.time
                                } else null
                            } catch (_: Exception) { null }

                            episodes.add(
                                newEpisode(
                                    AnimeCubeLinkData(
                                        id = null,
                                        imdbId = null,
                                        type = "tv",
                                        season = sNum,
                                        episode = epNum,
                                        title = title,
                                        slug = slug,
                                        episodeId = epId,
                                        primaryTabId = "primary-1",
                                        seasonId = tabId
                                    ).toJson()
                                ) {
                                    this.name = epTitle
                                    this.season = sNum
                                    this.episode = epNum
                                    this.posterUrl = cover
                                    this.description = epDesc
                                    this.score = Score.from10(rating?.toString())
                                    if (parsedEpoch != null) {
                                        this.date = parsedEpoch
                                    }
                                }.apply {
                                    if (!ep.publishedAt.isNullOrBlank()) {
                                        this.addDate(ep.publishedAt.substringBefore("T"))
                                    }
                                }
                            )
                        }
                    }
                    pos = end + 1
                } else {
                    break
                }
            }

            // Search TMDB to enrich with official YouTube trailer and backdrop
            var trailerUrl: String? = null
            var bgPoster: String? = null
            var tmdbId: String? = null
            try {
                val cleanTitle = URLEncoder.encode(title, "utf-8")
                val searchRes = app.get("$tmdbAPI/search/tv?api_key=$apiKey&query=$cleanTitle").parsedSafe<Results>()
                val firstResult = searchRes?.results?.firstOrNull()
                if (firstResult?.id != null) {
                    tmdbId = firstResult.id.toString()
                    val tmdbDetails = app.get("$tmdbAPI/tv/$tmdbId?api_key=$apiKey&append_to_response=videos").parsedSafe<MediaDetail>()
                    bgPoster = getBackdropUrl(tmdbDetails?.backdropPath)
                    trailerUrl = tmdbDetails?.videos?.results?.firstOrNull { it.key != null }?.let {
                        "https://www.youtube.com/watch?v=${it.key}"
                    }
                }
            } catch (_: Exception) {
            }

            return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = cover
                this.backgroundPosterUrl = bgPoster ?: cover
                this.plot = plot
                this.score = Score.from10(rating?.toString())
                this.showStatus = ShowStatus.Ongoing
                addTrailer(trailerUrl)
                if (tmdbId != null) {
                    addTMDbId(tmdbId)
                }
            }
        }

        val type = if (url.contains("/tv/")) TvType.TvSeries else TvType.Movie
        val id = url.substringAfterLast("/").substringBefore("?").toIntOrNull()
            ?: throw ErrorLoadingException("ID TMDB tidak sah")

        val append = "credits,videos,recommendations,external_ids"
        val tmdbUrl = if (type == TvType.TvSeries) {
            "$tmdbAPI/tv/$id?api_key=$apiKey&append_to_response=$append"
        } else {
            "$tmdbAPI/movie/$id?api_key=$apiKey&append_to_response=$append"
        }

        val res = try {
            app.get(tmdbUrl).parsedSafe<MediaDetail>()
        } catch (_: Exception) {
            app.get(tmdbUrl.replace(apiKey, fallbackApiKey)).parsedSafe<MediaDetail>()
        } ?: throw ErrorLoadingException("Gagal memuatkan butiran TMDB")

        val title = res.title ?: res.name ?: res.originalTitle ?: res.originalName ?: "AnimeCube"
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
                    val epDate = eps.airDate
                    val epParsedEpoch = try {
                        if (epDate != null) {
                            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(epDate)?.time
                        } else null
                    } catch (_: Exception) { null }

                    newEpisode(
                        AnimeCubeLinkData(
                            id = id,
                            imdbId = res.external_ids?.imdb_id,
                            type = "tv",
                            season = sNum,
                            episode = epNum,
                            title = title,
                            year = year
                        ).toJson()
                    ) {
                        this.name = eps.name
                        this.season = sNum
                        this.episode = epNum
                        this.posterUrl = getImageUrl(eps.stillPath)
                        this.description = eps.overview
                        this.score = Score.from10(eps.voteAverage?.toString())
                        if (epParsedEpoch != null) {
                            this.date = epParsedEpoch
                        }
                    }.apply {
                        if (epDate != null) {
                            this.addDate(epDate)
                        }
                    }
                }
            }?.flatten() ?: emptyList()

            return newTvSeriesLoadResponse(
                title,
                url,
                TvType.TvSeries,
                episodes
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
        } else {
            return newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                AnimeCubeLinkData(
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
        val cleanImdb = imdbId.replace("tt", "")

        val openSubUrl = if (season != null && episode != null) {
            "https://rest.opensubtitles.org/search/episode-$episode/imdbid-$cleanImdb/season-$season"
        } else {
            "https://rest.opensubtitles.org/search/imdbid-$cleanImdb"
        }

        try {
            val response = app.get(
                openSubUrl,
                headers = mapOf("X-User-Agent" to "VLSub 0.10.2")
            ).parsedSafe<List<OpenSubItem>>()

            response?.forEach { sub ->
                val dlLink = sub.SubDownloadLink ?: return@forEach
                val langCode = sub.SubLanguageID?.lowercase() ?: ""
                val langName = when (langCode) {
                    "eng", "en" -> "English"
                    "ind", "id" -> "Indonesian"
                    "may", "ms", "msa" -> "Malay"
                    else -> sub.LanguageName ?: langCode.uppercase()
                }

                if (langCode in listOf("eng", "en", "ind", "id", "may", "ms", "msa")) {
                    subtitleCallback(
                        SubtitleFile(
                            langName,
                            dlLink.replace(".gz", "").replace(".zip", "")
                        )
                    )
                }
            }
        } catch (_: Throwable) {
        }

        try {
            val subUrl = "https://sub.wyzie.ru/search?id=$imdbId"
            val subRes = app.get(subUrl).parsedSafe<AnimeCubeSubResponse>()
            subRes?.subtitles?.forEach { sub ->
                val subLang = sub.lang?.lowercase() ?: ""
                val label = when (subLang) {
                    "en", "eng" -> "English"
                    "id", "ind" -> "Indonesian"
                    "ms", "may" -> "Malay"
                    else -> subLang.uppercase()
                }
                if (subLang in listOf("en", "eng", "id", "ind", "ms", "may") && !sub.url.isNullOrBlank()) {
                    subtitleCallback(
                        SubtitleFile(
                            label,
                            sub.url
                        )
                    )
                }
            }
        } catch (_: Throwable) {
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linkData = parseJson<AnimeCubeLinkData>(data)

        // 1. If AnimeCube native item, extract AnimeCube native video source
        if (!linkData.slug.isNullOrBlank() && !linkData.episodeId.isNullOrBlank()) {
            try {
                invokeAnimeCubeLiveNative(
                    slug = linkData.slug,
                    episodeId = linkData.episodeId,
                    primaryTabId = linkData.primaryTabId ?: "primary-1",
                    seasonId = linkData.seasonId ?: "tab-1",
                    subtitleCallback = subtitleCallback,
                    callback = callback
                )
            } catch (_: Throwable) {
            }
        }

        // 2. Run multi-server scrapers for TMDB/IMDb
        var tmdbId = linkData.id
        val isMovie = linkData.type == "movie"
        val season = linkData.season
        val episode = linkData.episode
        var imdbId = linkData.imdbId

        if (tmdbId == null && !linkData.title.isNullOrBlank()) {
            try {
                val cleanTitle = URLEncoder.encode(linkData.title, "utf-8")
                val searchRes = app.get("$tmdbAPI/search/tv?api_key=$apiKey&query=$cleanTitle").parsedSafe<Results>()
                tmdbId = searchRes?.results?.firstOrNull()?.id
            } catch (_: Throwable) {
            }
        }

        if (tmdbId != null) {
            if (imdbId.isNullOrBlank()) {
                imdbId = fetchImdbId(tmdbId, isMovie)
            }

            fetchSubtitles(imdbId, season, episode, subtitleCallback)

            listOf(
                suspend { invokeMoviesAPI(tmdbId, isMovie, season, episode, subtitleCallback, callback) },
                suspend { invokeVidCore(tmdbId, isMovie, season, episode, subtitleCallback, callback) },
                suspend { invokeVidrock(tmdbId, isMovie, season, episode, subtitleCallback, callback) },
                suspend { invokeVidlink(tmdbId, season, episode, subtitleCallback, callback) },
                suspend { invoke2Embed(tmdbId, imdbId, isMovie, season, episode, subtitleCallback, callback) },
                suspend { invokeSuperEmbed(tmdbId, isMovie, season, episode, subtitleCallback, callback) },
                suspend { invokeAutoEmbed(tmdbId, isMovie, season, episode, subtitleCallback, callback) },
                suspend { invokeVidsrcTo(tmdbId, isMovie, season, episode, subtitleCallback, callback) },
                suspend { invokeAnimeCubeNative(tmdbId, season, episode, subtitleCallback, callback) }
            ).amap { call ->
                try {
                    call.invoke()
                } catch (_: Throwable) {
                }
            }
        }

        return true
    }

    private suspend fun invokeAnimeCubeLiveNative(
        slug: String,
        episodeId: String,
        primaryTabId: String,
        seasonId: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val nonce1 = randomHex(16)
        val vRes = app.get(
            "$mainUrl/api/anime-sources-versions",
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Accept" to "application/json",
                "X-Obf" to nonce1
            ),
            timeout = 10
        )

        val etag = vRes.headers["X-Registry-ETag"] ?: vRes.headers["x-registry-etag"] ?: return
        val vBody = vRes.parsedSafe<EncryptedResponse>()?.d ?: return
        val rawV = base64DecodeArray(vBody)
        val iv1 = rawV.copyOfRange(0, 12)
        val ct1 = rawV.copyOfRange(12, rawV.size)
        val key1 = sha256("$nonce1|$etag")
        val decVersions = decryptAesGcm(key1, iv1, ct1)

        val vJson = tryParseJson<VersionsResponse>(decVersions) ?: return
        val vVal = vJson.bySeason?.get(slug)?.get(primaryTabId)?.get(seasonId)
            ?: vJson.bySeason?.get(slug)?.values?.firstOrNull()?.get(seasonId)
            ?: vJson.bySeason?.get(slug)?.values?.firstOrNull()?.values?.firstOrNull()
            ?: return

        val nonce2 = randomHex(16)
        val sUrl = "$mainUrl/api/anime/$slug/episode/$episodeId/sources?v=$vVal&primaryTabId=$primaryTabId&seasonId=$seasonId"
        val sRes = app.get(
            sUrl,
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Accept" to "application/json",
                "X-Obf" to nonce2
            ),
            timeout = 10
        )

        val sBody = sRes.parsedSafe<EncryptedResponse>()?.d ?: return
        val rawS = base64DecodeArray(sBody)
        val iv2 = rawS.copyOfRange(0, 12)
        val ct2 = rawS.copyOfRange(12, rawS.size)
        val key2 = sha256("$nonce2|$vVal")
        val decSources = decryptAesGcm(key2, iv2, ct2)

        val sourcesJson = tryParseJson<AnimeCubeSourcesResponse>(decSources) ?: return
        sourcesJson.sources?.forEach { src ->
            if (src.platform == "dailymotion" && !src.videoId.isNullOrBlank()) {
                val dmUrl = "https://www.dailymotion.com/video/${src.videoId}"
                loadExtractor(dmUrl, mainUrl, subtitleCallback, callback)
            }
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
            val serverName = "AnimeCube - Server 1 (MoviesAPI)"
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
            val serverName = "AnimeCube - Server 2 (VidCore [$label])"

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
            val serverName = "AnimeCube - Server 3 (Vidrock [$sourceName])"

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
            loadExtractor(url, "https://popcornflix.com/", subtitleCallback, callback)
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

    private suspend fun invokeAnimeCubeNative(
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
                loadExtractor(embedUrl, "https://popcornflix.com/", subtitleCallback, callback)
            } catch (_: Throwable) {
            }
        }
    }



    data class AnimeCubeCard(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("coverImage") val coverImage: String? = null,
        @JsonProperty("rating") val rating: Double? = null,
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("genres") val genres: List<String>? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("hasEpisodes") val hasEpisodes: Boolean? = null
    )

    data class AnimeCubeSeasonTab(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("number") val number: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("customName") val customName: String? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("rating") val rating: Double? = null,
        @JsonProperty("episodes") val episodes: List<AnimeCubeEpisodeItem>? = null
    )

    data class AnimeCubeEpisodeItem(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("number") val number: Int? = null,
        @JsonProperty("numberDisplay") val numberDisplay: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("duration") val duration: Int? = null,
        @JsonProperty("publishedAt") val publishedAt: String? = null
    )

    data class AnimeCubeData(
        val id: Int? = null,
        val type: String? = null
    )

    data class AnimeCubeLinkData(
        val id: Int? = null,
        val imdbId: String? = null,
        val type: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val title: String? = null,
        val year: Int? = null,
        val slug: String? = null,
        val episodeId: String? = null,
        val primaryTabId: String? = null,
        val seasonId: String? = null
    )

    data class EncryptedResponse(
        @JsonProperty("d") val d: String? = null
    )

    data class VersionsResponse(
        @JsonProperty("bySeason") val bySeason: Map<String, Map<String, Map<String, String>>>? = null
    )

    data class AnimeCubeSource(
        @JsonProperty("platform") val platform: String? = null,
        @JsonProperty("videoId") val videoId: String? = null,
        @JsonProperty("quality") val quality: String? = null,
        @JsonProperty("goodSub") val goodSub: Boolean? = null
    )

    data class AnimeCubeSourcesResponse(
        @JsonProperty("sources") val sources: List<AnimeCubeSource>? = null
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

    data class AnimeCubeSubResponse(
        @JsonProperty("subtitles") val subtitles: List<AnimeCubeSubItem>? = null
    )

    data class AnimeCubeSubItem(
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
