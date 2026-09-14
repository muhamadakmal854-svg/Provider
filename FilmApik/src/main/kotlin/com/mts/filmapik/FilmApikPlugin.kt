package com.mts.filmapik

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class FilmApikPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FilmApik())
        registerExtractorAPI(EfekStream())
        registerExtractorAPI(AbyssCdn())
        registerExtractorAPI(AbyssPlayer())
        registerExtractorAPI(AbyssTo())
        registerExtractorAPI(ShortAbyssCdn())
        registerExtractorAPI(MovieAbyssCdn())
        registerExtractorAPI(ByseqExtractor())
        registerExtractorAPI(FilemoonMirror())
        registerExtractorAPI(StreamP2PExtractor())
    }
}
