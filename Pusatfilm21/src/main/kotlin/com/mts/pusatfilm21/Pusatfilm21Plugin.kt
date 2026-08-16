package com.mts.pusatfilm21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Pusatfilm21Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Pusatfilm21Provider())
        registerExtractorAPI(KotakajaibMe())
        registerExtractorAPI(EmturbovidCom())
        registerExtractorAPI(PlayhydraxCom())
        registerExtractorAPI(PlaycinematicCom())
        registerExtractorAPI(EmbedpyroxXyz())
        registerExtractorAPI(MorenciusCom())
        registerExtractorAPI(AbyssplayerCom())
        registerExtractorAPI(RpmPlayShare())
        registerExtractorAPI(Embed4MePlay())
        registerExtractorAPI(GoogleVideo())
    }
}
