package com.mts.anichin

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

const val COOKIE_KEY = "anichin_cf_cookies"
const val USER_AGENT_KEY = "anichin_cf_ua"
const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
const val ANICHIN_MAIN_URL = "https://anichin.moe"

class AnichinSettingsDialog : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val fragmentContainer = FragmentContainerView(requireContext())
        fragmentContainer.id = View.generateViewId()
        fragmentContainer.layoutParams = ViewGroup.LayoutParams(-1, -1)
        return fragmentContainer
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childFragmentManager.beginTransaction()
            .replace(view.id, PrefsFragment())
            .commit()
    }

    class PrefsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val ctx = preferenceManager.context
            val screen = preferenceManager.createPreferenceScreen(ctx)
            preferenceScreen = screen

            val category = PreferenceCategory(ctx).apply {
                title = "Anichin Cloudflare & Sesi"
            }
            screen.addPreference(category)

            val openWvPref = Preference(ctx).apply {
                title = "Bypass Cloudflare / Turnstile (Manual)"
                summary = "Buka tetingkap WebView untuk menyelesaikan Turnstile / reCAPTCHA secara manual dan simpan cookies"
                setOnPreferenceClickListener {
                    WebViewCaptureDialog().show(parentFragmentManager, "AnichinWVCapture")
                    true
                }
            }
            category.addPreference(openWvPref)

            val clearPref = Preference(ctx).apply {
                title = "Padam Cookies Tersimpan"
                summary = "Kosongkan session cookies yang telah disimpan"
                setOnPreferenceClickListener {
                    try {
                        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
                        prefs.edit().remove(COOKIE_KEY).remove(USER_AGENT_KEY).apply()
                        Anichin.savedCookies = ""
                        CookieManager.getInstance().removeAllCookies(null)
                        Toast.makeText(ctx, "Cookies berjaya dikosongkan", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Ralat: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
            }
            category.addPreference(clearPref)

            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            val hasCookie = !prefs.getString(COOKIE_KEY, null).isNullOrBlank()
            val statusPref = Preference(ctx).apply {
                title = "Status Cookies"
                summary = if (hasCookie) "Aktif (Cookie tersimpan)" else "Tiada Cookie (Gunakan Bypass jika disekat)"
                isEnabled = false
            }
            category.addPreference(statusPref)

            val closePref = Preference(ctx).apply {
                title = "Tutup"
                setOnPreferenceClickListener {
                    (parentFragment as? DialogFragment)?.dismiss()
                    true
                }
            }
            screen.addPreference(closePref)
        }
    }

    class WebViewCaptureDialog : DialogFragment() {
        private lateinit var webView: WebView

        override fun onStart() {
            super.onStart()
            dialog?.window?.let { win ->
                val dm = resources.displayMetrics
                val width = (dm.widthPixels * 0.95).toInt()
                val height = (dm.heightPixels * 0.85).toInt()
                win.setLayout(width, height)
                win.setGravity(Gravity.CENTER)
            }
        }

        @SuppressLint("SetJavaScriptEnabled")
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val ctx = requireContext()
            val cleanUserAgent = try {
                WebSettings.getDefaultUserAgent(ctx)
            } catch (_: Exception) {
                DEFAULT_USER_AGENT
            }

            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                setBackgroundColor(Color.parseColor("#121212"))
            }

            val toolbar = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 20, 24, 20)
                setBackgroundColor(Color.parseColor("#202020"))
                gravity = Gravity.CENTER_VERTICAL
            }

            val closeBtn = Button(ctx).apply {
                text = "Batal"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { dismiss() }
            }

            val titleView = TextView(ctx).apply {
                text = "Sahkan Cloudflare di bawah & tekan 'Simpan'"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#FFD700"))
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }

            val reloadBtn = Button(ctx).apply {
                text = "Refresh"
                setTextColor(Color.LTGRAY)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { webView.reload() }
            }

            val saveBtn = Button(ctx).apply {
                text = "Simpan"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#007AFF"))
                setOnClickListener { validateAndSave(cleanUserAgent, titleView) }
            }

            toolbar.addView(closeBtn)
            toolbar.addView(reloadBtn)
            toolbar.addView(titleView)
            toolbar.addView(saveBtn)

            val webContainer = FrameLayout(ctx).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
            webView = WebView(ctx).apply { layoutParams = FrameLayout.LayoutParams(-1, -1) }

            val settings = webView.settings
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                userAgentString = cleanUserAgent
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdThirdPartyCookies(webView)

            // Clear any stale clearance cookies when opening manual bypass
            try {
                cookieManager.setCookie(ANICHIN_MAIN_URL, "cf_clearance=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/; domain=.anichin.moe")
                cookieManager.setCookie(ANICHIN_MAIN_URL, "cf_clearance=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/")
                cookieManager.flush()
            } catch (_: Exception) {}

            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false

                override fun onPageFinished(view: WebView?, url: String?) {
                    // Informative guidance only - strictly NO auto-close, NO auto-touch
                    view?.evaluateJavascript("""
                        (function() {
                            var text = (document.body ? document.body.innerText : '') + ' ' + document.title;
                            text = text.toLowerCase();
                            var isCf = text.indexOf('performing security verification') !== -1 ||
                                       text.indexOf('security service to protect') !== -1 ||
                                       text.indexOf('verifying you are not a bot') !== -1 ||
                                       text.indexOf('verify you are human') !== -1 ||
                                       text.indexOf('just a moment') !== -1;
                            return isCf;
                        })();
                    """.trimIndent()) { res ->
                        if (isAdded) {
                            val isCf = res?.removeSurrounding("\"") == "true"
                            if (isCf) {
                                titleView.text = "Sila selesaikan pengesahan pada skrin"
                                titleView.setTextColor(Color.parseColor("#FFA500"))
                            } else {
                                titleView.text = "Laman sedia! Tekan butang 'Simpan'"
                                titleView.setTextColor(Color.parseColor("#4CAF50"))
                            }
                        }
                    }
                }
            }

            webView.loadUrl(ANICHIN_MAIN_URL)
            webContainer.addView(webView)
            root.addView(toolbar)
            root.addView(webContainer)
            return root
        }

        private fun CookieManager.setAcceptThirdThirdPartyCookies(view: WebView) {
            try {
                setAcceptThirdPartyCookies(view, true)
            } catch (_: Exception) {}
        }

        private fun validateAndSave(cleanUserAgent: String, titleView: TextView) {
            val cm = CookieManager.getInstance()
            cm.flush()
            val currentUrl = webView.url ?: ANICHIN_MAIN_URL
            val cookies = (cm.getCookie(ANICHIN_MAIN_URL) ?: "") + "; " + (cm.getCookie(currentUrl) ?: "")

            val checkJs = """
            (function() {
                var title = (document.title || '').toLowerCase();
                var body = (document.body ? document.body.innerText : '').toLowerCase();
                var isCf = title.indexOf('just a moment') !== -1 ||
                           title.indexOf('security verification') !== -1 ||
                           body.indexOf('performing security verification') !== -1 ||
                           body.indexOf('security service to protect') !== -1 ||
                           body.indexOf('verifying you are not a bot') !== -1 ||
                           body.indexOf('verify you are human') !== -1 ||
                           body.indexOf('checking if the site connection is secure') !== -1 ||
                           body.indexOf('challenge-platform') !== -1 ||
                           document.querySelector("iframe[src*='cloudflare.com']") != null;

                var hasRealAnime = document.querySelector('.listupd .bsx, .bsx a, article.bs, .eplister, #daftarepisode, .releases h2, .releases h3, header#masthead') != null;
                return isCf + "|" + hasRealAnime;
            })();
            """.trimIndent()

            webView.evaluateJavascript(checkJs) { res ->
                if (!isAdded) return@evaluateJavascript
                val parts = res?.removeSurrounding("\"")?.split("|")
                val isCf = parts?.getOrNull(0) == "true"
                val hasRealAnime = parts?.getOrNull(1) == "true"
                val hasClearance = cookies.contains("cf_clearance")

                if (isCf) {
                    titleView.text = "Pengesahan belum selesai!"
                    titleView.setTextColor(Color.RED)
                    Toast.makeText(context, "Pengesahan Cloudflare belum selesai! Sila selesaikan pengesahan pada skrin terlebih dahulu.", Toast.LENGTH_LONG).show()
                    return@evaluateJavascript
                }

                if (hasClearance || hasRealAnime) {
                    try {
                        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                        prefs.edit().putString(COOKIE_KEY, cookies).apply()
                        prefs.edit().putString(USER_AGENT_KEY, cleanUserAgent).apply()
                        Anichin.savedCookies = cookies
                        Toast.makeText(context, "Cookies berjaya disimpan! Anichin sedia digunakan.", Toast.LENGTH_SHORT).show()
                        dismiss()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Ralat menyimpan cookies: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    titleView.text = "Laman belum sedia. Sila tunggu / refresh"
                    titleView.setTextColor(Color.YELLOW)
                    Toast.makeText(context, "Laman web belum selesai dimuatkan. Sila tunggu seketika atau tekan Refresh.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        fun open(context: Context) {
            var ctx: Context? = context
            while (ctx is ContextWrapper) {
                if (ctx is FragmentActivity) break
                ctx = ctx.baseContext
            }
            (ctx as? FragmentActivity)?.let { activity ->
                AnichinSettingsDialog().show(activity.supportFragmentManager, "AnichinSettings")
            }
        }
    }
}
