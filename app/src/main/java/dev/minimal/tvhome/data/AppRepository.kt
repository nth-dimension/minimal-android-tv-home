package dev.minimal.tvhome.data

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * Lists installed TV apps (those with a Leanback launcher entry), with their art already scaled
 * to card size. Call off the main thread. Art is cached per package version, so a reload after
 * a package change only converts the app that changed.
 */
class AppRepository(private val context: Context) {
    private val pm = context.packageManager
    private val art = HashMap<String, Art>()

    private class Art(val updatedAt: Long, val banner: ImageBitmap?, val icon: ImageBitmap?)

    fun loadApps(): List<TvApp> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        @Suppress("DEPRECATION")
        val infos = pm.queryIntentActivities(query, 0)
        val apps = infos.asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName && it.packageName !in HIDDEN_PACKAGES }
            .distinctBy { it.packageName }
            .map { info ->
                val updatedAt = runCatching { pm.getPackageInfo(info.packageName, 0).lastUpdateTime }.getOrDefault(0L)
                val a = artFor(info, updatedAt)
                TvApp(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    banner = a.banner,
                    icon = a.icon,
                    launchIntent = Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
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

        /** Stock launcher pieces that should never appear as apps. */
        val HIDDEN_PACKAGES = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tungsten.setupwraith",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
        )
    }
}
