package com.sad25kag.drakorkita

import com.lagradost.cloudstream3.extractors.VidStack

class StbP2P : VidStack() {
    override var mainUrl = "https://stb.strp2p.com"
    override var name = "STBP2P"
}

class Playerupnone : VidStack() {
    override var mainUrl = "https://player.upn.one"
    override var name = "UPNP2P"
}

class FastdlP2P : VidStack() {
    override var mainUrl = "https://fastdl.p2pstream.online"
    override var name = "FastDLP2P"
}

class P2PStreamOnline : VidStack() {
    override var mainUrl = "https://p2pstream.online"
    override var name = "P2PStream"
}

class Strp2pCom : VidStack() {
    override var mainUrl = "https://strp2p.com"
    override var name = "STRP2P"
}

class UpnOneCom : VidStack() {
    override var mainUrl = "https://upn.one"
    override var name = "UPNOne"
}

class DrakorKitaStream : VidStack() {
    override var mainUrl = "https://drakorkita.stream"
    override var name = "DrakorKitaP2P"
}
