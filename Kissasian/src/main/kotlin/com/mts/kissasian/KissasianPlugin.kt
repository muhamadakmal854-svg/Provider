package com.mts.kissasian

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

import com.lagradost.cloudstream3.plugins.Plugin

import android.content.Context

@CloudstreamPlugin

class KissasianPlugin : Plugin() {

    override fun load(context: Context) {

        registerMainAPI(KissasianProvider())

    }

}

