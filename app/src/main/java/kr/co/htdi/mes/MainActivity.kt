package kr.co.htdi.mes

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.net.http.SslError
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    companion object {
        private const val DEFAULT_SERVER_URL = "https://192.168.0.50:4443"
    }

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView

    private val prefs by lazy {
        getSharedPreferences("htdi_mes_prefs", Context.MODE_PRIVATE)
    }

    private var serverUrl: String
        get() = prefs.getString("server_url", "") ?: ""
        set(value) = prefs.edit().putString(
            "server_url",
            value.trim().removeSuffix("/")
        ).apply()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        root = FrameLayout(this)
        setContentView(root)

        migrateServerUrlIfNeeded()

        if (serverUrl.isBlank()) {
            showServerSetup()
        } else {
            showWebView(serverUrl)
        }
    }

    private fun migrateServerUrlIfNeeded() {
        val saved = serverUrl

        if (
            saved.isBlank() ||
            saved.contains("192.168.0.163") ||
            saved.contains("192.168.0.50:443")
        ) {
            serverUrl = DEFAULT_SERVER_URL
        }
    }

    private fun normalizeUrl(raw: String): String {
        val value = raw.trim().removeSuffix("/")

        return if (
            value.startsWith("http://") ||
            value.startsWith("https://")
        ) {
            value
        } else {
            "https://$value"
        }
    }

    private fun showServerSetup() {

        root.removeAllViews()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(60, 40, 60, 40)
            setBackgroundColor(Color.rgb(2, 6, 23))
        }

        val title = TextView(this).apply {
            text = "HTDI MES"
            textSize = 40f
            setTextColor(Color.CYAN)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Enter server URL"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        val input = EditText(this).apply {
            setText(
                if (serverUrl.isNotBlank())
                    serverUrl
                else
                    DEFAULT_SERVER_URL
            )

            hint = "https://server-address"
            textSize = 20f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setBackgroundColor(Color.rgb(15, 23, 42))
            setPadding(20, 20, 20, 20)
        }

        val status = TextView(this).apply {
            text = ""
            textSize = 18f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        val testBtn = Button(this).apply {
            text = "Connection Test"
        }

        val saveBtn = Button(this).apply {
            text = "Save and Start"
        }

        testBtn.setOnClickListener {

            val url = normalizeUrl(input.text.toString())

            status.text = "Testing connection..."

            testConnection(url) { ok, msg ->

                runOnUiThread {

                    status.text = msg

                    status.setTextColor(
                        if (ok)
                            Color.GREEN
                        else
                            Color.RED
                    )
                }
            }
        }

        saveBtn.setOnClickListener {

            val url = normalizeUrl(input.text.toString())

            serverUrl = url

            showWebView(url)
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(input, LinearLayout.LayoutParams(800, 120))
        layout.addView(status)

        layout.addView(testBtn)
        layout.addView(saveBtn)

        root.addView(layout)
    }

    private fun testConnection(
        url: String,
        callback: (Boolean, String) -> Unit
    ) {

        thread {

            try {

                val conn = URL(url).openConnection() as HttpURLConnection

                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.requestMethod = "GET"

                val code = conn.responseCode

                callback(
                    code in 200..399,
                    "Connection OK : HTTP $code"
                )

            } catch (e: Exception) {

                callback(
                    false,
                    "Connection Failed : ${e.message}"
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showWebView(url: String) {

        root.removeAllViews()

        webView = WebView(this)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mixedContentMode =
            WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.addJavascriptInterface(HtdiMesBridge(), "Android")
        webView.addJavascriptInterface(HtdiMesBridge(), "htdiApp")

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {

                handler?.proceed()
            }
        }

        root.addView(webView)

        webView.loadUrl(url)
    }

    private inner class HtdiMesBridge {

        @JavascriptInterface
        fun exitApp() {
            runOnUiThread {
                cleanupWebViewAndExit()
            }
        }

        @JavascriptInterface
        fun exit() {
            exitApp()
        }
    }

    private fun cleanupWebViewAndExit() {
        try {
            if (::webView.isInitialized) {
                webView.stopLoading()
                webView.clearHistory()
                webView.clearCache(true)
                webView.loadUrl("about:blank")
            }

            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        } catch (_: Exception) {
        }

        try {
            finishAndRemoveTask()
        } catch (_: Exception) {
            finish()
        }
    }

    override fun onBackPressed() {

        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?
    ): Boolean {

        if (keyCode == KeyEvent.KEYCODE_BACK) {

            onBackPressed()

            return true
        }

        return super.onKeyDown(keyCode, event)
    }
}
