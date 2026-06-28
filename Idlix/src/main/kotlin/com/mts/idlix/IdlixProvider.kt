package com.mts.idlix

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.newSubtitleFile
import java.net.URI
import kotlinx.coroutines.delay

class IdlixProvider : MainAPI() {
    override var mainUrl = "https://comblank.com"
    override var name = "Idlix"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "movies" to "Movies Terbaru",
        "series" to "TV Series Terbaru",
    )

    private fun getPosterUrl(path: String?): String? {
        if (path.isNullOrEmpty()) return null
        if (path.startsWith("http")) return path
        return "https://image.tmdb.org/t/p/w342" + path
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val type = request.data
        val response = app.get("$mainUrl/api/$type?page=$page").parsedSafe<IdlixListResponse>()
        val home = response?.data?.mapNotNull { it.toSearchResult() } ?: emptyList()
        return newHomePageResponse(request.name, home)
    }

    private fun IdlixContent.toSearchResult(): SearchResponse {
        val title = this.title ?: ""
        val type = this.contentType ?: "movie"
        val path = if (type == "tv_series" || type == "series") "series" else "movies"
        val href = "$mainUrl/api/$path/${this.slug}"
        val poster = getPosterUrl(this.posterPath)
        val quality = getQualityFromString(this.quality)
        
        return if (type == "tv_series" || type == "series") {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
                this.quality = quality
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
                this.quality = quality
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/api/search?q=$query").parsedSafe<IdlixSearchResponse>()
        return response?.results?.mapNotNull { it.toSearchResult() } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val isMovie = url.contains("/movies/")
        
        if (isMovie) {
            val detail = app.get(url).parsedSafe<IdlixMovieDetail>() ?: throw Exception("Failed to load details")
            val title = detail.title ?: ""
            val poster = getPosterUrl(detail.posterPath)
            val year = detail.releaseDate?.substringBefore("-")?.toIntOrNull()
            val description = detail.overview
            val rating = detail.voteAverage
            val trailer = detail.trailerUrl
            
            val actors = detail.cast?.mapNotNull { actor ->
                val name = actor.name ?: return@mapNotNull null
                val image = getPosterUrl(actor.profilePath)
                Actor(name, image)
            } ?: emptyList()
            
            return newMovieLoadResponse(title, url, TvType.Movie, "movie|" + detail.id) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.score = rating?.let { Score.from10(it) }
                addActors(actors)
                addTrailer(trailer)
            }
        } else {
            val detail = app.get(url).parsedSafe<IdlixSeriesDetail>() ?: throw Exception("Failed to load TV details")
            val title = detail.title ?: ""
            val poster = getPosterUrl(detail.posterPath)
            val year = detail.firstAirDate?.substringBefore("-")?.toIntOrNull()
            val description = detail.overview
            val rating = detail.voteAverage
            val trailer = detail.trailerUrl
            
            val actors = detail.cast?.mapNotNull { actor ->
                val name = actor.name ?: return@mapNotNull null
                val image = getPosterUrl(actor.profilePath)
                Actor(name, image)
            } ?: emptyList()
            
            val episodes = detail.seasons?.amap { season ->
                val seasonNum = season.seasonNumber ?: return@amap emptyList<Episode>()
                val seasonJson = app.get("$mainUrl/api/series/${detail.slug}/season/$seasonNum").parsedSafe<IdlixSeasonResponse>()
                seasonJson?.season?.episodes?.mapNotNull { ep ->
                    newEpisode("episode|" + ep.id) {
                        this.name = ep.name
                        this.season = seasonNum
                        this.episode = ep.episodeNumber
                        this.posterUrl = getPosterUrl(ep.stillPath)
                    }
                } ?: emptyList()
            }?.flatten() ?: emptyList()
            
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.score = rating?.let { Score.from10(it) }
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parts = data.split("|")
        if (parts.size < 2) return false
        val type = parts[0]
        val id = parts[1]
        
        val playInfoUrl = "$mainUrl/api/watch/play-info/$type/$id"
        val playInfoReq = app.get(playInfoUrl)
        val playInfo = playInfoReq.parsedSafe<IdlixPlayInfo>() ?: return false
        val gateToken = playInfo.gateToken ?: return false
        
        val cookies = playInfoReq.okhttpResponse.headers("Set-Cookie")
        val didCookie = cookies.firstOrNull { it.contains("did=") }
            ?.substringAfter("did=")?.substringBefore(";")
        val cookieHeader = if (didCookie != null) "did=$didCookie" else null
        
        val serverNow = playInfo.serverNow ?: 0L
        val unlockAt = playInfo.unlockAt ?: 0L
        val waitTime = maxOf(0L, unlockAt - serverNow) + 1000L
        delay(waitTime)
        
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "Referer" to "$mainUrl/",
            "Origin" to mainUrl
        )
        if (cookieHeader != null) {
            headers["Cookie"] = cookieHeader
        }
        
        val claimReq = app.post(
            "$mainUrl/api/watch/session/claim",
            headers = headers,
            json = mapOf("gateToken" to gateToken)
        )
        val claimRes = claimReq.parsedSafe<IdlixClaimResponse>() ?: return false
        val claimToken = claimRes.claim ?: return false
        val redeemUrl = claimRes.redeemUrl ?: return false
        
        val redeemReq = app.post(
            redeemUrl,
            headers = mapOf(
                "Referer" to "$mainUrl/",
                "Origin" to mainUrl
            ),
            json = mapOf("claim" to claimToken)
        )
        val redeemRes = redeemReq.parsedSafe<IdlixRedeemResponse>() ?: return false
        val m3u8Url = redeemRes.url ?: return false
        
        M3u8Helper.generateM3u8(
            name,
            m3u8Url,
            referer = "$mainUrl/"
        ).forEach { link ->
            callback(link)
        }
        
        redeemRes.subtitles?.forEach { sub ->
            val lang = sub.lang ?: "id"
            val label = sub.label ?: "Indonesian"
            val path = sub.path ?: return@forEach
            subtitleCallback(
                newSubtitleFile(label, path)
            )
        }
        
        return true
    }

    data class IdlixListResponse(
        @JsonProperty("data") val data: List<IdlixContent>? = null
    )

    data class IdlixSearchResponse(
        @JsonProperty("results") val results: List<IdlixContent>? = null
    )

    data class IdlixContent(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("posterPath") val posterPath: String? = null,
        @JsonProperty("contentType") val contentType: String? = null,
        @JsonProperty("quality") val quality: String? = null,
        @JsonProperty("voteAverage") val voteAverage: Double? = null,
    )

    data class IdlixMovieDetail(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("posterPath") val posterPath: String? = null,
        @JsonProperty("releaseDate") val releaseDate: String? = null,
        @JsonProperty("voteAverage") val voteAverage: Double? = null,
        @JsonProperty("trailerUrl") val trailerUrl: String? = null,
        @JsonProperty("cast") val cast: List<IdlixCast>? = null
    )

    data class IdlixSeriesDetail(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("posterPath") val posterPath: String? = null,
        @JsonProperty("firstAirDate") val firstAirDate: String? = null,
        @JsonProperty("voteAverage") val voteAverage: Double? = null,
        @JsonProperty("trailerUrl") val trailerUrl: String? = null,
        @JsonProperty("cast") val cast: List<IdlixCast>? = null,
        @JsonProperty("seasons") val seasons: List<IdlixSeason>? = null
    )

    data class IdlixCast(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("profilePath") val profilePath: String? = null
    )

    data class IdlixSeason(
        @JsonProperty("seasonNumber") val seasonNumber: Int? = null,
        @JsonProperty("episodeCount") val episodeCount: Int? = null
    )

    data class IdlixSeasonResponse(
        @JsonProperty("season") val season: IdlixSeasonEpisodes? = null
    )

    data class IdlixSeasonEpisodes(
        @JsonProperty("episodes") val episodes: List<IdlixEpisode>? = null
    )

    data class IdlixEpisode(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("episodeNumber") val episodeNumber: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("stillPath") val stillPath: String? = null
    )

    data class IdlixPlayInfo(
        @JsonProperty("gateToken") val gateToken: String? = null,
        @JsonProperty("serverNow") val serverNow: Long? = null,
        @JsonProperty("unlockAt") val unlockAt: Long? = null
    )

    data class IdlixClaimResponse(
        @JsonProperty("claim") val claim: String? = null,
        @JsonProperty("redeemUrl") val redeemUrl: String? = null
    )

    data class IdlixRedeemResponse(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("subtitles") val subtitles: List<IdlixSub>? = null
    )

    data class IdlixSub(
        @JsonProperty("lang") val lang: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("path") val path: String? = null
    )
}
