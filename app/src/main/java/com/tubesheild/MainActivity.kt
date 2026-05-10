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
        
        // Initialize WebView
        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view: WebView?, url)
                
                // Inject the scripts defined in the companion object
                webView.evaluateJavascript(CLEANER_SCRIPT, null)
                webView.evaluateJavascript(FINGERPRINT_SCRIPT, null)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false // Stay inside the app
            }
        }

        // Load YouTube Mobile
        webView.loadUrl("https://m.youtube.com")
    }

    fun clearSession() {
        webView.evaluateJavascript(SESSION_CLEAR, null)
    }

    // ALL CONSTANTS MUST BE IN THIS SINGLE BLOCK
    companion object {
        const val CLEANER_SCRIPT = """
            (function() {
                var style = document.createElement('style');
                style.innerHTML = `
                    .ad-showing, .ad-container, .ytp-ad-overlay-container, 
                    ytd-ad-slot-renderer, #player-ads, .masthead-ad { 
                        display: none !important; 
                    }
                `;
                document.head.appendChild(style);
                
                // Logic to skip video ads automatically
                setInterval(function() {
                    var skipBtn = document.querySelector('.ytp-ad-skip-button');
                    if (skipBtn) skipBtn.click();
                }, 500);
            })();
        """

        const val FINGERPRINT_SCRIPT = """
            (function() {
                // Basic protection against browser fingerprinting
                Object.defineProperty(navigator, 'webdriver', {get: () => false});
                console.log('Shield Active: Fingerprint spoofing enabled');
            })();
        """

        const val SESSION_CLEAR = """
            (function() {
                window.localStorage.clear();
                window.sessionStorage.clear();
                console.log('Session Cleared');
            })();
        """
    }
}
