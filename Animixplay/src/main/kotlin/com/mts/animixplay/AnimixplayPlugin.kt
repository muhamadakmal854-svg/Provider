package com.mts.animixplay

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnimixplayPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimixplayProvider())
        registerExtractorAPI(EoBilstedquotasCom())
        registerExtractorAPI(TamilembedLol())
        registerExtractorAPI(KwikCx())
        registerExtractorAPI(GogoanimetvEs())
        registerExtractorAPI(AnimesamaSe())
    }
}
