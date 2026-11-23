package com.shilapi.xcertplay

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.text.StaticLayout
import android.text.Layout
import android.view.View
import com.shilapi.xcertplay.airplay.ClusterPlayerSettings
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.hud.ClusterVehicleReading
import java.text.NumberFormat

/** A local dashboard page. No video, controls, per-frame bitmap blur or vehicle writes. */
internal class ClusterPlayerView(
    context: Context,
    private val observeVehicle: ((ClusterVehicleReading) -> Unit) -> (() -> Unit) = { changed ->
        com.shilapi.xcertplay.hud.BydClusterVehicleReader.observe(context, changed)
    },
    private val observeSpeed: ((Int?) -> Unit) -> (() -> Unit) = { changed ->
        com.shilapi.xcertplay.hud.BydClusterSpeedReader.observe(context, changed)
    },
) : View(context), ClusterOutputVisibility {
    private val main = Handler(Looper.getMainLooper())
    private var stopState: (() -> Unit)? = null
    private var stopMotion: (() -> Unit)? = null
    private var motion = false
    private var fit = true
    private var showSpeed = true
    private var showVehicle = false
    private var outputVisible = true
    private var stopSettings: (() -> Unit)? = null
    private var stopSpeed: (() -> Unit)? = null
    private var stopVehicle: (() -> Unit)? = null
    private var vehicleGeneration = 0
    private var vehicle = ClusterVehicleReading()
    private var generation = 0
    private var speedKmh: Int? = null
    private var snapshot = ClusterMusicState.Snapshot()
    private var cover: Bitmap? = null
    private var previousCover: Bitmap? = null
    private var coverChangedAt = 0L
    private val background = ClusterArtworkBackground()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val clip = Path()
    private val artworkRect = RectF()
    private val artworkSource = Rect()
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = text.typeface; color = Color.WHITE }
    private var cachedTitle: StaticLayout? = null
    private var cachedTitleText = ""
    private var cachedTitleWidth = -1
    private var cachedBase = -1f
    private var cachedTitleY = -1f
    private var cachedBarY = -1f
    private var fittedBase = 0f
    private var fittedTop = 0f
    private var cachedFooter: StaticLayout? = null
    private var cachedFooterValue = ""
    private var cachedFooterWidth = -1
    private var cachedFooterSize = -1f
    private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = text.typeface; color = Color.argb(230,255,255,255) }
    private var numberLocale: java.util.Locale? = null
    private var numbers: NumberFormat? = null
    private val expireSpeed = Runnable { speedKmh = null; invalidate() }
    private val expireVehicle = Runnable { vehicle = ClusterVehicleReading(); invalidate() }
    private val frame = Runnable { invalidate() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        stopSettings = ClusterPlayerSettings.observe(context) { refresh() }
        stopMotion = com.shilapi.xcertplay.airplay.ClusterBackgroundSettings.observe(context) { refresh() }
        stopState = ClusterMusicState.observe {
            val now = SystemClock.elapsedRealtime()
            val redraw = snapshot.artwork !== it.artwork || snapshot.info.title != it.info.title ||
                snapshot.info.artist != it.info.artist || snapshot.info.album != it.info.album ||
                snapshot.info.playing != it.info.playing || snapshot.info.durationMillis != it.info.durationMillis ||
                kotlin.math.abs((elapsed(snapshot, now) ?: 0) - (elapsed(it, now) ?: 0)) > 1_500
            snapshot = it
            if (cover !== it.artwork) {
                val now = SystemClock.elapsedRealtime()
                previousCover = cover?.takeUnless { bitmap -> bitmap.isRecycled }
                cover = it.artwork?.takeUnless { bitmap -> bitmap.isRecycled }
                if (cover == null) previousCover = null
                coverChangedAt = if (previousCover == null) now - ClusterMusicView.FADE_MILLIS else now
                background.setArtwork(cover, now)
            }
            if (redraw) invalidate()
        }
        refresh()
    }

    override fun setOutputVisible(visible: Boolean) {
        outputVisible = visible
        main.removeCallbacks(frame); removeCallbacks(frame)
        invalidate()
    }

    private fun refresh() {
        motion = com.shilapi.xcertplay.airplay.ClusterBackgroundSettings.motion(context)
        fit = ClusterPlayerSettings.fitVisible(context)
        showSpeed = ClusterPlayerSettings.speed(context)
        showVehicle = ClusterPlayerSettings.vehicle(context)
        val enabled = ClusterPlayerSettings.enabled(context)
        visibility = if (enabled) VISIBLE else GONE
        if (enabled && isShown && windowVisibility == VISIBLE && ClusterPlayerSettings.speed(context) && stopSpeed == null) {
            val token = ++generation
            stopSpeed = observeSpeed { value ->
                val received = SystemClock.elapsedRealtime()
                main.post {
                    if (generation == token && isAttachedToWindow) {
                        speedKmh = value?.takeIf { it in 0..300 && SystemClock.elapsedRealtime() - received < 2_000 }
                        main.removeCallbacks(expireSpeed)
                        main.postDelayed(expireSpeed, 2_000)
                        invalidate()
                    }
                }
            }
        } else if (!enabled || !isShown || windowVisibility != VISIBLE || !ClusterPlayerSettings.speed(context)) {
            ++generation
            stopSpeed?.invoke(); stopSpeed = null
            speedKmh = null
            main.removeCallbacks(expireSpeed)
        }
        val readVehicle = enabled && isShown && windowVisibility == VISIBLE && ClusterPlayerSettings.vehicle(context)
        if (readVehicle && stopVehicle == null) {
            val token = ++vehicleGeneration
            stopVehicle = observeVehicle { reading ->
                main.post {
                    if (vehicleGeneration == token && isAttachedToWindow) {
                        vehicle = reading.fresh(SystemClock.elapsedRealtime())
                        main.removeCallbacks(expireVehicle)
                        main.postDelayed(expireVehicle, 3_000)
                        invalidate()
                    }
                }
            }
        } else if (!readVehicle) {
            ++vehicleGeneration
            stopVehicle?.invoke(); stopVehicle = null
            vehicle = ClusterVehicleReading()
            main.removeCallbacks(expireVehicle)
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        ++vehicleGeneration
        stopVehicle?.invoke(); stopVehicle = null
        vehicle = ClusterVehicleReading()
        ++generation
        stopSpeed?.invoke(); stopSpeed = null
        stopState?.invoke(); stopState = null
        stopSettings?.invoke(); stopSettings = null
        stopMotion?.invoke(); stopMotion = null
        main.removeCallbacksAndMessages(null); removeCallbacks(frame)
        speedKmh = null
        cover = null
        previousCover = null
        snapshot = ClusterMusicState.Snapshot()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // Visibility callbacks may run from View's constructor, before our fields initialize.
        if (isAttachedToWindow) { main.removeCallbacks(frame); removeCallbacks(frame); refresh() }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) { main.removeCallbacks(frame); removeCallbacks(frame); refresh() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val w = width.toFloat(); val backgroundHeight = height.toFloat()
        val now = SystemClock.elapsedRealtime()
        background.draw(canvas, w, backgroundHeight, now, motion)
        // Temporary layout for the reported native strips. The gradient still fills the canvas;
        // only foreground content is laid out inside the unobscured projection area.
        val contentTop = if (fit) backgroundHeight * .18f else 0f
        val contentBottom = if (fit) backgroundHeight * .76f else backgroundHeight
        val foreground = canvas.save()
        canvas.clipRect(0f,contentTop,w,contentBottom)
        canvas.translate(0f,contentTop)
        val h = contentBottom - contentTop
        val stacked = w / h < 1.65f
        val side = if (stacked) minOf(w * .64f, h * .30f) else minOf(h * .84f, w * .27f)
        val left = if (stacked) (w - side) / 2 else w * .075f
        val top = if (stacked) h * .22f else (h - side) / 2
        val rect = artworkRect.apply { set(left, top, left + side, top + side) }
        clip.reset(); clip.addRoundRect(rect, side * .045f, side * .045f, Path.Direction.CW)
        val saved = canvas.save(); canvas.clipPath(clip)
        val artwork = cover?.takeUnless { it.isRecycled }
        val coverProgress = ClusterMusicView.progress(now, coverChangedAt)
        if (artwork != null) {
            fun drawCover(bitmap: Bitmap, alpha: Int) {
                val crop = minOf(bitmap.width, bitmap.height)
                val source = artworkSource.apply { set((bitmap.width - crop) / 2, (bitmap.height - crop) / 2,
                    (bitmap.width + crop) / 2, (bitmap.height + crop) / 2) }
                paint.color = Color.WHITE; paint.alpha = alpha
                canvas.drawBitmap(bitmap, source, rect, paint)
            }
            val old = previousCover?.takeUnless { it.isRecycled }
            if (old != null && coverProgress < 1f) drawCover(old, 255)
            drawCover(artwork, if (old != null) (coverProgress * 255).toInt() else 255)
            paint.alpha = 255
            if (coverProgress >= 1f) previousCover = null
        } else {
            paint.color = Color.argb(35, 255, 255, 255); canvas.drawRect(rect, paint)
            text.textAlign = Paint.Align.CENTER; text.textSize = side * .40f; text.color = Color.WHITE
            canvas.drawText("♫", rect.centerX(), rect.centerY() + side * .14f, text)
        }
        canvas.restoreToCount(saved)
        text.textAlign = Paint.Align.LEFT
        val x = if (stacked) w * .08f else rect.right + w * .055f
        val available = (w * .93f - x).coerceAtLeast(1f)
        val speedSize = minOf(h * .20f, w * .17f)
        text.textSize = speedSize
        val speedLeft = w * .91f - text.measureText("888")
        val metadataWidth = if (showSpeed && !stacked)
            (minOf(w * .93f, speedLeft - w * .025f) - x).coerceAtLeast(1f)
        else available
        val scale = resources.configuration.fontScale.coerceIn(1f, 1.5f)
        var base = (minOf(h * (if (stacked) .070f else .16f), available * .085f) * scale).coerceAtMost(h * .18f)
        val timeSize = minOf(backgroundHeight * .033f * scale, h * .063f)
        val barY = if (stacked) h * (if (showVehicle) .80f else .84f)
            else rect.bottom - timeSize * 2.15f - (if (showVehicle) h * .045f else 0f)
        val titleY = if (stacked) h * .61f else h * .30f
        text.textSize = base; text.color = Color.WHITE
        val title = snapshot.info.title ?: context.getString(R.string.settings_cluster_player_idle)
        val titleWidth = metadataWidth.toInt().coerceAtLeast(1)
        if (cachedTitle == null || title != cachedTitleText || titleWidth != cachedTitleWidth ||
            base != cachedBase || titleY != cachedTitleY || barY != cachedBarY) {
            cachedTitleText = title; cachedTitleWidth = titleWidth; cachedBase = base
            cachedTitleY = titleY; cachedBarY = barY
            titlePaint.textSize = base
            fun build() = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, titleWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false).setLineSpacing(0f, 1.02f).build()
            var layout = build()
            var top = titleY + titlePaint.fontMetrics.ascent
            repeat(10) {
                if (top + layout.height + base * 1.42f > barY - h * .025f) {
                    base *= .90f; titlePaint.textSize = base
                    layout = build(); top = titleY + titlePaint.fontMetrics.ascent
                }
            }
            cachedTitle = layout; fittedBase = base; fittedTop = top
        }
        base = fittedBase
        val titleTop = fittedTop
        val titleLayout = cachedTitle!!
        val titleSave = canvas.save(); canvas.translate(x, titleTop); titleLayout.draw(canvas); canvas.restoreToCount(titleSave)
        val artistY = titleTop + titleLayout.height + base * .68f
        line(canvas, snapshot.info.artist.orEmpty(), x, artistY, metadataWidth, base * .53f, 235)
        line(canvas, snapshot.info.album.orEmpty(), x, artistY + base * .61f, metadataWidth, base * .43f, 205)
        val barHeight = maxOf(3f, backgroundHeight * .008f)
        paint.color = Color.argb(65, 255, 255, 255)
        canvas.drawRoundRect(x, barY, x + available, barY + barHeight, barHeight, barHeight, paint)
        val duration = snapshot.info.durationMillis?.takeIf { it > 0 }
        val elapsed = elapsed(snapshot, now)
        if (duration != null && elapsed != null) {
            paint.color = Color.WHITE
            canvas.drawRoundRect(x, barY, x + available * (elapsed.toFloat() / duration).coerceIn(0f, 1f), barY + barHeight, barHeight, barHeight, paint)
        }
        line(canvas, formatTime(elapsed), x, barY + timeSize * 1.9f, available / 2, timeSize, 225)
        text.textAlign = Paint.Align.RIGHT
        text.textSize = timeSize
        canvas.drawText(formatTime(duration), x + available, barY + timeSize * 1.9f, text)
        if (showSpeed) {
            text.textAlign = Paint.Align.RIGHT; text.color = Color.WHITE
            text.textSize = speedSize
            canvas.drawText(speedKmh?.toString() ?: "—", w * .91f, h * .23f, text)
            text.textSize = minOf(h * .05f, w * .05f)
            canvas.drawText("km/h", w * .91f, h * .30f, text)
        }
        if (showVehicle) {
            val current = vehicle.fresh(now)
            val locale = resources.configuration.locales[0]
            if (numberLocale != locale) { numberLocale = locale; numbers = NumberFormat.getIntegerInstance(locale) }
            val numbers = numbers!!
            val percent = current.batteryPercent?.let { numbers.format(kotlin.math.round(it)) + "%" } ?: "—"
            val range = current.rangeKm?.let { numbers.format(it) + " km" } ?: "—"
            val value = context.getString(R.string.cluster_player_vehicle_readout, current.gear ?: "—", percent, range)
            val footerSize = minOf(backgroundHeight * .034f * scale, w * .033f)
            val footerWidth = (w*.85f).toInt().coerceAtLeast(1)
            if (cachedFooter == null || value != cachedFooterValue || footerWidth != cachedFooterWidth || footerSize != cachedFooterSize) {
                cachedFooterValue = value; cachedFooterWidth = footerWidth; cachedFooterSize = footerSize
                footerPaint.textSize = footerSize
                fun footer() = StaticLayout.Builder.obtain(value,0,value.length,footerPaint,footerWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(2).setIncludePad(false)
                    .setEllipsize(TextUtils.TruncateAt.END).build()
                var layout = footer()
                if (layout.height > h * .095f) { footerPaint.textSize *= h * .095f / layout.height; layout = footer() }
                cachedFooter = layout
            }
            val layout = cachedFooter!!
            val save = canvas.save(); canvas.translate(w*.075f,h*.90f)
            layout.draw(canvas); canvas.restoreToCount(save)
        }
        canvas.restoreToCount(foreground)
        main.removeCallbacks(frame); removeCallbacks(frame)
        if (outputVisible && isShown && windowVisibility == VISIBLE) {
            // Match the map artwork's display-synchronized fade, not the 20 fps drift ticker.
            if (coverProgress < 1f || background.isTransitioning(now)) postOnAnimation(frame)
            else if (snapshot.info.playing) main.postDelayed(frame, if (motion) 50 else 1_000)
        }
    }

    private fun line(canvas: Canvas, value: String, x: Float, y: Float, available: Float, size: Float, alpha: Int) {
        text.textAlign = Paint.Align.LEFT; text.textSize = size; text.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawText(TextUtils.ellipsize(value, text, available, TextUtils.TruncateAt.END).toString(), x, y, text)
    }

    companion object {
        internal fun elapsed(snapshot: ClusterMusicState.Snapshot, now: Long): Long? {
            val start = snapshot.info.elapsedMillis?.coerceAtLeast(0) ?: return null
            val value = start + if (snapshot.info.playing) (now - snapshot.positionAt).coerceAtLeast(0) else 0
            return snapshot.info.durationMillis?.takeIf { it > 0 }?.let { value.coerceAtMost(it) } ?: value
        }
        internal fun formatTime(value: Long?): String = value?.let {
            val seconds = it.coerceAtLeast(0) / 1_000
            "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
        } ?: "—:—"
        internal fun palette(art: Bitmap?): IntArray = ClusterArtworkBackground.palette(art)
    }
}
