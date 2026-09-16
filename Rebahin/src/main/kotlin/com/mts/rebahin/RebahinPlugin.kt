package com.mts.rebahin

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class RebahinPlugin : Plugin() {
    override fun load(context: Context) {
        // Hanya daftarkan 1 provider Rebahin tunggal (http://143.198.83.188/)
        registerMainAPI(Rebahin())
        registerExtractorAPI(MorenciusExtractor())
        registerExtractorAPI(MinochinosExtractor())
        registerExtractorAPI(AsnwishExtractor())
        registerExtractorAPI(VidHideExtractor())
        registerExtractorAPI(VidhidehubExtractor())
        registerExtractorAPI(KotakajaibMe())
        registerExtractorAPI(Playhydrax())
        registerExtractorAPI(AbyssPlayer())
        registerExtractorAPI(AbyssTo())
        registerExtractorAPI(Gdriveplayer())
        registerExtractorAPI(Emturbovid())
        registerExtractorAPI(PlaycinematicCom())
        registerExtractorAPI(EmbedpyroxXyz())
        registerExtractorAPI(MasukestinExtractor())
        registerExtractorAPI(StreamP2PExtractor())
    }
}
