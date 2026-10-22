package dev.minimal.tvhome.data

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dev.minimal.tvhome.MainActivity

/**
 * Fire TV only. Fire OS keeps the Home button for Amazon's launcher and refuses to disable it,
 * so this watches for that launcher's home screen coming to the front and opens Minimal Home
 * over it. Amazon's screen may show for a moment first (about a quarter second on Fire OS 8,
 * a few seconds on Fire OS 7, where the system holds back apps opened from Home).
 *
 * It is an accessibility service because that is the only way a sideloaded app learns which
 * window is in front. It asks for no window content: see res/xml/home_redirect.xml. Enabled over
 * adb by scripts/install.sh; the Fire OS accessibility settings page often can't toggle it.
 */
class HomeRedirectService : AccessibilityService() {
    private var lastRedirectAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != HomeRedirectRules.AMAZON_LAUNCHER) return
        // Every Amazon launcher window goes to logcat, so a build with a differently named home
        // activity can be matched by reading `adb logcat -s MinimalHome`.
        Log.i(TAG, "Amazon launcher window: ${event.className}")
        val now = SystemClock.elapsedRealtime()
        if (!HomeRedirectRules.shouldRedirect(event.packageName, event.className, now, lastRedirectAt)) return
        lastRedirectAt = now
        val home = Intent(this, MainActivity::class.java)
            // CATEGORY_HOME makes MainActivity treat this as a Home press: back to the top, first card focused.
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(home) }
            .onFailure { Log.w(TAG, "Could not open Minimal Home over the Amazon launcher: ${it.message}") }
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "MinimalHome"

        /** Whether the system lists this service as enabled. Shown in Diagnostics. */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, HomeRedirectService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty()
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
