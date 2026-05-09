package kr.co.htdi.mes

import android.annotation.SuppressLint
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
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private val prefs by lazy { getSharedPreferences("htdi_mes_prefs", Context.MODE_PRIVATE) }

    private var serverUrl: String
        get() = prefs.getString("server_url", "") ?: ""
        set(value) = prefs.edit().putString("server_url", value.trim().removeSuffix("/")).apply()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterFullScreen()

        root = FrameLayout(this)
        setContentView(root)

        if (serverUrl.isBlank()) showServerSetup() else showWebView(serverUrl)
    }

    private fun enterFullScreen() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterFullScreen()
    }

    private fun normalizeUrl(raw: String): String {
        val value = raw.trim().removeSuffix("/")
        return if (value.startsWith("http://") || value.startsWith("https://")) value else "https://$value"
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
            textSize = 44f
            setTextColor(Color.rgb(34, 211, 238))
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "서버 주소를 입력하세요"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 22)
        }

        val input = EditText(this).apply {
            setText(if (serverUrl.isNotBlank()) serverUrl else "https://192.168.0.163:4443")
            hint = "예: https://192.168.0.163:4443"
            textSize = 22f
            singleLine = true
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(22, 18, 22, 18)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }

        val status = TextView(this).apply {
            text = ""
            textSize = 18f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 10)
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        val testBtn = Button(this).apply { text = "연결 테스트"; textSize = 20f }
        val saveBtn = Button(this).apply { text = "저장 및 시작"; textSize = 20f }

        row.addView(testBtn, LinearLayout.LayoutParams(230, 72).apply { setMargins(10, 0, 10, 0) })
        row.addView(saveBtn, LinearLayout.LayoutParams(230, 72).apply { setMargins(10, 0, 10, 0) })

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(input, LinearLayout.LayoutParams(780, 72))
        layout.addView(status)
        layout.addView(row)

        root.addView(layout, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        testBtn.setOnClickListener {
            val url = normalizeUrl(input.text.toString())
            status.text = "연결 확인 중..."
            testConnection(url) { ok, msg ->
                runOnUiThread {
                    status.setTextColor(if (ok) Color.rgb(34, 197, 94) else Color.rgb(248, 113, 113))
                    status.text = msg
                }
            }
        }

        saveBtn.setOnClickListener {
            val url = normalizeUrl(input.text.toString())
            serverUrl = url
            showWebView(url)
        }

        title.setOnLongClickListener {
            serverUrl = ""
            Toast.makeText(this, "서버 설정 초기화", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun testConnection(url: String, callback: (Boolean, String) -> Unit) {
        thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                callback(code in 200..399, "연결 결과: HTTP $code")
            } catch (e: Exception) {
                callback(false, "연결 실패: ${e.message ?: "unknown"}")
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showWebView(url: String) {
        root.removeAllViews()

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(2, 6, 23))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = false
            settings.displayZoomControls = false

            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    // HTDI 사내 로컬 인증서 환경을 위해 허용.
                    // 공인 인증서 도입 시 제거 권장.
                    handler?.proceed()
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        showError(url, error?.description?.toString() ?: "페이지 로드 실패")
                    }
                }
            }
        }

        root.addView(webView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        webView.loadUrl(url)
    }

    private fun showError(url: String, message: String) {
        root.removeAllViews()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(60, 40, 60, 40)
            setBackgroundColor(Color.rgb(2, 6, 23))
        }

        val title = TextView(this).apply {
            text = "서버 연결 실패"
            textSize = 34f
            setTextColor(Color.rgb(248, 113, 113))
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val detail = TextView(this).apply {
            text = "$url\n\n$message"
            textSize = 19f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }

        val retry = Button(this).apply {
            text = "다시 연결"
            textSize = 20f
            setOnClickListener { showWebView(serverUrl) }
        }

        val setting = Button(this).apply {
            text = "서버 설정"
            textSize = 20f
            setOnClickListener { showServerSetup() }
        }

        layout.addView(title)
        layout.addView(detail)
        layout.addView(retry, LinearLayout.LayoutParams(260, 72))
        layout.addView(setting, LinearLayout.LayoutParams(260, 72).apply { setMargins(0, 18, 0, 0) })

        root.addView(layout, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            Toast.makeText(this, "HTDI MES 실행 중입니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onBackPressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
