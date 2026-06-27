package com.mts.animexin

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnimexinPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimexinProvider())
        registerExtractorAPI(DqEndebedouseShop())
        registerExtractorAPI(GeoDailymotionCom())
        registerExtractorAPI(MirroredTo())
        registerExtractorAPI(IsLysategriphusCfd())
    }
}
