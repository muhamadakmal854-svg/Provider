package com.mts.layarkaca

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class LayarkacaPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(LayarkacaProvider())
        registerExtractorAPI(TeleLk21De())
        registerExtractorAPI(TvLk21OfficialLove())
        registerExtractorAPI(CdnAmpprojectOrg())
        registerExtractorAPI(TvLk21OfficialDev())
        registerExtractorAPI(D21Team())
        registerExtractorAPI(MLk21Party())
        registerExtractorAPI(PlayerXExtractor())
    }
}
