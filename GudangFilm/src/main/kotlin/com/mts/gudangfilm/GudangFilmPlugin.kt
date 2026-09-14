package com.mts.gudangfilm

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class GudangFilmPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(GudangFilm())
        registerExtractorAPI(VidHideExtractor())
        registerExtractorAPI(MorenciusExtractor())
        registerExtractorAPI(CallistaniseExtractor())
        registerExtractorAPI(VidhidePlusExtractor())
        registerExtractorAPI(VidhidePreExtractor())
        registerExtractorAPI(VidhideProExtractor())
        registerExtractorAPI(ByseqExtractor())
        registerExtractorAPI(AbyssCdn())
        registerExtractorAPI(AbyssPlayer())
        registerExtractorAPI(EfekStream())
        registerExtractorAPI(StreamP2PExtractor())
    }
}
