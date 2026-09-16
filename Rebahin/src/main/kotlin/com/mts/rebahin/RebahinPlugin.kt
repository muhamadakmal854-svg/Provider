package com.mts.rebahin

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class RebahinPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Rebahin())
        registerMainAPI(RebahinProvider())
        registerExtractorAPI(KotakajaibMe())
        registerExtractorAPI(Playhydrax())
        registerExtractorAPI(AbyssPlayer())
        registerExtractorAPI(AbyssTo())
        registerExtractorAPI(Gdriveplayer())
        registerExtractorAPI(Emturbovid())
        registerExtractorAPI(PlaycinematicCom())
        registerExtractorAPI(EmbedpyroxXyz())
        registerExtractorAPI(MasukestinExtractor())
        registerExtractorAPI(VidHideExtractor())
        registerExtractorAPI(StreamP2PExtractor())
    }
}
