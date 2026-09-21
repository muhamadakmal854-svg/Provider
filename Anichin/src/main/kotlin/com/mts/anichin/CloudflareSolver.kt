package com.mts.anichin

import android.app.Activity
import org.jsoup.nodes.Document

object CloudflareSolver {
    // Pengesahan automatik dinonaktifkan sepenuhnya.
    // Pengguna mengesahkan secara manual 100% di Tetapan > Extensions > Anichin > Bypass Cloudflare (Manual).
    @Suppress("UNUSED_PARAMETER")
    suspend fun solve(activity: Activity?, url: String, userAgent: String): Document? {
        return null
    }
}
