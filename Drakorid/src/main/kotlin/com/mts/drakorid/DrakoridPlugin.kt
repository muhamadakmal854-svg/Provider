package com.mts.drakorid

import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class GembengCom : StreamWishExtractor() {
    override var name = "GembengCom"
    override var mainUrl = "https://drakorid.cam"
}

class PsLarinpaymentCom : StreamWishExtractor() {
    override var name = "PsLarinpaymentCom"
    override var mainUrl = "https://ps.larinpayment.com"
}

class Prx1559AntVmwesaOnline : StreamWishExtractor() {
    override var name = "Prx1559AntVmwesaOnline"
    override var mainUrl = "https://prx-1559-ant.vmwesa.online"
}

class Prx1328AntVmwesaOnline : StreamWishExtractor() {
    override var name = "Prx1328AntVmwesaOnline"
    override var mainUrl = "https://prx-1328-ant.vmwesa.online"
}

class PzEerfumerelCom : StreamWishExtractor() {
    override var name = "PzEerfumerelCom"
    override var mainUrl = "https://pz.eerfumerel.com"
}

class KisskhMegaplaySu : StreamWishExtractor() {
    override var name = "KisskhMegaplaySu"
    override var mainUrl = "https://kisskh.megaplay.su"
}

class StreamtapeCom : StreamTape() {
    override var name = "StreamtapeCom"
    override var mainUrl = "https://streamtape.com"
}

@CloudstreamPlugin
class DrakoridPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Drakorid())
        registerExtractorAPI(GembengCom())
        registerExtractorAPI(PsLarinpaymentCom())
        registerExtractorAPI(Prx1559AntVmwesaOnline())
        registerExtractorAPI(Prx1328AntVmwesaOnline())
        registerExtractorAPI(PzEerfumerelCom())
        registerExtractorAPI(KisskhMegaplaySu())
        registerExtractorAPI(StreamtapeCom())
    }
}
