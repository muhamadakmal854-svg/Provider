package com.mts.donghuafilm

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

import com.lagradost.cloudstream3.plugins.Plugin

import android.content.Context

@CloudstreamPlugin

class DonghuaFilmPlugin : Plugin() {

    override fun load(context: Context) {

        registerMainAPI(DonghuaFilmProvider())

    }

}

