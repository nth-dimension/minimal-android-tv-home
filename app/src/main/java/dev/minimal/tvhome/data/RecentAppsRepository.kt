package dev.minimal.tvhome.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.util.concurrent.TimeUnit

/** Apps ordered by when they were last on screen, from UsageStatsManager. */
class RecentAppsRepository(private val context: Context) {

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        // The non-deprecated replacement (unsafeCheckOpNoThrow) needs API 29; Fire OS 7 is API 28.
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
        )
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT ->
                context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                    PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    /** Latest use time per package, or null when usage access is missing. */
    fun lastUsed(): Map<String, Long>? {
        if (!hasAccess()) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val end = System.currentTimeMillis()
        val start = end - TimeUnit.DAYS.toMillis(LOOKBACK_DAYS)
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end).orEmpty()
        return Ranking.mergeLastUsed(
            stats.map { s ->
                // lastTimeVisible (API 29) is the better signal; Fire OS 7 is API 28 and has only lastTimeUsed.
                val visible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) s.lastTimeVisible else 0L
                s.packageName to (if (visible > 0) visible else s.lastTimeUsed)
            },
        )
    }

    companion object {
        const val LOOKBACK_DAYS = 14L
    }
}
