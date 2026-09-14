package com.mts.klikxxi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class KlikxxiPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Klikxxi())
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
