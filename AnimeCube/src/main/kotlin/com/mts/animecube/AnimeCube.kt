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
import java.text.SimpleDateFormat
import java.util.Locale
import java.security.MessageDigest
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

        val list = mutableListOf<AnimeCubeItem>()
        val pushRegex = Regex("""self\.__next_f\.push\(\[1,\s*"(.*?)"\]\)""")
        val sb = StringBuilder()
        pushRegex.findAll(html).forEach {
            sb.append(it.groupValues[1].replace("\"", """).replace("\\", "\"))
        }
        val fullData = sb.toString()

        val animeRegex = Regex("""\{"aliases":\[.*?\][^}]+"slug":"([^"]+)"[^}]+"title":"([^"]+)"[^}]+(?:\}|\}\])""")
        animeRegex.findAll(fullData).forEach { m ->
            val chunk = m.value
            val slug = m.groupValues[1]
            val title = m.groupValues[2]
            val cover = Regex(""""coverImage":"([^"]+)"""").find(chunk)?.groupValues?.get(1)
            val rating = Regex(""""rating":([0-9.]+)""").find(chunk)?.groupValues?.get(1)?.toDoubleOrNull()
            val status = Regex(""""status":"([^"]+)"""").find(chunk)?.groupValues?.get(1)
            val genresRaw = Regex(""""genres":\[(.*?)\]""").find(chunk)?.groupValues?.get(1) ?: ""
            val genres = genresRaw.split(",").map { it.trim().removeSurrounding(""") }.filter { it.isNotBlank() }
            val hasUpcoming = chunk.contains("__hasUpcoming":true")

            list.add(AnimeCubeItem(slug, title, cover, rating, status, genres, hasUpcoming))
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
                sb.append(it.groupValues[1].replace("\"", """).replace("\\", "\"))
            }
            val fullData = sb.toString()

            val titleMatch = Regex(""""title":"([^"]+)".*?"description":"([^"]*)"""").find(fullData)
            val title = titleMatch?.groupValues?.get(1)?.trim() ?: slug.replace("-", " ").capitalize(Locale.ROOT)
            val plot = titleMatch?.groupValues?.get(2)
            val cover = Regex(""""coverImage":"([^"]+)"""").find(fullData)?.groupValues?.get(1)
            val rating = Regex(""""rating":([0-9.]+)""").find(fullData)?.groupValues?.get(1)

            // Parse seasons and episodes
            val episodes = mutableListOf<Episode>()
            val seasonMatches = Regex("""\{"id":"(tab-[^"]+)","number":(\d+),"title":"([^"]+)","customName":"([^"]*)","year":(\d+),"rating":([0-9.]+),"episodes":\[(.*?)\]\}""").findAll(fullData)

            for (sm in seasonMatches) {
                val tabId = sm.groupValues[1]
                val sNum = sm.groupValues[2].toIntOrNull() ?: 1
                val sTitle = sm.groupValues[3]
                val epRaw = sm.groupValues[7]

                val epMatches = Regex("""\{"id":"([^"]+)","number":(\d+),"numberDisplay":"([^"]*)","title":"([^"]*)","description":"([^"]*)","duration":(\d+),"publishedAt":"([^"]*)"\}""").findAll(epRaw)
                for (em in epMatches) {
                    val epId = em.groupValues[1]
                    val epNum = em.groupValues[2].toIntOrNull() ?: 1
                    val epNumDisplay = em.groupValues[3]
                    val epTitleRaw = em.groupValues[4]
                    val epDesc = em.groupValues[5]
                    val epDateStr = em.groupValues[7]

                    val epTitle = if (epTitleRaw.isNotBlank()) epTitleRaw else "Episod $epNumDisplay"
                    val parsedEpoch = try {
                        if (epDateStr.isNotBlank()) {
                            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).parse(epDateStr)?.time
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
                                primaryTabId = tabId,
                                seasonId = tabId
                            ).toJson()
                        ) {
                            this.name = epTitle
                            this.season = sNum
                            this.episode = epNum
                            this.posterUrl = cover
                            this.description = epDesc.takeIf { it.isNotBlank() } ?: plot
                            this.score = Score.from10(rating)
                            if (parsedEpoch != null) {
                                this.date = parsedEpoch
                            }
                        }.apply {
                            if (epDateStr.isNotBlank()) {
                                this.addDate(epDateStr.substringBefore("T"))
                            }
                        }
                    )
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
                this.score = Score.from10(rating)
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
                    val epTitle = eps.name.takeIf { !it.isNullOrBlank() } ?: "Episod $epNum"
                    val epStill = getOriImageUrl(eps.stillPath) ?: getImageUrl(eps.stillPath) ?: poster
                    val airDateStr = eps.airDate

                    val parsedEpoch = try {
                        if (!airDateStr.isNullOrBlank()) {
                            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(airDateStr)?.time
                        } else null
                    } catch (_: Exception) { null }

                    newEpisode(
                        AnimeCubeLinkData(
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
        val cleanImdb = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
        val numericImdb = cleanImdb.removePrefix("tt")

        // 1. Stremio OpenSubtitles v3 (Menghasilkan fail .srt UTF-8 secara terus)
        try {
            val stremioUrl = if (season != null && episode != null) {
                "https://opensubtitles-v3.strem.io/subtitles/series/$cleanImdb:$season:$episode.json"
            } else {
                "https://opensubtitles-v3.strem.io/subtitles/movie/$cleanImdb.json"
            }
            val res = app.get(stremioUrl, timeout = 10L).parsedSafe<AnimeCubeSubResponse>()
            res?.subtitles?.forEach { sub ->
                val subUrl = sub.url ?: return@forEach
                val langCode = sub.lang?.lowercase() ?: "eng"

                val langName = when {
                    langCode.startsWith("en") -> "English [Eng]"
                    langCode.startsWith("id") || langCode == "ind" -> "Indonesian [Ind]"
                    langCode.startsWith("ms") || langCode.startsWith("my") || langCode == "may" -> "Malay [My]"
                    else -> null
                }

                if (langName != null) {
                    subtitleCallback.invoke(
                        SubtitleFile(
                            langName,
                            subUrl
                        )
                    )
                }
            }
        } catch (_: Throwable) {
        }

        // 2. OpenSubtitles API Langsung (Menapis Bahasa Melayu, Indonesia, Inggeris)
        try {
            val queryParams = mutableListOf(
                "imdbid" to numericImdb,
                "sublanguageid" to "eng,ind,may,msa"
            )
            if (season != null && episode != null) {
                queryParams.add("season" to season.toString())
                queryParams.add("episode" to episode.toString())
            }

            val osUrl = "https://rest.opensubtitles.org/search/${queryParams.joinToString("/") { "${it.first}-${it.second}" }}"
            val osRes = app.get(
                osUrl,
                headers = mapOf("User-Agent" to "TemporaryUserAgent"),
                timeout = 10L
            ).parsedSafe<List<OpenSubItem>>()

            osRes?.forEach { sub ->
                val dlLink = sub.SubDownloadLink ?: return@forEach
                val subLang = sub.SubLanguageID?.lowercase() ?: ""
                val cleanDl = dlLink.replace(".gz", "").replace(".zip", "")

                val label = when (subLang) {
                    "eng" -> "English [Eng] (OS)"
                    "ind" -> "Indonesian [Ind] (OS)"
                    "may", "msa" -> "Malay [My] (OS)"
                    else -> null
                }

                if (label != null) {
                    subtitleCallback.invoke(
                        SubtitleFile(label, cleanDl)
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
                suspend { invokeVidLink(tmdbId, isMovie, season, episode, subtitleCallback, callback) },
                suspend { invoke2Embed(tmdbId, isMovie, season, episode, subtitleCallback, callback) },
                suspend { invokeSuperEmbed(tmdbId, isMovie, season, episode, subtitleCallback, callback) },
                suspend { invokeAutoEmbed(tmdbId, isMovie, season, episode, subtitleCallback, callback) },
                suspend { invokeVidsrc(tmdbId, isMovie, season, episode, subtitleCallback, callback) },
                suspend { invokeAnimeCubeNative(tmdbId, season, episode, subtitleCallback, callback) }
            ).amap { it.invoke() }
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
            "https://moviesapi.to/api/v1/movie/$tmdbId"
        } else {
            "https://moviesapi.to/api/v1/tv/$tmdbId/$season/$episode"
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
            val streamUrl = stream.url ?: return@forEach
            val serverName = "AnimeCube - Server 2 (VidCore ${stream.label ?: "HD"})"
            val qualityVal = when (stream.quality) {
                "1080" -> Qualities.P1080.value
                "720" -> Qualities.P720.value
                "480" -> Qualities.P480.value
                else -> Qualities.Unknown.value
            }

            if (streamUrl.contains(".m3u8")) {
                callback.invoke(
                    newExtractorLink(
                        serverName,
                        "VidCore [1080p FHD]",
                        streamUrl,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://vidcore.net/"
                        this.quality = qualityVal
                    }
                )

                try {
                    generateM3u8(
                        serverName,
                        streamUrl,
                        referer = "https://vidcore.net/"
                    ).forEach(callback)
                } catch (_: Throwable) {
                }
            } else {
                callback.invoke(
                    newExtractorLink(
                        serverName,
                        "VidCore Direct",
                        streamUrl,
                        ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://vidcore.net/"
                        this.quality = qualityVal
                    }
                )
            }
        }

        res.subtitles?.forEach { sub ->
            val subUrl = sub.file ?: return@forEach
            subtitleCallback.invoke(
                SubtitleFile(sub.label ?: "English", subUrl)
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
        val path = if (isMovie) "movie/$tmdbId" else "tv/$tmdbId/$season/$episode"
        val embedUrl = "$vidrockAPI/embed/$path"

        val html = try {
            app.get(embedUrl, headers = mapOf("User-Agent" to USER_AGENT)).text
        } catch (_: Throwable) {
            return
        }

        val encData = Regex("""data-source=["']([^"']+)["']""").find(html)?.groupValues?.get(1) ?: return
        val ivHex = Regex("""data-iv=["']([^"']+)["']""").find(html)?.groupValues?.get(1) ?: return
        val keyHex = "9b7d8f4e2a1c6b5d3e7f0a9b8c7d6e5f4a3b2c1d0e9f8a7b6c5d4e3f2a1b0c9d"

        val decrypted = try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), "AES")
            val ivSpec = IvParameterSpec(ivHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decodedBytes = base64DecodeArray(encData)
            String(cipher.doFinal(decodedBytes))
        } catch (_: Throwable) {
            return
        }

        val videoUrl = Regex("""['"](https?://[^'"]+\.m3u8[^'"]*)['"]""").find(decrypted)?.groupValues?.get(1) ?: return
        val serverName = "AnimeCube - Server 3 (Vidrock)"

        callback.invoke(
            newExtractorLink(
                serverName,
                "Vidrock [1080p FHD]",
                videoUrl,
                ExtractorLinkType.M3U8
            ) {
                this.referer = "$vidrockAPI/"
                this.quality = Qualities.P1080.value
            }
        )

        try {
            generateM3u8(
                serverName,
                videoUrl,
                referer = "$vidrockAPI/"
            ).forEach(callback)
        } catch (_: Throwable) {
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
        val path = if (isMovie) "movie/$tmdbId" else "tv/$tmdbId/$season/$episode"
        val url = "$vidlinkAPI/embed/$path"

        val res = try {
            app.get(url, headers = mapOf("User-Agent" to USER_AGENT)).text
        } catch (_: Throwable) {
            return
        }

        val streamUrl = Regex("""['"](https?://[^'"]+\.m3u8[^'"]*)['"]""").find(res)?.groupValues?.get(1) ?: return
        val serverName = "AnimeCube - Server 4 (VidLink Pro)"

        callback.invoke(
            newExtractorLink(
                serverName,
                "VidLink [1080p FHD]",
                streamUrl,
                ExtractorLinkType.M3U8
            ) {
                this.referer = "$vidlinkAPI/"
                this.quality = Qualities.P1080.value
            }
        )
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

        try {
            val html = app.get(url).text
            val iframe = Regex("""<iframe[^>]+src=["']([^"']+)["']""").find(html)?.groupValues?.get(1) ?: return
            val finalUrl = if (iframe.startsWith("//")) "https:$iframe" else iframe
            loadExtractor(finalUrl, url, subtitleCallback, callback)
        } catch (_: Throwable) {
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
            val html = app.get(url).text
            val regex = Regex("""['"](https?://[^'"]+stream[^'"]*)['"]""")
            regex.findAll(html).forEach { match ->
                val streamUrl = match.groupValues[1]
                loadExtractor(streamUrl, url, subtitleCallback, callback)
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
            "https://autoembed.to/movie/tmdb/$tmdbId"
        } else {
            "https://autoembed.to/tv/tmdb/$tmdbId-$season-$episode"
        }

        try {
            loadExtractor(url, "https://autoembed.to/", subtitleCallback, callback)
        } catch (_: Throwable) {
        }
    }

    private suspend fun invokeVidsrc(
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
            val doc = app.get(url).document
            doc.select("iframe").forEach { iframe ->
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
                loadExtractor(embedUrl, "https://animecube.live/", subtitleCallback, callback)
            } catch (_: Throwable) {
            }
        }
    }

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
