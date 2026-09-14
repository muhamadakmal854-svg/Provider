package com.mts.lk21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Lk21Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Lk21())
        registerExtractorAPI(AbyssPlayer())
        registerExtractorAPI(VidHideExtractor())
        registerExtractorAPI(MorenciusExtractor())
        registerExtractorAPI(CallistaniseExtractor())
        registerExtractorAPI(EfekStream())
        registerExtractorAPI(ByseqExtractor())
        registerExtractorAPI(StreamWishExtractor())
        registerExtractorAPI(StreamP2PExtractor())
        registerExtractorAPI(EmbedPyroxExtractor())
    }
}
