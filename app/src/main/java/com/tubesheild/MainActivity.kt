package com.tubesheild

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // Injecting scripts
                view.evaluateJavascript(CLEANER_SCRIPT, null)
                view.evaluateJavascript(FINGERPRINT_SCRIPT, null)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return false 
            }
        }

        webView.loadUrl("https://m.youtube.com")
    }

    fun clearSession() {
        webView.evaluateJavascript(SESSION_CLEAR, null)
    }

    companion object {
        const val CLEANER_SCRIPT = """
            (function() {
                var style = document.createElement('style');
                style.innerHTML = '.ad-showing, ytd-ad-slot-renderer { display: none !important; }';
                document.head.appendChild(style);
                
                setInterval(function() {
                    var skipBtn = document.querySelector('.ytp-ad-skip-button');
                    if (skipBtn) skipBtn.click();
                }, 500);
            })();
        """

        const val FINGERPRINT_SCRIPT = """
            (function() {
                Object.defineProperty(navigator, 'webdriver', {get: () => false});
            })();
        """

        const val SESSION_CLEAR = """
            (function() {
                window.localStorage.clear();
                window.sessionStorage.clear();
            })();
        """
    }
}
