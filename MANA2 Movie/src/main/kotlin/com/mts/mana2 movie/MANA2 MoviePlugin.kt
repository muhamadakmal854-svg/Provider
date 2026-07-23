package com.mts.mana2 movie

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

import com.lagradost.cloudstream3.plugins.Plugin

import android.content.Context

@CloudstreamPlugin

class MANA2 MoviePlugin : Plugin() {

    override fun load(context: Context) {

        registerMainAPI(MANA2 MovieProvider())

    }

}

