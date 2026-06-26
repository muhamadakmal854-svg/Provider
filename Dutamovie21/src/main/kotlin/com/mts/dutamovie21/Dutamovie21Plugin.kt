package com.mts.dutamovie21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Dutamovie21Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Dutamovie21Provider())
        registerExtractorAPI(KetikLive())
        registerExtractorAPI(BokinshopCom())
        registerExtractorAPI(GacorVin())
        registerExtractorAPI(Upload18Cc())
        registerExtractorAPI(EmbedpyroxXyz())
        registerExtractorAPI(IamcdnNet())
        registerExtractorAPI(RedditCom())
        registerExtractorAPI(TumblrCom())
        registerExtractorAPI(McYandex())
        registerExtractorAPI(IYtimgCom())
        registerExtractorAPI(YoutuBe())
    }
}
