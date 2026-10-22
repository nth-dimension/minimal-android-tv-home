package dev.minimal.tvhome.data

import android.content.Context
import androidx.core.content.edit
import java.util.Locale

/**
 * What the Fire TV Home redirect has done since install, for Diagnostics. Kept in shared
 * preferences: the service and the home screen share a process, but either can restart.
 */
object RedirectStats {
    private const val PREFS = "home_redirect_stats"
    private const val STARTED = "started"
    private const val LANDED = "landed"
    private const val CAME_BACK = "came_back"
    private const val GAVE_UP = "gave_up"
    private const val LAST_LANDED_AT = "last_landed_at"
    private const val LAST_TOOK_MS = "last_took_ms"

    data class Summary(
        val started: Int = 0,
        val landed: Int = 0,
        /** Times Amazon's home came back over Minimal Home and it was opened again. */
        val cameBack: Int = 0,
        val gaveUp: Int = 0,
        /** Wall-clock time Minimal Home last settled in front, or 0. */
        val lastLandedAt: Long = 0,
        /** From the Home press to Minimal Home in front, last time. */
        val lastTookMs: Long = 0,
    )

    fun started(context: Context) = bump(context, STARTED)

    fun cameBack(context: Context) = bump(context, CAME_BACK)

    fun gaveUp(context: Context) = bump(context, GAVE_UP)

    fun landed(context: Context, tookMs: Long) {
        val p = prefs(context)
        p.edit {
            putInt(LANDED, p.getInt(LANDED, 0) + 1)
            putLong(LAST_LANDED_AT, System.currentTimeMillis())
            putLong(LAST_TOOK_MS, tookMs)
        }
    }

    fun read(context: Context): Summary {
        val p = prefs(context)
        return Summary(
            started = p.getInt(STARTED, 0),
            landed = p.getInt(LANDED, 0),
            cameBack = p.getInt(CAME_BACK, 0),
            gaveUp = p.getInt(GAVE_UP, 0),
            lastLandedAt = p.getLong(LAST_LANDED_AT, 0),
            lastTookMs = p.getLong(LAST_TOOK_MS, 0),
        )
    }

    /** One line for Diagnostics; [lastAt] formats the time of the last landing. */
    fun describe(s: Summary, lastAt: (Long) -> String): String {
        if (s.started == 0) return "none yet"
        val last = if (s.lastLandedAt > 0) {
            " (last in %.1f s, at %s)".format(Locale.ROOT, s.lastTookMs / 1000.0, lastAt(s.lastLandedAt))
        } else {
            ""
        }
        return "${s.started} started, ${s.landed} landed$last, ${s.cameBack} reopened after Amazon's home came back, " +
            "${s.gaveUp} gave up"
    }

    private fun bump(context: Context, key: String) {
        val p = prefs(context)
        p.edit { putInt(key, p.getInt(key, 0) + 1) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
