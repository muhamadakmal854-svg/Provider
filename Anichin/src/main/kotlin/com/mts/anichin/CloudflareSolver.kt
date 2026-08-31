package com.mts.anichin

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
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
                val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                if (rootView == null) {
                    continuation.resume(null)
                    return@post
                }

                val webView = WebView(activity)
                val params = FrameLayout.LayoutParams(1, 1).apply {
                    leftMargin = -5000
                    topMargin = -5000
                }
                webView.layoutParams = params

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

                val pollingHandler = Handler(Looper.getMainLooper())
                var isSolved = false
                var isProcessingClick = false

                fun finishSuccess(html: String?) {
                    if (isSolved) return
                    isSolved = true

                    try {
                        cookieManager.flush()
                        pollingHandler.removeCallbacksAndMessages(null)
                        rootView.removeView(webView)
                        webView.destroy()
                    } catch (_: Exception) {}

                    if (html == null) {
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

                pollingHandler.postDelayed({ finishSuccess(null) }, 60000)

                fun simulateRealTouch(view: WebView, cssX: Float, cssY: Float) {
                    val density = activity.resources.displayMetrics.density
                    val realX = cssX * density
                    val realY = cssY * density
                    val downTime = SystemClock.uptimeMillis()
                    val eventTime = SystemClock.uptimeMillis() + 50
                    val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, realX, realY, 0)
                    view.dispatchTouchEvent(downEvent)
                    view.postDelayed({
                        val upEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, realX, realY, 0)
                        view.dispatchTouchEvent(upEvent)
                        downEvent.recycle()
                        upEvent.recycle()
                    }, 50)
                }

                val targetCssPath = "html > body > div:nth-of-type(1) > div > div:nth-of-type(2) > div"

                fun startPolling() {
                    val runnable = object : Runnable {
                        override fun run() {
                            if (isSolved || isProcessingClick) {
                                pollingHandler.postDelayed(this, 2000)
                                return
                            }

                            val jsGetCoords = """
                                (function(){
                                    try{
                                        var box = document.querySelector("$targetCssPath");
                                        if(!box) return "NO_BOX";
                                        var r = box.getBoundingClientRect();
                                        if(r.width === 0 && r.height === 0) return "NO_BOX";
                                        var size = Math.min(36, Math.max(18, Math.round(r.height * 0.55)));
                                        var margin = Math.round(Math.max(8, r.width * 0.03));
                                        var centerY = r.top + (r.height / 2);
                                        var rightSideX = r.right - (size / 2) - margin;
                                        var leftSideX = r.left + (size / 2) + margin;
                                        return rightSideX + "," + centerY + "|" + leftSideX + "," + centerY;
                                    }catch(e){ return "ERROR"; }
                                })();
                            """.trimIndent()

                            webView.evaluateJavascript(jsGetCoords) { res ->
                                try {
                                    val clean = res?.removeSurrounding("\"")
                                    if (clean != null && clean.contains("|")) {
                                        isProcessingClick = true
                                        val sides = clean.split("|")
                                        val (rx, ry) = sides[0].split(",").map { it.toFloatOrNull() }
                                        val (lx, ly) = sides[1].split(",").map { it.toFloatOrNull() }
                                        if (rx != null && ry != null && lx != null && ly != null) {
                                            simulateRealTouch(webView, rx, ry)
                                            pollingHandler.postDelayed({
                                                simulateRealTouch(webView, lx, ly)
                                                pollingHandler.postDelayed({ isProcessingClick = false }, 3000)
                                            }, 250)
                                        } else { isProcessingClick = false }
                                    }
                                } catch (_: Exception) { isProcessingClick = false }
                            }
                            pollingHandler.postDelayed(this, 2000)
                        }
                    }
                    pollingHandler.post(runnable)
                }

                var lastUrl: String? = null
                var stableSince = 0L
                var fetched = false

                fun waitUntilReady() {
                    if (isSolved) return
                    val js = """
                        (function(){
                            try{
                                var hasBox = document.querySelector("$targetCssPath") != null;
                                var html = document.documentElement.innerHTML || "";
                                var stillCloudflare = html.toLowerCase().includes("cloudflare") || html.toLowerCase().includes("checking your browser") || html.toLowerCase().includes("challenge-platform");
                                return location.href + "|" + document.readyState + "|" + hasBox + "|" + stillCloudflare;
                            }catch(e){ return location.href + "|loading|false|true"; }
                        })();
                    """.trimIndent()

                    webView.evaluateJavascript(js) { res ->
                        if (res == null) {
                            pollingHandler.postDelayed({ waitUntilReady() }, 200)
                            return@evaluateJavascript
                        }

                        val parts = res.replace("\"", "").split("|")
                        if (parts.size < 4) {
                            pollingHandler.postDelayed({ waitUntilReady() }, 200)
                            return@evaluateJavascript
                        }

                        val (currentUrl, ready, hasBox, stillCloudflare) = parts
                        val now = SystemClock.uptimeMillis()
                        if (currentUrl != lastUrl) {
                            lastUrl = currentUrl
                            stableSince = now
                        }
                        val stableTime = now - stableSince

                        if (hasBox == "false" && stillCloudflare == "false" && ready == "complete" && stableTime > 1500 && !fetched) {
                            fetched = true
                            pollingHandler.postDelayed({
                                webView.evaluateJavascript("document.documentElement.outerHTML") { html ->
                                    finishSuccess(html)
                                }
                            }, 500)
                            return@evaluateJavascript
                        }
                        pollingHandler.postDelayed({ waitUntilReady() }, 200)
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isProcessingClick = false
                        startPolling()
                        waitUntilReady()
                    }
                }

                rootView.addView(webView)
                webView.loadUrl(url)
            }
        }
    }
}
