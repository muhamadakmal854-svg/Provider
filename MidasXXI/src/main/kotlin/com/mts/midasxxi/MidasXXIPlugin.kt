package com.mts.midasxxi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

import com.lagradost.cloudstream3.plugins.Plugin

import android.content.Context

@CloudstreamPlugin

class MidasXXIPlugin : Plugin() {

    override fun load(context: Context) {

        registerMainAPI(MidasXXIProvider())
        registerExtractorAPI(PlaycinematicCom())

    }

}

