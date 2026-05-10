package com.tubesheild

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PictureInPictureParams
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import java.io.ByteArrayInputStream
import java.net.URL
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    private var webView: WebView? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isFullscreen = false
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var isDestroyed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // FIX: Initialize AudioManager before anything else
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // FIX: Create WebView and set content FIRST
        webView = WebView(this)
        setContentView(webView)
        
        // FIX: NOW enable immersive mode after setContentView()
        enableImmersiveMode()

        // WebView settings
        webView?.settings?.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.0.36 Chrome/126.0.0.0 Mobile Safari/537.36"
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            loadsImagesAutomatically = true
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView?.addJavascriptInterface(WebAppInterface(this), "TubeShield")
        webView?.webViewClient = TubeShieldWebViewClient()
        webView?.webChromeClient = TubeShieldWebChromeClient()

        webView?.loadUrl("https://www.youtube.com/?theme=dark&hl=en")
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, BackgroundPlaybackService::class.java))
            } else {
                startService(Intent(this, BackgroundPlaybackService::class.java))
            }
        } catch (e: Exception) {
            Log.e("TubeShield", "Failed to start service: ${e.message}")
        }
    }

    // FIX: Safe immersive mode that works before/after decor view
    private fun enableImmersiveMode() {
        // Method 1: Android 11+ (R) - safe after setContentView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                window.insetsController?.let {
                    it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } catch (e: Exception) {
                // Fallback if insetsController is null
                enableImmersiveLegacy()
            }
        } else {
            // Method 2: Legacy flags - works on all API levels
            enableImmersiveLegacy()
        }
    }

    // FIX: Legacy immersive mode using deprecated but reliable flags
    @Suppress("DEPRECATION")
    private fun enableImmersiveLegacy() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    // ==================== AUDIO FOCUS ====================
    
    private fun requestAudioFocus() {
        if (isDestroyed || audioManager == null) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            runOnUiThread {
                                if (!isDestroyed) {
                                    webView?.evaluateJavascript("document.querySelector('video')?.pause();", null)
                                }
                            }
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            runOnUiThread {
                                if (!isDestroyed) {
                                    webView?.evaluateJavascript("document.querySelector('video')?.play();", null)
                                }
                            }
                        }
                    }
                }
                .build()
            audioFocusRequest = focusRequest
            audioManager?.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    // ==================== WEBVIEW CLIENT ====================

    inner class TubeShieldWebViewClient : WebViewClient() {
        
        private val adPatterns = listOf(
            Pattern.compile(".*doubleclick\\.net.*"),
            Pattern.compile(".*googleads.*"),
            Pattern.compile(".*googlesyndication\\.com.*"),
            Pattern.compile(".*googleadservices\\.com.*"),
            Pattern.compile(".*adservice\\.google\\.com.*"),
            Pattern.compile(".*ads\\.youtube\\.com.*"),
            Pattern.compile(".*ad-delivery\\.net.*"),
            Pattern.compile(".*amazon-adsystem\\.com.*"),
            Pattern.compile(".*facebook\\.com/tr.*"),
            Pattern.compile(".*google-analytics\\.com.*"),
            Pattern.compile(".*googletagmanager\\.com.*"),
            Pattern.compile(".*pagead.*"),
            Pattern.compile(".*ad_status.*"),
            Pattern.compile(".*youtube\\.com/api/stats.*"),
            Pattern.compile(".*youtube\\.com/pagead.*"),
            Pattern.compile(".*youtube\\.com/ptracking.*")
        )

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            val host = request.url.host ?: ""
            
            if (isAdRequest(url, host)) {
                Log.d("TubeShield", "Blocked: $url")
                return createEmptyResponse()
            }

            return super.shouldInterceptRequest(view, request)
        }

        @Suppress("DEPRECATION")
        override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
            val host = try { URL(url).host } catch (e: Exception) { "" }
            if (isAdRequest(url, host)) {
                return createEmptyResponse()
            }
            return super.shouldInterceptRequest(view, url)
        }

        private fun isAdRequest(url: String, host: String): Boolean {
            return adPatterns.any { it.matcher(url).matches() } ||
                    AD_DOMAINS.any { host.contains(it, ignoreCase = true) } ||
                    url.contains("ad_status=", ignoreCase = true) ||
                    url.contains("googleads", ignoreCase = true)
        }

        private fun createEmptyResponse(): WebResourceResponse {
            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            if (isDestroyed) return
            
            requestAudioFocus()
            
            view.postDelayed({
                if (!isDestroyed) {
                    view.evaluateJavascript(ULTRA_ADBLOCK_SCRIPT, null)
                    view.evaluateJavascript(BACKGROUND_PLAY_SCRIPT, null)
                    view.evaluateJavascript(DARK_MODE_SCRIPT, null)
                    view.evaluateJavascript(AUTO_SKIP_ADS_SCRIPT, null)
                }
            }, 1000)
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
            if (isFinishing || isDestroyed) {
                handler.cancel()
                return
            }
            
            try {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("SSL Error")
                    .setMessage("Certificate error. Continue?")
                    .setPositiveButton("Continue") { _, _ -> handler.proceed() }
                    .setNegativeButton("Cancel") { _, _ -> handler.cancel() }
                    .setOnCancelListener { handler.cancel() }
                    .show()
            } catch (e: Exception) {
                handler.cancel()
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.e("TubeShield", "Render process gone")
            if (!isDestroyed) {
                webView?.destroy()
                webView = null
                recreate()
            }
            return true
        }
    }

    // ==================== WEB CHROME CLIENT ====================

    inner class TubeShieldWebChromeClient : WebChromeClient() {
        
        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (customView != null) {
                callback.onCustomViewHidden()
                return
            }
            customView = view
            customViewCallback = callback
            
            (window.decorView as FrameLayout).addView(view, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            
            isFullscreen = true
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        override fun onHideCustomView() {
            (window.decorView as FrameLayout).removeView(customView)
            customView = null
            customViewCallback?.onCustomViewHidden()
            isFullscreen = false
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // ==================== PICTURE IN PICTURE ====================

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Log.e("TubeShield", "PiP error: ${e.message}")
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            webView?.evaluateJavascript("""
                document.querySelector('.ytp-chrome-top')?.style?.setProperty('display', 'none');
                document.querySelector('.ytp-gradient-top')?.style?.setProperty('display', 'none');
            """.trimIndent(), null)
        }
    }

    // ==================== LIFECYCLE ====================

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        requestAudioFocus()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        isDestroyed = true
        abandonAudioFocus()
        
        try {
            stopService(Intent(this, BackgroundPlaybackService::class.java))
        } catch (e: Exception) {
            Log.e("TubeShield", "Stop service error: ${e.message}")
        }
        
        webView?.let { wv ->
            wv.stopLoading()
            wv.loadUrl("about:blank")
            wv.clearHistory()
            wv.removeAllViews()
            wv.destroy()
        }
        webView = null
        
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (customView != null) {
            customViewCallback?.onCustomViewHidden()
            return
        }
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // ==================== JS INTERFACE ====================

    class WebAppInterface(private val context: Context) {
        @JavascriptInterface
        fun showToast(message: String) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        @JavascriptInterface
        fun downloadFile(url: String, filename: String) {
            try {
                val request = android.app.DownloadManager.Request(Uri.parse(url))
                    .setTitle(filename)
                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename)
                
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                dm.enqueue(request)
            } catch (e: Exception) {
                Log.e("TubeShield", "Download error: ${e.message}")
            }
        }
    }

    // ==================== SCRIPTS ====================

    companion object {
        private val AD_DOMAINS = arrayOf(
            "doubleclick.net", "googleads.g.doubleclick.net", "pagead2.googlesyndication.com",
            "googleadservices.com", "adservice.google.com", "fls.doubleclick.net",
            "ads.youtube.com", "ad-delivery.net", "amazon-adsystem.com",
            "googletagservices.com", "google-analytics.com",
            "googletagmanager.com", "connect.facebook.net", "analytics.google.com",
            "stats.g.doubleclick.net", "tpc.googlesyndication.com"
        )

        const val ULTRA_ADBLOCK_SCRIPT = """
            (function() {
                if (window.__tubeShieldLoaded) return;
                window.__tubeShieldLoaded = true;

                const adSelectors = [
                    'ytd-ad-slot-renderer', 'ytm-promoted-video-renderer', 
                    '.ad-showing', '.ad-container', '.ytp-ad-overlay-container',
                    'div#player-ads', '.masthead-ad', '.ytd-carousel-ad-render',
                    '[class*="ytd-ad-"]', '[id*="ad-"]', '.ytp-ad-button',
                    'ytd-display-ad-renderer', 'ytd-promoted-sparkles-web-renderer',
                    'ytd-promoted-video-renderer', 'ytd-banner-promo-renderer',
                    'ytd-statement-banner-renderer', 'ytd-in-feed-ad-layout-renderer',
                    'ytd-reel-shelf-renderer:has(.ytd-ad-slot-renderer)',
                    'tp-yt-paper-dialog:has(#confirm-button)',
                    'ytd-popup-container:has(.ytd-enforcement-message-view-model)',
                    'yt-confirm-dialog-renderer', 'ytd-mealbar-promo-renderer',
                    'ytd-engagement-panel-section-list-renderer:has(.ytd-statement-banner-renderer)'
                ];

                const style = document.createElement('style');
                style.id = 'tube-shield-styles';
                style.innerHTML = adSelectors.join(',') + ` {
                    display: none !important; 
                    visibility: hidden !important;
                    height: 0px !important;
                    width: 0px !important;
                    opacity: 0 !important;
                    pointer-events: none !important;
                }
                .ytp-chrome-top-buttons { display: none !important; }
                .ytp-paid-content-overlay { display: none !important; }
                `;
                document.head.appendChild(style);

                const observer = new MutationObserver(() => {
                    adSelectors.forEach(selector => {
                        document.querySelectorAll(selector).forEach(el => el.remove());
                    });

                    const video = document.querySelector('video');
                    const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
                    
                    if (skipBtn && skipBtn.offsetParent !== null) {
                        skipBtn.click();
                    }
                    
                    if (document.querySelector('.ad-showing') || document.querySelector('.ytp-ad-player-overlay')) {
                        if (video && isFinite(video.duration) && video.currentTime < video.duration - 0.5) {
                            video.currentTime = video.duration;
                        }
                    }

                    document.querySelectorAll('yt-confirm-dialog-renderer, ytd-enforcement-message-view-model').forEach(el => {
                        const closest = el.closest('tp-yt-paper-dialog, ytd-popup-container');
                        if (closest) closest.remove();
                    });
                });

                observer.observe(document.documentElement, { 
                    childList: true, 
                    subtree: true,
                    attributes: true,
                    attributeFilter: ['class', 'style']
                });

                const originalFetch = window.fetch;
                window.fetch = function(...args) {
                    const url = args[0]?.toString() || '';
                    if (url.includes('pagead') || url.includes('googlesyndication') || url.includes('doubleclick')) {
                        return Promise.resolve(new Response('', {status: 200}));
                    }
                    return originalFetch.apply(this, args);
                };

                const originalXHROpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url, ...rest) {
                    if (url.toString().includes('pagead') || url.toString().includes('googlesyndication')) {
                        this.send = () => {};
                    }
                    return originalXHROpen.call(this, method, url, ...rest);
                };
            })();
        """

        const val BACKGROUND_PLAY_SCRIPT = """
            (function() {
                if (window.__bgPlayLoaded) return;
                window.__bgPlayLoaded = true;

                Object.defineProperty(document, 'hidden', { 
                    get() { return false; }, 
                    configurable: true 
                });
                Object.defineProperty(document, 'visibilityState', { 
                    get() { return 'visible'; }, 
                    configurable: true 
                });
                
                const origAddEventListener = document.addEventListener;
                document.addEventListener = function(type, listener, ...args) {
                    if (type === 'visibilitychange') return;
                    return origAddEventListener.call(this, type, listener, ...args);
                };

                setInterval(() => {
                    const videos = document.querySelectorAll('video');
                    videos.forEach(video => {
                        if (video.paused && !video.ended && video.readyState > 2) {
                            const playPromise = video.play();
                            if (playPromise) playPromise.catch(() => {});
                        }
                        if (video.muted && video.volume > 0) {
                            video.muted = false;
                        }
                    });
                }, 500);

                if ('onfreeze' in document) document.onfreeze = null;
                if ('onresume' in document) document.onresume = null;
            })();
        """

        const val DARK_MODE_SCRIPT = """
            (function() {
                document.documentElement.setAttribute('dark', '');
                document.documentElement.style.setProperty('--yt-spec-base-background', '#0f0f0f', 'important');
                
                if (window.matchMedia) {
                    const origMatchMedia = window.matchMedia;
                    window.matchMedia = function(query) {
                        if (query.includes('prefers-color-scheme')) {
                            return {
                                matches: true,
                                media: query,
                                addListener: function(){},
                                removeListener: function(){},
                                addEventListener: function(){},
                                removeEventListener: function(){},
                                dispatchEvent: function(){ return true; }
                            };
                        }
                        return origMatchMedia(query);
                    };
                }
            })();
        """

        const val AUTO_SKIP_ADS_SCRIPT = """
            (function() {
                if (window.yt && window.yt.config_) {
                    window.yt.config_.EXPERIMENT_FLAGS = window.yt.config_.EXPERIMENT_FLAGS || {};
                    window.yt.config_.EXPERIMENT_FLAGS.enable_ad_pod_quantity = false;
                }

                const origDefineProperty = Object.defineProperty;
                Object.defineProperty = function(obj, prop, desc) {
                    if (prop === 'adPlacements' || prop === 'playerAds') {
                        return origDefineProperty(obj, prop, {value: [], writable: false});
                    }
                    return origDefineProperty(obj, prop, desc);
                };
            })();
        """
    }
}

// ==================== FOREGROUND SERVICE ====================

class BackgroundPlaybackService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, notification)
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "tube_shield_bg",
                "Background Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps YouTube playing in background"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, "tube_shield_bg")
            .setContentTitle("TubeShield")
            .setContentText("Playing in background")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()
    }
}
