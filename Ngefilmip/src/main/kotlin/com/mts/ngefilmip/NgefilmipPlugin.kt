package com.mts.ngefilmip

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class NgefilmipPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(NgefilmipProvider())
        registerExtractorAPI(LrHepcatsanemicCom())
        registerExtractorAPI(Pl25203512EffectivegatecpmCom())
        registerExtractorAPI(YoutuBe())
        registerExtractorAPI(IYtimgCom())
    }
}
