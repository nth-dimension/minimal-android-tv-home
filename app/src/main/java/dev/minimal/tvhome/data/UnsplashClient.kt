package dev.minimal.tvhome.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Unsplash API calls shared by the home background and the screensaver. Blocking; call off the main thread. */
class UnsplashClient(private val context: Context, private val accessKey: String) {

    val isConfigured: Boolean get() = accessKey.isNotBlank()

    /** Downloads a random landscape photo for [topic] into [dir] as `<id>.jpg`, sized to the screen. */
    fun fetchRandom(topic: WallpaperTopic, dir: File, now: Long): Wallpaper {
        val photo = WallpaperRules.parsePhoto(apiGet(WallpaperRules.randomPhotoUrl(topic.query)))
        val width = context.resources.displayMetrics.widthPixels.coerceIn(1280, 3840)
        dir.mkdirs()
        val target = File(dir, "${photo.id}.jpg")
        val partial = File(dir, "${photo.id}.part")
        download(WallpaperRules.sizedImageUrl(photo.rawUrl, width), partial)
        if (!partial.renameTo(target)) throw IOException("Couldn't save the photo.")

        // Unsplash API guidelines: report each photo that gets used.
        photo.downloadLocation?.let { runCatching { apiGet(it) } }
        return Wallpaper(photo.id, target.absolutePath, photo.photographer, now, topic.name)
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

    private companion object {
        const val MAX_BYTES = 15L * 1024 * 1024
    }
}
