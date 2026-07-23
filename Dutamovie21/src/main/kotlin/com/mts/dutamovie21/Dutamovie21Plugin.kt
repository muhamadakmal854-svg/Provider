package com.mts.dutamovie21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Dutamovie21Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Dutamovie21Provider())
        registerExtractorAPI(AbyssplayerCom())
        registerExtractorAPI(Dm21Embed4meVip())
        registerExtractorAPI(LivePlayerp2pOnline())
        registerExtractorAPI(VoeSx())
        registerExtractorAPI(MorenciusCom())
        registerExtractorAPI(HgcloudTo())
        registerExtractorAPI(Dm21UpnsLive())
        registerExtractorAPI(VeevTo())
        registerExtractorAPI(EmbedpyroxXyz())
        registerExtractorAPI(HelvidNet())
        registerExtractorAPI(RpmPlayShare())
        registerExtractorAPI(Embed4MePlay())
        registerExtractorAPI(GoogleVideo())
    }
}
