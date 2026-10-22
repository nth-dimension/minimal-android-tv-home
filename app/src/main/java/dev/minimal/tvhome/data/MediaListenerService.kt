package dev.minimal.tvhome.data

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * Watches media sessions so Up Next can include apps that never publish Watch Next rows.
 * Android only lets notification listeners see other apps' sessions; no notification is read.
 * Access is granted over ADB by scripts/install.sh.
 */
class MediaListenerService : NotificationListenerService() {

    // Everything here runs on one background thread: session callbacks land on it, and saving a
    // playback (JSON plus a scaled poster) never touches the home screen's main thread.
    private val thread = HandlerThread("media-listener").apply { start() }
    private val handler = Handler(thread.looper)
    private var sessions: MediaSessionManager? = null
    private val tracked = mutableMapOf<MediaSession.Token, Tracked>()

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { track(it.orEmpty()) }

    override fun onListenerConnected() {
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        sessions = manager
        val me = ComponentName(this, MediaListenerService::class.java)
        handler.post {
            runCatching {
                manager.addOnActiveSessionsChangedListener(sessionsChanged, me, handler)
                track(manager.getActiveSessions(me))
            }.onFailure { Log.w(TAG, "Media sessions unavailable: ${it.message}") }
        }
    }

    override fun onListenerDisconnected() {
        handler.post {
            runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsChanged) }
            tracked.values.forEach { it.stop() }
            tracked.clear()
        }
    }

    override fun onDestroy() {
        // super first: it calls onListenerDisconnected, whose cleanup is posted to this thread.
        // quitSafely still runs it; quitting first dropped it ("sending message to a dead thread").
        super.onDestroy()
        thread.quitSafely()
    }

    private fun track(controllers: List<MediaController>) {
        // Hidden apps would never be shown, so their plays aren't recorded either. (Qualified:
        // NotificationListenerService has its own nested Ranking class.)
        val hidden = dev.minimal.tvhome.data.Ranking.HIDDEN_FROM_WATCH_NEXT
        val current = controllers
            .filter { it.packageName != packageName && it.packageName !in hidden }
            .associateBy { it.sessionToken }
        (tracked.keys - current.keys).forEach { token -> tracked.remove(token)?.stop() }
        current.forEach { (token, controller) ->
            if (token !in tracked) tracked[token] = Tracked(controller).also { it.start() }
        }
    }

    /** One app's session. Records on pause, stop, title change, and every [SAVE_INTERVAL_MS] while playing. */
    private inner class Tracked(private val controller: MediaController) : MediaController.Callback() {
        private var lastSaved = 0L
        private var artFor: String? = null
        private var artUri: String? = null

        fun start() {
            controller.registerCallback(this, handler)
            capture(force = true)
        }

        fun stop() {
            capture(force = true)
            runCatching { controller.unregisterCallback(this) }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) = capture(force = true)

        override fun onPlaybackStateChanged(state: PlaybackState?) =
            capture(force = state?.state != PlaybackState.STATE_PLAYING)

        override fun onSessionDestroyed() {
            tracked.remove(controller.sessionToken)
            stop()
        }

        private fun capture(force: Boolean) {
            val now = SystemClock.elapsedRealtime()
            if (!force && now - lastSaved < SAVE_INTERVAL_MS) return
            val metadata = controller.metadata ?: return
            val state = controller.playbackState ?: return
            if (state.state == PlaybackState.STATE_NONE || state.state == PlaybackState.STATE_ERROR) return
            // Music has an album; Up Next is for things you watch.
            if (!metadata.getString(MediaMetadata.METADATA_KEY_ALBUM).isNullOrBlank()) return
            val title = (metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_TITLE))?.trim()
            if (title.isNullOrEmpty()) return
            val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            lastSaved = now
            PlayHistory.record(
                this@MediaListenerService,
                PlayedItem(
                    packageName = controller.packageName,
                    title = title,
                    subtitle = (metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                        ?: metadata.getString(MediaMetadata.METADATA_KEY_ARTIST))?.trim()?.ifEmpty { null },
                    imageUris = images(metadata, title),
                    positionMillis = position(state, now),
                    durationMillis = duration,
                    lastPlayedMillis = System.currentTimeMillis(),
                ),
            )
        }

        private fun images(metadata: MediaMetadata, title: String): List<String> {
            val url = listOf(
                MediaMetadata.METADATA_KEY_ART_URI,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
                MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            ).firstNotNullOfOrNull { key ->
                metadata.getString(key)?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            }
            // Saving the bitmap is the slow part, so do it once per title.
            if (artFor != title) {
                artFor = title
                val bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                artUri = bitmap?.let { PlayHistory.saveArt(this@MediaListenerService, controller.packageName, title, it) }
            }
            return listOfNotNull(url, artUri).distinct()
        }

        /** The session reports position at its last update; extrapolate while it plays. */
        private fun position(state: PlaybackState, now: Long): Long {
            if (state.state != PlaybackState.STATE_PLAYING || state.lastPositionUpdateTime <= 0) return state.position
            return state.position + ((now - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        }
    }

    companion object {
        private const val TAG = "MinimalHome"
        // Position is also saved on pause, stop and title change, so this only bounds how stale a
        // playback that ends abruptly (TV switched off) can be.
        private const val SAVE_INTERVAL_MS = 120_000L

        fun hasAccess(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    }
}
