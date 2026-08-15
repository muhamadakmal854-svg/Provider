package com.mtsflix.pencurimovie

import org.jsoup.nodes.Element
import java.net.URL

fun Element?.getIframeSrc(): String? {
    if (this == null) return null
    val candidates = listOf(
        attr("data-src"),
        attr("src"),
        attr("data-lazy-src"),
        attr("data-litespeed-src"),
        attr("data-video"),
        attr("data-embed"),
        attr("data-url"),
        attr("data-iframe")
    )
    return candidates.firstOrNull { it.isNotBlank() && !it.equals("about:blank", true) && !it.startsWith("javascript", true) }
}

fun getPosterUrl(element: Element): String {
    for (attr in listOf("src", "data-src", "data-original", "data-lazy-src", "data-lazy", "data-cfsrc", "data-image", "data-bg")) {
        val v = element.attr(attr)
        if (v.isNotBlank() && !v.startsWith("data:image", true) && (v.startsWith("http") || v.startsWith("//"))) {
            return if (v.startsWith("//")) "https:$v" else v
        }
    }
    return ""
}

fun fixUrl(url: String, dataUrl: String, mainUrl: String): String {
    if (url.isBlank()) return ""
    if (url.startsWith("http")) return url
    if (url.startsWith("//")) return "https:$url"
    return try {
        val u = URL(dataUrl)
        if (url.startsWith("/")) {
            "${u.protocol}://${u.host}$url"
        } else {
            val path = u.path.substringBeforeLast("/")
            "${u.protocol}://${u.host}$path/$url"
        }
    } catch (_: Exception) {
        if (url.startsWith("/")) "$mainUrl$url" else "$mainUrl/$url"
    }
}
