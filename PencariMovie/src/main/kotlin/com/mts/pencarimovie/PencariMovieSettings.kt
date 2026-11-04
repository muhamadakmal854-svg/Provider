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
import androidx.appcompat.app.AlertDialog
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
                dialogMessage = "Lalai: http://127.0.0.1:8088 (Local / Companion APK). Untuk Android TV / Stick, masukkan IP LAN (cth: http://192.168.1.50:8088) atau pautan Tunnel."
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
                                Toast.makeText(ctx, "Pelayan TIDAK dapat dihubungi di $currentUrl. Pastikan daemon APK diaktifkan.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    true
                }
            }
            serverCategory.addPreference(testServerPref)

            val apkGuidePref = Preference(ctx).apply {
                title = "Muat Turun APK Pelayan (Android TV / Stick)"
                summary = "Buka pautan https://telegra.my/apk untuk memuat turun pelayan penstriman tempatan"
                setOnPreferenceClickListener {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://telegra.my/apk"))
                        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(browserIntent)
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Pautan: https://telegra.my/apk", Toast.LENGTH_LONG).show()
                    }
                    true
                }
            }
            serverCategory.addPreference(apkGuidePref)

            // Kategori 2: Sesi & Pengesahan Telegram
            val tgCategory = PreferenceCategory(ctx).apply {
                title = "Log Masuk Telegram Web & Kuki Sesi"
            }
            screen.addPreference(tgCategory)

            val tgStatusPref = Preference(ctx).apply {
                title = "Status Sesi Telegram"
                isEnabled = false
            }
            tgCategory.addPreference(tgStatusPref)

            fun updateTgStatus() {
                val p = PreferenceManager.getDefaultSharedPreferences(ctx)
                val cookies = p.getString(PREF_PM_TG_COOKIES, null)
                val hasCookies = !cookies.isNullOrBlank()
                tgStatusPref.summary = if (hasCookies) {
                    "Aktif (Kuki sesi tersimpan)"
                } else {
                    "Belum Log Masuk (Pilihan tambahan untuk scraping pautan DDL)"
                }
            }
            updateTgStatus()

            val tgLoginPref = Preference(ctx).apply {
                title = "Log Masuk Telegram Web (Capture Sesi)"
                summary = "Buka tetingkap WebView untuk log masuk ke akaun Telegram anda dan simpan kuki sesi secara automatik"
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

            val clearTgPref = Preference(ctx).apply {
                title = "Padam Sesi Telegram"
                summary = "Kosongkan kuki sesi Telegram yang tersimpan di peranti ini"
                setOnPreferenceClickListener {
                    val p = PreferenceManager.getDefaultSharedPreferences(ctx)
                    p.edit().remove(PREF_PM_TG_COOKIES).apply()
                    updateTgStatus()
                    Toast.makeText(ctx, "Sesi Telegram telah dikosongkan.", Toast.LENGTH_SHORT).show()
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
            text = "Batal"
            setTextColor(Color.LTGRAY)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { dismiss() }
        }

        val titleView = TextView(ctx).apply {
            text = "Log Masuk Telegram Web"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        val saveBtn = Button(ctx).apply {
            text = "Simpan Kuki"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2481CC"))
            setOnClickListener {
                captureAndSaveCookies()
            }
        }

        toolbar.addView(cancelBtn)
        toolbar.addView(titleView)
        toolbar.addView(saveBtn)
        root.addView(toolbar)

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
                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val cm = CookieManager.getInstance()
                    val cookies = cm.getCookie(url) ?: ""
                    if (cookies.contains("stel_token") || cookies.contains("stel_ssid") || cookies.contains("session")) {
                        captureAndSaveCookies()
                    }
                }
            }

            loadUrl("https://web.telegram.org/k/")
        }

        frame.addView(webView)
        root.addView(frame)

        return root
    }

    private fun captureAndSaveCookies() {
        try {
            val cm = CookieManager.getInstance()
            cm.flush()
            val tgCookies = cm.getCookie("https://web.telegram.org") ?: ""
            val pmCookies = cm.getCookie(PM_MAIN_URL) ?: ""
            val combined = listOf(tgCookies, pmCookies).filter { it.isNotBlank() }.joinToString("; ")

            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            prefs.edit().putString(PREF_PM_TG_COOKIES, combined).apply()

            Toast.makeText(requireContext(), "Kuki sesi berjaya disimpan!", Toast.LENGTH_SHORT).show()
            onSaved?.invoke()
            dismiss()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ralat: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        webView?.destroy()
        webView = null
        super.onDestroyView()
    }
}
