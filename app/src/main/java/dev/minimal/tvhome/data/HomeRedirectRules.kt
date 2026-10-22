package dev.minimal.tvhome.data

/**
 * Pure rules for [HomeRedirectService]: when a window coming to the front means the Fire TV
 * Home button was pressed, so Minimal Home should open over it, and when that has worked.
 */
object HomeRedirectRules {
    /** The Fire OS home screen; the Home button always opens it, and Fire OS won't let it be disabled. */
    const val AMAZON_LAUNCHER = "com.amazon.tv.launcher"

    /** Two redirects closer together than this are one Home press, not two. */
    const val MIN_GAP_MS = 1_000L

    /**
     * How often a redirect checks what is in front. Fire OS 7 queues a launch made right after Home
     * for about five seconds. A second Home press in that time releases the queued launch and then
     * puts Amazon's home back on top of it, without any window event saying so (seen on Fire OS
     * 7.7.1.3). So a redirect keeps checking, and launching again, until Minimal Home stays up.
     */
    const val CHECK_EVERY_MS = 1_500L

    /** Minimal Home has to stay in front this long before a redirect counts as done. */
    const val SETTLE_MS = 3_000L

    /** A redirect stops after this long without landing, so a failing launch can't loop forever. */
    const val GIVE_UP_AFTER_MS = 15_000L

    /**
     * Only the launcher's home activity (`.ui.HomeActivity_vNext` on Fire OS 7 and 8, `.ui.HomeActivity`
     * before that). The same package also holds the Fire TV settings screens and the Home long-press
     * panel, which must stay reachable.
     */
    fun isAmazonHome(packageName: CharSequence?, className: CharSequence?): Boolean =
        packageName?.toString() == AMAZON_LAUNCHER &&
            className?.toString()?.substringAfterLast('.')?.startsWith("HomeActivity") == true

    fun shouldRedirect(packageName: CharSequence?, className: CharSequence?, now: Long, lastRedirectAt: Long): Boolean =
        isAmazonHome(packageName, className) && now - lastRedirectAt >= MIN_GAP_MS

    /** Whether a window event means the user is no longer on Amazon's home screen. Toasts and panels don't count. */
    fun leftAmazonHome(packageName: CharSequence?, className: CharSequence?): Boolean =
        !isAmazonHome(packageName, className) && className?.toString()?.contains("Activity") == true

    /**
     * Whether a focus change (a key pressed) should open Minimal Home: it happened in Amazon's
     * launcher, no redirect is under way, and Amazon's home is in front or not yet known not to be.
     * Unknown counts as in front, so the first key after an update or crash leaves Amazon's home;
     * the price is one jump to Minimal Home if the service restarts while Fire TV settings are open.
     */
    fun redirectOnFocus(packageName: CharSequence?, amazonHomeInFront: Boolean?, pending: Boolean): Boolean =
        packageName?.toString() == AMAZON_LAUNCHER && amazonHomeInFront != false && !pending

    /** What is in front, as far as the service can tell. */
    enum class Front { MinimalHome, AmazonHome, OtherApp, Unknown }

    enum class Step {
        /** Minimal Home has stayed up: stop checking. */
        Done,
        /** Amazon's home is in front: cover it and launch Minimal Home again. */
        Relaunch,
        /** Not settled yet: check again later. */
        Wait,
        /** The user went to another app, or it took too long: stop and drop the cover. */
        Stop,
    }

    /**
     * One check of a pending redirect. [startedAt] is when the last Home press started it; [landedAt]
     * is when Minimal Home was last seen arriving in front, or 0.
     */
    fun nextStep(front: Front, now: Long, startedAt: Long, landedAt: Long): Step = when {
        front == Front.OtherApp -> Step.Stop
        front == Front.MinimalHome && landedAt != 0L && now - landedAt >= SETTLE_MS -> Step.Done
        now - startedAt >= GIVE_UP_AFTER_MS -> Step.Stop
        front == Front.AmazonHome -> Step.Relaunch
        else -> Step.Wait
    }
}
