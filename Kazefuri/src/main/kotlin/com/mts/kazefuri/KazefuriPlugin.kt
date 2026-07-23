package com.mts.kazefuri

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

import com.lagradost.cloudstream3.plugins.Plugin

import android.content.Context

@CloudstreamPlugin

class KazefuriPlugin : Plugin() {

    override fun load(context: Context) {

        registerMainAPI(KazefuriProvider())
        registerExtractorAPI(Emas5000Gold())
        registerExtractorAPI(BrightMoonlightcodexUs())
        registerExtractorAPI(FilesSitestaticNet())
        registerExtractorAPI(QqsawerBiz())
        registerExtractorAPI(StoretnIn())
        registerExtractorAPI(Server7KucingNet())
        registerExtractorAPI(Vip69MecdnXyz())

    }

}

