package com.mtsflix.oploverz

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

import com.lagradost.cloudstream3.plugins.Plugin

import android.content.Context

@CloudstreamPlugin

class OploverzPlugin : Plugin() {

    override fun load(context: Context) {

        registerMainAPI(OploverzProvider())
        registerExtractorAPI(BloggerCom())
        registerExtractorAPI(GoogleVideo())

    }

}

