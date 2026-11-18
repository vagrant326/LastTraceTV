package io.github.vagrant326.atvlasttrace

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private lateinit var web: WebView
    private lateinit var status: TextView
    private var mainFrameFailed = false
    private var awaitingGate = false

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // The page's own Wake Lock needs a button press and is not available in WebView anyway.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = getSharedPreferences("gate", MODE_PRIVATE)
        status = findViewById(R.id.status)
        web = findViewById(R.id.web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        // Nobody will ever touch this screen, so the host's soundtrack must start without a gesture.
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.webViewClient = HostClient()
        web.addJavascriptInterface(GateBridge(), "LastTraceTv")

        // A TV at density 2 gives the page a ~960x540 CSS viewport, smaller than a laptop, and
        // the game's 18+ card already overflows it. The layout leans on vh units, so CSS zoom
        // would distort it; instead the view is laid out larger and scaled down as a whole.
        val root = findViewById<View>(R.id.root)
        val metrics = resources.displayMetrics
        val innerWidth = metrics.widthPixels - root.paddingLeft - root.paddingRight
        val innerHeight = metrics.heightPixels - root.paddingTop - root.paddingBottom
        val zoomOut = maxOf(1f, TARGET_CSS_WIDTH * metrics.density / innerWidth)
        web.layoutParams = FrameLayout.LayoutParams((innerWidth * zoomOut).toInt(), (innerHeight * zoomOut).toInt())
        web.pivotX = 0f
        web.pivotY = 0f
        web.scaleX = 1 / zoomOut
        web.scaleY = 1 / zoomOut

        if (prefs.getBoolean(KEY_ADULT_CONFIRMED, false)) {
            openRoom()
        } else {
            // First launch: the site's own 18+ dialog, answered once with the remote.
            awaitingGate = true
            web.visibility = View.VISIBLE
            web.requestFocus()
            web.loadUrl("${BuildConfig.GAME_URL}/")
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        web.destroy()
        super.onDestroy()
    }

    private fun openRoom() {
        web.visibility = View.INVISIBLE
        status.setText(R.string.status_opening)
        status.visibility = View.VISIBLE
        val language = if (Locale.getDefault().language == "pl") "pl" else "en"
        Thread {
            val session = try {
                RoomClient.createRoom(BuildConfig.GAME_URL, language)
            } catch (e: IOException) {
                null
            } catch (e: JSONException) {
                null
            }
            runOnUiThread {
                if (isDestroyed) {
                    return@runOnUiThread
                }
                if (session == null) {
                    status.setText(R.string.status_retrying)
                    handler.postDelayed(::openRoom, RETRY_MS)
                } else {
                    loadHost(session)
                }
            }
        }.start()
    }

    /**
     * Seeds the game's origin storage the way the page itself would after the 18+ dialog and
     * room creation, then hands over to /host/<code>. The data page is loaded under the game's
     * own origin, so this writes into the same storage the game reads.
     */
    private fun loadHost(session: HostSession) {
        val bootstrap = """
            <script>
            sessionStorage.setItem('os-adult', 'yes');
            if (localStorage.getItem('os-sound') === null) { localStorage.setItem('os-sound', 'yes'); }
            localStorage.setItem('os-host-${session.code}', ${JSONObject.quote(session.json)});
            location.replace('/host/${session.code}');
            </script>
        """.trimIndent()
        web.loadDataWithBaseURL("${BuildConfig.GAME_URL}/", bootstrap, "text/html", "utf-8", null)
    }

    private inner class GateBridge {
        @JavascriptInterface
        fun adultConfirmed() {
            runOnUiThread {
                if (!awaitingGate || isDestroyed) {
                    return@runOnUiThread
                }
                awaitingGate = false
                prefs.edit().putBoolean(KEY_ADULT_CONFIRMED, true).commit()
                openRoom()
            }
        }
    }

    private inner class HostClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // Stay on the game; anything else (e.g. the author's page) has no way back on a TV.
            return request.url.host != Uri.parse(BuildConfig.GAME_URL).host
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            mainFrameFailed = false
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (mainFrameFailed) {
                return
            }
            if (awaitingGate) {
                // The site's dialog does not focus its button, and a remote has no pointer.
                view.evaluateJavascript(GATE_WATCH, null)
            } else if (Uri.parse(url ?: "").path.orEmpty().startsWith("/host/")) {
                status.visibility = View.GONE
                web.visibility = View.VISIBLE
                web.requestFocus()
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) {
                return
            }
            mainFrameFailed = true
            web.visibility = View.INVISIBLE
            status.setText(R.string.status_retrying)
            status.visibility = View.VISIBLE
            handler.postDelayed({
                web.visibility = if (awaitingGate) View.VISIBLE else View.INVISIBLE
                status.visibility = if (awaitingGate) View.GONE else View.VISIBLE
                web.reload()
            }, RETRY_MS)
        }
    }

    private companion object {
        const val KEY_ADULT_CONFIRMED = "adult_confirmed"
        const val RETRY_MS = 5_000L
        const val TARGET_CSS_WIDTH = 1280

        val GATE_WATCH = """
            (function () {
              if (window.__lastTraceGate) { return; }
              window.__lastTraceGate = true;
              var focused = false;
              var timer = setInterval(function () {
                if (sessionStorage.getItem('os-adult') === 'yes') {
                  clearInterval(timer);
                  LastTraceTv.adultConfirmed();
                  return;
                }
                var accept = document.querySelector('.adult-accept');
                if (accept && document.activeElement !== accept && !focused) {
                  accept.focus();
                  focused = true;
                }
              }, 250);
            })();
        """.trimIndent()
    }
}
