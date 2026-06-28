package com.mts.drama

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DramaPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DramaProvider())
        registerExtractorAPI(TeleLk21De())
        registerExtractorAPI(D21Team())
        registerExtractorAPI(Tv4NontondramaMy())
    }
}
