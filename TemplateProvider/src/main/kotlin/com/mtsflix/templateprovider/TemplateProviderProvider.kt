package com.mtsflix.{class_name.lower()}

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class {class_name}Provider : MainAPI() {
    override var mainUrl = "{meta.get('url', '')}"
    override var name = "{class_name}"
    override var supportedTypes = setOf(TvType.Movie)
}
