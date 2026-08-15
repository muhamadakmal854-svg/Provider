package com.mts.sarangfilm21

import com.fasterxml.jackson.annotation.JsonProperty

data class Sarangfilm21SearchResult(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("poster") val poster: String? = null
)

data class Sarangfilm21MediaData(
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("user_id") val userId: String? = null,
    @JsonProperty("md5_id") val md5Id: String? = null,
    @JsonProperty("media") val media: String? = null
)
