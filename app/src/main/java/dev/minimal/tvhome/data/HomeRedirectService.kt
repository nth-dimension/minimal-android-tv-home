package dev.minimal.tvhome.data

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import dev.minimal.tvhome.data.HomeRedirectRules.Front
import dev.minimal.tvhome.data.HomeRedirectRules.Step

/**
 * Fire TV only. Fire OS keeps the Home button for Amazon's launcher and refuses to disable it,
 * so this watches for that launcher's home screen coming to the front and opens Minimal Home
 * over it.
 *
 * Fire OS 7 holds back any app started right after a Home press for about five seconds (measured
 * on a Fire TV Stick 4K Max, Fire OS 7.7.1.3); Fire OS 8 for about a quarter second. Meanwhile a
 * plain cover in Minimal Home's background colour hides Amazon's screen and its ads, and swallows
 * D-pad and OK presses so they can't act on Amazon's hidden screen.
 *
 * A second Home press in the wait lets Minimal Home up and then puts Amazon's home back over it
 * with no window event, so a redirect isn't trusted until Minimal Home has stayed in front: every
 * [HomeRedirectRules.CHECK_EVERY_MS] it reads what is in front from usage events (the app already
 * has usage access for Recent apps) and covers and relaunches while Amazon's home is back.
 *
 * It is an accessibility service because that is the only way a sideloaded app learns which
 * window is in front. It asks for no window content: see res/xml/home_redirect.xml. Enabled over
 * adb by scripts/install.sh; the Fire OS accessibility settings page often can't toggle it.
 */
