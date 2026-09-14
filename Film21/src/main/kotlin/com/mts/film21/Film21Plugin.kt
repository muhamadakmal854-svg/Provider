package com.mts.film21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Film21Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Film21())
        registerExtractorAPI(AbyssCdn())
        registerExtractorAPI(AbyssPlayer())
        registerExtractorAPI(AbyssStream())
        registerExtractorAPI(TurboVidExtractor())
        registerExtractorAPI(EmturbovidExtractor())
        registerExtractorAPI(StreamWishExtractor())
        registerExtractorAPI(MorenciusExtractor())
        registerExtractorAPI(MinochinosExtractor())
        registerExtractorAPI(EmbedWishExtractor())
        registerExtractorAPI(VidHideExtractor())
        registerExtractorAPI(VidHideProExtractor())
        registerExtractorAPI(VidHideVipExtractor())
        registerExtractorAPI(FileLionsExtractor())
        registerExtractorAPI(FilemoonExtractor())
        registerExtractorAPI(FilemoonSxExtractor())
        registerExtractorAPI(FilemoonInExtractor())
        registerExtractorAPI(RpmVidExtractor())
    }
}
