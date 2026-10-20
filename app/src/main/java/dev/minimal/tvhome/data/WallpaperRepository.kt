package dev.minimal.tvhome.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches a random landscape photo from the Unsplash API and keeps the current one on disk,
 * so it shows instantly after a reboot and while offline.
 */
class WallpaperRepository(private val context: Context, private val accessKey: String) {

    class Result(val wallpaper: Wallpaper?, val error: String?)

    val isConfigured: Boolean get() = accessKey.isNotBlank()

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
        val photo = WallpaperRules.parsePhoto(apiGet(WallpaperRules.randomPhotoUrl(settings.topic.query)))
        val width = context.resources.displayMetrics.widthPixels.coerceIn(1280, 3840)
        val target = File(dir, "${photo.id}.jpg")
        val partial = File(dir, "${photo.id}.part")
        download(WallpaperRules.sizedImageUrl(photo.rawUrl, width), partial)
        if (!partial.renameTo(target)) throw IOException("Couldn't save the photo.")

        // Unsplash API guidelines: report each photo that gets used.
        photo.downloadLocation?.let { runCatching { apiGet(it) } }

        val wallpaper = Wallpaper(photo.id, target.absolutePath, photo.photographer, now, settings.topic.name)
        metaFile.writeText(WallpaperRules.encode(wallpaper))
        dir.listFiles()?.filter { it != target && it != metaFile }?.forEach { it.delete() }
        return wallpaper
    }

    private fun apiGet(url: String): String {
        val conn = open(url).apply {
            setRequestProperty("Authorization", "Client-ID $accessKey")
            setRequestProperty("Accept-Version", "v1")
        }
        try {
            when (val code = conn.responseCode) {
                in 200..299 -> return conn.inputStream.bufferedReader().use { it.readText() }
                401 -> throw IOException("Unsplash rejected the access key. Check it in local.properties.")
                403 -> throw IOException("Unsplash hourly limit reached. A new photo will load later.")
                else -> throw IOException("Unsplash returned HTTP $code.")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun download(url: String, to: File) {
        val conn = open(url)
        try {
            if (conn.responseCode !in 200..299) throw IOException("Photo download failed (HTTP ${conn.responseCode}).")
            var total = 0L
            conn.inputStream.use { input ->
                to.outputStream().use { out ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw IOException("Photo too large.")
                        out.write(buffer, 0, n)
                    }
                }
            }
        } catch (e: Exception) {
            to.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 20_000
        instanceFollowRedirects = true
    }

    companion object {
        private const val TAG = "MinimalHome"
        private const val MAX_BYTES = 15L * 1024 * 1024
    }
}