class HomeRedirectService : AccessibilityService() {
    private var lastRedirectAt = 0L
    /** When the current redirect started (elapsedRealtime), or 0 when none is under way. */
    private var startedAt = 0L
    /** When Minimal Home was last seen arriving in front during this redirect, or 0. */
    private var landedAt = 0L
    /** Whether Amazon's home screen is in front: null until the first window event after the service starts. */
    private var amazonHomeInFront: Boolean? = null
    private var cover: View? = null
    private val main = Handler(Looper.getMainLooper())
    private val check = Runnable { checkTick() }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindowChanged(event)
            // A key pressed on Amazon's home screen with no redirect under way: after an update, a
            // crash or a boot, Amazon's home can be in front without this service having seen it
            // arrive, and Home then sends no new window event. Focus events carry only the app's
            // name here; window content is never requested.
            AccessibilityEvent.TYPE_VIEW_FOCUSED ->
                if (HomeRedirectRules.redirectOnFocus(event.packageName, amazonHomeInFront, startedAt != 0L)) {
                    Log.i(TAG, "Key pressed on the Amazon launcher; opening Minimal Home")
                    startRedirect(SystemClock.elapsedRealtime())
                }
            else -> Unit
        }
    }

    private fun onWindowChanged(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        if (pkg == HomeRedirectRules.AMAZON_LAUNCHER) {
            // Every Amazon launcher window goes to logcat, so a build with a differently named home
            // activity can be matched by reading `adb logcat -s MinimalHome`.
            Log.i(TAG, "Amazon launcher window: ${event.className}")
        }
        if (HomeRedirectRules.leftAmazonHome(pkg, event.className)) {
            amazonHomeInFront = false
            if (pkg == packageName) {
                // Up, but not yet trusted: the checks end the redirect once it stays up.
                landedAt = SystemClock.elapsedRealtime()
                hideCover()
            } else {
                if (startedAt != 0L) Log.i(TAG, "Redirect ended by $pkg/${event.className}")
                endRedirect()
            }
            return
        }
        if (!HomeRedirectRules.isAmazonHome(pkg, event.className)) return
        amazonHomeInFront = true
        val now = SystemClock.elapsedRealtime()
        if (HomeRedirectRules.shouldRedirect(pkg, event.className, now, lastRedirectAt)) startRedirect(now)
    }

    private fun startRedirect(now: Long) {
        lastRedirectAt = now
        startedAt = now
        landedAt = 0L
        showCover()
        launch()
        main.removeCallbacks(check)
        main.postDelayed(check, HomeRedirectRules.CHECK_EVERY_MS)
    }

    private fun checkTick() {
        if (startedAt == 0L) return
        val now = SystemClock.elapsedRealtime()
        val front = front()
        when (HomeRedirectRules.nextStep(front, now, startedAt, landedAt)) {
            Step.Done -> endRedirect()
            Step.Stop -> {
                if (front != Front.OtherApp) Log.w(TAG, "Gave up opening Minimal Home over the Amazon launcher")
                endRedirect()
            }
            Step.Relaunch -> {
                if (landedAt != 0L) Log.i(TAG, "Amazon's home came back over Minimal Home; opening it again")
                amazonHomeInFront = true
                landedAt = 0L
                showCover()
                launch()
                main.postDelayed(check, HomeRedirectRules.CHECK_EVERY_MS)
            }
            Step.Wait -> main.postDelayed(check, HomeRedirectRules.CHECK_EVERY_MS)
        }
    }

    /**
     * What is in front, from the last activity usage events saw come to the front. Falls back to
     * what window events said when usage access is missing.
     */
    private fun front(): Front {
        val last = runCatching { lastForeground() }.getOrNull()
            ?: return when {
                amazonHomeInFront == true -> Front.AmazonHome
                landedAt != 0L -> Front.MinimalHome
                else -> Front.Unknown
            }
        val (pkg, cls) = last
        return when {
            pkg == packageName -> Front.MinimalHome
            HomeRedirectRules.isAmazonHome(pkg, cls) -> Front.AmazonHome
            else -> Front.OtherApp // includes Fire TV settings, which live in Amazon's launcher package
        }
    }

    private fun lastForeground(): Pair<String, String?>? {
        val usage = getSystemService(UsageStatsManager::class.java) ?: return null
        val end = System.currentTimeMillis()
        val events = usage.queryEvents(end - LOOKBACK_MS, end) ?: return null
        val e = UsageEvents.Event()
        var last: Pair<String, String?>? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION") // MOVE_TO_FOREGROUND is ACTIVITY_RESUMED from API 29; same value.
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last = e.packageName to e.className
        }
        return last
    }

    private fun launch() {
        val home = Intent(this, MainActivity::class.java)
            // CATEGORY_HOME makes MainActivity treat this as a Home press: back to the top, first card focused.
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(home) }
            .onFailure { Log.w(TAG, "Could not open Minimal Home over the Amazon launcher: ${it.message}") }
    }

    private fun endRedirect() {
        startedAt = 0L
        landedAt = 0L
        main.removeCallbacks(check)
        hideCover()
    }

    private fun showCover() {
        if (cover != null) return
        // Focusable, and it swallows every key it gets: otherwise D-pad and OK pressed during the
        // wait move around Amazon's hidden home screen and can open whatever is selected there.
        // Home and the other system keys never reach a window, so they still work.
        val view = View(this).apply {
            setBackgroundColor(getColor(R.color.background))
            isFocusable = true
            setOnKeyListener { _, _, _ -> true }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        runCatching { getSystemService(WindowManager::class.java).addView(view, params) }
            .onSuccess { cover = view; view.requestFocus() }
            .onFailure { Log.w(TAG, "Could not cover the Amazon launcher: ${it.message}") }
    }

    private fun hideCover() {
        val view = cover ?: return
        cover = null
        runCatching { getSystemService(WindowManager::class.java).removeView(view) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        endRedirect()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MinimalHome"

        /** Usage events this far back are enough to find what is in front now. */
        private const val LOOKBACK_MS = 60_000L

        /**
         * Turns the service back on if it was switched off, which Android does to a force-stopped
         * app's accessibility services. Needs WRITE_SECURE_SETTINGS, which scripts/install.sh grants
         * over adb on a Fire TV; without it, or off Fire TV, this does nothing. Returns whether it acted.
         */
        fun ensureEnabled(context: Context): Boolean {
            if (!Platform.isFireTv(context) || isEnabled(context)) return false
            if (context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) !=
                PackageManager.PERMISSION_GRANTED
            ) return false
            val me = ComponentName(context, HomeRedirectService::class.java).flattenToString()
            val resolver = context.contentResolver
            val others = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty().split(':').filter { it.isNotBlank() && it != me }
            return runCatching {
                Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (others + me).joinToString(":"))
                Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                Log.i(TAG, "Home redirect was off; turned it back on")
            }.onFailure { Log.w(TAG, "Could not turn the Home redirect back on: ${it.message}") }.isSuccess
        }

        /** Whether the system lists this service as enabled. Shown in Diagnostics. */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, HomeRedirectService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty()
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
