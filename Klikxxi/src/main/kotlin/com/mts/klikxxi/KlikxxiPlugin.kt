package com.mts.klikxxi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class KlikxxiPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(KlikxxiProvider())
        registerExtractorAPI(OracleCom())
        registerExtractorAPI(ConsentTrustarcCom())
        registerExtractorAPI(LoginApiaryIo())
        registerExtractorAPI(MicrosoftCom())
        registerExtractorAPI(VapehusetSe())
        registerExtractorAPI(BuycheapestfollowersCom())
        registerExtractorAPI(AitexthumanizerCom())
        registerExtractorAPI(IbanCom())
        registerExtractorAPI(Views4YouCom())
        registerExtractorAPI(ImgLulucdnCom())
        registerExtractorAPI(Iujj82L8X5NtTnmrOrg())
        registerExtractorAPI(Dh8Azcl753E1ECloudfrontNet())
        registerExtractorAPI(YfDiasyrmunionicCom())
        registerExtractorAPI(Server36784GomatinequisheoiCom())
        registerExtractorAPI(LuluvdoCom())
        registerExtractorAPI(LulustreamCom())
        registerExtractorAPI(VoeSx())
        registerExtractorAPI(VeevTo())
        registerExtractorAPI(MediaFastcheckerUs())
        registerExtractorAPI(RisdanlyCom())
        registerExtractorAPI(ItunesAppleCom())
        registerExtractorAPI(DnsperfCom())
        registerExtractorAPI(Emas188May14Ink())
        registerExtractorAPI(XfilesharingproDocsApiaryIo())
    }
}
