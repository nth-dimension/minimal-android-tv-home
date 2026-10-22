package dev.minimal.tvhome.data

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * Lists installed TV apps (those with a Leanback launcher entry, and on Fire TV some phone-style
 * ones too), with their art already scaled to card size. Call off the main thread. Art is cached
 * per package version, so a reload after a package change only converts the app that changed.
 */
class AppRepository(private val context: Context) {
    private val pm = context.packageManager
    private val art = HashMap<String, Art>()

    private class Art(val updatedAt: Long, val banner: ImageBitmap?, val icon: ImageBitmap?)

    fun loadApps(): List<TvApp> {
        // Many apps sideloaded on a Fire TV have only a phone-style launcher entry, and Amazon's home
        // shows them. Fire OS's own phone-style entries are mostly plumbing (profile picker,
        // screensaver, file picker), so of the system ones only the Appstore and Silk are kept.
        val fireTv = Platform.isFireTv(context)
        val categories = if (fireTv) {
            listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        } else {
            listOf(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        val entries = categories.flatMap { category ->
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
                .map { it.activityInfo to category }
                .filter { (info, cat) ->
                    cat == Intent.CATEGORY_LEANBACK_LAUNCHER || info.packageName in FIRE_TV_SYSTEM_APPS ||
                        (info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                }
        }
        val apps = entries.asSequence()
            .filter { (info, _) -> info.packageName != context.packageName && info.packageName !in HIDDEN_PACKAGES }
            // The Leanback entry wins when an app has both: it is the one made for a TV.
            .distinctBy { (info, _) -> info.packageName }
            .map { (info, category) ->
                val updatedAt = runCatching { pm.getPackageInfo(info.packageName, 0).lastUpdateTime }.getOrDefault(0L)
                val a = artFor(info, updatedAt)
                TvApp(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    banner = a.banner,
                    icon = a.icon,
                    launchIntent = Intent(Intent.ACTION_MAIN)
                        .addCategory(category)
                        .setClassName(info.packageName, info.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                    updatedAt = updatedAt,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
        art.keys.retainAll(apps.map { it.packageName }.toSet())
        return apps
    }

    private fun artFor(info: ActivityInfo, updatedAt: Long): Art {
        art[info.packageName]?.takeIf { it.updatedAt == updatedAt }?.let { return it }
        val banner = (info.loadBanner(pm) ?: info.applicationInfo?.loadBanner(pm))
            ?.let { runCatching { it.toBitmap(BANNER_WIDTH, BANNER_HEIGHT).asImageBitmap() }.getOrNull() }
        val icon = info.loadIcon(pm)?.let { runCatching { it.toBitmap(ICON_SIZE, ICON_SIZE).asImageBitmap() }.getOrNull() }
        return Art(updatedAt, banner, icon).also { art[info.packageName] = it }
    }

    companion object {
        /** Card art is 192x108 dp; this covers it at 1.5x density without being wasteful. */
        const val BANNER_WIDTH = 320
        const val BANNER_HEIGHT = 180
        const val ICON_SIZE = 96

        /** Fire OS system apps with only a phone-style entry that are worth a tile. */
        val FIRE_TV_SYSTEM_APPS = setOf(
            "com.amazon.venezia", // Appstore
            "com.amazon.cloud9",  // Silk browser
        )

        /** Stock launcher pieces, and Amazon's shopping storefront, which should never appear as apps. */
        val HIDDEN_PACKAGES = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tungsten.setupwraith",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
            // Fire TV
            "com.amazon.tv.launcher",          // Amazon's home screen
            "com.amazon.firehomestarter",      // starts it after boot
            "com.amazon.tv.oobe",              // first-run setup
            "com.amazon.shoptv.firetv.client", // Amazon Shopping: a storefront, not something to watch
        )
    }
}
