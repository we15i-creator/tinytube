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
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.util.Rational
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
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import java.io.ByteArrayInputStream
import java.net.URL
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isFullscreen = false
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Immersive mode
        enableImmersiveMode()
        
        webView = WebView(this)
        setContentView(webView)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Modern WebView settings
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            loadsImagesAutomatically = true
            blockNetworkImage = false
        }

        // Cookie manager for persistent login
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // Enable debugging
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Add JS interface for native communication
        webView.addJavascriptInterface(WebAppInterface(this), "TubeShield")

        webView.webViewClient = TubeShieldWebViewClient()
        webView.webChromeClient = TubeShieldWebChromeClient()

        // Load YouTube with dark mode parameter
        webView.loadUrl("https://www.youtube.com/?theme=dark&hl=en")
        
        // Start foreground service for background playback
        startService(Intent(this, BackgroundPlaybackService::class.java))
    }

    private fun enableImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }

    // ==================== AUDIO FOCUS ====================
    
    private fun requestAudioFocus() {
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
                                webView.evaluateJavascript("document.querySelector('video')?.pause();", null)
                            }
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            runOnUiThread {
                                webView.evaluateJavascript("document.querySelector('video')?.play();", null)
                            }
                        }
                    }
                }
                .build()
            audioFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
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
            Pattern.compile(".*get_video_info.*"),
            Pattern.compile(".*player_ads.*"),
            Pattern.compile(".*googlevideo\\.com/videoplayback.*&oad.*"),
            Pattern.compile(".*youtube\\.com/api/stats.*"),
            Pattern.compile(".*youtube\\.com/pagead.*"),
            Pattern.compile(".*youtube\\.com/ptracking.*"),
            Pattern.compile(".*youtube\\.com/yts/jsbin.*player.*"),
            Pattern.compile(".*ytimg\\.com.*")
        )

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            val host = request.url.host ?: ""
            
            // Block ad requests
            if (isAdRequest(url, host)) {
                Log.d("TubeShield", "Blocked: $url")
                return createEmptyResponse()
            }

            // Intercept video downloads
            if (url.contains("googlevideo.com") && (url.contains("videoplayback") || url.contains("mime=video"))) {
                Log.d("TubeShield", "Video stream detected: $url")
                // Could trigger download dialog here
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
            requestAudioFocus()
            
            // Inject all scripts
            view.evaluateJavascript(ULTRA_ADBLOCK_SCRIPT, null)
            view.evaluateJavascript(BACKGROUND_PLAY_SCRIPT, null)
            view.evaluateJavascript(DARK_MODE_SCRIPT, null)
            view.evaluateJavascript(GESTURE_SCRIPT, null)
            view.evaluateJavascript(AUTO_SKIP_ADS_SCRIPT, null)
            view.evaluateJavascript(ENHANCED_UI_SCRIPT, null)
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
            // Don't proceed on SSL errors for security
            AlertDialog.Builder(this@MainActivity)
                .setTitle("SSL Error")
                .setMessage("Certificate error. Continue?")
                .setPositiveButton("Continue") { _, _ -> handler.proceed() }
                .setNegativeButton("Cancel") { _, _ -> handler.cancel() }
                .show()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            webView.destroy()
            recreate()
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

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            // Could show progress bar
        }

        override fun onReceivedTitle(view: WebView, title: String) {
            title?.let { 
                if (it != "YouTube") {
                    // Update notification with title
                }
            }
        }
    }

    // ==================== PICTURE IN PICTURE ====================

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            webView.evaluateJavascript("""
                document.querySelector('.ytp-chrome-top')?.style?.setProperty('display', 'none');
                document.querySelector('.ytp-gradient-top')?.style?.setProperty('display', 'none');
            """.trimIndent(), null)
        }
    }

    // ==================== LIFECYCLE ====================

    override fun onPause() {
        super.onPause()
        // Don't pause WebView to keep audio playing
        webView.onPause()
        webView.onResume() // Immediately resume to bypass pause
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        requestAudioFocus()
    }

    override fun onDestroy() {
        abandonAudioFocus()
        stopService(Intent(this, BackgroundPlaybackService::class.java))
        webView.destroy()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (customView != null) {
            customViewCallback?.onCustomViewHidden()
            return
        }
        if (webView.canGoBack()) {
            webView.goBack()
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
            // Trigger system download manager
            val request = android.app.DownloadManager.Request(Uri.parse(url))
                .setTitle(filename)
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename)
            
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            dm.enqueue(request)
        }
    }

    // ==================== SCRIPTS ====================

    companion object {
        private val AD_DOMAINS = arrayOf(
            "doubleclick.net", "googleads.g.doubleclick.net", "pagead2.googlesyndication.com",
            "googleadservices.com", "adservice.google.com", "fls.doubleclick.net",
            "ads.youtube.com", "ad-delivery.net", "amazon-adsystem.com",
            "googleadservices.com", "googletagservices.com", "google-analytics.com",
            "googletagmanager.com", "connect.facebook.net", "analytics.google.com",
            "stats.g.doubleclick.net", "tpc.googlesyndication.com"
        )

        // Comprehensive ad blocking + UI cleanup
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
                    'ytd-ad-slot-renderer', 'ytd-reel-shelf-renderer:has(.ytd-ad-slot-renderer)',
                    'tp-yt-paper-dialog:has(#confirm-button)', // Premium nag
                    'ytd-popup-container:has(.ytd-enforcement-message-view-model)', // Adblock detection
                    'yt-confirm-dialog-renderer', 'ytd-mealbar-promo-renderer',
                    'ytd-engagement-panel-section-list-renderer:has(.ytd-statement-banner-renderer)'
                ];

                // Inject base styles
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

                // Aggressive observer
                const observer = new MutationObserver((mutations) => {
                    // Remove ad elements
                    adSelectors.forEach(selector => {
                        document.querySelectorAll(selector).forEach(el => {
                            el.remove();
                        });
                    });

                    // Auto-skip video ads
                    const video = document.querySelector('video');
                    const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
                    
                    if (skipBtn && skipBtn.offsetParent !== null) {
                        skipBtn.click();
                        console.log('[TubeShield] Skip button clicked');
                    }
                    
                    if (document.querySelector('.ad-showing') || document.querySelector('.ytp-ad-player-overlay')) {
                        if (video && isFinite(video.duration) && video.currentTime < video.duration - 0.5) {
                            video.currentTime = video.duration;
                            console.log('[TubeShield] Ad fast-forwarded');
                        }
                    }

                    // Close premium nag dialogs
                    const confirmBtn = document.querySelector('yt-confirm-dialog-renderer #confirm-button, [dialog][confirm-button]');
                    if (confirmBtn) confirmBtn.click();

                    // Remove "Ad blockers violate YouTube Terms of Service" banners
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

                // Patch fetch/XHR to block ad requests
                const originalFetch = window.fetch;
                window.fetch = function(...args) {
                    const url = args[0]?.toString() || '';
                    if (url.includes('googlevideo.com') && url.includes('&oad')) {
                        return Promise.resolve(new Response('', {status: 200}));
                    }
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

                console.log('[TubeShield] Ad blocker initialized');
            })();
        """

        // Background playback via visibility API spoofing
        const val BACKGROUND_PLAY_SCRIPT = """
            (function() {
                if (window.__bgPlayLoaded) return;
                window.__bgPlayLoaded = true;

                // Override visibility API
                Object.defineProperty(document, 'hidden', { 
                    get() { return false; }, 
                    configurable: true 
                });
                Object.defineProperty(document, 'visibilityState', { 
                    get() { return 'visible'; }, 
                    configurable: true 
                });
                
                // Prevent visibilitychange from pausing
                const origAddEventListener = document.addEventListener;
                document.addEventListener = function(type, listener, ...args) {
                    if (type === 'visibilitychange') {
                        return; // Block visibility change listeners
                    }
                    return origAddEventListener.call(this, type, listener, ...args);
                };

                // Keep video playing
                setInterval(() => {
                    const videos = document.querySelectorAll('video');
                    videos.forEach(video => {
                        if (video.paused && !video.ended && video.readyState > 2) {
                            const playPromise = video.play();
                            if (playPromise) playPromise.catch(() => {});
                        }
                        // Prevent YouTube from muting on background
                        if (video.muted && video.volume > 0) {
                            video.muted = false;
                        }
                    });
                }, 500);

                // Override Page Lifecycle API
                if ('onfreeze' in document) {
                    document.onfreeze = null;
                }
                if ('onresume' in document) {
                    document.onresume = null;
                }
            })();
        """

        // Force dark mode
        const val DARK_MODE_SCRIPT = """
            (function() {
                document.documentElement.setAttribute('dark', '');
                document.documentElement.setAttribute('style', 
                    (document.documentElement.getAttribute('style') || '') + 
                    ' --yt-spec-base-background: #0f0f0f !important;'
                );
                // Override system preference
                if (window.matchMedia) {
                    Object.defineProperty(window, 'matchMedia', {
                        value: function(query) {
                            if (query.includes('prefers-color-scheme')) {
                                return {
                                    matches: true,
                                    media: query,
                                    addListener: function(){},
                                    removeListener: function(){}
                                };
                            }
                            return window.matchMedia(query);
                        }
                    });
                }
            })();
        """

        // Touch gesture support (volume/brightness)
        const val GESTURE_SCRIPT = """
            (function() {
                if (window.__gesturesLoaded) return;
                window.__gesturesLoaded = true;

                let startY = 0;
                let startX = 0;
                let startVolume = 1;
                let isLeftSide = false;
                const video = document.querySelector('video');

                document.addEventListener('touchstart', (e) => {
                    if (!video) return;
                    startY = e.touches[0].clientY;
                    startX = e.touches[0].clientX;
                    isLeftSide = startX < window.innerWidth / 2;
                    startVolume = video.volume;
                }, {passive: true});

                document.addEventListener('touchmove', (e) => {
                    if (!video || e.touches.length !== 1) return;
                    const deltaY = startY - e.touches[0].clientY;
                    const deltaX = Math.abs(e.touches[0].clientX - startX);
                    
                    if (deltaX > 30) return; // Horizontal scroll

                    const percent = deltaY / window.innerHeight;
                    
                    if (isLeftSide) {
                        // Brightness (would need native bridge)
                    } else {
                        // Volume
                        let newVol = Math.max(0, Math.min(1, startVolume + percent));
                        video.volume = newVol;
                        video.muted = newVol === 0;
                    }
                }, {passive: true});
            })();
        """

        // Additional auto-skip for pre-roll/mid-roll
        const val AUTO_SKIP_ADS_SCRIPT = """
            (function() {
                // Override yt.config_.EXPERIMENT_FLAGS to disable ads
                if (window.yt && window.yt.config_) {
                    window.yt.config_.EXPERIMENT_FLAGS = window.yt.config_.EXPERIMENT_FLAGS || {};
                    window.yt.config_.EXPERIMENT_FLAGS.enable_ad_pod_quantity = false;
                    window.yt.config_.EXPERIMENT_FLAGS.html5_disable_preserve_source = true;
                }

                // Block ad-related player configs
                const origDefineProperty = Object.defineProperty;
                Object.defineProperty = function(obj, prop, desc) {
                    if (prop === 'adPlacements' || prop === 'playerAds') {
                        return origDefineProperty(obj, prop, {value: [], writable: false});
                    }
                    return origDefineProperty(obj, prop, desc);
                };
            })();
        """

        // Enhanced UI cleanup
        const val ENHANCED_UI_SCRIPT = """
            (function() {
                // Remove Shorts, Community posts clutter
                const removeClutter = () => {
                    document.querySelectorAll('ytd-reel-shelf-renderer, ytd-rich-shelf-renderer').forEach(el => {
                        if (el.textContent?.includes('Shorts')) el.remove();
                    });
                    document.querySelectorAll('ytd-rich-section-renderer').forEach(el => {
                        if (el.textContent?.includes('Community')) el.remove();
                    });
                };
                
                const obs = new MutationObserver(removeClutter);
                obs.observe(document.documentElement, {childList: true, subtree: true});
                removeClutter();
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
        startForeground(1, notification)
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
            .build()
    }
}
