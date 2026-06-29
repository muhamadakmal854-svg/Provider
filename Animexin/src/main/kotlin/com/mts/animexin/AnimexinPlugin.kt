package com.mts.animexin

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnimexinPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimexinProvider())
        registerExtractorAPI(AhCuneiblabbedCyou())
        registerExtractorAPI(GeoDailymotionCom())
        registerExtractorAPI(MirroredTo())
        registerExtractorAPI(RedditCom())
        registerExtractorAPI(CdnWuxiaworldCom())
        registerExtractorAPI(ForumWuxiaworldCom())
        registerExtractorAPI(MerchWuxiaworldCom())
        registerExtractorAPI(AppleCom())
        registerExtractorAPI(CareerspageCom())
        registerExtractorAPI(StripeCom())
        registerExtractorAPI(AppleComCn())
        registerExtractorAPI(SupportAppleCom())
        registerExtractorAPI(TvAppleCom())
        registerExtractorAPI(FilemakerCom())
        registerExtractorAPI(YoutuBe())
        registerExtractorAPI(AmTeindpumpageCfd())
    }
}
