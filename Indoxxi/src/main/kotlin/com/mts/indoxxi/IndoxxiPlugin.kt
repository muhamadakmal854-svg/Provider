package com.mts.indoxxi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class IndoxxiPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Indoxxi())
        registerExtractorAPI(PutarinExtractor())
        registerExtractorAPI(VidHideExtractor())
        registerExtractorAPI(MorenciusExtractor())
        registerExtractorAPI(CallistaniseExtractor())
        registerExtractorAPI(EfekStream())
        registerExtractorAPI(StreamWishExtractor())
        registerExtractorAPI(ByseqExtractor())
        registerExtractorAPI(AbyssPlayer())
        registerExtractorAPI(StreamP2PExtractor())
    }
}
