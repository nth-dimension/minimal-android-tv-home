package dev.minimal.tvhome.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.BufferedInputStream
import java.io.InputStream

/** The one bitmap decoder: scaled down to what's shown, and in 16-bit colour for photos. */
object Images {
    /** Nothing on a TV needs more than this; a 4K-wide photo would be 33 MB decoded. */
    const val MAX_WIDTH = 1920

    /** Enough for any normal JPEG or PNG header; a reset past this re-opens the source instead. */
    private const val HEADER_BYTES = 256 * 1024

    /** Subsampling factor so the decoded width stays at or above [targetWidth]. */
    fun sampleSize(sourceWidth: Int, targetWidth: Int): Int {
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth) sample *= 2
        return sample
    }

    fun decodeFile(path: String, targetWidth: Int, photo: Boolean = true): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        BitmapFactory.decodeFile(path, options(bounds.outWidth, targetWidth, photo))
    }.getOrNull()

    /**
     * Decodes a stream without first reading the whole image into memory: the size comes from the
     * header, which is re-read from a small buffer, and the pixels are then decoded straight from
     * the stream. If the header is unusually long (a big embedded thumbnail), [open] is called
     * again for the pixels.
     */
    fun decodeStream(targetWidth: Int, photo: Boolean = true, open: () -> InputStream): Bitmap? {
        val stream = BufferedInputStream(open(), HEADER_BYTES)
        try {
            stream.mark(HEADER_BYTES)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, bounds)
            if (bounds.outWidth <= 0) return null
            val opts = options(bounds.outWidth, targetWidth, photo)
            val rewound = runCatching { stream.reset() }.isSuccess
            return if (rewound) {
                BitmapFactory.decodeStream(stream, null, opts)
            } else {
                stream.close()
                open().use { BitmapFactory.decodeStream(it, null, opts) }
            }
        } finally {
            runCatching { stream.close() }
        }
    }

    private fun options(sourceWidth: Int, targetWidth: Int, photo: Boolean) = BitmapFactory.Options().apply {
        inSampleSize = sampleSize(sourceWidth, targetWidth.coerceAtMost(MAX_WIDTH))
        // Photos have no transparency and the difference from 32-bit isn't visible on a TV; half the memory.
        if (photo) inPreferredConfig = Bitmap.Config.RGB_565
    }

}
