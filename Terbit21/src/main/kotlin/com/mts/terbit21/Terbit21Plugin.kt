package com.mts.terbit21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Terbit21Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Terbit21Provider())
        registerExtractorAPI(Sf21VidplayerLive())
        registerExtractorAPI(Sf21RpmvidCom())
        registerExtractorAPI(New13Savefilm21InfoCom())
        registerExtractorAPI(S3Bk21Net())
        registerExtractorAPI(FileditchfilesMe())
        registerExtractorAPI(EmbedpyroxXyz())
        registerExtractorAPI(MorenciusCom())
        registerExtractorAPI(AbyssplayerCom())
        registerExtractorAPI(RpmPlayShare())
        registerExtractorAPI(Embed4MePlay())
        registerExtractorAPI(GoogleVideo())
    }
}
