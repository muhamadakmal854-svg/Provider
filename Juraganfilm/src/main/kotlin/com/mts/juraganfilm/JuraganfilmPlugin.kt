package com.mts.juraganfilm

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class JuraganfilmPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(JuraganfilmProvider())
        registerExtractorAPI(Indo666Com())
        registerExtractorAPI(FkuponCom())
        registerExtractorAPI(BioSite())
        registerExtractorAPI(Cina777Com())
        registerExtractorAPI(WapCina777Sbs())
        registerExtractorAPI(HistoryJlfafafa3Com())
        registerExtractorAPI(Bravobet77Monster())
        registerExtractorAPI(WindblueCfd())
        registerExtractorAPI(PentaslotNet())
        registerExtractorAPI(OnescreenerCom())
        registerExtractorAPI(ApkblockS3Apnortheast1AmazonawsCom())
        registerExtractorAPI(VpnnawalaSite())
        registerExtractorAPI(ApiLivechatincCom())
        registerExtractorAPI(ItunesAppleCom())
        registerExtractorAPI(DnsperfCom())
        registerExtractorAPI(MediaBioSite())
        registerExtractorAPI(MyLivechatincCom())
        registerExtractorAPI(AccountsLivechatCom())
        registerExtractorAPI(TLy())
        registerExtractorAPI(Cina1Com())
        registerExtractorAPI(WapCina777Com())
        registerExtractorAPI(Bravobet77Webns2Live())
        registerExtractorAPI(StoretnIn())
        registerExtractorAPI(LivechatCom())
        registerExtractorAPI(TextCom())
        registerExtractorAPI(PlatformTextCom())
        registerExtractorAPI(JsStripeCom())
        registerExtractorAPI(AppOnescreenerCom())
        registerExtractorAPI(Gratu89Com())
        registerExtractorAPI(Gaza88Vip())
        registerExtractorAPI(JuraganFilm())
    }
}
