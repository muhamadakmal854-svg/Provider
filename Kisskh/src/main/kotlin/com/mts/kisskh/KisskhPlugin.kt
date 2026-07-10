package com.mts.kisskh

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class KisskhPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(KisskhProvider())
        registerExtractorAPI(MozillaOrg())
        registerExtractorAPI(AppleCom())
        registerExtractorAPI(VivaldiCom())
        registerExtractorAPI(MicrosoftCom())
        registerExtractorAPI(BraveCom())
        registerExtractorAPI(Server20577834Fs1Hubspotusercontentna1Net())
        registerExtractorAPI(Makaagency4740449HssitesCom())
        registerExtractorAPI(Server4740449HssitesCom())
        registerExtractorAPI(MakaagencyCom())
        registerExtractorAPI(DocsJwplayerCom())
        registerExtractorAPI(AtlassianCom())
        registerExtractorAPI(TagiviCom())
        registerExtractorAPI(TickcounterCom())
        registerExtractorAPI(PlayerXExtractor())
    }
}
