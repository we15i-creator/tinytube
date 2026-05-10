package com.tubesheild

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText

    companion object {
        const val HOME_PAGE = "https://invidious.fdn.fr"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Top bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
        }

        val torLabel = TextView(this).apply {
            text = "🧅 Herax"
            setTextColor(0xFF00CC66.toInt())
            textSize = 14f
            setPadding(0, 0, 8, 0)
        }

        urlBar = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setText(HOME_PAGE)
            textSize = 14f
        }

        val goBtn = Button(this).apply {
            text = "Go"
            textSize = 12f
            setOnClickListener { loadUrl(urlBar.text.toString()) }
        }

        topBar.addView(torLabel)
        topBar.addView(urlBar)
        topBar.addView(goBtn)

        // WebView
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
            }

            webViewClient = object : WebViewClient() {

                private val blocked = listOf(
                    "doubleclick.net", "googleadservices.com", "googlesyndication.com",
                    "google-analytics.com", "googletagmanager.com", "facebook.com/tr",
                    "amazon-adsystem.com", "scorecardresearch.com", "outbrain.com",
                    "taboola.com", "criteo.com", "adnxs.com", "adsrvr.org"
                )

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    return blocked.any { request.url.toString().contains(it) }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    urlBar.setText(url)
                    view.evaluateJavascript(CLEANER_SCRIPT, null)
                    view.evaluateJavascript(FINGERPRINT_SCRIPT, null)
                }
            }

            loadUrl(HOME_PAGE)
        }

        layout.addView(topBar)
        layout.addView(webView)
        setContentView(layout)
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
        webView.evaluateJavascript(SESSION_CLEAR, null)
        super.onDestroy()
    }

    companion object Scripts {
        const val CLEANER_SCRIPT = """
        (function(){var k=function(){
        ['ytd-display-ad-renderer','.ytp-ad-module','ytd-promoted-video-renderer',
        '#masthead-ad','.ytd-banner-promo-renderer','.ytp-ad-overlay-container']
        .forEach(function(s){document.querySelectorAll(s).forEach(function(e){e.remove()})});
        var skip=document.querySelector('.ytp-ad-skip-button,.ytp-ad-skip-button-modern');
        if(skip)skip.click();
        var v=document.querySelector('video');
        if(v&&document.querySelector('.ytp-ad-player-overlay')){v.playbackRate=16;v.muted=true}
        };k();setInterval(k,300);
        new MutationObserver(k).observe(document.body,{childList:true,subtree:true});
        document.cookie='CONSENT=YES+; domain=.youtube.com; path=/';})();
        """

        const val FINGERPRINT_SCRIPT = """
        (function(){
        Object.defineProperty(navigator,'deviceMemory',{get:function(){return 8}});
        Object.defineProperty(navigator,'hardwareConcurrency',{get:function(){return 8}});
        Object.defineProperty(navigator,'platform',{get:function(){return 'Linux x86_64'}});
        Object.defineProperty(navigator,'plugins',{get:function(){return [1,2,3,4,5]}});
        })();
        """

        const val SESSION_CLEAR = """
        (function(){
        document.cookie.split(';').forEach(function(c){
        document.cookie=c.split('=')[0]+'=;expires=Thu,01 Jan 1970 00:00:00 GMT;path=/';
        });
        try{localStorage.clear();sessionStorage.clear()}catch(e){}
        })();
        """
    }
}
