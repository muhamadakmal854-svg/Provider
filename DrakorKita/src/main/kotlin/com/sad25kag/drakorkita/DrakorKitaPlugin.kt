package com.sad25kag.drakorkita

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DrakorKitaPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DrakorKita())
        registerExtractorAPI(DrakorKitaStream())
        registerExtractorAPI(AbyssCdn())
        registerExtractorAPI(StbP2P())
        registerExtractorAPI(Playerupnone())
        registerExtractorAPI(FastdlP2P())
        registerExtractorAPI(P2PStreamOnline())
        registerExtractorAPI(Strp2pCom())
        registerExtractorAPI(UpnOneCom())
    }
}
