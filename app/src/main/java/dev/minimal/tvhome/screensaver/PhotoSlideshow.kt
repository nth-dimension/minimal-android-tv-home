package dev.minimal.tvhome.screensaver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextClock
import android.widget.TextView
import dev.minimal.tvhome.data.ScreensaverPhotos
import dev.minimal.tvhome.data.ScreensaverRules
import dev.minimal.tvhome.data.Wallpaper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Full-screen Unsplash photos with a clock, shared by the screensaver and its preview.
 * Shows the newest saved photo at once, then a new photo every five minutes, crossfading and
 * drifting slowly. When no new photo can be fetched it cycles through the saved ones.
 * Plain Views rather than Compose: a screensaver window has no lifecycle owner to host Compose.
 */
class PhotoSlideshow(private val context: Context) {
    private val photos = ScreensaverPhotos(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null
    private var current: Wallpaper? = null
    private var drift: Job? = null

    private val first = image()
    private val second = image()
    private var top = second
    private val credit = text(14f, alpha = 0.8f)
    private val status = text(18f, alpha = 0.8f)
    private val clock = TextClock(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 56f)
        setShadowLayer(12f, 0f, 2f, 0x99000000.toInt())
        letterSpacing = 0.02f
    }

    val view: View = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        addView(first, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(second, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        // Darken the bottom so the clock and credit read on any photo.
        addView(
            View(context).apply {
                background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0x88000000.toInt(), 0x00000000))
            },
            FrameLayout.LayoutParams(MATCH_PARENT, dp(220), Gravity.BOTTOM),
        )
        addView(clock, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).margins(dp(56), dp(40)))
        addView(credit, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).margins(dp(56), dp(52)))
        addView(status, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        // Photos use translationZ 0 and 1 to take turns on top; everything else stays above them.
        for (i in 2 until childCount) getChildAt(i).translationZ = 2f
    }

    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            val start = withContext(Dispatchers.IO) { photos.firstPhoto() }
            if (start != null) show(start) else status.text = if (photos.isConfigured) "Loading photos…" else
                "Unsplash photos need an access key. Reinstall with scripts/install.sh --unsplash-key YOUR_KEY"
            val newest = withContext(Dispatchers.IO) { photos.pool().firstOrNull()?.fetchedAt }
            // A photo fetched a minute ago (the screensaver restarted) stays up for the rest of its five minutes.
            var nextAt = SystemClock.elapsedRealtime() +
                if (start == null) 0 else ScreensaverRules.waitBeforeFetch(newest, System.currentTimeMillis())
            while (isActive) {
                // Fetch a minute early, so a slow download doesn't push the swap past five minutes.
                delay((nextAt - PREFETCH_MS - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                Log.i(TAG, "Screensaver: fetching a new photo")
                val pick = photos.fetch() ?: withContext(Dispatchers.IO) { ScreensaverRules.next(photos.pool(), current?.id) }
                delay((nextAt - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                if (pick != null && pick.id != current?.id) show(pick)
                nextAt = SystemClock.elapsedRealtime() + ScreensaverRules.SWAP_MS
            }
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        drift?.cancel()
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private suspend fun show(photo: Wallpaper) {
        val width = context.resources.displayMetrics.widthPixels
        val bitmap = withContext(Dispatchers.IO) { decode(photo.file, width) }
        Log.i(TAG, "Screensaver: showing ${photo.id}${if (bitmap == null) " failed to decode" else ""}")
        if (bitmap == null) return
        current = photo
        status.text = ""
        credit.text = "Photo by ${photo.photographer} on Unsplash"

        // The two image views take turns: the new photo fades in above the old one, then the old one empties.
        val incoming = if (top === first) second else first
        val outgoing = top
        incoming.setImageBitmap(bitmap)
        incoming.alpha = 0f
        incoming.translationZ = 1f
        outgoing.translationZ = 0f
        startDrift(incoming)
        incoming.animate().alpha(1f).setDuration(FADE_MS).withEndAction { outgoing.setImageDrawable(null) }.start()
        top = incoming
    }

    /**
     * A slow zoom and pan across the whole five minutes, like a photo frame's Ken Burns effect.
     * Stepped ten times a second, not every frame: it moves under a pixel per step, so the result
     * looks the same while the TV redraws a sixth as often.
     */
    private fun startDrift(target: ImageView) {
        drift?.cancel()
        val screen = context.resources.displayMetrics
        val dx = (if (Random.nextBoolean()) 1 else -1) * screen.widthPixels * 0.02f
        val dy = (if (Random.nextBoolean()) 1 else -1) * screen.heightPixels * 0.015f
        val length = (ScreensaverRules.SWAP_MS + FADE_MS).toFloat()
        val startedAt = SystemClock.uptimeMillis()
        drift = scope.launch {
            while (isActive) {
                val f = ((SystemClock.uptimeMillis() - startedAt) / length).coerceAtMost(1f)
                val scale = 1f + 0.07f * f
                target.scaleX = scale
                target.scaleY = scale
                target.translationX = dx * f
                target.translationY = dy * f
                if (f >= 1f) break
                delay(DRIFT_STEP_MS)
            }
        }
    }

    private fun image() = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        // Zoom and pan as a GPU layer: no redraw of the photo itself on each frame.
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    private fun text(sizeSp: Float, alpha: Float) = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setShadowLayer(8f, 0f, 1f, 0x99000000.toInt())
        this.alpha = alpha
        gravity = Gravity.CENTER
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private fun FrameLayout.LayoutParams.margins(h: Int, bottom: Int) = apply { setMargins(h, 0, h, bottom) }

    private companion object {
        const val FADE_MS = 1_500L
        const val PREFETCH_MS = 60_000L
        const val DRIFT_STEP_MS = 100L
        const val TAG = "MinimalHome"

        fun decode(path: String, targetWidth: Int): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }
}
