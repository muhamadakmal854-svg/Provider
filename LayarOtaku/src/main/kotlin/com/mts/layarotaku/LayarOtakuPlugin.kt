package com.mts.layarotaku

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

import com.lagradost.cloudstream3.plugins.Plugin

import android.content.Context

@CloudstreamPlugin

class LayarOtakuPlugin : Plugin() {

    override fun load(context: Context) {

        registerMainAPI(LayarOtakuProvider())
        registerExtractorAPI(AssetLayarotakuId())
        registerExtractorAPI(CdnBaymnOrg())
        registerExtractorAPI(LumendatabaseOrg())
        registerExtractorAPI(CommunityNginxOrg())
        registerExtractorAPI(F5Com())
        registerExtractorAPI(CookiedatabaseOrg())
        registerExtractorAPI(Server1LayarotakuId())
        registerExtractorAPI(Server2LayarotakuId())
        registerExtractorAPI(TecharoLol())
        registerExtractorAPI(BskySocial())
        registerExtractorAPI(AtprotoCom())
        registerExtractorAPI(StrmupTo())

    }

}

