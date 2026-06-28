package com.mts.xnxx

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class XnxxPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(XnxxProvider())
        registerExtractorAPI(XnxxindiaCom())
        registerExtractorAPI(XnxxGold())
        registerExtractorAPI(EsChaturbateCom())
        registerExtractorAPI(FrChaturbateCom())
        registerExtractorAPI(ItChaturbateCom())
        registerExtractorAPI(MozillaOrg())
        registerExtractorAPI(ExtensionworkshopCom())
        registerExtractorAPI(BlogMozillaCom())
        registerExtractorAPI(DiscourseMozillacommunityOrg())
        registerExtractorAPI(MozillafoundationOrg())
        registerExtractorAPI(AppleCom())
        registerExtractorAPI(DownloadMozillaOrg())
        registerExtractorAPI(Hwcdn2AdtngCom())
        registerExtractorAPI(NutakuNet())
        registerExtractorAPI(XnxxEs())
        registerExtractorAPI(XnxxruCom())
        registerExtractorAPI(XnxxarabicCom())
    }
}
