package com.mts.film21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Film21Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Film21Provider())
        registerExtractorAPI(MinochinosCom())
        registerExtractorAPI(MorenciusCom())
        registerExtractorAPI(PlaycinematicCom())
        registerExtractorAPI(EmbedpyroxXyz())
        registerExtractorAPI(AbyssplayerCom())
        registerExtractorAPI(RpmPlayShare())
        registerExtractorAPI(Embed4MePlay())
        registerExtractorAPI(GoogleVideo())
    }
}
