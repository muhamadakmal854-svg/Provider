package com.mts.gudangfilm

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class GudangFilmPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(GudangFilmProvider())
        registerExtractorAPI(CdnAmpprojectOrg())
        registerExtractorAPI(Poker88PlayMe())
        registerExtractorAPI(MorenciusCom())
        registerExtractorAPI(Server3VipBcdnNet())
        registerExtractorAPI(PremicloudNet())
        registerExtractorAPI(VidhidehubCom())
        registerExtractorAPI(VidhideplusCom())
        registerExtractorAPI(BestxStream())
        registerExtractorAPI(VidhideproCom())
        registerExtractorAPI(PlaycinematicCom())
        registerExtractorAPI(EmbedpyroxXyz())
        registerExtractorAPI(AbyssplayerCom())
        registerExtractorAPI(RpmPlayShare())
        registerExtractorAPI(Embed4MePlay())
        registerExtractorAPI(GoogleVideo())
    }
}
