package com.mts.pmsm

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class PMSMPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(PMSMProvider())
        registerExtractorAPI(AbyssplayerCom())
        registerExtractorAPI(AbysscdnCom())
        registerExtractorAPI(DsvplayCom())
        registerExtractorAPI(HgcloudTo())
        registerExtractorAPI(HglinkTo())
    }
}
