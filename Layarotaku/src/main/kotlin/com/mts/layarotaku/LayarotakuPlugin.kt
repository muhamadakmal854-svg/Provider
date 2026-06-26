package com.mts.layarotaku

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class LayarotakuPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(LayarotakuProvider())
        registerExtractorAPI(AssetLayarotakuId())
        registerExtractorAPI(CdnBaymnOrg())
        registerExtractorAPI(LumendatabaseOrg())
        registerExtractorAPI(KubernetesNginxOrg())
        registerExtractorAPI(IYtimgCom())
        registerExtractorAPI(Nginxblog8De1046Ff5A84F2CendpointAzureedgeNet())
        registerExtractorAPI(BlogNginxOrg())
        registerExtractorAPI(CommunityNginxOrg())
        registerExtractorAPI(F5Com())
        registerExtractorAPI(CookiedatabaseOrg())
        registerExtractorAPI(Server1LayarotakuId())
        registerExtractorAPI(Server2LayarotakuId())
        registerExtractorAPI(TecharoLol())
        registerExtractorAPI(BskySocial())
        registerExtractorAPI(WebcdnBskyApp())
        registerExtractorAPI(AtprotoCom())
    }
}
