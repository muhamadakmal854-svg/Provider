package com.mts.animekhor

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

import com.lagradost.cloudstream3.plugins.Plugin

import android.content.Context

@CloudstreamPlugin

class AnimeKhorPlugin : Plugin() {

    override fun load(context: Context) {

        registerMainAPI(AnimeKhorProvider())
        registerExtractorAPI(BunnycdnSe())
        registerExtractorAPI(JodwishCom())
        registerExtractorAPI(VidhidevipCom())
        registerExtractorAPI(HelpDoodstreamCom())
        registerExtractorAPI(D1F05Vr3Sjsuy7CloudfrontNet())
        registerExtractorAPI(RyderjetCom())
        registerExtractorAPI(DailymotionCom())
        registerExtractorAPI(OkRu())
        registerExtractorAPI(RumbleCom())
        registerExtractorAPI(FlaswishCom())
        registerExtractorAPI(Mp4UploadCom())
        registerExtractorAPI(AbyssplayerCom())
        registerExtractorAPI(SblonaCom())
        registerExtractorAPI(FembedCom())
        registerExtractorAPI(BloggerCom())
        registerExtractorAPI(VipdownloaderxyzBlogspotCom())
        registerExtractorAPI(EmturbovidCom())
        registerExtractorAPI(AnimekhorUpnsLive())
        registerExtractorAPI(ListeamedNet())
        registerExtractorAPI(TurbovidhlsCom())
        registerExtractorAPI(BysekozeCom())
        registerExtractorAPI(PlayAbyssplayerCom())
        registerExtractorAPI(RubyvidhubCom())
        registerExtractorAPI(GoogleVideo())

    }

}

