package com.mts.juraganfilm

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class JuraganfilmPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(JuraganfilmProvider())
        registerExtractorAPI(Indo666Com())
        registerExtractorAPI(ApiLivechatincCom())
        registerExtractorAPI(FkuponCom())
        registerExtractorAPI(ItunesAppleCom())
        registerExtractorAPI(DnsperfCom())
        registerExtractorAPI(MediaBioSite())
        registerExtractorAPI(MyLivechatincCom())
        registerExtractorAPI(AccountsLivechatCom())
        registerExtractorAPI(Cina777Com())
        registerExtractorAPI(TLy())
        registerExtractorAPI(Cina1Com())
        registerExtractorAPI(WapCina777Com())
        registerExtractorAPI(HistoryJlfafafa3Com())
        registerExtractorAPI(WapCina3Xyz())
        registerExtractorAPI(Bravobet77Monster())
        registerExtractorAPI(Bravobet77Webns2Live())
        registerExtractorAPI(StoretnIn())
        registerExtractorAPI(LivechatCom())
        registerExtractorAPI(TextCom())
        registerExtractorAPI(PlatformTextCom())
        registerExtractorAPI(WindblueCfd())
        registerExtractorAPI(JsStripeCom())
        registerExtractorAPI(AppOnescreenerCom())
        registerExtractorAPI(ApkblockS3Apnortheast1AmazonawsCom())
        registerExtractorAPI(VpnnawalaSite())
        registerExtractorAPI(Ratu89Com())
        registerExtractorAPI(Vpn89Site())
        registerExtractorAPI(Gratu89Com())
        registerExtractorAPI(BioSite())
        registerExtractorAPI(Gaza88Com())
        registerExtractorAPI(PlayerXExtractor())
    }
}
