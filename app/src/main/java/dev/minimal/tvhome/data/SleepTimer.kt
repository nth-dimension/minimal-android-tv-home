package dev.minimal.tvhome.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Turns the TV off after a set time, even while another app is playing.
 *
 * An alarm fires at the end time; the receiver pauses whatever is playing and then puts the TV
 * to sleep through [DevicePolicyManager.lockNow], which on Android TV means standby. That needs
 * this app to be a device admin (lock screen policy only), which scripts/install.sh turns on.
 * A minute before the end, a toast warns that the TV is about to turn off.
 */
object SleepTimer {
    private const val TAG = "MinimalHome"
    private const val PREFS = "sleep_timer"
    private const val KEY_ENDS_AT = "ends_at_elapsed"
    private const val WARNING_MS = 60_000L

    const val ACTION_FIRE = "dev.minimal.tvhome.SLEEP_TIMER_FIRE"
    const val ACTION_WARN = "dev.minimal.tvhome.SLEEP_TIMER_WARN"

    /** Choices offered on the home screen, in minutes. */
    val CHOICES = listOf(10, 15, 20, 30)

    private val _endsAt = MutableStateFlow<Long?>(null)

    /** When the TV turns off, in [SystemClock.elapsedRealtime] time, or null with no timer set. */
    val endsAt: StateFlow<Long?> = _endsAt.asStateFlow()

    /** Reads the saved timer once per process; drops it if its alarm no longer exists (after a reboot). */
    fun load(context: Context) {
        val saved = prefs(context).getLong(KEY_ENDS_AT, 0L)
        val alive = saved > SystemClock.elapsedRealtime() && pending(context, ACTION_FIRE, create = false) != null
        if (saved != 0L && !alive) prefs(context).edit { remove(KEY_ENDS_AT) }
        _endsAt.value = saved.takeIf { alive }
    }

    fun start(context: Context, durationMillis: Long) {
        val app = context.applicationContext
        val endsAt = SystemClock.elapsedRealtime() + durationMillis
        val alarms = app.getSystemService(AlarmManager::class.java)
        schedule(app, alarms, endsAt, pending(app, ACTION_FIRE, create = true)!!)
        if (durationMillis > WARNING_MS * 2) {
            schedule(app, alarms, endsAt - WARNING_MS, pending(app, ACTION_WARN, create = true)!!)
        } else {
            pending(app, ACTION_WARN, create = false)?.let { alarms.cancel(it); it.cancel() }
        }
        prefs(app).edit { putLong(KEY_ENDS_AT, endsAt) }
        _endsAt.value = endsAt
        Log.i(TAG, "Sleep timer set for ${durationMillis / 1000}s")
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java)
        for (action in listOf(ACTION_FIRE, ACTION_WARN)) {
            pending(app, action, create = false)?.let { alarms.cancel(it); it.cancel() }
        }
        prefs(app).edit { remove(KEY_ENDS_AT) }
        _endsAt.value = null
    }

    /** Whether the TV can be turned off: this app is an active device admin. */
    fun canTurnOff(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)?.isAdminActive(adminComponent(context)) == true

    fun adminComponent(context: Context) = ComponentName(context, SleepAdmin::class.java)

    /** Minutes left, rounded up, so a timer never reads "0 min" while still running. */
    fun minutesLeft(endsAt: Long, now: Long): Int = ((endsAt - now + 59_999) / 60_000).toInt().coerceAtLeast(0)

    internal fun fire(context: Context) {
        cancel(context)
        val power = context.getSystemService(PowerManager::class.java)
        // Already off: the user turned it off early. Don't do anything when it next wakes.
        if (power?.isInteractive == false) return
        pausePlayback(context)
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (dpm != null && canTurnOff(context)) {
            runCatching { dpm.lockNow() }.onFailure { Log.w(TAG, "Could not turn the TV off: ${it.message}") }
        } else {
            Log.w(TAG, "Sleep timer ended but the app isn't a device admin; run scripts/install.sh")
            Toast.makeText(context, "Timer ended. Run scripts/install.sh so Minimal Home can turn the TV off.", Toast.LENGTH_LONG).show()
        }
    }

    internal fun warn(context: Context) {
        if (context.getSystemService(PowerManager::class.java)?.isInteractive == false) return
        Toast.makeText(context, "The TV turns off in 1 minute", Toast.LENGTH_LONG).show()
    }

    /** Pauses every playing session, so apps keep their place. Needs playback access (the media listener). */
    private fun pausePlayback(context: Context) {
        runCatching {
            val sessions = context.getSystemService(MediaSessionManager::class.java) ?: return
            val listener = ComponentName(context, MediaListenerService::class.java)
            sessions.getActiveSessions(listener)
                .filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                .forEach { it.transportControls.pause() }
        }.onFailure { Log.w(TAG, "Could not pause playback: ${it.message}") }
    }

    private fun schedule(context: Context, alarms: AlarmManager, at: Long, intent: PendingIntent) {
        // A wakeup alarm, so it fires on time; fire() checks whether the TV is still on.
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (exact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, intent)
            } else {
                alarms.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, 30_000, intent)
            }
        } catch (e: SecurityException) {
            alarms.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, 30_000, intent)
        }
    }

    private fun pending(context: Context, action: String, create: Boolean): PendingIntent? {
        val intent = Intent(context, SleepTimerReceiver::class.java).setAction(action)
        val flags = PendingIntent.FLAG_IMMUTABLE or (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, flags)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Receives the timer's own alarms. Not exported. */
class SleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            SleepTimer.ACTION_FIRE -> SleepTimer.fire(context)
            SleepTimer.ACTION_WARN -> SleepTimer.warn(context)
        }
    }
}

/**
 * Commands from adb only (the DUMP permission is held by the shell, not by apps):
 *   am broadcast -a dev.minimal.tvhome.SLEEP_TIMER_SET --el seconds 90 -n dev.minimal.tvhome/.data.SleepTimerCommands
 *   am broadcast -a dev.minimal.tvhome.SLEEP_TIMER_CANCEL -n ...
 *   am broadcast -a dev.minimal.tvhome.REMOVE_ADMIN -n ...   (lets the app be uninstalled)
 */
class SleepTimerCommands : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "dev.minimal.tvhome.SLEEP_TIMER_SET" -> {
                val seconds = intent.getLongExtra("seconds", 0L)
                if (seconds > 0) SleepTimer.start(context, seconds * 1000)
            }
            "dev.minimal.tvhome.SLEEP_TIMER_CANCEL" -> SleepTimer.cancel(context)
            "dev.minimal.tvhome.REMOVE_ADMIN" -> {
                SleepTimer.cancel(context)
                context.getSystemService(DevicePolicyManager::class.java)
                    ?.removeActiveAdmin(SleepTimer.adminComponent(context))
            }
        }
        resultData = "ok"
    }
}

/** Device admin with one policy, lock screen, used only to put the TV into standby. */
class SleepAdmin : DeviceAdminReceiver()
