package com.mts.anichin

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.Window
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.PreferenceManager
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object CloudflareSolver {
    private const val TAG = "AnichinCFSolver"

    suspend fun solve(activity: Activity?, url: String, userAgent: String): Document? {
        if (activity == null || activity.isFinishing) return null

        return suspendCoroutine { continuation ->
            Handler(Looper.getMainLooper()).post {
                var isSolved = false
                var dialog: Dialog? = null

                fun finishSuccess(html: String?) {
                    if (isSolved) return
                    isSolved = true

                    try {
                        CookieManager.getInstance().flush()
                        if (dialog?.isShowing == true && !activity.isFinishing) {
                            dialog?.dismiss()
                        }
                    } catch (_: Exception) {}

                    if (html.isNullOrBlank()) {
                        continuation.resume(null)
                        return
                    }

                    val cleanHtml = html.removeSurrounding("\"")
                        .replace("\\u003C", "<")
                        .replace("\\u003E", ">")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")

                    continuation.resume(Jsoup.parse(cleanHtml))
                }

                try {
                    val rootLayout = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setBackgroundColor(Color.parseColor("#141414"))
                        layoutParams = ViewGroup.LayoutParams(-1, -1)
                    }

                    val headerLayout = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(30, 24, 30, 24)
                        gravity = Gravity.CENTER_VERTICAL
                        setBackgroundColor(Color.parseColor("#202020"))
                    }

                    val titleView = TextView(activity).apply {
                        text = "Sedang mengesahkan Cloudflare..."
                        setTextColor(Color.YELLOW)
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    }

                    val cancelBtn = Button(activity).apply {
                        text = "Batal"
                        setTextColor(Color.LTGRAY)
                        setBackgroundColor(Color.TRANSPARENT)
                        setOnClickListener { finishSuccess(null) }
                    }

                    headerLayout.addView(titleView)
                    headerLayout.addView(cancelBtn)
                    rootLayout.addView(headerLayout)

                    val webContainer = FrameLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(-1, -1)
                    }

                    val webView = WebView(activity).apply {
                        layoutParams = FrameLayout.LayoutParams(-1, -1)
                    }

                    val settings = webView.settings
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        userAgentString = userAgent
                    }

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(webView, true)

                    class TouchInterface(private val view: WebView) {
                        @JavascriptInterface
                        fun performClick(x: Float, y: Float) {
                            Handler(Looper.getMainLooper()).post {
                                val density = view.resources.displayMetrics.density
                                val realX = x * density
                                val realY = y * density
                                val downTime = SystemClock.uptimeMillis()
                                val eventTime = SystemClock.uptimeMillis() + 100

                                val down = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, realX, realY, 0)
                                val up = MotionEvent.obtain(downTime, eventTime + 100, MotionEvent.ACTION_UP, realX, realY, 0)
                                view.dispatchTouchEvent(down)
                                view.dispatchTouchEvent(up)
                                down.recycle()
                                up.recycle()
                            }
                        }
                    }

                    webView.addJavascriptInterface(TouchInterface(webView), "AndroidTouch")

                    webContainer.addView(webView)
                    rootLayout.addView(webContainer)

                    val newDialog = Dialog(activity)
                    newDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                    newDialog.setContentView(rootLayout)
                    newDialog.setCancelable(true)
                    newDialog.setOnCancelListener { finishSuccess(null) }

                    val window = newDialog.window
                    if (window != null) {
                        val dm = activity.resources.displayMetrics
                        val width = (dm.widthPixels * 0.94).toInt()
                        val height = (dm.heightPixels * 0.65).toInt()
                        window.setLayout(width, height)
                        window.setGravity(Gravity.CENTER)
                    }

                    dialog = newDialog
                    newDialog.show()

                    val pollingHandler = Handler(Looper.getMainLooper())
                    pollingHandler.postDelayed({ finishSuccess(null) }, 75000)

                    fun checkSolved() {
                        if (isSolved) return
                        val cookies = (cookieManager.getCookie("https://anichin.moe") ?: "") + "; " + (cookieManager.getCookie(url) ?: "")
                        if (cookies.contains("cf_clearance")) {
                            try {
                                val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
                                prefs.edit().putString(COOKIE_KEY, cookies).apply()
                                prefs.edit().putString(USER_AGENT_KEY, userAgent).apply()
                                Anichin.savedCookies = cookies
                            } catch (_: Exception) {}

                            titleView.text = "Berjaya! Memuatkan..."
                            titleView.setTextColor(Color.GREEN)
                            pollingHandler.postDelayed({
                                webView.evaluateJavascript("document.documentElement.outerHTML") { html ->
                                    finishSuccess(html)
                                }
                            }, 800)
                            return
                        }

                        // Check page content strictly
                        val jsCheck = """
                        (function() {
                            var text = (document.body ? document.body.innerText : '') + ' ' + document.title;
                            text = text.toLowerCase();
                            var isCf = text.indexOf('performing security verification') !== -1 ||
                                       text.indexOf('security service to protect') !== -1 ||
                                       text.indexOf('verifying you are not a bot') !== -1 ||
                                       text.indexOf('verify you are human') !== -1 ||
                                       text.indexOf('just a moment') !== -1 ||
                                       text.indexOf('checking if the site connection is secure') !== -1 ||
                                       text.indexOf('challenge-platform') !== -1 ||
                                       text.indexOf('cf-turnstile') !== -1 ||
                                       document.querySelector("iframe[src*='cloudflare.com']") != null;

                            // STRICT check for real Anichin anime elements
                            var hasRealAnime = document.querySelector('.listupd .bsx, .bsx a, article.bs, .eplister, #daftarepisode, .releases h2, .releases h3') != null;

                            // Auto click turnstile if present
                            var clickTarget = document.evaluate(
                                "//*[contains(text(), 'Verify') or contains(text(), 'Verifikasi') or contains(text(), 'human') or contains(text(), 'manusia')]",
                                document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null
                            ).singleNodeValue;

                            if (clickTarget) {
                                var rect = clickTarget.getBoundingClientRect();
                                var targetY = rect.top + (rect.height / 2);
                                var xLeft = rect.left - 35;
                                if (window.AndroidTouch) window.AndroidTouch.performClick(xLeft, targetY);
                            }

                            return isCf + "|" + hasRealAnime;
                        })();
                        """.trimIndent()

                        webView.evaluateJavascript(jsCheck) { res ->
                            val parts = res?.removeSurrounding("\"")?.split("|")
                            if (parts != null && parts.size >= 2) {
                                val isCf = parts[0] == "true"
                                val hasRealAnime = parts[1] == "true"

                                // ONLY succeed if Cloudflare is genuinely GONE and real anime items exist!
                                if (!isCf && hasRealAnime) {
                                    val finalCookies = (cookieManager.getCookie("https://anichin.moe") ?: "") + "; " + (cookieManager.getCookie(url) ?: "")
                                    try {
                                        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
                                        prefs.edit().putString(COOKIE_KEY, finalCookies).apply()
                                        prefs.edit().putString(USER_AGENT_KEY, userAgent).apply()
                                        Anichin.savedCookies = finalCookies
                                    } catch (_: Exception) {}

                                    titleView.text = "Berjaya! Memuatkan..."
                                    titleView.setTextColor(Color.GREEN)
                                    pollingHandler.postDelayed({
                                        webView.evaluateJavascript("document.documentElement.outerHTML") { html ->
                                            finishSuccess(html)
                                        }
                                    }, 800)
                                    return@evaluateJavascript
                                }
                            }
                            pollingHandler.postDelayed({ checkSolved() }, 1000)
                        }
                    }

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                            super.onPageFinished(view, pageUrl)
                            checkSolved()
                        }
                    }

                    webView.loadUrl(url)
                } catch (_: Exception) {
                    finishSuccess(null)
                }
            }
        }
    }
}
