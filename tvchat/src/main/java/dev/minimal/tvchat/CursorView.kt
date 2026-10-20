package dev.minimal.tvchat

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** The remote's pointer: a ring drawn over the page. Draws only; input goes to the WebView. */
class CursorView(context: Context) : View(context) {

    // Not x and y: View already has those (its own position).
    var px = 0f
        private set
    var py = 0f
        private set
    private var pulse = 0f
    private val density = resources.displayMetrics.density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 37, 211, 102) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = Color.WHITE
        setShadowLayer(4 * density, 0f, 0f, Color.BLACK)
    }

    init {
        isFocusable = false
        isClickable = false
        setLayerType(LAYER_TYPE_SOFTWARE, null) // the shadow needs it
    }

    fun moveTo(nx: Float, ny: Float) {
        px = nx.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
        py = ny.coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
        invalidate()
    }

    /** A brief grow-and-shrink to show a click landed. */
    fun pulse() {
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 220
            addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val r = (11 + 6 * pulse) * density
        canvas.drawCircle(px, py, r, fill)
        canvas.drawCircle(px, py, r, ring)
    }
}
