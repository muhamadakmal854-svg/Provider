package com.mtsflix.{class_name.lower()}

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class {class_name}Plugin: Plugin() {
    override fun load() {
        registerMainAPI({class_name}Provider())
    }
}
