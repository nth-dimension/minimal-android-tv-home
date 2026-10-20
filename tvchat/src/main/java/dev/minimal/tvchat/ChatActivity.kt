package dev.minimal.tvchat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * WhatsApp Web in a WebView, driven by the TV remote through an on-screen cursor.
 * Calls use the TV's USB camera and mic through the page's normal camera permission prompt.
 */
class ChatActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private lateinit var cursor: CursorView
    private var lastBack = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Calls and reading chats shouldn't be interrupted by the screensaver.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WebView.setWebContentsDebuggingEnabled(true) // TEMP: diagnosing calls; remove before done
        trimMemory()
        val scale = intent?.getFloatExtra(EXTRA_RENDER_SCALE, RENDER_SCALE)?.coerceIn(1f, 2f) ?: RENDER_SCALE
        web = WebView(scaledContext(scale))
        allowIsolation()
        lightenCalls()
        cursor = CursorView(this)
        val metrics = resources.displayMetrics
        root = FrameLayout(this).apply {
            addView(web, FrameLayout.LayoutParams((metrics.widthPixels / scale).toInt(), (metrics.heightPixels / scale).toInt()))
            addView(cursor, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        web.pivotX = 0f
        web.pivotY = 0f
        web.scaleX = scale
        web.scaleY = scale
        Log.i(TAG, "Rendering at 1/$scale: ${(metrics.widthPixels / scale).toInt()}x${(metrics.heightPixels / scale).toInt()} px")
        setContentView(root)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            // WhatsApp Web only serves supported desktop browsers.
            userAgentString = desktopUserAgent(WebSettings.getDefaultUserAgent(this@ChatActivity))
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        // Chrome also describes itself in client hints (navigator.userAgentData and Sec-CH-UA headers),
        // where WebView says "Android WebView" on a phone. Make those say desktop Chrome too.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            val version = Regex("Chrome/([\\d.]+)").find(WebSettings.getDefaultUserAgent(this))?.groupValues?.get(1) ?: "140.0.0.0"
            val major = version.substringBefore('.')
            WebSettingsCompat.setUserAgentMetadata(
                web.settings,
                UserAgentMetadata.Builder()
                    .setBrandVersionList(
                        listOf(
                            UserAgentMetadata.BrandVersion.Builder().setBrand("Google Chrome").setMajorVersion(major).setFullVersion(version).build(),
                            UserAgentMetadata.BrandVersion.Builder().setBrand("Chromium").setMajorVersion(major).setFullVersion(version).build(),
                            UserAgentMetadata.BrandVersion.Builder().setBrand("Not=A?Brand").setMajorVersion("24").setFullVersion("24.0.0.0").build(),
                        ),
                    )
                    .setFullVersion(version)
                    .setPlatform("Linux")
                    .setPlatformVersion("6.8.0")
                    .setArchitecture("x86")
                    .setBitness(64)
                    .setMobile(false)
                    .setModel("")
                    .build(),
            )
        }
        CookieManager.getInstance().setAcceptCookie(true)
        web.webViewClient = object : WebViewClient() {
            // Stay on WhatsApp. Anything else (shared links) is refused rather than opened in here.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !isAllowed(request.url)

            // The page's renderer can be killed when the TV runs short of memory (seen mid-call).
            // Without this the whole app crashes; instead, start over with a fresh WebView.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                Log.w(TAG, "Renderer gone (crashed: ${detail.didCrash()}); reloading")
                (view.parent as? FrameLayout)?.removeView(view)
                view.destroy()
                recreate()
                return true
            }

            // Logs which browser features the page can see, to tell why calling is or isn't offered.
            // Feature checks only; nothing from the page's content.
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(FEATURE_PROBE) { Log.i(TAG, "Features: $it") }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val allowed = setOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                val wanted = request.resources.filter { it in allowed }.toTypedArray()
                Log.i(TAG, "Page asks for ${request.resources.joinToString()} from ${request.origin}")
                if (isAllowed(request.origin) && wanted.isNotEmpty()) request.grant(wanted) else request.deny()
            }
        }

        val missing = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)

        // After a renderer crash there's no saved page to restore, so load WhatsApp afresh.
        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) web.loadUrl(HOME)
        cursor.post { cursor.moveTo(cursor.width / 2f, cursor.height / 2f) }
    }

    /**
     * Painting the page at full HD costs this TV about half a core during a call. The WebView is
     * created with a lower screen density and laid out at 1/[scale] size, then scaled up: same page
     * layout, a fraction of the pixels to paint, slightly softer text.
     */
    private fun scaledContext(scale: Float): Context {
        if (scale <= 1f) return this
        val config = Configuration(resources.configuration).apply { densityDpi = (densityDpi / scale).toInt() }
        return ContextThemeWrapper(this, theme).apply { applyOverrideConfiguration(config) }
    }

    /**
     * WhatsApp's calling engine needs SharedArrayBuffer shared with its worker threads, which Chrome
     * only allows on cross-origin isolated pages. WebView can't isolate pages unless the app lists
     * the origin (androidx.webkit 1.18, WebView 15x). Without this, WhatsApp says the browser
     * doesn't support calling.
     */
    private fun allowIsolation() {
        val supported = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.CROSS_ORIGIN_ISOLATED_ALLOWLIST)
        Log.i(TAG, "Cross-origin isolation allowlist supported: $supported")
        if (!supported) return
        runCatching {
            ProfileStore.getInstance().getOrCreateProfile(Profile.DEFAULT_PROFILE_NAME)
                .setCrossOriginIsolatedAllowlist(setOf("https://web.whatsapp.com"))
        }.onFailure { Log.w(TAG, "Could not allow isolation: $it") }
    }

    /**
     * The TV has 3 GB and a four-core chip; a video call needs most of both. Ask Android to drop
     * every other app's background processes first (a normal permission; the apps restart when used).
     */
    private fun trimMemory() {
        val am = getSystemService(ActivityManager::class.java) ?: return
        val before = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        packageManager.getInstalledPackages(0)
            .map { it.packageName }
            .filter { it != packageName && it != "android" && !it.startsWith("com.android.") && it != "com.google.android.webview" }
            .forEach { runCatching { am.killBackgroundProcesses(it) } }
        val after = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        Log.i(TAG, "Trimmed memory: ${before / 1_048_576} MB -> ${after / 1_048_576} MB available")
    }

    /**
     * Before WhatsApp's page runs, wrap getUserMedia: video capped at 720p (this camera gives the
     * same ~11 fps at every size from 848x480 up), and with no real mic, echo and noise processing
     * off (it cost about a fifth of a core cleaning silence).
     */
    private fun lightenCalls() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Log.w(TAG, "No document-start scripts; calls run at WhatsApp's own settings")
            return
        }
        val usbMic = hasUsbMic()
        Log.i(TAG, "USB microphone attached: $usbMic")
        WebViewCompat.addDocumentStartJavaScript(web, lightCallScript(audioProcessing = usbMic), setOf("https://web.whatsapp.com"))
    }

    /** The TV reports a built-in mic it doesn't have; only a USB mic is a real one. */
    private fun hasUsbMic(): Boolean =
        getSystemService(AudioManager::class.java)?.getDevices(AudioManager.GET_DEVICES_INPUTS).orEmpty()
            .any { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (web.parent != null) web.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        if (web.parent != null) web.destroy()
        super.onDestroy()
    }

    /**
     * Arrows move the cursor (faster while held) and scroll at the edges, OK clicks, Back closes
     * whatever is open in WhatsApp (Escape) and leaves the app when pressed twice.
     * While the on-screen keyboard is up it gets the keys first, so typing is unaffected.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (down) move(event.keyCode, event.repeatCount)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.action == KeyEvent.ACTION_UP) tap()
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                if (down) wheel(-PAGE_NOTCHES)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (down) wheel(PAGE_NOTCHES)
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (event.action == KeyEvent.ACTION_UP) back()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun move(keyCode: Int, repeat: Int) {
        val step = (STEP_DP + repeat * ACCEL_DP).coerceAtMost(MAX_STEP_DP) * resources.displayMetrics.density
        var dx = 0f
        var dy = 0f
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> dx = -step
            KeyEvent.KEYCODE_DPAD_RIGHT -> dx = step
            KeyEvent.KEYCODE_DPAD_UP -> dy = -step
            KeyEvent.KEYCODE_DPAD_DOWN -> dy = step
        }
        val atTop = dy < 0 && cursor.py <= EDGE
        val atBottom = dy > 0 && cursor.py >= cursor.height - EDGE
        // At the top or bottom edge, scroll whatever is under the cursor instead of moving off screen.
        if (atTop || atBottom) wheel(if (atTop) -1f else 1f) else cursor.moveTo(cursor.px + dx, cursor.py + dy)
        mouse(MotionEvent.ACTION_HOVER_MOVE)
    }

    private fun tap() {
        val now = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val e = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, cursor.px, cursor.py, 0)
            e.source = InputDevice.SOURCE_TOUCHSCREEN
            // Through the root, which maps screen coordinates into the scaled WebView.
            root.dispatchTouchEvent(e)
            e.recycle()
        }
        cursor.pulse()
    }

    /** A mouse-wheel turn at the cursor, so the chat list or conversation under it scrolls. */
    private fun wheel(notches: Float) {
        mouse(MotionEvent.ACTION_SCROLL) { setAxisValue(MotionEvent.AXIS_VSCROLL, -notches) }
    }

    private fun mouse(action: Int, axes: MotionEvent.PointerCoords.() -> Unit = {}) {
        val props = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
        val coords = MotionEvent.PointerCoords().apply { x = cursor.px; y = cursor.py; axes() }
        val now = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(now, now, action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        root.dispatchGenericMotionEvent(e)
        e.recycle()
    }

    private fun back() {
        val now = SystemClock.uptimeMillis()
        if (now - lastBack < DOUBLE_BACK_MS) {
            finish()
            return
        }
        lastBack = now
        web.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE))
        web.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE))
        Toast.makeText(this, "Press Back again to leave", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "TvChat"
        private const val HOME = "https://web.whatsapp.com/"
        const val EXTRA_RENDER_SCALE = "renderScale"
        // 1 = off. Scale 2 shrank the page layout (WebView kept devicePixelRatio 2); needs another approach.
        private const val RENDER_SCALE = 1f
        private const val VIDEO_W = 1280
        private const val VIDEO_H = 720

        private fun lightCallScript(audioProcessing: Boolean) = """
            (function() {
              var cap = { width: { ideal: $VIDEO_W, max: $VIDEO_W }, height: { ideal: $VIDEO_H, max: $VIDEO_H } };
              var audioProcessing = $audioProcessing;
              function lighten(c) {
                if (!c) return c;
                c = Object.assign({}, c);
                if (c.video) {
                  var v = (typeof c.video === 'object') ? c.video : {};
                  // Keep which camera was asked for; replace size. No frame rate: this TV's camera
                  // driver fails when asked for anything but its native rate.
                  var out = { width: cap.width, height: cap.height };
                  if (v.deviceId) out.deviceId = v.deviceId;
                  if (v.facingMode) out.facingMode = v.facingMode;
                  c.video = out;
                }
                if (c.audio && !audioProcessing) {
                  var a = (typeof c.audio === 'object') ? Object.assign({}, c.audio) : {};
                  a.echoCancellation = false; a.noiseSuppression = false; a.autoGainControl = false;
                  c.audio = a;
                }
                return c;
              }
              var md = navigator.mediaDevices;
              var gum = md.getUserMedia.bind(md);
              md.getUserMedia = function(c) { return gum(lighten(c)); };
              var apply = MediaStreamTrack.prototype.applyConstraints;
              MediaStreamTrack.prototype.applyConstraints = function(c) {
                if (this.kind === 'video' && c) { c = Object.assign({}, c, { width: cap.width, height: cap.height }); delete c.frameRate; }
                return apply.call(this, c);
              };

              // Diagnostics: remember call connections so their stats can be read over DevTools.
              var pcs = [];
              var PC = window.RTCPeerConnection;
              window.RTCPeerConnection = function(cfg, opt) { var pc = new PC(cfg, opt); pcs.push(pc); return pc; };
              window.RTCPeerConnection.prototype = PC.prototype;
              Object.setPrototypeOf(window.RTCPeerConnection, PC);
              window.__tvchatStats = async function() {
                var out = [];
                for (var i = 0; i < pcs.length; i++) {
                  if (pcs[i].connectionState === 'closed') continue;
                  (await pcs[i].getStats()).forEach(function(r) {
                    if (r.kind !== 'video' && r.kind !== 'audio') return;
                    if (r.type === 'outbound-rtp') out.push({ dir: 'out', kind: r.kind, w: r.frameWidth, h: r.frameHeight, fps: r.framesPerSecond,
                      limit: r.qualityLimitationReason, encoder: r.encoderImplementation, kbps: r.targetBitrate && Math.round(r.targetBitrate / 1000) });
                    if (r.type === 'inbound-rtp') out.push({ dir: 'in', kind: r.kind, w: r.frameWidth, h: r.frameHeight, fps: r.framesPerSecond,
                      decoder: r.decoderImplementation, dropped: r.framesDropped, lost: r.packetsLost, jitter: r.jitter });
                    if (r.type === 'candidate-pair' && r.nominated) out.push({ rttMs: Math.round((r.currentRoundTripTime || 0) * 1000), outKbps: r.availableOutgoingBitrate && Math.round(r.availableOutgoingBitrate / 1000) });
                  });
                }
                return JSON.stringify(out);
              };
              console.log('TvChat: video capped at ${VIDEO_W}x$VIDEO_H, audio processing ' + audioProcessing);
            })();
        """.trimIndent()

        private val FEATURE_PROBE = """
            (function() {
              var d = navigator.userAgentData;
              var s = window.RTCRtpSender && RTCRtpSender.prototype;
              return JSON.stringify({
                brands: d ? d.brands.map(function(b) { return b.brand + '/' + b.version; }).join(',') : null,
                platform: d ? d.platform : null, mobile: d ? d.mobile : null,
                rtc: !!window.RTCPeerConnection,
                encodedStreams: !!(s && s.createEncodedStreams),
                scriptTransform: !!window.RTCRtpScriptTransform,
                getUserMedia: !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia),
                audioWorklet: !!(window.AudioContext && AudioContext.prototype.hasOwnProperty('audioWorklet')),
                webCodecs: !!window.VideoEncoder, trackProcessor: !!window.MediaStreamTrackProcessor,
                isolated: !!window.crossOriginIsolated, sab: typeof SharedArrayBuffer,
                serviceWorker: 'serviceWorker' in navigator, notification: 'Notification' in window,
                wasm: typeof WebAssembly
              });
            })()
        """.trimIndent()
        private const val STEP_DP = 10f
        private const val ACCEL_DP = 4f
        private const val MAX_STEP_DP = 48f
        private const val EDGE = 4f
        private const val PAGE_NOTCHES = 8f
        private const val DOUBLE_BACK_MS = 1500L

        /** WhatsApp's own pages only. Nothing else loads in here. */
        private val ALLOWED_HOSTS = listOf("whatsapp.com", "whatsapp.net")

        fun isAllowed(uri: Uri?): Boolean {
            val host = uri?.host ?: return false
            return uri.scheme == "https" && ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }
        }

        /**
         * The WebView's agent with the Android and WebView markers swapped for desktop Chrome on
         * Linux, keeping the real Chrome version so the page sees a current browser.
         */
        fun desktopUserAgent(webView: String): String {
            val chrome = Regex("Chrome/(\\d+)").find(webView)?.groupValues?.get(1) ?: "140"
            return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/$chrome.0.0.0 Safari/537.36"
        }
    }
}
