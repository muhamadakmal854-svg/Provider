package com.mts.mytvlive

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class MYTVLiveProvider : MainAPI() {
    override var mainUrl              = "https://mana2.my/live"
    override var name                 = "MYTVLive"
    override var lang                 = "ms"
    override val hasMainPage          = true
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(
        TvType.Live,
        TvType.Others
    )

    override val mainPage = mainPageOf(
        "https://mana2.my/live" to "Saluran TV Live"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = mutableListOf<SearchResponse>()
        items.add(newMovieSearchResponse("TV1", "https://mana2.my/live/watch?title=TV1&stream=mytvlive_api://26f16d3a-5713-4dc2-ad1c-4e0880899a13&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/26f16d3a-5713-4dc2-ad1c-4e0880899a13/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/26f16d3a-5713-4dc2-ad1c-4e0880899a13/logo.png" })
        items.add(newMovieSearchResponse("TV2", "https://mana2.my/live/watch?title=TV2&stream=mytvlive_api://659c6568-8cc8-4083-bbdc-2b7df2431f62&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/659c6568-8cc8-4083-bbdc-2b7df2431f62/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/659c6568-8cc8-4083-bbdc-2b7df2431f62/logo.png" })
        items.add(newMovieSearchResponse("TV5 ENJOY TV", "https://mana2.my/live/watch?title=TV5 ENJOY TV&stream=mytvlive_api://7e4f64fa-af73-4ed6-a285-b3c845154d08&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/7e4f64fa-af73-4ed6-a285-b3c845154d08/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/7e4f64fa-af73-4ed6-a285-b3c845154d08/logo.png" })
        items.add(newMovieSearchResponse("FREE MOVIES", "https://mana2.my/live/watch?title=FREE MOVIES&stream=mytvlive_api://02a8b5b3-8478-46d8-99c4-daa34f9c5672&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/48967da4-9597-48eb-8a8b-d91d68cad3ff/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/48967da4-9597-48eb-8a8b-d91d68cad3ff/logo.png" })
        items.add(newMovieSearchResponse("TVS", "https://mana2.my/live/watch?title=TVS&stream=mytvlive_api://c62a6f83-ebd8-4106-890d-d8b299b7a3fe&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/c62a6f83-ebd8-4106-890d-d8b299b7a3fe/logo.jpeg", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/c62a6f83-ebd8-4106-890d-d8b299b7a3fe/logo.jpeg" })
        items.add(newMovieSearchResponse("TV ALHIJRAH", "https://mana2.my/live/watch?title=TV ALHIJRAH&stream=mytvlive_api://e725be7c-615d-42bd-9399-fef5cf386ecb&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/e725be7c-615d-42bd-9399-fef5cf386ecb/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/e725be7c-615d-42bd-9399-fef5cf386ecb/logo.png" })
        items.add(newMovieSearchResponse("SUKAN+", "https://mana2.my/live/watch?title=SUKAN+&stream=mytvlive_api://f99e8ffe-7f22-4639-9d7a-497e21f24ad0&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f99e8ffe-7f22-4639-9d7a-497e21f24ad0/logo.jpeg", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f99e8ffe-7f22-4639-9d7a-497e21f24ad0/logo.jpeg" })
        items.add(newMovieSearchResponse("BERITA RTM", "https://mana2.my/live/watch?title=BERITA RTM&stream=mytvlive_api://6245eaa8-f825-4fef-b1f8-2d44fa8676e9&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/6245eaa8-f825-4fef-b1f8-2d44fa8676e9/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/6245eaa8-f825-4fef-b1f8-2d44fa8676e9/logo.png" })
        items.add(newMovieSearchResponse("TV OKEY", "https://mana2.my/live/watch?title=TV OKEY&stream=mytvlive_api://fc596cbc-6f4f-4ab8-bb60-82fb27e38089&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/fc596cbc-6f4f-4ab8-bb60-82fb27e38089/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/fc596cbc-6f4f-4ab8-bb60-82fb27e38089/logo.png" })
        items.add(newMovieSearchResponse("BERNAMA", "https://mana2.my/live/watch?title=BERNAMA&stream=mytvlive_api://1a41a91b-64c1-4feb-a119-76ebd88fed4a&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/2fc2f258-9179-414e-ba99-d700499aed50/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/2fc2f258-9179-414e-ba99-d700499aed50/logo.png" })
        items.add(newMovieSearchResponse("The Indonesia Channel", "https://mana2.my/live/watch?title=The Indonesia Channel&stream=mytvlive_api://f1d943b2-b73d-495a-a450-4dad590f750b&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f1d943b2-b73d-495a-a450-4dad590f750b/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f1d943b2-b73d-495a-a450-4dad590f750b/logo.png" })
        items.add(newMovieSearchResponse("CNA", "https://mana2.my/live/watch?title=CNA&stream=mytvlive_api://10792dcd-a26e-441f-95ec-c9c5ba965205&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/10792dcd-a26e-441f-95ec-c9c5ba965205/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/10792dcd-a26e-441f-95ec-c9c5ba965205/logo.png" })
        items.add(newMovieSearchResponse("Al JAZEERA ENGLISH HD", "https://mana2.my/live/watch?title=Al JAZEERA ENGLISH HD&stream=mytvlive_api://c8ae5728-1231-40cf-b7f1-ed6b304bd753&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f5461f91-5590-4ec5-95e4-6de834005c57/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f5461f91-5590-4ec5-95e4-6de834005c57/logo.png" })
        items.add(newMovieSearchResponse("EURONEWS", "https://mana2.my/live/watch?title=EURONEWS&stream=mytvlive_api://8e664047-dc59-43f2-b43a-f99594c75094&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/8e664047-dc59-43f2-b43a-f99594c75094/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/8e664047-dc59-43f2-b43a-f99594c75094/logo.png" })
        items.add(newMovieSearchResponse("ARIRANG", "https://mana2.my/live/watch?title=ARIRANG&stream=mytvlive_api://91a0697b-3d4c-4324-8b56-3fa5d9cba67d&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/91a0697b-3d4c-4324-8b56-3fa5d9cba67d/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/91a0697b-3d4c-4324-8b56-3fa5d9cba67d/logo.png" })
        items.add(newMovieSearchResponse("TaiwanPlus", "https://mana2.my/live/watch?title=TaiwanPlus&stream=mytvlive_api://a6de27c9-8a5d-48a7-941f-4511efc94808&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/a6de27c9-8a5d-48a7-941f-4511efc94808/logo.jpeg", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/a6de27c9-8a5d-48a7-941f-4511efc94808/logo.jpeg" })
        items.add(newMovieSearchResponse("NHK WORLD", "https://mana2.my/live/watch?title=NHK WORLD&stream=mytvlive_api://632813f5-d3a7-4e17-a225-14a61b323f32&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/632813f5-d3a7-4e17-a225-14a61b323f32/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/632813f5-d3a7-4e17-a225-14a61b323f32/logo.png" })
        items.add(newMovieSearchResponse("DW", "https://mana2.my/live/watch?title=DW&stream=mytvlive_api://3c3902e0-ae27-4043-9ca0-f308611ec32b&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/3c3902e0-ae27-4043-9ca0-f308611ec32b/logo.jpeg", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/3c3902e0-ae27-4043-9ca0-f308611ec32b/logo.jpeg" })
        items.add(newMovieSearchResponse("RT International", "https://mana2.my/live/watch?title=RT International&stream=mytvlive_api://0f03e6f4-7cbf-4f01-9e19-ce48837d3fdb&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/0f03e6f4-7cbf-4f01-9e19-ce48837d3fdb/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/0f03e6f4-7cbf-4f01-9e19-ce48837d3fdb/logo.png" })
        items.add(newMovieSearchResponse("Al JAZEERA ARABIC HD", "https://mana2.my/live/watch?title=Al JAZEERA ARABIC HD&stream=mytvlive_api://48bd4c45-e84a-40d1-8be1-0d58e9f95bf6&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/165093ed-e20e-4723-ac2e-0535d33822fe/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/165093ed-e20e-4723-ac2e-0535d33822fe/logo.png" })
        items.add(newMovieSearchResponse("USIM TV", "https://mana2.my/live/watch?title=USIM TV&stream=mytvlive_api://3d0a1579-dd79-453b-b4f6-3c861f4d35da&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/3d0a1579-dd79-453b-b4f6-3c861f4d35da/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/3d0a1579-dd79-453b-b4f6-3c861f4d35da/logo.png" })
        items.add(newMovieSearchResponse("SELANGOR TV", "https://mana2.my/live/watch?title=SELANGOR TV&stream=mytvlive_api://8c00c624-d7aa-4828-8abe-b0665ebf5772&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/8c00c624-d7aa-4828-8abe-b0665ebf5772/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/8c00c624-d7aa-4828-8abe-b0665ebf5772/logo.png" })
        items.add(newMovieSearchResponse("TVIKIM", "https://mana2.my/live/watch?title=TVIKIM&stream=mytvlive_api://6c4508d0-51c4-4a6c-9d64-7f19d0301646&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/784dbb7e-ddd3-4183-8914-1983291524a3/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/784dbb7e-ddd3-4183-8914-1983291524a3/logo.png" })
        items.add(newMovieSearchResponse("SIARA TV", "https://mana2.my/live/watch?title=SIARA TV&stream=mytvlive_api://d2b549cc-218c-4cd2-aab3-e9e479c3f24f&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/c30bcb6d-8671-4fbf-8b82-9b322c65fadb/logo.jpg", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/c30bcb6d-8671-4fbf-8b82-9b322c65fadb/logo.jpg" })
        items.add(newMovieSearchResponse("MANIS FM", "https://mana2.my/live/watch?title=MANIS FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/manisfm/playlist.m3u8?md5=CiJGmUpdUdqOkUPELba56w&expires=1784824004&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/6cb8b5c0-b4bc-4fa2-9f34-57efa0ea9cfe/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/6cb8b5c0-b4bc-4fa2-9f34-57efa0ea9cfe/logo.png" })
        items.add(newMovieSearchResponse("BORNEO TV", "https://mana2.my/live/watch?title=BORNEO TV&stream=mytvlive_api://ebb8ddd1-318a-471c-96c6-13bf7125be93&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/ebb8ddd1-318a-471c-96c6-13bf7125be93/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/ebb8ddd1-318a-471c-96c6-13bf7125be93/logo.png" })
        items.add(newMovieSearchResponse("SURIA FM", "https://mana2.my/live/watch?title=SURIA FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/suriafm/playlist.m3u8?md5=BjqtKryTUtx4SFGe47fteQ&expires=1784824004&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/7761d925-7202-44cf-851a-941306007abb/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/7761d925-7202-44cf-851a-941306007abb/logo.png" })
        items.add(newMovieSearchResponse("FLY FM", "https://mana2.my/live/watch?title=FLY FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/flyfm/playlist.m3u8?md5=t9OWJRTJ4ZxS7TqDuQsJ1A&expires=1784824004&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/11ba9ef2-34fa-468f-b75d-8248494bccb4/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/11ba9ef2-34fa-468f-b75d-8248494bccb4/logo.png" })
        items.add(newMovieSearchResponse("RAKITA FM", "https://mana2.my/live/watch?title=RAKITA FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/rakitafm/playlist.m3u8?md5=XNMWTZ1HCXEFyvKTs8-W_w&expires=1784824005&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/4304b16e-ecf3-4b00-ac69-ca613bdf0f53/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/4304b16e-ecf3-4b00-ac69-ca613bdf0f53/logo.png" })
        items.add(newMovieSearchResponse("HOT FM", "https://mana2.my/live/watch?title=HOT FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/hotfm/playlist.m3u8?md5=_JLrFwfVaOO6kaYvDVBesA&expires=1784824005&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/dc2fce66-d6a5-4ecb-96cb-6825c19afa51/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/dc2fce66-d6a5-4ecb-96cb-6825c19afa51/logo.png" })
        items.add(newMovieSearchResponse("IKIMfm", "https://mana2.my/live/watch?title=IKIMfm&stream=https://cuwf8jayq1.tenbytecdn.com/radio/ikimfm/playlist.m3u8?md5=iF46q86ZH6v02k6EnYDPaQ&expires=1784824005&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f9a05d44-7468-4cca-89f9-74dafc1518da/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f9a05d44-7468-4cca-89f9-74dafc1518da/logo.png" })
        items.add(newMovieSearchResponse("988 FM", "https://mana2.my/live/watch?title=988 FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/988fm/playlist.m3u8?md5=lBS7gKUKhBWRyiuTC_RWRA&expires=1784824005&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/88142ced-9d68-4d23-9389-bbf1fc013029/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/88142ced-9d68-4d23-9389-bbf1fc013029/logo.png" })
        items.add(newMovieSearchResponse("MOLEK FM", "https://mana2.my/live/watch?title=MOLEK FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/molekfm/playlist.m3u8?md5=Kpi1MX1wBBVQwal4Vs292Q&expires=1784824006&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/e147cf59-f9e0-4945-8ccc-a85f983a500c/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/e147cf59-f9e0-4945-8ccc-a85f983a500c/logo.png" })
        items.add(newMovieSearchResponse("EIGHT FM", "https://mana2.my/live/watch?title=EIGHT FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/eightfm/playlist.m3u8?md5=YVZBKauFTFLi5tBfig63OA&expires=1784824006&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/35375bf3-77fc-454b-ad95-07ab99a9153b/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/35375bf3-77fc-454b-ad95-07ab99a9153b/logo.png" })
        items.add(newMovieSearchResponse("KOOL FM", "https://mana2.my/live/watch?title=KOOL FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/koolfm/playlist.m3u8?md5=7VLbdWaXurV7a3vRop_7zg&expires=1784824006&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/9f2416db-418a-4bac-a9b3-039a3b82ac75/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/9f2416db-418a-4bac-a9b3-039a3b82ac75/logo.png" })
        items.add(newMovieSearchResponse("NASIONAL FM", "https://mana2.my/live/watch?title=NASIONAL FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/nasionalfm/playlist.m3u8?md5=sBfbjxPVAMfXJjV6V4GpsA&expires=1784824006&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f1dcbb38-cc79-4e04-a5a3-10e057eb64bb/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f1dcbb38-cc79-4e04-a5a3-10e057eb64bb/logo.png" })
        items.add(newMovieSearchResponse("TRAXX FM", "https://mana2.my/live/watch?title=TRAXX FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/traxxfm/playlist.m3u8?md5=g9_P2uIeQYvelPK-o6m_FA&expires=1784824006&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/e0471aaf-592c-406a-9d41-ad3160bb0985/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/e0471aaf-592c-406a-9d41-ad3160bb0985/logo.png" })
        items.add(newMovieSearchResponse("MINNAL FM", "https://mana2.my/live/watch?title=MINNAL FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/minnalfm/playlist.m3u8?md5=sOPJIZmlG-7a17xznbmrAg&expires=1784824007&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/496a28e4-5a0c-4c9a-b8f2-c2a48498bf9d/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/496a28e4-5a0c-4c9a-b8f2-c2a48498bf9d/logo.png" })
        items.add(newMovieSearchResponse("AI FM", "https://mana2.my/live/watch?title=AI FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/aifm/playlist.m3u8?md5=Qu4skiePQnv1Gr6A9i9R6w&expires=1784824007&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/0a3d39c9-ec58-47c6-8481-7c5883163c5c/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/0a3d39c9-ec58-47c6-8481-7c5883163c5c/logo.png" })
        items.add(newMovieSearchResponse("RADIO KLASIK", "https://mana2.my/live/watch?title=RADIO KLASIK&stream=https://cuwf8jayq1.tenbytecdn.com/radio/radioklasik/playlist.m3u8?md5=WEyNw8OUrA4w6dzFVx4E6w&expires=1784824007&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/fb1f3e1e-7675-4469-93fe-f01482f4c448/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/fb1f3e1e-7675-4469-93fe-f01482f4c448/logo.png" })
        items.add(newMovieSearchResponse("SABAH FM", "https://mana2.my/live/watch?title=SABAH FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/sabahfm/playlist.m3u8?md5=bGPI8feTDiXTec3xgcp-TA&expires=1784824007&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/00274ca1-53a9-4bd9-bbef-460b46df228e/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/00274ca1-53a9-4bd9-bbef-460b46df228e/logo.png" })
        items.add(newMovieSearchResponse("SABAHV FM", "https://mana2.my/live/watch?title=SABAHV FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/sabahvfm/playlist.m3u8?md5=xlDBrFltQ6nHCS4KkyCX6w&expires=1784824008&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/575a4646-6c41-4620-b4a7-8b4110f82e07/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/575a4646-6c41-4620-b4a7-8b4110f82e07/logo.png" })
        items.add(newMovieSearchResponse("SARAWAK FM", "https://mana2.my/live/watch?title=SARAWAK FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/sarawakfm/playlist.m3u8?md5=vV7SrEWCzm_0pVvEedIx3w&expires=1784824008&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/d0de4a99-396c-4a4a-bde2-f4d642435878/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/d0de4a99-396c-4a4a-bde2-f4d642435878/logo.png" })
        items.add(newMovieSearchResponse("BERNAMA RADIO", "https://mana2.my/live/watch?title=BERNAMA RADIO&stream=https://cuwf8jayq1.tenbytecdn.com/radio/bernamaradio/playlist.m3u8?md5=gXe9bpD9VMcFaXNpR2dAoA&expires=1784824008&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/97320e8a-8443-49d1-b47d-144aec29794d/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/97320e8a-8443-49d1-b47d-144aec29794d/logo.png" })
        items.add(newMovieSearchResponse("WAI FM", "https://mana2.my/live/watch?title=WAI FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/waifm/playlist.m3u8?md5=GI7fQ_FPEQlXcquLS2FMOg&expires=1784824008&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/5d830a07-aa2e-4f4a-9577-9640c7c8628b/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/5d830a07-aa2e-4f4a-9577-9640c7c8628b/logo.png" })
        items.add(newMovieSearchResponse("ASYIK FM", "https://mana2.my/live/watch?title=ASYIK FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/asyikfm/playlist.m3u8?md5=Uo_vX3xvz-6sevDwV7_PLg&expires=1784824008&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/7bad759d-8124-4318-b455-07cbcbe66c5c/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/7bad759d-8124-4318-b455-07cbcbe66c5c/logo.png" })
        items.add(newMovieSearchResponse("BEST FM", "https://mana2.my/live/watch?title=BEST FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/bestfm/playlist.m3u8?md5=6IyToABs0JAbxE63t4WbcA&expires=1784824009&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/b9ae6780-dda4-4e65-9c84-e84237759f4b/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/b9ae6780-dda4-4e65-9c84-e84237759f4b/logo.png" })
        return newHomePageResponse(request.name, items, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val items = mutableListOf<SearchResponse>()
        items.add(newMovieSearchResponse("TV1", "https://mana2.my/live/watch?title=TV1&stream=mytvlive_api://26f16d3a-5713-4dc2-ad1c-4e0880899a13&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/26f16d3a-5713-4dc2-ad1c-4e0880899a13/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/26f16d3a-5713-4dc2-ad1c-4e0880899a13/logo.png" })
        items.add(newMovieSearchResponse("TV2", "https://mana2.my/live/watch?title=TV2&stream=mytvlive_api://659c6568-8cc8-4083-bbdc-2b7df2431f62&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/659c6568-8cc8-4083-bbdc-2b7df2431f62/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/659c6568-8cc8-4083-bbdc-2b7df2431f62/logo.png" })
        items.add(newMovieSearchResponse("TV5 ENJOY TV", "https://mana2.my/live/watch?title=TV5 ENJOY TV&stream=mytvlive_api://7e4f64fa-af73-4ed6-a285-b3c845154d08&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/7e4f64fa-af73-4ed6-a285-b3c845154d08/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/7e4f64fa-af73-4ed6-a285-b3c845154d08/logo.png" })
        items.add(newMovieSearchResponse("FREE MOVIES", "https://mana2.my/live/watch?title=FREE MOVIES&stream=mytvlive_api://02a8b5b3-8478-46d8-99c4-daa34f9c5672&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/48967da4-9597-48eb-8a8b-d91d68cad3ff/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/48967da4-9597-48eb-8a8b-d91d68cad3ff/logo.png" })
        items.add(newMovieSearchResponse("TVS", "https://mana2.my/live/watch?title=TVS&stream=mytvlive_api://c62a6f83-ebd8-4106-890d-d8b299b7a3fe&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/c62a6f83-ebd8-4106-890d-d8b299b7a3fe/logo.jpeg", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/c62a6f83-ebd8-4106-890d-d8b299b7a3fe/logo.jpeg" })
        items.add(newMovieSearchResponse("TV ALHIJRAH", "https://mana2.my/live/watch?title=TV ALHIJRAH&stream=mytvlive_api://e725be7c-615d-42bd-9399-fef5cf386ecb&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/e725be7c-615d-42bd-9399-fef5cf386ecb/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/e725be7c-615d-42bd-9399-fef5cf386ecb/logo.png" })
        items.add(newMovieSearchResponse("SUKAN+", "https://mana2.my/live/watch?title=SUKAN+&stream=mytvlive_api://f99e8ffe-7f22-4639-9d7a-497e21f24ad0&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f99e8ffe-7f22-4639-9d7a-497e21f24ad0/logo.jpeg", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f99e8ffe-7f22-4639-9d7a-497e21f24ad0/logo.jpeg" })
        items.add(newMovieSearchResponse("BERITA RTM", "https://mana2.my/live/watch?title=BERITA RTM&stream=mytvlive_api://6245eaa8-f825-4fef-b1f8-2d44fa8676e9&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/6245eaa8-f825-4fef-b1f8-2d44fa8676e9/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/6245eaa8-f825-4fef-b1f8-2d44fa8676e9/logo.png" })
        items.add(newMovieSearchResponse("TV OKEY", "https://mana2.my/live/watch?title=TV OKEY&stream=mytvlive_api://fc596cbc-6f4f-4ab8-bb60-82fb27e38089&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/fc596cbc-6f4f-4ab8-bb60-82fb27e38089/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/fc596cbc-6f4f-4ab8-bb60-82fb27e38089/logo.png" })
        items.add(newMovieSearchResponse("BERNAMA", "https://mana2.my/live/watch?title=BERNAMA&stream=mytvlive_api://1a41a91b-64c1-4feb-a119-76ebd88fed4a&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/2fc2f258-9179-414e-ba99-d700499aed50/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/2fc2f258-9179-414e-ba99-d700499aed50/logo.png" })
        items.add(newMovieSearchResponse("The Indonesia Channel", "https://mana2.my/live/watch?title=The Indonesia Channel&stream=mytvlive_api://f1d943b2-b73d-495a-a450-4dad590f750b&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f1d943b2-b73d-495a-a450-4dad590f750b/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f1d943b2-b73d-495a-a450-4dad590f750b/logo.png" })
        items.add(newMovieSearchResponse("CNA", "https://mana2.my/live/watch?title=CNA&stream=mytvlive_api://10792dcd-a26e-441f-95ec-c9c5ba965205&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/10792dcd-a26e-441f-95ec-c9c5ba965205/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/10792dcd-a26e-441f-95ec-c9c5ba965205/logo.png" })
        items.add(newMovieSearchResponse("Al JAZEERA ENGLISH HD", "https://mana2.my/live/watch?title=Al JAZEERA ENGLISH HD&stream=mytvlive_api://c8ae5728-1231-40cf-b7f1-ed6b304bd753&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f5461f91-5590-4ec5-95e4-6de834005c57/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f5461f91-5590-4ec5-95e4-6de834005c57/logo.png" })
        items.add(newMovieSearchResponse("EURONEWS", "https://mana2.my/live/watch?title=EURONEWS&stream=mytvlive_api://8e664047-dc59-43f2-b43a-f99594c75094&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/8e664047-dc59-43f2-b43a-f99594c75094/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/8e664047-dc59-43f2-b43a-f99594c75094/logo.png" })
        items.add(newMovieSearchResponse("ARIRANG", "https://mana2.my/live/watch?title=ARIRANG&stream=mytvlive_api://91a0697b-3d4c-4324-8b56-3fa5d9cba67d&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/91a0697b-3d4c-4324-8b56-3fa5d9cba67d/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/91a0697b-3d4c-4324-8b56-3fa5d9cba67d/logo.png" })
        items.add(newMovieSearchResponse("TaiwanPlus", "https://mana2.my/live/watch?title=TaiwanPlus&stream=mytvlive_api://a6de27c9-8a5d-48a7-941f-4511efc94808&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/a6de27c9-8a5d-48a7-941f-4511efc94808/logo.jpeg", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/a6de27c9-8a5d-48a7-941f-4511efc94808/logo.jpeg" })
        items.add(newMovieSearchResponse("NHK WORLD", "https://mana2.my/live/watch?title=NHK WORLD&stream=mytvlive_api://632813f5-d3a7-4e17-a225-14a61b323f32&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/632813f5-d3a7-4e17-a225-14a61b323f32/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/632813f5-d3a7-4e17-a225-14a61b323f32/logo.png" })
        items.add(newMovieSearchResponse("DW", "https://mana2.my/live/watch?title=DW&stream=mytvlive_api://3c3902e0-ae27-4043-9ca0-f308611ec32b&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/3c3902e0-ae27-4043-9ca0-f308611ec32b/logo.jpeg", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/3c3902e0-ae27-4043-9ca0-f308611ec32b/logo.jpeg" })
        items.add(newMovieSearchResponse("RT International", "https://mana2.my/live/watch?title=RT International&stream=mytvlive_api://0f03e6f4-7cbf-4f01-9e19-ce48837d3fdb&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/0f03e6f4-7cbf-4f01-9e19-ce48837d3fdb/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/0f03e6f4-7cbf-4f01-9e19-ce48837d3fdb/logo.png" })
        items.add(newMovieSearchResponse("Al JAZEERA ARABIC HD", "https://mana2.my/live/watch?title=Al JAZEERA ARABIC HD&stream=mytvlive_api://48bd4c45-e84a-40d1-8be1-0d58e9f95bf6&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/165093ed-e20e-4723-ac2e-0535d33822fe/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/165093ed-e20e-4723-ac2e-0535d33822fe/logo.png" })
        items.add(newMovieSearchResponse("USIM TV", "https://mana2.my/live/watch?title=USIM TV&stream=mytvlive_api://3d0a1579-dd79-453b-b4f6-3c861f4d35da&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/3d0a1579-dd79-453b-b4f6-3c861f4d35da/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/3d0a1579-dd79-453b-b4f6-3c861f4d35da/logo.png" })
        items.add(newMovieSearchResponse("SELANGOR TV", "https://mana2.my/live/watch?title=SELANGOR TV&stream=mytvlive_api://8c00c624-d7aa-4828-8abe-b0665ebf5772&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/8c00c624-d7aa-4828-8abe-b0665ebf5772/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/8c00c624-d7aa-4828-8abe-b0665ebf5772/logo.png" })
        items.add(newMovieSearchResponse("TVIKIM", "https://mana2.my/live/watch?title=TVIKIM&stream=mytvlive_api://6c4508d0-51c4-4a6c-9d64-7f19d0301646&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/784dbb7e-ddd3-4183-8914-1983291524a3/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/784dbb7e-ddd3-4183-8914-1983291524a3/logo.png" })
        items.add(newMovieSearchResponse("SIARA TV", "https://mana2.my/live/watch?title=SIARA TV&stream=mytvlive_api://d2b549cc-218c-4cd2-aab3-e9e479c3f24f&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/c30bcb6d-8671-4fbf-8b82-9b322c65fadb/logo.jpg", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/c30bcb6d-8671-4fbf-8b82-9b322c65fadb/logo.jpg" })
        items.add(newMovieSearchResponse("MANIS FM", "https://mana2.my/live/watch?title=MANIS FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/manisfm/playlist.m3u8?md5=CiJGmUpdUdqOkUPELba56w&expires=1784824004&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/6cb8b5c0-b4bc-4fa2-9f34-57efa0ea9cfe/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/6cb8b5c0-b4bc-4fa2-9f34-57efa0ea9cfe/logo.png" })
        items.add(newMovieSearchResponse("BORNEO TV", "https://mana2.my/live/watch?title=BORNEO TV&stream=mytvlive_api://ebb8ddd1-318a-471c-96c6-13bf7125be93&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/ebb8ddd1-318a-471c-96c6-13bf7125be93/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/ebb8ddd1-318a-471c-96c6-13bf7125be93/logo.png" })
        items.add(newMovieSearchResponse("SURIA FM", "https://mana2.my/live/watch?title=SURIA FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/suriafm/playlist.m3u8?md5=BjqtKryTUtx4SFGe47fteQ&expires=1784824004&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/7761d925-7202-44cf-851a-941306007abb/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/7761d925-7202-44cf-851a-941306007abb/logo.png" })
        items.add(newMovieSearchResponse("FLY FM", "https://mana2.my/live/watch?title=FLY FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/flyfm/playlist.m3u8?md5=t9OWJRTJ4ZxS7TqDuQsJ1A&expires=1784824004&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/11ba9ef2-34fa-468f-b75d-8248494bccb4/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/11ba9ef2-34fa-468f-b75d-8248494bccb4/logo.png" })
        items.add(newMovieSearchResponse("RAKITA FM", "https://mana2.my/live/watch?title=RAKITA FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/rakitafm/playlist.m3u8?md5=XNMWTZ1HCXEFyvKTs8-W_w&expires=1784824005&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/4304b16e-ecf3-4b00-ac69-ca613bdf0f53/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/4304b16e-ecf3-4b00-ac69-ca613bdf0f53/logo.png" })
        items.add(newMovieSearchResponse("HOT FM", "https://mana2.my/live/watch?title=HOT FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/hotfm/playlist.m3u8?md5=_JLrFwfVaOO6kaYvDVBesA&expires=1784824005&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/dc2fce66-d6a5-4ecb-96cb-6825c19afa51/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/dc2fce66-d6a5-4ecb-96cb-6825c19afa51/logo.png" })
        items.add(newMovieSearchResponse("IKIMfm", "https://mana2.my/live/watch?title=IKIMfm&stream=https://cuwf8jayq1.tenbytecdn.com/radio/ikimfm/playlist.m3u8?md5=iF46q86ZH6v02k6EnYDPaQ&expires=1784824005&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f9a05d44-7468-4cca-89f9-74dafc1518da/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f9a05d44-7468-4cca-89f9-74dafc1518da/logo.png" })
        items.add(newMovieSearchResponse("988 FM", "https://mana2.my/live/watch?title=988 FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/988fm/playlist.m3u8?md5=lBS7gKUKhBWRyiuTC_RWRA&expires=1784824005&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/88142ced-9d68-4d23-9389-bbf1fc013029/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/88142ced-9d68-4d23-9389-bbf1fc013029/logo.png" })
        items.add(newMovieSearchResponse("MOLEK FM", "https://mana2.my/live/watch?title=MOLEK FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/molekfm/playlist.m3u8?md5=Kpi1MX1wBBVQwal4Vs292Q&expires=1784824006&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/e147cf59-f9e0-4945-8ccc-a85f983a500c/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/e147cf59-f9e0-4945-8ccc-a85f983a500c/logo.png" })
        items.add(newMovieSearchResponse("EIGHT FM", "https://mana2.my/live/watch?title=EIGHT FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/eightfm/playlist.m3u8?md5=YVZBKauFTFLi5tBfig63OA&expires=1784824006&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/35375bf3-77fc-454b-ad95-07ab99a9153b/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/35375bf3-77fc-454b-ad95-07ab99a9153b/logo.png" })
        items.add(newMovieSearchResponse("KOOL FM", "https://mana2.my/live/watch?title=KOOL FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/koolfm/playlist.m3u8?md5=7VLbdWaXurV7a3vRop_7zg&expires=1784824006&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/9f2416db-418a-4bac-a9b3-039a3b82ac75/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/9f2416db-418a-4bac-a9b3-039a3b82ac75/logo.png" })
        items.add(newMovieSearchResponse("NASIONAL FM", "https://mana2.my/live/watch?title=NASIONAL FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/nasionalfm/playlist.m3u8?md5=sBfbjxPVAMfXJjV6V4GpsA&expires=1784824006&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f1dcbb38-cc79-4e04-a5a3-10e057eb64bb/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/f1dcbb38-cc79-4e04-a5a3-10e057eb64bb/logo.png" })
        items.add(newMovieSearchResponse("TRAXX FM", "https://mana2.my/live/watch?title=TRAXX FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/traxxfm/playlist.m3u8?md5=g9_P2uIeQYvelPK-o6m_FA&expires=1784824006&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/e0471aaf-592c-406a-9d41-ad3160bb0985/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/e0471aaf-592c-406a-9d41-ad3160bb0985/logo.png" })
        items.add(newMovieSearchResponse("MINNAL FM", "https://mana2.my/live/watch?title=MINNAL FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/minnalfm/playlist.m3u8?md5=sOPJIZmlG-7a17xznbmrAg&expires=1784824007&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/496a28e4-5a0c-4c9a-b8f2-c2a48498bf9d/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/496a28e4-5a0c-4c9a-b8f2-c2a48498bf9d/logo.png" })
        items.add(newMovieSearchResponse("AI FM", "https://mana2.my/live/watch?title=AI FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/aifm/playlist.m3u8?md5=Qu4skiePQnv1Gr6A9i9R6w&expires=1784824007&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/0a3d39c9-ec58-47c6-8481-7c5883163c5c/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/0a3d39c9-ec58-47c6-8481-7c5883163c5c/logo.png" })
        items.add(newMovieSearchResponse("RADIO KLASIK", "https://mana2.my/live/watch?title=RADIO KLASIK&stream=https://cuwf8jayq1.tenbytecdn.com/radio/radioklasik/playlist.m3u8?md5=WEyNw8OUrA4w6dzFVx4E6w&expires=1784824007&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/fb1f3e1e-7675-4469-93fe-f01482f4c448/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/fb1f3e1e-7675-4469-93fe-f01482f4c448/logo.png" })
        items.add(newMovieSearchResponse("SABAH FM", "https://mana2.my/live/watch?title=SABAH FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/sabahfm/playlist.m3u8?md5=bGPI8feTDiXTec3xgcp-TA&expires=1784824007&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/00274ca1-53a9-4bd9-bbef-460b46df228e/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/00274ca1-53a9-4bd9-bbef-460b46df228e/logo.png" })
        items.add(newMovieSearchResponse("SABAHV FM", "https://mana2.my/live/watch?title=SABAHV FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/sabahvfm/playlist.m3u8?md5=xlDBrFltQ6nHCS4KkyCX6w&expires=1784824008&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/575a4646-6c41-4620-b4a7-8b4110f82e07/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/575a4646-6c41-4620-b4a7-8b4110f82e07/logo.png" })
        items.add(newMovieSearchResponse("SARAWAK FM", "https://mana2.my/live/watch?title=SARAWAK FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/sarawakfm/playlist.m3u8?md5=vV7SrEWCzm_0pVvEedIx3w&expires=1784824008&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/d0de4a99-396c-4a4a-bde2-f4d642435878/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/d0de4a99-396c-4a4a-bde2-f4d642435878/logo.png" })
        items.add(newMovieSearchResponse("BERNAMA RADIO", "https://mana2.my/live/watch?title=BERNAMA RADIO&stream=https://cuwf8jayq1.tenbytecdn.com/radio/bernamaradio/playlist.m3u8?md5=gXe9bpD9VMcFaXNpR2dAoA&expires=1784824008&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/97320e8a-8443-49d1-b47d-144aec29794d/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/97320e8a-8443-49d1-b47d-144aec29794d/logo.png" })
        items.add(newMovieSearchResponse("WAI FM", "https://mana2.my/live/watch?title=WAI FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/waifm/playlist.m3u8?md5=GI7fQ_FPEQlXcquLS2FMOg&expires=1784824008&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/5d830a07-aa2e-4f4a-9577-9640c7c8628b/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/5d830a07-aa2e-4f4a-9577-9640c7c8628b/logo.png" })
        items.add(newMovieSearchResponse("ASYIK FM", "https://mana2.my/live/watch?title=ASYIK FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/asyikfm/playlist.m3u8?md5=Uo_vX3xvz-6sevDwV7_PLg&expires=1784824008&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/7bad759d-8124-4318-b455-07cbcbe66c5c/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/7bad759d-8124-4318-b455-07cbcbe66c5c/logo.png" })
        items.add(newMovieSearchResponse("BEST FM", "https://mana2.my/live/watch?title=BEST FM&stream=https://cuwf8jayq1.tenbytecdn.com/radio/bestfm/playlist.m3u8?md5=6IyToABs0JAbxE63t4WbcA&expires=1784824009&logo=https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/b9ae6780-dda4-4e65-9c84-e84237759f4b/logo.png", TvType.Live) { this.posterUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/b9ae6780-dda4-4e65-9c84-e84237759f4b/logo.png" })
        return items.filter { it.name.contains(query, ignoreCase = true) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val title = if (url.contains("title=")) url.substringAfter("title=").substringBefore("&") else name
        val streamUrl = if (url.contains("stream=")) url.substringAfter("stream=").substringBefore("&") else url
        val logo = if (url.contains("logo=")) url.substringAfter("logo=").substringBefore("&") else ""

        val cleanTitle = java.net.URLDecoder.decode(title, "UTF-8")
        val cleanStream = java.net.URLDecoder.decode(streamUrl, "UTF-8")
        val cleanLogo = java.net.URLDecoder.decode(logo, "UTF-8")

        return newMovieLoadResponse(cleanTitle, url, TvType.Live, cleanStream) {
            this.posterUrl = if (cleanLogo.isNotBlank()) cleanLogo else null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isClipped: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val cleanData = java.net.URLDecoder.decode(data, "UTF-8")

        // Handle MYTVLive dynamic API channels (mytvlive_api://channel-uuid)
        if (cleanData.startsWith("mytvlive_api://")) {
            val channelId = cleanData.removePrefix("mytvlive_api://")
            val apiUrl = "https://co3y6iwoio.tenbytecdn.com/api/v1/public/streaming/channel-play"
            val deviceId = "web-browser-device-12345"

            try {
                val respText = app.post(
                    apiUrl,
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                        "Referer" to "https://mana2.my/live"
                    ),
                    json = mapOf("channelId" to channelId, "deviceId" to deviceId)
                ).text

                val playbackUrl = if (respText.contains("playbackUrl\":\"")) {
                    respText.substringAfter("playbackUrl\":\"").substringBefore("\"")
                } else ""

                val dashUrl = if (respText.contains("dash\":\"")) {
                    respText.substringAfter("dash\":\"").substringBefore("\"")
                } else if (playbackUrl.contains("playlist.m3u8")) {
                    playbackUrl.replace("playlist.m3u8", "manifest.mpd")
                } else ""

                val hlsUrl = if (respText.contains("hls\":\"")) {
                    respText.substringAfter("hls\":\"").substringBefore("\"")
                } else playbackUrl

                val reqHeaders = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                    "Referer" to "https://mana2.my/"
                )

                if (dashUrl.isNotBlank()) {
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = "$name Live (DASH DRM-Free)",
                            url = dashUrl,
                            type = ExtractorLinkType.DASH
                        ) {
                            this.headers = reqHeaders
                            this.referer = "https://mana2.my/"
                            this.quality = Qualities.P1080.value
                        }
                    )
                }

                if (hlsUrl.isNotBlank()) {
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = "$name Live (HLS Master M3U8)",
                            url = hlsUrl,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.headers = reqHeaders
                            this.referer = "https://mana2.my/"
                            this.quality = Qualities.P720.value
                        }
                    )
                }
            } catch (e: Exception) {}
            return true
        }

        val reqHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Referer" to "https://rtmklik.rtm.gov.my/",
            "Origin" to "https://rtmklik.rtm.gov.my"
        )

        if (cleanData.contains(".m3u8") || cleanData.contains("m3u8")) {
            val dashUrl = if (cleanData.contains("/HLS/")) {
                cleanData.replace("/HLS/", "/DASH/").replace(".m3u8", ".mpd")
            } else if (cleanData.contains("playlist.m3u8")) {
                cleanData.replace("playlist.m3u8", "manifest.mpd")
            } else ""

            if (dashUrl.isNotBlank()) {
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name Live (DASH DRM-Free)",
                        url = dashUrl,
                        type = ExtractorLinkType.DASH
                    ) {
                        this.headers = reqHeaders
                        this.referer = "https://rtmklik.rtm.gov.my/"
                        this.quality = Qualities.P1080.value
                    }
                )
            }

            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "$name Live (HLS Master M3U8)",
                    url = cleanData,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.headers = reqHeaders
                    this.referer = "https://rtmklik.rtm.gov.my/"
                    this.quality = Qualities.P720.value
                }
            )

            try {
                val masterText = app.get(cleanData, headers = reqHeaders).text
                val baseDir = if (cleanData.contains("/")) cleanData.substringBeforeLast("/") + "/" else cleanData
                val subFiles = masterText.lines().filter { it.endsWith(".m3u8") || it.contains(".m3u8?") }

                subFiles.forEach { subFile ->
                    val subUrl = if (subFile.startsWith("http")) subFile else "$baseDir$subFile"
                    val label = if (subFile.contains("2500000") || subFile.contains("3000000")) "1080p"
                                else if (subFile.contains("800000") || subFile.contains("600000")) "720p"
                                else if (subFile.contains("400000")) "480p"
                                else "360p"
                    val qual = if (label == "1080p") Qualities.P1080.value
                               else if (label == "720p") Qualities.P720.value
                               else if (label == "480p") Qualities.P480.value
                               else Qualities.P360.value

                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = "$name Live (Sub-playlist $label)",
                            url = subUrl,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.headers = reqHeaders
                            this.referer = "https://rtmklik.rtm.gov.my/"
                            this.quality = qual
                        }
                    )
                }
            } catch (e: Exception) {}
        } else {
            loadExtractor(cleanData, mainUrl, subtitleCallback, callback)
        }
        return true
    }
}
