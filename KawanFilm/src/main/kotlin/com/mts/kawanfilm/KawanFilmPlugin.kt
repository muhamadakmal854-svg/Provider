package com.mts.kawanfilm

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class KawanFilmPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(KawanFilmProvider())
        registerExtractorAPI(RebrandLy())
        registerExtractorAPI(Bioskopgratis21Top())
        registerExtractorAPI(Blogger())
        registerExtractorAPI(Ransplaytualan())
        registerExtractorAPI(WebKawanfilm21Co())
        registerExtractorAPI(TambakbetVip())
        registerExtractorAPI(StyleMixlinkTop())
        registerExtractorAPI(TrustText())
        registerExtractorAPI(AccountsLivechat())
        registerExtractorAPI(CdnMixlinkTop())
        registerExtractorAPI(AixassetS3Apsoutheast1Amazonaws())
        registerExtractorAPI(IYtimg())
        registerExtractorAPI(YoutuBe())
        registerExtractorAPI(RPMVid())
        registerExtractorAPI(STRP2P())
        registerExtractorAPI(Abyssplayer())
        registerExtractorAPI(Morencius())
        registerExtractorAPI(EarnVids())
        registerExtractorAPI(RPMShare())
        registerExtractorAPI(EmbedpyroxXyz())
        registerExtractorAPI(MorenciusCom())
        registerExtractorAPI(AbyssplayerCom())
        registerExtractorAPI(RpmPlayShare())
        registerExtractorAPI(Embed4MePlay())
        registerExtractorAPI(GoogleVideo())
    }
}
