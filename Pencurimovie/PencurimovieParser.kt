package com.mtsflix.pencurimovie

import com.fasterxml.jackson.annotation.JsonProperty

data class PencurimovieSearchResult(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("poster") val poster: String? = null
)

data class PencurimovieMediaData(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("iframe_src") val iframeSrc: String? = null
)
