package com.mts.drakorid

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import java.security.MessageDigest

class Drakorid : MainAPI() {
    override var mainUrl = "https://drakorid.cam"
    override var name = "Drakorid"
    override var lang = "id"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.TvSeries, TvType.Movie)
    override val hasQuickSearch = false

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

        // SeekPlayer static encryption keys
        private const val SEEK_AES_KEY = "kiemtienmua911ca"
        private const val SEEK_AES_IV = "1234567890oiuytr"

        fun cleanSeriesTitle(title: String): String {
            return title
                .replace(Regex("""(?i)Episode\s+\d+.*"""), "")
                .replace(Regex("""(?i)Ep\s+\d+.*"""), "")
                .replace(Regex("""(?i)Subtitle\s+Indonesia.*"""), "")
                .replace(Regex("""(?i)Sub\s+Indo.*"""), "")
                .replace(Regex("""(?i)–\s*drakor\.?id.*"""), "")
                .replace(Regex("""(?i)-\s*drakor\.?id.*"""), "")
                .replace(Regex("""(?i)drakor\.?id.*"""), "")
                .replace(Regex("""(?i)\bEND\b"""), "")
                .trim()
                .trimEnd('-', '–', ':', '|', ' ')
        }

        fun parseDateToEpoch(dateStr: String?): Long? {
            if (dateStr.isNullOrBlank()) return null
            val normalized = dateStr.trim()
                .replace("Januari", "January", true)
                .replace("Februari", "February", true)
                .replace("Maret", "March", true)
                .replace("Mac", "March", true)
                .replace("Mei", "May", true)
                .replace("Juni", "June", true)
                .replace("Juli", "July", true)
                .replace("Agustus", "August", true)
                .replace("Ogos", "August", true)
                .replace("Oktober", "October", true)
                .replace("Desember", "December", true)
                .replace("Disember", "December", true)

            val formats = listOf(
                SimpleDateFormat("d MMMM yyyy", Locale.US),
                SimpleDateFormat("MMMM d, yyyy", Locale.US),
                SimpleDateFormat("yyyy-MM-dd", Locale.US),
                SimpleDateFormat("MMM d, yyyy", Locale.US)
            )

            for (fmt in formats) {
                try {
                    fmt.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
                    val parsed = fmt.parse(normalized)
                    if (parsed != null) return parsed.time
                } catch (_: Exception) {}
            }
            return null
        }

        private fun md5(input: String): String {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        private fun hexStringToByteArray(s: String): ByteArray {
            val clean = s.trim().replace("\"", "").replace("\n", "").replace("\r", "")
            val len = clean.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }

        fun decryptSeekPlayer(hexData: String): String? {
            return try {
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                val keySpec = SecretKeySpec(SEEK_AES_KEY.toByteArray(Charsets.UTF_8), "AES")
                val ivSpec = IvParameterSpec(SEEK_AES_IV.toByteArray(Charsets.UTF_8))
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                val decrypted = cipher.doFinal(hexStringToByteArray(hexData))
                String(decrypted, Charsets.UTF_8)
            } catch (e: Exception) {
                null
            }
        }

        fun decryptAbyssDatas(userId: String, slug: String, md5Id: String, media: String): String? {
            return try {
                val keySeed = "$userId:$slug:$md5Id"
                val md5Hex = md5(keySeed)
                val keyBytes = md5Hex.toByteArray(Charsets.UTF_8)
                val counterBytes = keyBytes.copyOfRange(0, 16)

                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                val keySpec = SecretKeySpec(keyBytes, "AES")
                val ivSpec = IvParameterSpec(counterBytes)
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

                val mediaBytes = ByteArray(media.length) { i -> media[i].code.toByte() }
                val decrypted = cipher.doFinal(mediaBytes)
                String(decrypted, Charsets.UTF_8)
            } catch (e: Exception) {
                null
            }
        }
    }

    // 12 Kategori Tampilan Netflix Lengkap
    override val mainPage = mainPageOf(
        "$mainUrl/#spotlight" to "✨ Pilihan Utama (Spotlight)",
        "$mainUrl/series/?status=ongoing" to "🔥 Sedang Hangat (Hot Series)",
        "$mainUrl/#latest" to "⚡ Rilisan Terbaru (Update Harian)",
        "$mainUrl/country/south-korea/" to "🇰🇷 K-Drama (Drama Korea)",
        "$mainUrl/country/china/" to "🇨🇳 C-Drama (Drama China)",
        "$mainUrl/country/japan/" to "🇯🇵 J-Drama (Dorama Jepang)",
        "$mainUrl/movie/" to "🍿 Film Layar Lebar (Movies)",
        "$mainUrl/completed/" to "🏆 Drama Tamat (Completed)",
        "$mainUrl/genres/romance/" to "💖 Romantis (Romance)",
        "$mainUrl/genres/action/" to "💥 Aksi & Misteri (Action & Mystery)",
        "$mainUrl/genres/comedy/" to "🎭 Komedi & Keluarga (Comedy)",
        "$mainUrl/genres/fantasy/" to "🔮 Fantasi & Sejarah (Fantasy & Historical)"
    )

    private fun getPosterUrl(element: Element?): String? {
        if (element == null) return null
        val targetEl = if (element.tagName().equals("img", true)) element else (element.selectFirst("img") ?: element)
        for (attr in listOf("data-src", "data-lazy-src", "data-cfsrc", "data-original", "data-image", "data-bg", "src")) {
            val v = targetEl.attr(attr).trim()
            if (v.isNotBlank() && !v.contains("data:image") && (v.startsWith("http") || v.startsWith("//"))) {
                return if (v.startsWith("//")) "https:$v" else v
            }
        }
        return null
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        return try {
            val a = if (element.tagName().equals("a", true)) element else element.selectFirst("a[href]") ?: return null
            val href = fixUrl(a.attr("href"))
            if (href.isBlank() || href == "$mainUrl/" || href.contains("/genres/") || href.contains("/country/")) return null

            val img = a.selectFirst("img") ?: element.selectFirst("img")

            var rawTitle = element.selectFirst(".tt, .entry-title, h2, h3, .title")?.text()?.trim().orEmpty()
            if (rawTitle.isBlank()) {
                rawTitle = a.attr("title").trim()
            }
            if (rawTitle.isBlank()) {
                rawTitle = img?.attr("alt")?.trim().orEmpty()
            }
            if (rawTitle.isBlank()) {
                rawTitle = a.text().trim()
            }

            rawTitle = rawTitle.lines().firstOrNull()?.trim() ?: ""
            if (rawTitle.isBlank()) return null

            val seriesTitle = cleanSeriesTitle(rawTitle)
            val displayTitle = if (seriesTitle.isNotBlank()) seriesTitle else rawTitle

            val poster = getPosterUrl(img ?: element)

            val isMovie = href.contains("/movie", true) || href.contains("-movie-", true)
            val type = if (isMovie) TvType.Movie else TvType.AsianDrama

            val epText = element.selectFirst(".ep, .bt .ep, .epx, .egg, .typez")?.text()?.trim()
            val epNum = epText?.filter { it.isDigit() }?.toIntOrNull()

            newAnimeSearchResponse(displayTitle, href, type) {
                this.posterUrl = poster
                this.posterHeaders = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/"
                )
                if (epNum != null) {
                    addDubStatus(false, epNum)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val isSpotlight = request.data.contains("#spotlight")
        val isLatest = request.data.contains("#latest")

        val targetUrl = when {
            isSpotlight -> {
                if (page > 1) return newHomePageResponse(request.name, emptyList())
                "$mainUrl/"
            }
            isLatest -> {
                if (page <= 1) "$mainUrl/" else "$mainUrl/page/$page/"
            }
            request.data.contains("?") -> {
                if (page <= 1) request.data else "${request.data}&page=$page"
            }
            else -> {
                val cleanBase = request.data.trimEnd('/')
                if (page <= 1) "$cleanBase/" else "$cleanBase/page/$page/"
            }
        }

        val res = app.get(
            targetUrl,
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "$mainUrl/"
            )
        )
        val doc = res.document
        val items = doc.select(".listupd article, .bsx, article.bs")

        val list = if (isSpotlight) {
            items.take(6)
        } else {
            items
        }

        val seen = mutableSetOf<String>()
        val results = list.mapNotNull {
            val sr = toSearchResult(it) ?: return@mapNotNull null
            val key = sr.name.lowercase()
            if (seen.contains(key)) null else {
                seen.add(key)
                sr
            }
        }

        return newHomePageResponse(request.name, results, hasNext = results.size >= 10)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=${URLEncoder.encode(query, "UTF-8")}"
        val res = app.get(
            searchUrl,
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "$mainUrl/"
            )
        )
        val doc = res.document
        val items = doc.select(".listupd article, .bsx, article.bs")
        val seen = mutableSetOf<String>()
        return items.mapNotNull {
            val sr = toSearchResult(it) ?: return@mapNotNull null
            val key = sr.name.lowercase()
            if (seen.contains(key)) null else {
                seen.add(key)
                sr
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val initialRes = app.get(
            url,
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "$mainUrl/"
            )
        )
        val initialDoc = initialRes.document

        // If URL is an episode, attempt to navigate to full series page
        val parentSeriesUrl = initialDoc.selectFirst(".naveps.bignav .nvsc a, .ts-breadcrumb li a[href*='/series/']")?.attr("href")
        val (doc, isSeriesPage) = if (!parentSeriesUrl.isNullOrBlank() && !url.contains("/series/")) {
            val seriesRes = app.get(
                fixUrl(parentSeriesUrl),
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to url
                )
            )
            Pair(seriesRes.document, true)
        } else {
            Pair(initialDoc, url.contains("/series/"))
        }

        val rawTitle = doc.selectFirst("h1.entry-title")?.text()?.trim() ?: initialDoc.selectFirst("h1.entry-title")?.text()?.trim().orEmpty()
        val title = cleanSeriesTitle(rawTitle)
        val poster = getPosterUrl(doc.selectFirst(".thumb img, .bigcontent img, .poster img") ?: initialDoc.selectFirst(".thumb img, .bigcontent img, .poster img"))
        val description = doc.selectFirst(".entry-content, .sinopsis, .desc")?.text()?.trim()

        val genres = doc.select(".infox .genxed a, .spe .genxed a").map { it.text().trim() }.filter { it.isNotBlank() }
        val statusText = doc.selectFirst(".info-content .spe span:contains(Status), .spe span:contains(Status)")?.text().orEmpty()
        val status = when {
            statusText.contains("Completed", true) -> ShowStatus.Completed
            statusText.contains("Ongoing", true) -> ShowStatus.Ongoing
            else -> null
        }

        val year = doc.selectFirst(".info-content .spe span:contains(Dirilis), .spe span:contains(Dirilis), .spe span:contains(Released)")?.text()?.let {
            Regex("""\b(19\d\d|20\d\d)\b""").find(it)?.value?.toIntOrNull()
        }

        val rating = doc.selectFirst(".rating strong, .num, .score")?.text()?.toDoubleOrNull()
        val actors = doc.selectFirst(".info-content .spe span:contains(Artis), .spe span:contains(Artis)")?.text()
            ?.replace(Regex("""(?i)^Artis:\s*"""), "")
            ?.split(",")
            ?.map { it.trim() }
            ?: emptyList()

        val epElements = doc.select(".eplister li, .episodelst li")
        val episodes = mutableListOf<Episode>()

        if (epElements.isNotEmpty()) {
            for (ep in epElements) {
                val a = ep.selectFirst("a") ?: continue
                val epHref = fixUrl(a.attr("href"))
                val epNum = ep.selectFirst(".epl-num")?.text()?.trim()?.toIntOrNull()
                val epTitle = ep.selectFirst(".epl-title")?.text()?.trim() ?: "Episode $epNum"
                val epDateStr = ep.selectFirst(".epl-date")?.text()?.trim()
                val epEpoch = parseDateToEpoch(epDateStr)

                val episode = newEpisode(epHref) {
                    this.name = epTitle
                    this.episode = epNum
                    this.posterUrl = poster
                    this.date = epEpoch
                    this.description = if (!epDateStr.isNullOrBlank()) "Rilis: $epDateStr • Sub Indo" else "Sub Indo"
                }
                episodes.add(episode)
            }
        } else {
            // Standalone single video or movie
            val dateStr = doc.selectFirst(".info-content .spe span:contains(Dirilis), .spe span:contains(Dirilis)")?.text()
            val epEpoch = parseDateToEpoch(dateStr)
            episodes.add(
                newEpisode(url) {
                    this.name = title
                    this.episode = 1
                    this.posterUrl = poster
                    this.date = epEpoch
                    this.description = if (!dateStr.isNullOrBlank()) "Rilis: $dateStr • Sub Indo" else "Sub Indo"
                }
            )
        }

        // Return TvSeriesLoadResponse to show episode list with Card View layout
        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
            this.posterUrl = poster
            this.posterHeaders = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "$mainUrl/"
            )
            this.plot = description
            this.tags = genres
            this.showStatus = status
            this.year = year
            this.actors = actors.map { ActorData(Actor(it)) }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = app.get(
            data,
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "$mainUrl/"
            )
        )
        val doc = res.document

        // Collect all mirror URLs and server elements
        val serverUrls = mutableListOf<Pair<String, String>>() // Pair(serverName, serverUrl)

        // 1. Mirror selector options
        doc.select("select.mirror option").forEach { opt ->
            val optName = opt.text().trim()
            if (optName.isNotBlank() && !optName.contains("Pilih Server", true)) {
                val rawVal = opt.attr("value").ifBlank { opt.attr("data-value") }.trim()
                if (rawVal.isNotBlank()) {
                    val decoded = try {
                        val b64 = Base64.decode(rawVal, Base64.DEFAULT)
                        String(b64, Charsets.UTF_8)
                    } catch (_: Exception) {
                        rawVal
                    }
                    val iframeSrc = Regex("""src=["']([^"']+)["']""").find(decoded)?.groupValues?.get(1) ?: decoded
                    if (iframeSrc.startsWith("http")) {
                        serverUrls.add(Pair(optName, iframeSrc))
                    }
                }
            }
        }

        // 2. Default iframes on episode page
        doc.select(".player-embed iframe, #pembed iframe, .responsive-embed-stream iframe, iframe[src*='drakor'], iframe[src*='seekplayer'], iframe[src*='abyss']").forEach { iframe ->
            val src = iframe.attr("src").trim()
            if (src.startsWith("http")) {
                serverUrls.add(Pair("Default Player", src))
            }
        }

        // 3. Download section links
        doc.select(".dlbox a, .soradl a, .mvdload a").forEach { dlA ->
            val href = dlA.attr("href").trim()
            val text = dlA.text().trim().ifBlank { "Download" }
            if (href.startsWith("http")) {
                serverUrls.add(Pair(text, href))
            }
        }

        // Deduplicate servers by URL
        val seenUrls = mutableSetOf<String>()
        val uniqueServers = serverUrls.filter { (name, url) ->
            if (seenUrls.contains(url)) false else {
                seenUrls.add(url)
                true
            }
        }

        var foundLinks = false

        for ((serverName, targetUrl) in uniqueServers) {
            try {
                // A. SeekPlayer Decryption (drakorku.seekplayer.vip/#<id>)
                if (targetUrl.contains("seekplayer.vip")) {
                    val videoId = Regex("""#([a-zA-Z0-9_-]+)""").find(targetUrl)?.groupValues?.get(1)
                    if (!videoId.isNullOrBlank()) {
                        val apiUrl = "https://drakorku.seekplayer.vip/api/v1/video?id=$videoId&w=1920&h=1080&r=drakorid.cam"
                        val apiRes = app.get(
                            apiUrl,
                            headers = mapOf(
                                "User-Agent" to USER_AGENT,
                                "Referer" to "https://drakorku.seekplayer.vip/#$videoId",
                                "Accept" to "application/json, text/plain, */*"
                            )
                        )
                        val hexData = apiRes.text.trim().replace("\"", "").replace("\n", "").replace("\r", "")
                        val decryptedJson = decryptSeekPlayer(hexData)
                        if (!decryptedJson.isNullOrBlank()) {
                            val json = JSONObject(decryptedJson)
                            val cfNative = json.optString("cfNative")
                            val source = json.optString("source")

                            if (cfNative.isNotBlank()) {
                                M3u8Helper.generateM3u8(
                                    this.name,
                                    cfNative,
                                    "https://drakorku.seekplayer.vip/"
                                ).forEach { m3u ->
                                    callback(m3u)
                                    foundLinks = true
                                }
                            }

                            if (source.isNotBlank()) {
                                M3u8Helper.generateM3u8(
                                    "${this.name} (Direct)",
                                    source,
                                    "https://drakorku.seekplayer.vip/"
                                ).forEach { m3u ->
                                    callback(m3u)
                                    foundLinks = true
                                }
                            }
                        }
                    }
                }

                // B. Abyss / Cita Decryption (drakorid.store/?v=<slug> or abyssplayer.com/<slug>)
                if (targetUrl.contains("drakorid.store") || targetUrl.contains("abyssplayer.com") || targetUrl.contains("abyss.to")) {
                    val abyssRes = app.get(
                        targetUrl,
                        headers = mapOf(
                            "User-Agent" to USER_AGENT,
                            "Referer" to "$mainUrl/"
                        )
                    )
                    val abyssHtml = abyssRes.text
                    val datasMatch = Regex("""const\s+datas\s*=\s*["']([^"']+)["']""").find(abyssHtml)
                    if (datasMatch != null) {
                        val b64Payload = datasMatch.groupValues[1]
                        val rawBytes = Base64.decode(b64Payload, Base64.DEFAULT)
                        val latin1Str = String(rawBytes, Charsets.ISO_8859_1)
                        val datasObj = JSONObject(latin1Str)

                        val userId = datasObj.optString("user_id")
                        val slug = datasObj.optString("slug")
                        val md5Id = datasObj.optString("md5_id")
                        val media = datasObj.optString("media")

                        if (userId.isNotBlank() && slug.isNotBlank() && md5Id.isNotBlank() && media.isNotBlank()) {
                            val decryptedMedia = decryptAbyssDatas(userId, slug, md5Id, media)
                            if (!decryptedMedia.isNullOrBlank()) {
                                val mediaJson = JSONObject(decryptedMedia)
                                val mp4Obj = mediaJson.optJSONObject("mp4")
                                val sourcesArr = mp4Obj?.optJSONArray("sources")

                                if (sourcesArr != null) {
                                    for (i in 0 until sourcesArr.length()) {
                                        val sObj = sourcesArr.optJSONObject(i) ?: continue
                                        val label = sObj.optString("label", "720p")
                                        val hostUrl = sObj.optString("url")
                                        val path = sObj.optString("path")

                                        if (hostUrl.isNotBlank() && path.isNotBlank()) {
                                            val fullVideoUrl = "$hostUrl/$path"
                                            val quality = getQualityFromName(label)

                                            callback(
                                                ExtractorLink(
                                                    source = "Cita ($label)",
                                                    name = "Cita ($label)",
                                                    url = fullVideoUrl,
                                                    referer = "https://drakorid.store/",
                                                    quality = quality,
                                                    type = ExtractorLinkType.VIDEO,
                                                    headers = mapOf(
                                                        "User-Agent" to USER_AGENT,
                                                        "Referer" to "https://drakorid.store/",
                                                        "Origin" to "https://drakorid.store"
                                                    )
                                                )
                                            )
                                            foundLinks = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // C. Fallback for standard third-party hosts
                if (!targetUrl.contains("seekplayer.vip") && !targetUrl.contains("drakorid.store") && !targetUrl.contains("abyssplayer.com")) {
                    loadExtractor(targetUrl, "$mainUrl/", subtitleCallback) { link ->
                        callback(link)
                        foundLinks = true
                    }
                }
            } catch (_: Exception) {}
        }

        return foundLinks
    }
}
