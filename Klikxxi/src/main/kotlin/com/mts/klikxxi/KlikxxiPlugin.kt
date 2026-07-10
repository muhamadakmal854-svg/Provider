package com.mts.klikxxi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class KlikxxiPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(KlikxxiProvider())
        registerExtractorAPI(ZombiemealCom())
        registerExtractorAPI(VipIdlix21Pro())
        registerExtractorAPI(EmbedpyroxXyz())
        registerExtractorAPI(AbyssplayerCom())
        registerExtractorAPI(CvGenipspillionCom())
        registerExtractorAPI(MorenciusCom())
        registerExtractorAPI(PortalMgaOrgMt())
        registerExtractorAPI(RedorangeComMt())
        registerExtractorAPI(Server9HdigitalCom())
        registerExtractorAPI(UseTypekitNet())
        registerExtractorAPI(VincentdesignCa())
        registerExtractorAPI(ResponsiblegamblingOrg())
        registerExtractorAPI(AjaxAspnetcdnCom())
        registerExtractorAPI(AppFive9Eu())
        registerExtractorAPI(GambleawareOrg())
        registerExtractorAPI(IYtimgCom())
        registerExtractorAPI(YoutuBe())
        registerExtractorAPI(PlayerXExtractor())
    }
}
