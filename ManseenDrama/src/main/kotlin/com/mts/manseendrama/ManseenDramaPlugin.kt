package com.mts.manseendrama

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

import com.lagradost.cloudstream3.plugins.Plugin

import android.content.Context

@CloudstreamPlugin

class ManseenDramaPlugin : Plugin() {

    override fun load(context: Context) {

        registerMainAPI(ManseenDramaProvider())

    }

}

