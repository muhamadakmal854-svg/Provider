package com.mts.anichin

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AnichinPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnichinProvider())
        // ── Dailymotion ───────────────────────────────────────────────
        registerExtractorAPI(Dailymotion())
        registerExtractorAPI(Geodailymotion())
        registerExtractorAPI(AnichinPlayerWrapper())   // anichin-player.web.id wrapper
        // ── OK.ru ──────────────────────────────────────────────────
        registerExtractorAPI(Odnoklassniki())
        registerExtractorAPI(OkRuSSL())
        registerExtractorAPI(OkRuHTTP())
        // ── Rumble ────────────────────────────────────────────────
        registerExtractorAPI(Rumble())
        // ── StreamRuby ────────────────────────────────────────────
        registerExtractorAPI(StreamRuby())
        registerExtractorAPI(svilla())
        registerExtractorAPI(svanila())
        // ── RPMShare ──────────────────────────────────────────────
        registerExtractorAPI(RPMShare())
        registerExtractorAPI(RPMShareEndstar())
        // ── EarnVids ──────────────────────────────────────────────
        registerExtractorAPI(EarnVids())
        registerExtractorAPI(EarnVidsMorencius())
        // ── Vidhide / Smoothpre ──────────────────────────────────
        registerExtractorAPI(Smoothpre())
        registerExtractorAPI(VidHidePro())
        registerExtractorAPI(VidHideHub())
        registerExtractorAPI(VidHidePro1())
        registerExtractorAPI(VidHidePro2())
        registerExtractorAPI(VidHidePro3())
        registerExtractorAPI(VidHidePro4())
        registerExtractorAPI(VidHidePro5())
        registerExtractorAPI(VidHidePro6())
        // ── AbyssPlayer / New Player ──────────────────────────────
        registerExtractorAPI(AbyssPlayer())
        // ── Play4Me ────────────────────────────────────────────────
        registerExtractorAPI(Play4Me())
        registerExtractorAPI(Play4MeEndstar())
        // ── Vidguard ──────────────────────────────────────────────
        registerExtractorAPI(Vidguardto())
        registerExtractorAPI(Vidguardto1())
        registerExtractorAPI(Vidguardto2())
        registerExtractorAPI(Vidguardto3())
    }
}
