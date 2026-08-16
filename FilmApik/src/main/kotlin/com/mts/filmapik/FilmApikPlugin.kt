package com.mts.filmapik

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

import com.lagradost.cloudstream3.plugins.Plugin

import android.content.Context

@CloudstreamPlugin

class FilmApikPlugin : Plugin() {

    override fun load(context: Context) {

        registerMainAPI(FilmApikProvider())

    }

}

