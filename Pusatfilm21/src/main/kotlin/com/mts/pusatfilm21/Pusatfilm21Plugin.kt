package com.mts.pusatfilm21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Pusatfilm21Plugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Pusatfilm21())
        registerExtractorAPI(KotakajaibMe())
        registerExtractorAPI(Playhydrax())
        registerExtractorAPI(AbyssPlayer())
        registerExtractorAPI(AbyssTo())
        registerExtractorAPI(Gdriveplayer())
        registerExtractorAPI(Emturbovid())
        registerExtractorAPI(PlaycinematicCom())
        registerExtractorAPI(EmbedpyroxXyz())
        registerExtractorAPI(MasukestinExtractor())
    }
}
