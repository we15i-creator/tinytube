package com.tubesheild

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var torIndicator: TextView

    companion object {
        const val TOR_PROXY = "127.0.0.1"
        const val TOR_PORT = 9050
        const val HOME_PAGE = "https://invidious.fdn.fr" // Private YouTube
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Top bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
        }

        torIndicator = TextView(this).apply {
            text = "🧅 Tor ON"
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            setPadding(0, 0, 8, 0)
        }

        urlBar = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setText(HOME_PAGE)
            textSize = 14f
        }

        val goButton = Button(this).apply {
            text = "Go"
            textSize = 14f
            setOnClickListener { loadUrl(urlBar.text.toString()) }
        }

        topBar.addView(torIndicator)
        topBar.addView(urlBar)
        topBar.addView(goButton)

        // WebView with Tor proxy
        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/115.0"
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)

                // Route through Tor
                setProxy(
                    ProxyConfig.Builder()
                        .addProxyRule("$TOR_PROXY:$TOR_PORT")
                        .addDirect()
                        .build()
                )
            }

            webViewClient = HeraxClient()
            loadUrl(HOME_PAGE)
        }

        layout.addView(topBar)
        layout.addView(webView)
        setContentView(layout)

        // Inject cleaner every page load
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(CLEANER_SCRIPT, null)
                view.evaluateJavascript(FINGERPRINT_SCRIPT, null)
                urlBar.setText(url)
            }
        }
    }

    private fun loadUrl(url: String) {
        val finalUrl = if (!url.startsWith("http")) "https://$url" else url
        urlBar.setText(finalUrl)
        webView.loadUrl(finalUrl)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.evaluateJavascript(SESSION_CLEAR_SCRIPT, null)
        super.onDestroy()
    }
}

// ─── WebView Client ───────────────────────────────

class HeraxClient : WebViewClient() {

    private val blocked = listOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "google-analytics.com", "googletagmanager.com", "facebook.com/tr",
        "amazon-adsystem.com", "scorecardresearch.com", "outbrain.com",
        "taboola.com", "criteo.com", "adnxs.com", "adsrvr.org"
    )

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        return blocked.any { request.url.toString().contains(it) }
    }
}

// ─── Injected Scripts ─────────────────────────────

const val CLEANER_SCRIPT = """
(function() {
    const kill = () => {
        ['ytd-display-ad-renderer','.ytp-ad-module','.ytp-ad-overlay-container',
         'ytd-promoted-video-renderer','#masthead-ad','.ytd-banner-promo-renderer']
        .forEach(s => document.querySelectorAll(s).forEach(e => e.remove()));
        const skip = document.querySelector('.ytp-ad-skip-button');
        if(skip) skip.click();
        const v = document.querySelector('video');
        if(v && document.querySelector('.ytp-ad-player-overlay')) {
            v.playbackRate = 16; v.muted = true;
        }
    };
    kill();
    setInterval(kill, 300);
    new MutationObserver(kill).observe(document.body, {childList:true,subtree:true});
    document.cookie = 'CONSENT=YES+; domain=.youtube.com; path=/';
})();
"""

const val FINGERPRINT_SCRIPT = """
(function() {
    Object.defineProperty(navigator, 'deviceMemory', {get:()=>8});
    Object.defineProperty(navigator, 'hardwareConcurrency', {get:()=>8});
    Object.defineProperty(navigator, 'platform', {get:()=>'Linux x86_64'});
    Object.defineProperty(navigator, 'plugins', {get:()=>[1,2,3,4,5]});
})();
"""

const val SESSION_CLEAR_SCRIPT = """
(function() {
    document.cookie.split(';').forEach(c => {
        document.cookie = c.split('=')[0]+'=;expires=Thu,01 Jan 1970 00:00:00 GMT;path=/';
    });
    try { localStorage.clear(); sessionStorage.clear(); } catch(e) {}
})();
"""
