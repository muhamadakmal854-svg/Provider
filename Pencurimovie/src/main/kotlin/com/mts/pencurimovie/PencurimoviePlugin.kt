package com.mts.pencurimovie

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class PencurimoviePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(PencurimovieProvider())
        registerExtractorAPI(PushsdkNet())
        registerExtractorAPI(ApiSubsourceNet())
        registerExtractorAPI(CdnSubsourceNet())
        registerExtractorAPI(InsignificantconstantCom())
        registerExtractorAPI(IDoodcdnIo())
        registerExtractorAPI(DoimgNet())
        registerExtractorAPI(HelpDoodstreamCom())
        registerExtractorAPI(Wws306LCloudatacdnCom())
        registerExtractorAPI(Static2DoodcdnIo())
        registerExtractorAPI(HgcloudTo())
        registerExtractorAPI(VoeSx())
        registerExtractorAPI(StreamtapeCom())
        registerExtractorAPI(MinochinosCom())
        registerExtractorAPI(SubsourceNet())
        registerExtractorAPI(AnchurlCom())
        registerExtractorAPI(CertakerInfo())
        registerExtractorAPI(UploadWikimediaOrg())
        registerExtractorAPI(Undefined())
        registerExtractorAPI(OsLiplesssaligotCom())
        registerExtractorAPI(HglinkTo())
        registerExtractorAPI(ListeamedNet())
        registerExtractorAPI(BigwarpPro())
        registerExtractorAPI(Dd315OCloudatacdnCom())
        registerExtractorAPI(AbyssplayerCom())
        registerExtractorAPI(AbysscdnCom())
        registerExtractorAPI(DsvplayCom())
    }
}
