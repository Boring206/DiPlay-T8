package com.shilapi.xcertplay

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * A tinted disc carrying a tick, a cross, an exclamation mark or a dot. Drawn rather than typed:
 * the text characters for these marks come out in a different size and weight on every head
 * unit's font, and some fonts lack them.
 */
internal class StatusMarkDrawable(private val kind: Kind, color: Int) : Drawable() {
    enum class Kind { TICK, CROSS, ALERT, DOT }

    private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = (color and 0x00FFFFFF) or 0x30000000 }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val size = minOf(bounds.width(), bounds.height()).toFloat()
        if (size <= 0f) return
        val left = bounds.exactCenterX() - size / 2
        val top = bounds.exactCenterY() - size / 2
        fun x(fraction: Float) = left + fraction * size
        fun y(fraction: Float) = top + fraction * size
        canvas.drawCircle(x(.5f), y(.5f), size / 2, disc)
        stroke.strokeWidth = size * .1f
        path.rewind()
        when (kind) {
            Kind.TICK -> {
                path.moveTo(x(.29f), y(.52f)); path.lineTo(x(.44f), y(.67f)); path.lineTo(x(.72f), y(.35f))
            }
            Kind.CROSS -> {
                path.moveTo(x(.34f), y(.34f)); path.lineTo(x(.66f), y(.66f))
                path.moveTo(x(.66f), y(.34f)); path.lineTo(x(.34f), y(.66f))
            }
            Kind.ALERT -> {
                path.moveTo(x(.5f), y(.27f)); path.lineTo(x(.5f), y(.55f))
                canvas.drawCircle(x(.5f), y(.72f), size * .06f, fill)
            }
            Kind.DOT -> canvas.drawCircle(x(.5f), y(.5f), size * .11f, fill)
        }
        canvas.drawPath(path, stroke)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
