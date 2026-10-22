package dev.minimal.tvhome.data

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import dev.minimal.tvhome.MainActivity
import dev.minimal.tvhome.R

/**
 * Fire TV only. Fire OS keeps the Home button for Amazon's launcher and refuses to disable it,
 * so this watches for that launcher's home screen coming to the front and opens Minimal Home
 * over it.
 *
 * Fire OS 7 holds back any app started right after a Home press for about five seconds (measured
 * on a Fire TV Stick 4K Max, Fire OS 7.7.1.3); Fire OS 8 for about a quarter second. Meanwhile a
 * plain cover in Minimal Home's background colour hides Amazon's screen and its ads. It takes no
 * keys or focus, and comes down when Minimal Home appears, another app does, or after a timeout.
 *
 * It is an accessibility service because that is the only way a sideloaded app learns which
 * window is in front. It asks for no window content: see res/xml/home_redirect.xml. Enabled over
 * adb by scripts/install.sh; the Fire OS accessibility settings page often can't toggle it.
 */
class HomeRedirectService : AccessibilityService() {
    private var lastRedirectAt = 0L
    private var cover: View? = null
    private val main = Handler(Looper.getMainLooper())
    private val removeCover = Runnable { hideCover() }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != HomeRedirectRules.AMAZON_LAUNCHER) {
            // Minimal Home is up, or the user went somewhere else: either way the cover's job is done.
            // Toasts and the like also send this event, so only full activities count.
            if (cover != null && event.className?.toString()?.contains("Activity") == true) hideCover()
            return
        }
        // Every Amazon launcher window goes to logcat, so a build with a differently named home
        // activity can be matched by reading `adb logcat -s MinimalHome`.
        Log.i(TAG, "Amazon launcher window: ${event.className}")
        val now = SystemClock.elapsedRealtime()
        if (!HomeRedirectRules.shouldRedirect(event.packageName, event.className, now, lastRedirectAt)) return
        lastRedirectAt = now
        showCover()
        val home = Intent(this, MainActivity::class.java)
            // CATEGORY_HOME makes MainActivity treat this as a Home press: back to the top, first card focused.
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(home) }
            .onFailure { Log.w(TAG, "Could not open Minimal Home over the Amazon launcher: ${it.message}"); hideCover() }
    }

    private fun showCover() {
        if (cover != null) return
        val view = View(this).apply { setBackgroundColor(getColor(R.color.background)) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        runCatching { getSystemService(WindowManager::class.java).addView(view, params) }
            .onSuccess {
                cover = view
                main.postDelayed(removeCover, COVER_TIMEOUT_MS)
            }
            .onFailure { Log.w(TAG, "Could not cover the Amazon launcher: ${it.message}") }
    }

    private fun hideCover() {
        main.removeCallbacks(removeCover)
        val view = cover ?: return
        cover = null
        runCatching { getSystemService(WindowManager::class.java).removeView(view) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideCover()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MinimalHome"

        /** Longer than Fire OS 7's hold-back, so a cover never outlives a launch that failed. */
        private const val COVER_TIMEOUT_MS = 7_000L

        /** Whether the system lists this service as enabled. Shown in Diagnostics. */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, HomeRedirectService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty()
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
