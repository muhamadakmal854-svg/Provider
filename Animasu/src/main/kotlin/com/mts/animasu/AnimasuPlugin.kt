package com.mts.animasu

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnimasuPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimasuProvider())
        registerExtractorAPI(I1WpCom())
        registerExtractorAPI(I0WpCom())
        registerExtractorAPI(I2WpCom())
        registerExtractorAPI(I3WpCom())
        registerExtractorAPI(AnimasuWork())
        registerExtractorAPI(AnimasuDev())
        registerExtractorAPI(BloggerCom())
        registerExtractorAPI(DraftBloggerCom())
        registerExtractorAPI(PlayerXExtractor())
    }
}
