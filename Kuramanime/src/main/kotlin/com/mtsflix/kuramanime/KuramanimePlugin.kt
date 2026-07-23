package com.mtsflix.kuramanime

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class KuramanimePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(KuramanimeProvider())
        registerExtractorAPI(KuramaSubindoNet())
        registerExtractorAPI(KuramashopNet())
        registerExtractorAPI(TrakteerId())
        registerExtractorAPI(SaweriaCo())
    }
}
