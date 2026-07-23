package com.mtsflix.animexin

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnimexinPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimexinProvider())
        // Dailymotion
        registerExtractorAPI(DailymotionAnimexin())
        registerExtractorAPI(GeoDailymotionAnimexin())
        // OK.ru
        registerExtractorAPI(OkRuAnimexin())
        registerExtractorAPI(OkRuAnimexinSSL())
        registerExtractorAPI(OkRuAnimexinHTTP())
        // Rumble
        registerExtractorAPI(RumbleAnimexin())
        // StreamWish
        registerExtractorAPI(EmbedWishAnimexin())
        registerExtractorAPI(StreamWishAnimexin())
        registerExtractorAPI(StreamWishToAnimexin())
        // FileLion
        registerExtractorAPI(FileLionAnimexin())
        registerExtractorAPI(FilelionsLive())
        registerExtractorAPI(FilelionsCom())
        registerExtractorAPI(FilelionsTo())
        registerExtractorAPI(FilelionsOnline())
        // Dood
        registerExtractorAPI(DoodsPro())
        registerExtractorAPI(DoodStreamCom())
        registerExtractorAPI(Ds2Play())
    }
}
