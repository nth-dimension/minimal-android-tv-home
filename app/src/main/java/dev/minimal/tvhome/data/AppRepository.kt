package dev.minimal.tvhome.data

import android.content.Context
import android.content.Intent

/** Lists installed TV apps (those with a Leanback launcher entry). */
class AppRepository(private val context: Context) {
    private val pm = context.packageManager

    fun loadApps(): List<TvApp> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        @Suppress("DEPRECATION")
        val infos = pm.queryIntentActivities(query, 0)
        return infos.asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName && it.packageName !in HIDDEN_PACKAGES }
            .distinctBy { it.packageName }
            .map { info ->
                TvApp(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    banner = info.loadBanner(pm) ?: info.applicationInfo?.loadBanner(pm),
                    icon = info.loadIcon(pm),
                    launchIntent = Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                        .setClassName(info.packageName, info.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    companion object {
        /** Stock launcher pieces that should never appear as apps. */
        val HIDDEN_PACKAGES = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tungsten.setupwraith",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
        )
    }
}
