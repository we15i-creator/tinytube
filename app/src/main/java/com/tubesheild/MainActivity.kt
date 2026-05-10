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
            mediaPlaybackRequiresUserGesture = false // Allows background play start
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // Injecting scripts
                view.evaluateJavascript(CLEANER_SCRIPT, null)
                view.evaluateJavascript(FINGERPRINT_SCRIPT, null)
                view.evaluateJavascript(BACKGROUND_PLAY_SCRIPT, null)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return false 
            }
        }

        webView.loadUrl("https://m.youtube.com")
    }

    // IMPORTANT: Keep empty to prevent WebView from pausing when app is minimized
    override fun onPause() {
        // super.onPause() is omitted purposely to keep audio alive
        super.onPause()
    }

    companion object {
        const val CLEANER_SCRIPT = """
            (function() {
                var style = document.createElement('style');
                style.innerHTML = `
                    .ad-showing, .ad-container, .ytp-ad-overlay-container, 
                    ytd-ad-slot-renderer, #player-ads, .masthead-ad,
                    .ytp-ad-progress-list { 
                        display: none !important; 
                    }
                `;
                document.head.appendChild(style);
                
                // Auto-skip mid-roll and pre-roll ads
                setInterval(function() {
                    var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern');
                    if (skipBtn) skipBtn.click();
                    
                    // Remove ad overlays if they appear
                    var adOverlay = document.querySelector('.ytp-ad-overlay-close-button');
                    if (adOverlay) adOverlay.click();
                }, 500);
            })();
        """

        const val BACKGROUND_PLAY_SCRIPT = """
            (function() {
                // Prevents YouTube from pausing when the tab/app is hidden
                Object.defineProperty(document, 'hidden', { value: false, writable: false });
                Object.defineProperty(document, 'visibilityState', { value: 'visible', writable: false });
                document.dispatchEvent(new Event('visibilitychange'));
                
                // Keep the video element playing even if the page tries to pause it
                setInterval(function() {
                    var video = document.querySelector('video');
                    if (video && video.paused && !video.ended) {
                        video.play();
                    }
                }, 1000);
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
