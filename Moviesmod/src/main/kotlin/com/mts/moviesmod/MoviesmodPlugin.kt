package com.mts.moviesmod

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class MoviesmodPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(MoviesmodProvider())
        registerExtractorAPI(BunnyNet())
        registerExtractorAPI(IImgurCom())
        registerExtractorAPI(DsRecrampwipedCom())
        registerExtractorAPI(SadsAdsboostersXyz())
        registerExtractorAPI(HealthJkssbworldIn())
        registerExtractorAPI(CloudUnblockedgamesWorld())
        registerExtractorAPI(ImgurCom())
        registerExtractorAPI(S2WpCom())
        registerExtractorAPI(S1WpCom())
        registerExtractorAPI(StatsWpCom())
        registerExtractorAPI(PixelWpCom())
        registerExtractorAPI(WpMe())
        registerExtractorAPI(TemplatelensCom())
        registerExtractorAPI(S0WpCom())
    }
}
