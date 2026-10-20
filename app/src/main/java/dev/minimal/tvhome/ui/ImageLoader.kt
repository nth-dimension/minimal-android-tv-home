package dev.minimal.tvhome.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Small poster loader so the app needs no image library. Memory cache only. */
object ImageLoader {
    private const val TAG = "MinimalHome"
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val MAX_REDIRECTS = 5

    private val cache = object : LruCache<String, Bitmap>(32 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    /** Why the last attempt at each URI failed, for the diagnostics screen. */
    private val failures = ConcurrentHashMap<String, String>()

    fun failureFor(uri: String): String? = failures[uri]

    /** Tries each URI in order (thumbnail, then poster) and returns the first that decodes. */
    suspend fun load(context: Context, uris: List<String>, targetWidthPx: Int): Bitmap? {
        for (uri in uris) {
            // Cached per size, so a big card never gets the small copy a thumbnail loaded first.
            val key = "$uri@$targetWidthPx"
            cache.get(key)?.let { return it }
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    open(context, uri).use { decode(it.readCapped(), targetWidthPx) }
                        ?: throw IOException("not a decodable image")
                } catch (e: Exception) {
                    val reason = e.message ?: e.javaClass.simpleName
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
        return when (parsed.scheme?.lowercase()) {
            "https", "http" -> openHttp(uri)
            "content", "android.resource", "file" ->
                context.contentResolver.openInputStream(parsed) ?: throw IOException("provider returned nothing")
            else -> throw IOException("unsupported scheme")
        }
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
                in 200..299 -> return conn.inputStream
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

    private fun InputStream.readCapped(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_BYTES) throw IOException("image too large")
        }
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray, targetWidthPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidthPx) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
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

@Composable
fun rememberDrawableImage(key: String, drawable: Drawable?, widthPx: Int, heightPx: Int): ImageBitmap? =
    // Keyed on the drawable's shared state, not its instance: each app-list load makes new instances.
    remember(key, drawable?.constantState ?: drawable) {
        drawable?.let { runCatching { it.toBitmap(widthPx, heightPx).asImageBitmap() }.getOrNull() }
    }
