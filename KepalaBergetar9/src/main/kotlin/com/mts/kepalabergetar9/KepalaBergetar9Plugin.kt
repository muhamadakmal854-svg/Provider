package com.mts.kepalabergetar9

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class KepalaBergetar9Plugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(KepalaBergetar9())
        registerExtractorAPI(VkspeedExtractor())
        registerExtractorAPI(DouvideoExtractor())
    }
}
