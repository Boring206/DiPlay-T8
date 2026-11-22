package com.shilapi.xcertplay

import android.graphics.*
import kotlin.math.sin

/** Shared artwork rendering; coordinates and opacity are owned by each host view. */
internal class ClusterArtworkBackground(
    /** A host-specific adjustment; the player keeps the source palette at 1.0. */
    private val saturationMultiplier: Float = 1f,
) {
    private var colors = palette(null, saturationMultiplier)
    private var fromColors = colors.copyOf()
    private var targetColors = colors.copyOf()
    private var changedAt = 0L
    private var hasArtwork = false
    private val evaluator = android.animation.ArgbEvaluator()
    private var shaders = emptyList<RadialGradient>()
    private var width = 0f
    private var height = 0f
    private val matrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun setArtwork(art: Bitmap?, now: Long = android.os.SystemClock.elapsedRealtime()) {
        updateColors(now)
        val next = palette(art, saturationMultiplier)
        val valid = art != null && !art.isRecycled
        if (!hasArtwork || !valid) {
            colors = next; fromColors = next.copyOf(); targetColors = next.copyOf()
        } else {
            fromColors = colors.copyOf()
            targetColors = next
        }
        changedAt = now
        hasArtwork = valid
        rebuild()
    }

    fun isTransitioning(now: Long): Boolean =
        now - changedAt < ClusterMusicView.FADE_MILLIS && !fromColors.contentEquals(targetColors)

    private fun updateColors(now: Long) {
        val progress = ClusterMusicView.progress(now, changedAt)
        var changed = false
        for (index in colors.indices) {
            val color = evaluator.evaluate(progress, fromColors[index], targetColors[index]) as Int
            if (colors[index] != color) { colors[index] = color; changed = true }
        }
        if (changed) rebuild()
    }

    /** Sample a central window of the player's wide composition, rather than shrink it to a square. */
    fun drawWideCrop(
        canvas: Canvas,
        w: Float,
        h: Float,
        now: Long,
        motion: Boolean = false,
        motionTimeMillis: Long = now,
    ) {
        val wide = maxOf(w, h * (1280f / 480f))
        val save = canvas.save()
        canvas.clipRect(0f, 0f, w, h)
        canvas.translate((w - wide) / 2f, 0f)
        draw(canvas, wide, h, now, motion, motionTimeMillis)
        canvas.restoreToCount(save)
    }

    private fun rebuild() {
        val radius = maxOf(width, height).coerceAtLeast(1f) * .48f
        shaders = colors.map { RadialGradient(0f, 0f, radius, it, Color.TRANSPARENT, Shader.TileMode.CLAMP) }
    }

    fun draw(
        canvas: Canvas,
        w: Float,
        h: Float,
        now: Long,
        motion: Boolean = false,
        motionTimeMillis: Long = now,
    ) {
        if (w != width || h != height || shaders.isEmpty()) {
            width = w; height = h; rebuild()
        }
        updateColors(now)
        val phase = if (motion) motionTimeMillis / 5_000.0 else 0.0
        canvas.drawColor(Color.rgb(16, 19, 27))
        shaders.forEachIndexed { index, shader ->
            matrix.setTranslate(w * (.5f + .48f * sin(phase + index * 2.1).toFloat()),
                h * (.5f + .5f * sin(phase * .73 + index * 2.7).toFloat()))
            shader.setLocalMatrix(matrix)
            paint.shader = shader; paint.alpha = 255
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        paint.shader = null
        paint.color = Color.argb(48, 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    companion object {
        fun palette(art: Bitmap?, saturationMultiplier: Float = 1f): IntArray {
            if (art == null || art.isRecycled) return IntArray(3) { Color.rgb(36, 36, 36) }
            // Count visible area, not saturation: a small vivid detail must not replace the sleeve.
            val hsv = FloatArray(3)
            val buckets = Array(27) { LongArray(4) }
            for (y in 0..23) for (x in 0..23) {
                val color = art.getPixel(x * art.width / 24, y * art.height / 24)
                if (Color.alpha(color) < 128) continue
                Color.colorToHSV(color, hsv)
                val key = if (hsv[1] < .08f || hsv[2] < .08f)
                    24 + (hsv[2] * 3).toInt().coerceIn(0, 2)
                else (hsv[0] / 15).toInt().coerceIn(0, 23)
                val bucket = buckets[key]
                bucket[0] += Color.red(color).toLong()
                bucket[1] += Color.green(color).toLong()
                bucket[2] += Color.blue(color).toLong()
                bucket[3]++
            }
            val ranked = buckets.filter { it[3] > 0 }.sortedByDescending { it[3] }
            if (ranked.isEmpty()) return intArrayOf(Color.DKGRAY, Color.DKGRAY, Color.DKGRAY)
            // Tiny contrasting details must not receive a whole animated layer.
            val visiblePixels = ranked.sumOf { it[3] }
            val minimumArea = maxOf(visiblePixels * .10, ranked.first()[3] * .25)
            val significant = ranked.filterIndexed { index, bucket -> index == 0 || bucket[3] >= minimumArea }
            val selected = mutableListOf<LongArray>()
            val selectedTones = mutableListOf<FloatArray>()
            for (bucket in significant) {
                val tone = FloatArray(3)
                Color.colorToHSV(Color.rgb((bucket[0]/bucket[3]).toInt(),
                    (bucket[1]/bucket[3]).toInt(), (bucket[2]/bucket[3]).toInt()), tone)
                val distinct = selectedTones.all { previous ->
                    if (tone[1] < .08f || previous[1] < .08f)
                        (tone[1] < .08f) != (previous[1] < .08f)
                    else {
                        val distance = kotlin.math.abs(tone[0]-previous[0])
                        minOf(distance,360f-distance) >= 40f
                    }
                }
                if (distinct) { selected.add(bucket); selectedTones.add(tone) }
                if (selected.size == 3) break
            }
            return (0..2).map { index ->
                val bucket = selected.getOrElse(index) { selected.first() }
                val color = Color.rgb((bucket[0]/bucket[3]).toInt(),
                    (bucket[1]/bucket[3]).toInt(), (bucket[2]/bucket[3]).toInt())
                Color.colorToHSV(color, hsv)
                // Do not lift tiny dark colored details to the same brightness as large light
                // regions of the artwork. The compact speedometer may opt into slightly more
                // saturation so its moving glows remain legible at dashboard distance.
                if (hsv[1] >= .08f && saturationMultiplier > 1f) {
                    hsv[1] = (hsv[1] * saturationMultiplier).coerceAtMost(1f)
                }
                hsv[2] = minOf(hsv[2] * 1.08f + .04f, floatArrayOf(.46f, .43f, .45f)[index])
                Color.HSVToColor(hsv)
            }.toIntArray()
        }
    }
}
