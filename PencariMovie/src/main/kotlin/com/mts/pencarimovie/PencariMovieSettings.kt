package com.mts.pencarimovie

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

const val PREF_PM_SERVER_URL = "pm_server_url"
const val PREF_PM_TG_COOKIES = "pm_tg_cookies"
const val DEFAULT_PM_SERVER_URL = "http://127.0.0.1:8088"
const val PM_MAIN_URL = "https://flixtorbot.pencarimovie.com"
const val PM_LOGIN_URL = "https://flixtorbot.pencarimovie.com/wp-login.php"

class PencariMovieSettingsDialog : DialogFragment() {

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

            // Kategori 1: Pelayan Penstriman (PencariMovie Server)
            val serverCategory = PreferenceCategory(ctx).apply {
                title = "Pelayan Penstriman (Streaming Server)"
            }
            screen.addPreference(serverCategory)

            val serverUrlPref = EditTextPreference(ctx).apply {
                key = PREF_PM_SERVER_URL
                title = "URL Pelayan PencariMovie"
                dialogTitle = "Masukkan URL Pelayan"
                dialogMessage = "Lalai: http://127.0.0.1:8088 (Local / Companion APK). Untuk Android TV / Stick, masukkan IP LAN komputer anda (cth: http://192.168.1.50:8088) atau pautan Tunnel."
                setDefaultValue(DEFAULT_PM_SERVER_URL)
                summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                    val text = pref.text
                    if (text.isNullOrBlank()) DEFAULT_PM_SERVER_URL else text
                }
            }
            serverCategory.addPreference(serverUrlPref)

            val testServerPref = Preference(ctx).apply {
                title = "Uji Sambungan Pelayan"
                summary = "Semak status sambungan pelayan port 8088 / URL pelayan yang dikonfigurasi"
                setOnPreferenceClickListener {
                    val p = PreferenceManager.getDefaultSharedPreferences(ctx)
                    val currentUrl = p.getString(PREF_PM_SERVER_URL, DEFAULT_PM_SERVER_URL)
                        ?.trimEnd('/') ?: DEFAULT_PM_SERVER_URL

                    Toast.makeText(ctx, "Menyemak sambungan ke $currentUrl...", Toast.LENGTH_SHORT).show()

                    thread {
                        var isOnline = false
                        var versionInfo = ""
                        try {
                            val u = URL("$currentUrl/api/version")
                            val conn = u.openConnection() as HttpURLConnection
                            conn.connectTimeout = 3500
                            conn.readTimeout = 3500
                            conn.requestMethod = "GET"
                            conn.connect()
                            val code = conn.responseCode
                            if (code in 200..299) {
                                isOnline = true
                                versionInfo = conn.inputStream.bufferedReader().use { it.readText() }
                            }
                            conn.disconnect()
                        } catch (e: Exception) {
                            versionInfo = e.message ?: "Ralat sambungan"
                        }

                        Handler(Looper.getMainLooper()).post {
                            if (isOnline) {
                                Toast.makeText(ctx, "Pelayan Aktif! Respons: $versionInfo", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(ctx, "Pelayan TIDAK dapat dihubungi di $currentUrl. Sila pastikan daemon APK sedang berjalan.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    true
                }
            }
            serverCategory.addPreference(testServerPref)

            val apkGuidePref = Preference(ctx).apply {
                title = "Muat Turun APK Pelayan (Android TV / Stick)"
                summary = "Buka pautan https://telegra.my/apk untuk memuat turun pelayan penstriman rasmi"
                setOnPreferenceClickListener {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://telegra.my/apk"))
                        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(browserIntent)
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Pautan APK: https://telegra.my/apk", Toast.LENGTH_LONG).show()
                    }
                    true
                }
            }
            serverCategory.addPreference(apkGuidePref)

            // Kategori 2: Sesi & Pengesahan Telegram
            val tgCategory = PreferenceCategory(ctx).apply {
                title = "Log Masuk PencariMovie & Telegram"
            }
            screen.addPreference(tgCategory)

            val tgStatusPref = Preference(ctx).apply {
                title = "Status Sesi & Kuki"
                isEnabled = false
            }
            tgCategory.addPreference(tgStatusPref)

            fun updateTgStatus() {
                val p = PreferenceManager.getDefaultSharedPreferences(ctx)
                val cookies = p.getString(PREF_PM_TG_COOKIES, null)
                val hasCookies = !cookies.isNullOrBlank()
                tgStatusPref.summary = if (hasCookies) {
                    val preview = if (cookies!!.length > 40) cookies.substring(0, 37) + "..." else cookies
                    "Aktif ($preview)"
                } else {
                    "Belum Log Masuk (Klik butang di bawah untuk log masuk)"
                }
            }
            updateTgStatus()

            val tgLoginPref = Preference(ctx).apply {
                title = "Log Masuk PencariMovie (Telegram Widget / Web)"
                summary = "Buka tetingkap log masuk flixtorbot.pencarimovie.com untuk log masuk menggunakan butang Telegram rasmi dan simpan kuki sesi"
                setOnPreferenceClickListener {
                    val dialog = TelegramCaptureDialog()
                    dialog.onSaved = {
                        updateTgStatus()
                    }
                    dialog.show(parentFragmentManager, "TelegramWVCapture")
                    true
                }
            }
            tgCategory.addPreference(tgLoginPref)

            val manualCookiePref = EditTextPreference(ctx).apply {
                key = PREF_PM_TG_COOKIES
                title = "Kuki Sesi Manual (Pilihan)"
                dialogTitle = "Masukkan Kuki Sesi"
                dialogMessage = "Anda boleh menampal kuki sesi anda di sini secara manual sekiranya diperlukan."
                summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                    val text = pref.text
                    if (text.isNullOrBlank()) "Tiada kuki manual" else if (text.length > 50) text.substring(0, 47) + "..." else text
                }
                setOnPreferenceChangeListener { _, _ ->
                    Handler(Looper.getMainLooper()).postDelayed({ updateTgStatus() }, 200)
                    true
                }
            }
            tgCategory.addPreference(manualCookiePref)

            val clearTgPref = Preference(ctx).apply {
                title = "Padam Sesi & Kuki"
                summary = "Kosongkan kuki sesi yang tersimpan di peranti ini"
                setOnPreferenceClickListener {
                    val p = PreferenceManager.getDefaultSharedPreferences(ctx)
                    p.edit().remove(PREF_PM_TG_COOKIES).apply()
                    try {
                        CookieManager.getInstance().removeAllCookies(null)
                    } catch (_: Exception) {}
                    updateTgStatus()
                    Toast.makeText(ctx, "Sesi telah dikosongkan.", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            tgCategory.addPreference(clearTgPref)

            // Kategori 3: Kawalan
            val closeCategory = PreferenceCategory(ctx).apply {
                title = "Tindakan"
            }
            screen.addPreference(closeCategory)

            val closePref = Preference(ctx).apply {
                title = "Tutup Tetapan"
                setOnPreferenceClickListener {
                    (parentFragment as? DialogFragment)?.dismiss()
                    true
                }
            }
            closeCategory.addPreference(closePref)
        }
    }

    companion object {
        fun open(context: Context) {
            val activity = getActivity(context)
            if (activity is FragmentActivity) {
                val dialog = PencariMovieSettingsDialog()
                dialog.show(activity.supportFragmentManager, "PencariMovieSettingsDialog")
            } else {
                Toast.makeText(context, "Sila buka dari antaramuka utama", Toast.LENGTH_SHORT).show()
            }
        }

        private fun getActivity(context: Context?): FragmentActivity? {
            var ctx = context
            while (ctx is ContextWrapper) {
                if (ctx is FragmentActivity) return ctx
                ctx = ctx.baseContext
            }
            return null
        }
    }
}

class TelegramCaptureDialog : DialogFragment() {

    var onSaved: (() -> Unit)? = null
    private var webView: WebView? = null
    private var childWebViews = mutableListOf<WebView>()
    private val pollHandler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null
    private var isDismissed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setBackgroundColor(Color.parseColor("#121212"))
        }

        // Toolbar Atas
        val toolbar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.parseColor("#1F1F1F"))
            gravity = Gravity.CENTER_VERTICAL
        }

        val cancelBtn = Button(ctx).apply {
            text = "Tutup"
            setTextColor(Color.LTGRAY)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { dismiss() }
        }

        val titleView = TextView(ctx).apply {
            text = "Log Masuk PencariMovie (Telegram)"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        val saveBtn = Button(ctx).apply {
            text = "Simpan Kuki"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2481CC"))
            setOnClickListener {
                captureAndSaveCookies(showToast = true)
            }
        }

        toolbar.addView(cancelBtn)
        toolbar.addView(titleView)
        toolbar.addView(saveBtn)
        root.addView(toolbar)

        // Banner Panduan
        val infoBanner = TextView(ctx).apply {
            text = "Sila log masuk menggunakan butang 'Log in with Telegram'. Selepas selesai, kuki akan dikesan secara automatik atau klik 'Simpan Kuki'."
            textSize = 12f
            setTextColor(Color.parseColor("#E0E0E0"))
            setBackgroundColor(Color.parseColor("#2C2C2C"))
            setPadding(24, 12, 24, 12)
        }
        root.addView(infoBanner)

        // Web Container
        val frame = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }

        webView = WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    checkAndAutoCapture()
                }
            }

            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    val child = WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(-1, -1)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            userAgentString = view?.settings?.userAgentString
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(v: WebView?, u: String?) {
                                super.onPageFinished(v, u)
                                checkAndAutoCapture()
                            }
                        }
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onCloseWindow(window: WebView?) {
                                frame.removeView(window)
                                childWebViews.remove(window)
                                window?.destroy()
                            }
                        }
                    }
                    childWebViews.add(child)
                    frame.addView(child)
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    transport?.webView = child
                    resultMsg?.sendToTarget()
                    return true
                }
            }

            loadUrl(PM_LOGIN_URL)
        }

        frame.addView(webView)
        root.addView(frame)

        // Setup Periodic Cookie Poller (Every 1500ms)
        pollRunnable = object : Runnable {
            override fun run() {
                if (isDismissed || activity == null || isDetached) return
                if (checkAndAutoCapture()) return
                pollHandler.postDelayed(this, 1500)
            }
        }
        pollHandler.postDelayed(pollRunnable!!, 2000)

        return root
    }

    private fun checkAndAutoCapture(): Boolean {
        try {
            val cm = CookieManager.getInstance()
            val pmCookies = cm.getCookie(PM_MAIN_URL) ?: ""
            if (pmCookies.contains("wordpress_logged_in") || pmCookies.contains("wordpress_sec")) {
                captureAndSaveCookies(showToast = true)
                return true
            }
        } catch (_: Exception) {}
        return false
    }

    private fun captureAndSaveCookies(showToast: Boolean = true) {
        if (isDismissed) return
        try {
            val cm = CookieManager.getInstance()
            cm.flush()
            val pmCookies = cm.getCookie(PM_MAIN_URL) ?: ""
            val tgCookies = cm.getCookie("https://oauth.telegram.org") ?: ""
            val tgWebCookies = cm.getCookie("https://web.telegram.org") ?: ""

            val combined = listOf(pmCookies, tgCookies, tgWebCookies)
                .filter { it.isNotBlank() }
                .joinToString("; ")

            if (combined.isNotBlank()) {
                val contextSafe = context ?: return
                val prefs = PreferenceManager.getDefaultSharedPreferences(contextSafe)
                prefs.edit().putString(PREF_PM_TG_COOKIES, combined).apply()

                if (showToast) {
                    Toast.makeText(contextSafe, "Kuki sesi berjaya disimpan!", Toast.LENGTH_SHORT).show()
                }
                isDismissed = true
                onSaved?.invoke()
                dismissAllowingStateLoss()
            } else if (showToast) {
                val contextSafe = context ?: return
                Toast.makeText(contextSafe, "Belum ada kuki dikesan. Sila lengkapkan log masuk terlebih dahulu.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            if (showToast) {
                context?.let {
                    Toast.makeText(it, "Ralat: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        isDismissed = true
        pollRunnable?.let { pollHandler.removeCallbacks(it) }
        childWebViews.forEach {
            try {
                it.destroy()
            } catch (_: Exception) {}
        }
        childWebViews.clear()
        try {
            webView?.destroy()
        } catch (_: Exception) {}
        webView = null
        super.onDestroyView()
    }
}
