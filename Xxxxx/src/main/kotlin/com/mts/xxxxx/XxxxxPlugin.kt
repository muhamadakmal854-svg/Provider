package com.mts.xxxxx

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class XxxxxPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(XxxxxProvider())
        registerExtractorAPI(StaticahXhcdnCom())
        registerExtractorAPI(IcvrmnssXhcdnCom())
        registerExtractorAPI(CollectorXhaccessCom())
        registerExtractorAPI(ThumbvnssXhcdnCom())
    }
}
