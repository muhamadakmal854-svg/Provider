package com.mts.sarangfilm21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Sarangfilm21Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Sarangfilm21Provider())
        registerExtractorAPI(SyAkrabatableCom())
        registerExtractorAPI(SimontokBlog())
        registerExtractorAPI(TodayecoCom())
        registerExtractorAPI(PeterboroughmovesCom())
        registerExtractorAPI(MukdenpowsOrg())
        registerExtractorAPI(SpaoShortpixelAi())
        registerExtractorAPI(IyengaryogacenterCom())
        registerExtractorAPI(UseFontawesomeCom())
        registerExtractorAPI(CleantechworldOrg())
        registerExtractorAPI(IYtimgCom())
        registerExtractorAPI(YoutuBe())
    }
}
