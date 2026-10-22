package dev.minimal.tvhome.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fetches a random landscape photo from the Unsplash API and keeps the current one on disk,
 * so it shows instantly after a reboot and while offline.
 */
class WallpaperRepository(private val context: Context, accessKey: String) {

    private val client = UnsplashClient(context, accessKey)

    class Result(val wallpaper: Wallpaper?, val error: String?)

    val isConfigured: Boolean get() = client.isConfigured

    private val mutex = Mutex()
    private val dir: File get() = File(context.filesDir, "wallpaper").apply { mkdirs() }
    private val metaFile: File get() = File(dir, "current.json")

    fun current(): Wallpaper? = runCatching {
        if (!metaFile.exists()) return null
        WallpaperRules.decode(metaFile.readText()).takeIf { File(it.file).exists() }
    }.getOrNull()

    /** Downloads a new photo if one is due (or [force] is set). On failure the old photo stays. */
    suspend fun refreshIfNeeded(settings: WallpaperSettings, force: Boolean = false): Result =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val current = current()
                if (settings.mode != BackgroundMode.Unsplash || !isConfigured) return@withContext Result(current, null)
                val now = System.currentTimeMillis()
                if (!force && !WallpaperRules.isStale(current, settings, now)) return@withContext Result(current, null)
                try {
                    Result(fetchNew(settings, now), null)
                } catch (e: Exception) {
                    Log.w(TAG, "Wallpaper refresh failed", e)
                    Result(current, e.message ?: "Couldn't load a new photo.")
                }
            }
        }

    private fun fetchNew(settings: WallpaperSettings, now: Long): Wallpaper {
        val wallpaper = client.fetchRandom(settings.topic, dir, now)
        metaFile.writeText(WallpaperRules.encode(wallpaper))
        val keep = File(wallpaper.file)
        dir.listFiles()?.filter { it != keep && it != metaFile }?.forEach { it.delete() }
        return wallpaper
    }

    companion object {
        private const val TAG = "MinimalHome"
    }
}
