package com.tubesheild

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream
import java.net.URL

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
            mediaPlaybackRequiresUserGesture = false
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }

        webView.webViewClient = object : WebViewClient() {
            
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                val host = try { URL(url).host } catch (e: Exception) { "" }

                if (AD_DOMAINS.any { host.contains(it) } || url.contains("googlesyndication") || url.contains("ad_status=")) {
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(ULTRA_ADBLOCK_SCRIPT, null)
                view.evaluateJavascript(BACKGROUND_PLAY_SCRIPT, null)
            }
        }

        webView.loadUrl("https://www.youtube.com")
    }

    override fun onPause() {
        // Keep playing in background by not pausing WebView
        super.onPause()
    }

    companion object {
        private val AD_DOMAINS = arrayOf(
            "doubleclick.net", "googleads.g.doubleclick.net", "pagead2.googlesyndication.com",
            "googleadservices.com", "adservice.google.com", "fls.doubleclick.net",
            "ads.youtube.com", "ad-delivery.net", "amazon-adsystem.com"
        )

        const val ULTRA_ADBLOCK_SCRIPT = """
            (function() {
                const style = document.createElement('style');
                style.innerHTML = `
                    ytd-ad-slot-renderer, ytm-promoted-video-renderer, 
                    .ad-showing, .ad-container, .ytp-ad-overlay-container,
                    div#player-ads, .masthead-ad, .ytd-carousel-ad-render,
                    [class*="ytd-ad-"], [id*="ad-"], .ytp-ad-button { 
                        display: none !important; 
                        visibility: hidden !important;
                        height: 0px !important;
                    }
                `;
                document.head.appendChild(style);

                const observer = new MutationObserver(() => {
                    const video = document.querySelector('video');
                    const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern');
                    
                    if (skipBtn) {
                        skipBtn.click();
                    } else if (document.querySelector('.ad-showing')) {
                        if (video && isFinite(video.duration)) {
                            video.currentTime = video.duration;
                        }
                    }
                    
                    const confirmBtn = document.querySelector('yt-confirm-dialog-renderer #confirm-button');
                    if (confirmBtn) confirmBtn.click();
                });

                observer.observe(document.body, { childList: true, subtree: true });
            })();
        """

        const val BACKGROUND_PLAY_SCRIPT = """
            (function() {
                Object.defineProperty(document, 'hidden', { value: false, writable: false });
                Object.defineProperty(document, 'visibilityState', { value: 'visible', writable: false });
                document.dispatchEvent(new Event('visibilitychange'));
                
                setInterval(() => {
                    const video = document.querySelector('video');
                    if (video && video.paused && !video.ended) {
                        video.play();
                    }
                }, 1000);
            })();
        """
    }
}
