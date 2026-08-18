package com.anime3rb

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
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

const val COOKIE_KEY = "anime3rb_cookie_v2"
const val USER_AGENT_KEY = "anime3rb_ua_v2"
const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

class Anime3rbSettingsDialog : DialogFragment() {

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
                title = "Anime3rb Cloudflare & Sesi"
            }
            screen.addPreference(category)

            val openWvPref = Preference(ctx).apply {
                title = "Bypass Cloudflare / Turnstile (Manual)"
                summary = "Buka tetingkap WebView untuk menyelesaikan Turnstile atau menyegarkan cookies jika disekat"
                setOnPreferenceClickListener {
                    WebViewCaptureDialog().show(parentFragmentManager, "Anime3rbWVCapture")
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
        private val cleanUserAgent = DEFAULT_USER_AGENT

        @SuppressLint("SetJavaScriptEnabled")
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val ctx = requireContext()
            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                setBackgroundColor(Color.WHITE)
            }

            val toolbar = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(30, 30, 30, 30)
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
                text = "Menyelesaikan Turnstile..."
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.YELLOW)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val saveBtn = Button(ctx).apply {
                text = "Simpan"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#007AFF"))
                setOnClickListener { captureAndClose() }
            }

            toolbar.addView(closeBtn)
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

            webView.addJavascriptInterface(WebAppInterface(webView), "AndroidTouch")

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)

            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false

                override fun onPageFinished(view: WebView?, url: String?) {
                    val jsTouchLogic = """
                    (function() {
                        function drawRedDot(x, y) {
                            var dot = document.createElement('div');
                            dot.style = "position:fixed; left:" + x + "px; top:" + y + "px; width:20px; height:20px; background:red; border:2px solid white; border-radius:50%; z-index:99999999; pointer-events:none; opacity:0.8;";
                            document.body.appendChild(dot);
                            setTimeout(function(){ dot.remove(); }, 300);
                        }

                        setInterval(function() {
                            var xpath = "//*[contains(text(), 'Verify') or contains(text(), 'تحقق') or contains(text(), 'Cloudflare')]";
                            var textEl = document.evaluate(xpath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;
                            if (textEl) {
                                var rect = textEl.getBoundingClientRect();
                                var targetY = rect.top + (rect.height / 2);
                                var xLeft = rect.left - 40;
                                var xRight = rect.right + 40;

                                drawRedDot(xLeft, targetY);
                                if(window.AndroidTouch) window.AndroidTouch.performClick(xLeft, targetY);

                                setTimeout(function() {
                                    drawRedDot(xRight, targetY);
                                    if(window.AndroidTouch) window.AndroidTouch.performClick(xRight, targetY);
                                }, 200);
                            }
                        }, 2000);
                    })();
                    """.trimIndent()
                    view?.evaluateJavascript(jsTouchLogic, null)

                    val cookies = cookieManager.getCookie(url) ?: ""
                    if (cookies.contains("cf_clearance")) {
                        titleView.text = "Selesai! Menyimpan..."
                        titleView.setTextColor(Color.GREEN)
                        view?.postDelayed({ captureAndClose() }, 1000)
                    }
                }
            }

            webView.loadUrl("https://anime3rb.com")
            webContainer.addView(webView)
            root.addView(toolbar)
            root.addView(webContainer)
            return root
        }

        private fun captureAndClose() {
            try {
                CookieManager.getInstance().flush()
                val url = webView.url ?: "https://anime3rb.com"
                val cookieStr = CookieManager.getInstance().getCookie(url) ?: ""
                if (cookieStr.isNotBlank()) {
                    val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    prefs.edit().putString(COOKIE_KEY, cookieStr).apply()
                    prefs.edit().putString(USER_AGENT_KEY, cleanUserAgent).apply()
                    Toast.makeText(context, "Cookies berjaya disimpan!", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            } catch (_: Exception) {}
        }

        class WebAppInterface(private val view: WebView) {
            @JavascriptInterface
            fun performClick(x: Float, y: Float) {
                Handler(Looper.getMainLooper()).post {
                    val density = view.resources.displayMetrics.density
                    val realX = x * density
                    val realY = y * density
                    val downTime = SystemClock.uptimeMillis()
                    val eventTime = SystemClock.uptimeMillis() + 100

                    val motionEventDown = MotionEvent.obtain(
                        downTime,
                        eventTime,
                        MotionEvent.ACTION_DOWN,
                        realX,
                        realY,
                        0
                    )
                    val motionEventUp = MotionEvent.obtain(
                        downTime,
                        eventTime + 100,
                        MotionEvent.ACTION_UP,
                        realX,
                        realY,
                        0
                    )

                    view.dispatchTouchEvent(motionEventDown)
                    view.dispatchTouchEvent(motionEventUp)

                    motionEventDown.recycle()
                    motionEventUp.recycle()
                }
            }
        }
    }

    companion object {
        fun open(context: Context) {
            (context as? FragmentActivity)?.let { activity ->
                Anime3rbSettingsDialog().show(activity.supportFragmentManager, "Anime3rbSettings")
            }
        }
    }
}
