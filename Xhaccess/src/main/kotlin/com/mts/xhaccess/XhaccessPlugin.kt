package com.mts.xhaccess

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class XhaccessPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(XhaccessProvider())
        registerExtractorAPI(StaticahXhcdnCom())
        registerExtractorAPI(IcvrmnssXhcdnCom())
        registerExtractorAPI(CollectorXhaccessCom())
        registerExtractorAPI(ThumbvnssXhcdnCom())
        registerExtractorAPI(VideonssXhcdnCom())
    }
}
