package dev.minimal.tvhome.data

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log

/** Starting other apps, settings, searches, and Watch Next deep links. */
object Launch {

    fun app(context: Context, app: TvApp): Boolean =
        start(context, app.launchIntent) || packageFallback(context, app.packageName)

    /** Opens the row's deep link, falling back to the app that published it. */
    fun watchNext(context: Context, item: WatchNextItem, app: TvApp?): Boolean {
        val deepLink = item.intentUri?.let { parseDeepLink(it, item.packageName) }
        if (deepLink != null && start(context, deepLink)) return true
        return if (app != null) app(context, app) else packageFallback(context, item.packageName)
    }

    /**
     * The row's intent, cleaned: it comes from another app's data, so it may not carry a selector
     * or URI grants, and it may only point into the app that published it. Null when unusable.
     */
    private fun parseDeepLink(uri: String, publisher: String): Intent? = runCatching {
        // "intent:..." needs URI_INTENT_SCHEME. Legacy "#Intent;..." and plain links parse with 0.
        val flags = if (uri.startsWith("intent:")) Intent.URI_INTENT_SCHEME else 0
        Intent.parseUri(uri, flags).apply {
            // Must go before setPackage: Android refuses a package on an intent with a selector.
            selector = null
            removeFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.takeIf { it.staysIn(publisher) }
    }.getOrNull()

    /** Apps that can be asked to search: a known search link, or an ACTION_SEARCH handler. */
    fun searchTargets(context: Context, apps: List<TvApp>, recent: List<TvApp>): List<TvApp> {
        val pm = context.packageManager
        val usable = apps.filter { app ->
            app.packageName !in SearchRules.NOT_VIDEO &&
                (SearchRules.hasLink(app.packageName) ||
                    // MATCH_DEFAULT_ONLY: startActivity needs CATEGORY_DEFAULT. JioHotstar and Xstream
                    // list ACTION_SEARCH without it and refuse the request when it's sent.
                    pm.queryIntentActivities(searchIntent(app.packageName, ""), PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty())
        }
        val byPackage = usable.associateBy { it.packageName }
        return SearchRules.orderTargets(usable.map { it.packageName }, recent.map { it.packageName })
            .mapNotNull { byPackage[it] }
    }

    /** Opens the app's own search with the query typed in, or just the app if it can't take one. */
    fun search(context: Context, app: TvApp, query: String): Boolean {
        val link = SearchRules.link(app.packageName, query)
        if (link != null) {
            val view = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                .setPackage(app.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (start(context, view)) return true
        }
        return start(context, searchIntent(app.packageName, query.trim())) || app(context, app)
    }

    /** The TV's own search across all apps (Google TV's, or Fire TV's if it answers). Null when there is none. */
    fun globalSearchIntent(context: Context, query: String): Intent? =
        Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH)
            .putExtra(SearchManager.QUERY, query.trim())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .takeIf { context.packageManager.resolveActivity(it, 0) != null }

    fun globalSearch(context: Context, query: String): Boolean =
        globalSearchIntent(context, query)?.let { start(context, it) } ?: false

    private fun searchIntent(packageName: String, query: String) =
        Intent(Intent.ACTION_SEARCH)
            .setPackage(packageName)
            .putExtra(SearchManager.QUERY, query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun settings(context: Context): Boolean =
        start(context, Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) ||
            FIRE_TV_SETTINGS.any { start(context, Intent().setComponent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    /**
     * Fire OS keeps its settings inside the launcher package, and not every build answers the
     * standard settings intent. Tried in order; the first that exists opens.
     */
    private val FIRE_TV_SETTINGS = listOf(
        ComponentName("com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.MainSettingsActivity"), // Fire OS 7 and 8
        ComponentName("com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.SettingsActivity"),     // older Fire OS
        ComponentName("com.amazon.tv.settings.v2", "com.amazon.tv.settings.v2.hud.HudActivity"),   // the Home long-press panel
    )

    fun appInfo(context: Context, packageName: String): Boolean =
        start(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

    private fun packageFallback(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return false
        return start(context, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * A row may only deep-link into the app that published it. An intent naming no app is pinned
     * to the publisher; one naming another app is refused, and the publisher opens instead.
     */
    private fun Intent.staysIn(packageName: String): Boolean {
        val target = component?.packageName ?: `package`
        if (target == null) {
            setPackage(packageName)
            return true
        }
        return target == packageName
    }

    /** Never lets a bad intent from another app take the home screen down. */
    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    } catch (e: RuntimeException) {
        Log.w(TAG, "Could not start ${intent.component ?: intent.action}: ${e.message}")
        false
    }

    private const val TAG = "MinimalHome"
}
