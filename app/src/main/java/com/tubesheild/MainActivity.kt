package com.tubesheild

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
            }

            webViewClient = PrivacyWebClient()
            loadUrl("https://m.youtube.com")
        }

        setContentView(webView)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}

// ─── Privacy WebView Client ───────────────────────────────

class PrivacyWebClient : WebViewClient() {

    // Block trackers and ad domains
    private val blockedDomains = listOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "google-analytics.com", "googletagmanager.com", "facebook.com/tr",
        "amazon-adsystem.com", "scorecardresearch.com", "outbrain.com",
        "taboola.com", "criteo.com", "adnxs.com", "adsrvr.org"
    )

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        return blockedDomains.any { url.contains(it) }
    }

    override fun onPageFinished(view: WebView, url: String) {
        // Inject YouTube ad-blocking and privacy CSS/JS
        val cleaner = """
            (function() {
                // Remove ad elements
                const hideAds = () => {
                    document.querySelectorAll(
                        'ytd-display-ad-renderer, .ytp-ad-module, ' +
                        'ytd-promoted-video-renderer, #masthead-ad, ' +
                        'ytd-compact-promoted-video-renderer, .ytd-banner-promo-renderer'
                    ).forEach(el => el.remove());
                };

                // Remove tracking attributes from links
                const cleanLinks = () => {
                    document.querySelectorAll('a[href*="googleadservices"]').forEach(el => {
                        el.href = '#';
                        el.onclick = () => false;
                    });
                };

                // Watch for dynamically loaded ads
                const observer = new MutationObserver(() => {
                    hideAds();
                    cleanLinks();
                });

                observer.observe(document.body, {
                    childList: true,
                    subtree: true
                });

                hideAds();
                cleanLinks();

                // Block cookie consent popups
                document.cookie = "CONSENT=YES+; domain=.youtube.com; path=/";
            })();
        """.trimIndent()

        view.evaluateJavascript(cleaner, null)
    }
}
