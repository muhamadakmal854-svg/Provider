package com.mts.rtmklik

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

import com.lagradost.cloudstream3.plugins.Plugin

import android.content.Context

@CloudstreamPlugin

class RTMKlikPlugin : Plugin() {

    override fun load(context: Context) {

        registerMainAPI(RTMKlikProvider())

    }

}

