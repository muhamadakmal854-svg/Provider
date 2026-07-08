package com.mts.drakorid

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DrakoridPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DrakoridProvider())
        registerExtractorAPI(AbyssplayerCom())
        registerExtractorAPI(KisskhMegaplaySu())
        registerExtractorAPI(BloggerCom())
        registerExtractorAPI(GembengCom())
        registerExtractorAPI(PsLarinpaymentCom())
        registerExtractorAPI(Prx1328AntVmwesaOnline())
        registerExtractorAPI(StreamtapeCom())
        registerExtractorAPI(PzEerfumerelCom())
        registerExtractorAPI(Prx1546AntVmwesaOnline())
        registerExtractorAPI(Prx1317AntVmwesaOnline())
    }
}
