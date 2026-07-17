package com.mts.sarangfilm21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

import com.lagradost.cloudstream3.plugins.Plugin

import android.content.Context

@CloudstreamPlugin

class Sarangfilm21Plugin : Plugin() {

    override fun load(context: Context) {

        registerMainAPI(Sarangfilm21Provider())
        registerExtractorAPI(CvGenipspillionCom())
        registerExtractorAPI(KuyhaaMe())
        registerExtractorAPI(Edgestorewebpmed2HnhfgbgnfmfuhaZ01AzurefdNet())
        registerExtractorAPI(StoreimagesSmicrosoftCom())
        registerExtractorAPI(MicrosoftCom())

    }

}

