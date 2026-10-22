package dev.minimal.tvhome.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.HttpResponseCache
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import dev.minimal.tvhome.data.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Small poster loader so the app needs no image library. Decoded posters sit in a memory cache;
 * the bytes behind them sit in the system HTTP cache on disk (see [installHttpCache]), so a
 * restart or a second size of the same poster costs no download.
 */
object ImageLoader {
    private const val TAG = "MinimalHome"
    private const val MAX_BYTES = 8L * 1024 * 1024
    private const val MAX_REDIRECTS = 5
    private const val HTTP_CACHE_BYTES = 48L * 1024 * 1024
    private const val MAX_FAILURES = 200

    private val cache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    /** Why the last attempt at each URI failed, for the diagnostics screen. Bounded: URLs are often signed. */
    private val failures = ConcurrentHashMap<String, String>()

    fun failureFor(uri: String): String? = failures[uri]

    /** Gives HttpURLConnection a disk cache for every image and Unsplash call. Safe to call again. */
    fun installHttpCache(context: Context) {
        if (HttpResponseCache.getInstalled() != null) return
        runCatching { HttpResponseCache.install(File(context.cacheDir, "http"), HTTP_CACHE_BYTES) }
            .onFailure { Log.w(TAG, "No HTTP cache: ${it.message}") }
    }

    /** Tries each URI in order (thumbnail, then poster) and returns the first that decodes. */
    suspend fun load(context: Context, uris: List<String>, targetWidthPx: Int): Bitmap? {
        for (uri in uris) {
            // Cached per size, so a big card never gets the small copy a thumbnail loaded first.
            val key = "$uri@$targetWidthPx"
            cache.get(key)?.let { return it }
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    Images.decodeStream(targetWidthPx) { open(context, uri) } ?: throw IOException("not a decodable image")
                } catch (e: Exception) {
                    val reason = e.message ?: e.javaClass.simpleName
                    if (failures.size >= MAX_FAILURES) failures.clear()
                    if (failures.put(uri, reason) == null) Log.w(TAG, "Image failed: ${describe(uri)}: $reason")
                    null
                }
            }
            if (bitmap != null) {
                failures.remove(uri)
                cache.put(key, bitmap)
                return bitmap
            }
        }
        return null
    }

    /** Scheme and host only, so logs don't carry signed query strings. */
    fun describe(uri: String): String = Uri.parse(uri).let { "${it.scheme}://${it.authority}" }

    private fun open(context: Context, uri: String): InputStream {
        val parsed = Uri.parse(uri)
        val stream = when (parsed.scheme?.lowercase()) {
            "https", "http" -> openHttp(uri)
            "content", "android.resource", "file" ->
                context.contentResolver.openInputStream(parsed) ?: throw IOException("provider returned nothing")
            else -> throw IOException("unsupported scheme")
        }
        return Capped(stream)
    }

    /** Follows redirects by hand, since HttpURLConnection won't cross between http and https. */
    private fun openHttp(start: String): InputStream {
        var url = URL(start)
        repeat(MAX_REDIRECTS + 1) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 10_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "MinimalHome/1.0 (Android TV)")
                setRequestProperty("Accept", "image/*")
            }
            val code = conn.responseCode
            when (code) {
                in 200..299 -> {
                    if (conn.contentLengthLong > MAX_BYTES) {
                        conn.disconnect()
                        throw IOException("image too large")
                    }
                    return conn.inputStream
                }
                301, 302, 303, 307, 308 -> {
                    val location = conn.getHeaderField("Location") ?: throw IOException("HTTP $code without Location")
                    conn.disconnect()
                    url = URL(url, location)
                    if (url.protocol != "https" && url.protocol != "http") throw IOException("redirect to ${url.protocol}")
                }
                else -> {
                    conn.disconnect()
                    throw IOException("HTTP $code")
                }
            }
        }
        throw IOException("too many redirects")
    }

    /** Stops reading past [MAX_BYTES], for sources that send no length. */
    private class Capped(stream: InputStream) : FilterInputStream(stream) {
        private var total = 0L

        override fun read(): Int = super.read().also { if (it >= 0) count(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count(it) }

        private fun count(n: Int) {
            total += n
            if (total > MAX_BYTES) throw IOException("image too large")
        }
    }
}

@Composable
fun rememberRemoteImage(uris: List<String>, targetWidthPx: Int): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, uris) {
        value = if (uris.isEmpty()) null else ImageLoader.load(context, uris, targetWidthPx)?.asImageBitmap()
    }
    return image
}
