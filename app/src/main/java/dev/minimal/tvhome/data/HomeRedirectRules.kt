package dev.minimal.tvhome.data

/**
 * Pure rules for [HomeRedirectService]: when a window coming to the front means the Fire TV
 * Home button was pressed, so Minimal Home should open over it.
 */
object HomeRedirectRules {
    /** The Fire OS home screen; the Home button always opens it, and Fire OS won't let it be disabled. */
    const val AMAZON_LAUNCHER = "com.amazon.tv.launcher"

    /** Two redirects closer together than this are one Home press, not two. */
    const val MIN_GAP_MS = 1_000L

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
}
