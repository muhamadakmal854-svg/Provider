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
        // ── Vidhide / Smoothpre / EarnVids ────────────────────────
        registerExtractorAPI(EarnVids())
        registerExtractorAPI(EarnVidsMorencius())
        registerExtractorAPI(Smoothpre())
        // ── AbyssPlayer / New Player ──────────────────────────────
        registerExtractorAPI(AbyssPlayer())
    }
}
