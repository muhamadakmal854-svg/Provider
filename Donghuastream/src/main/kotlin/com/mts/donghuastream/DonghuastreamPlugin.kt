package com.mts.donghuastream

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DonghuastreamPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DonghuastreamProvider())
        registerExtractorAPI(Aqle3Com())
        registerExtractorAPI(AzzzCom())
        registerExtractorAPI(GeoDailymotionCom())
        registerExtractorAPI(PlayStreamplayCoIn())
    }
}
