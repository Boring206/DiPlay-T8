package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import androidx.core.graphics.ColorUtils
import com.shilapi.xcertplay.airplay.ClusterSpeedometerOverlay

/**
 * Keeps the animation phase independent of the wall clock, so a speed refresh cannot move
 * an otherwise paused or hidden speedometer background.
 */
internal class ClusterArtworkMotionClock {
    private var elapsed = 0L
    private var runningSince = 0L
    private var running = false

    fun time(now: Long, active: Boolean): Long {
        if (!active) {
            pause(now)
            return elapsed
        }
        if (!running) {
            runningSince = now
            running = true
        }
        return elapsed + now - runningSince
    }

    fun pause(now: Long) {
        if (!running) return
        elapsed += now - runningSince
        running = false
    }
}

/**
 * Optional speedometer drawn by DiPlay over the dashboard map.
 *
 * The speed value is supplied independently from the CarPlay video stream so the
 * overlay remains available on clusters where full-screen projection hides the
 * OEM numerical speed readout.
 */
internal class ClusterSpeedometerView(
    context: Context,
    private val observeSpeed: ((Int?) -> Unit) -> (() -> Unit) = { changed ->
        com.shilapi.xcertplay.hud.BydClusterSpeedReader.observe(context, changed)
    },
) : View(context), ClusterOutputVisibility {

    private var speedKmh: Int? = null
    private var stopPlayerSettings: (() -> Unit)? = null
    private var animatedBackground = false
    private var motion = false
    private var playing = false
    private var outputVisible = true
    // The small dashboard card needs a little more chroma than the wide player canvas.
    // It does not alter the Now Playing palette or its appearance.
    private val artworkBackground = ClusterArtworkBackground(saturationMultiplier = 1.25f)
    private val backgroundClip = android.graphics.Path()
    private val frame = Runnable { invalidate() }
    private var stopObservingSettings: (() -> Unit)? = null
    private var stopObservingSpeed: (() -> Unit)? = null
    private var stopObservingArtwork: (() -> Unit)? = null
    private var albumArtwork: android.graphics.Bitmap? = null
    private var albumColor: Int? = null
    private var artworkFrom = 0f
    private var artworkTarget = 0f
    private var artworkChangedAt = 0L
    private val motionClock = ClusterArtworkMotionClock()

    private fun artworkAmount(now: Long): Float = artworkFrom +
        (artworkTarget - artworkFrom) * ClusterMusicView.progress(now, artworkChangedAt)

    private fun updateArtwork(artwork: android.graphics.Bitmap?) {
        val now = android.os.SystemClock.elapsedRealtime()
        val valid = artwork?.isRecycled == false
        val next = if (valid) 1f else 0f
        if (next != artworkTarget) {
            artworkFrom = artworkAmount(now)
            artworkTarget = next
            artworkChangedAt = now
        }
        // Keep the last palette until it has faded into the solid fallback.
        if (valid) artworkBackground.setArtwork(artwork)
        invalidate()
    }
    private var useAlbumColor = false
    private var previousBackgroundColor = Color.rgb(12, 14, 18)
    private var targetBackgroundColor = previousBackgroundColor
    private var backgroundChangedAt = 0L
    private val colorEvaluator = android.animation.ArgbEvaluator()
    private var readingGeneration = 0
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val expireSpeed = Runnable { setSpeedKmh(null) }

    private fun stopSpeed() {
        readingGeneration++
        stopObservingSpeed?.invoke()
        stopObservingSpeed = null
        main.removeCallbacksAndMessages(null)
        removeCallbacks(frame)
        setSpeedKmh(null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        stopPlayerSettings = com.shilapi.xcertplay.airplay.ClusterPlayerSettings.observe(context) { applySettings() }
        stopObservingSettings = AirPlayPersistence.observeClusterSpeedometer(context) { applySettings() }
        applySettings()
    }

    override fun onDetachedFromWindow() {
        stopPlayerSettings?.invoke(); stopPlayerSettings = null
        stopObservingSettings?.invoke()
        stopObservingSettings = null
        stopSpeed()
        stopArtwork()
        super.onDetachedFromWindow()
    }

    override fun setOutputVisible(visible: Boolean) {
        if (!visible) motionClock.pause(android.os.SystemClock.elapsedRealtime())
        outputVisible = visible
        main.removeCallbacks(frame)
        removeCallbacks(frame)
        invalidate()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) motionClock.pause(android.os.SystemClock.elapsedRealtime())
        if (isAttachedToWindow) {
            main.removeCallbacks(frame)
            removeCallbacks(frame)
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) motionClock.pause(android.os.SystemClock.elapsedRealtime())
        if (isAttachedToWindow) {
            main.removeCallbacks(frame)
            removeCallbacks(frame)
        }
    }

    private fun applySettings() {
        animatedBackground = com.shilapi.xcertplay.airplay.ClusterSpeedometerArtworkSettings.enabled(context)
        motion = com.shilapi.xcertplay.airplay.ClusterSpeedometerArtworkSettings.motion(context)
        if (!animatedBackground || !motion) motionClock.pause(android.os.SystemClock.elapsedRealtime())
        main.removeCallbacks(frame)
        removeCallbacks(frame)
        setLayout(AirPlayPersistence.loadClusterSpeedometerX(context),
            AirPlayPersistence.loadClusterSpeedometerY(context), AirPlayPersistence.loadClusterSpeedometerSize(context))
        setOpacity(AirPlayPersistence.loadClusterSpeedometerOpacity(context))
        useAlbumColor = AirPlayPersistence.loadClusterSpeedometerAlbumColor(context)
        val enabled = AirPlayPersistence.loadClusterSpeedometerEnabled(context) &&
            !com.shilapi.xcertplay.airplay.ClusterPlayerSettings.enabled(context)
        if (enabled && (useAlbumColor || animatedBackground)) {
            if (stopObservingArtwork == null) {
                stopObservingArtwork = ClusterMusicState.observe { snapshot ->
                    if (albumArtwork !== snapshot.artwork) {
                        albumArtwork = snapshot.artwork
                        updateArtwork(albumArtwork)
                        albumColor = snapshot.artwork?.takeUnless { it.isRecycled }
                            ?.let { ClusterMusicView.coverFrameColor(it) }
                        updateBackgroundTarget()
                    }
                    if (playing != snapshot.info.playing) {
                        playing = snapshot.info.playing
                        if (!playing) motionClock.pause(android.os.SystemClock.elapsedRealtime())
                        main.removeCallbacks(frame)
                        removeCallbacks(frame)
                        invalidate()
                    }
                }
            }
        } else {
            stopArtwork()
        }
        updateBackgroundTarget()
        invalidate()
        if (!enabled) {
            stopSpeed()
        } else if (stopObservingSpeed == null) {
            val generation = ++readingGeneration
            stopObservingSpeed = observeSpeed { speed ->
                val receivedAt = android.os.SystemClock.elapsedRealtime()
                main.post {
                    if (generation == readingGeneration && isAttachedToWindow) {
                        main.removeCallbacks(expireSpeed)
                        setSpeedKmh(if (android.os.SystemClock.elapsedRealtime() - receivedAt < 2_000) speed else null)
                        main.postDelayed(expireSpeed, 2_000)
                    }
                }
            }
        }
    }

    private var xPercent = ClusterSpeedometerOverlay.DEFAULT_X_PERCENT
    private var yPercent = ClusterSpeedometerOverlay.DEFAULT_Y_PERCENT
    private var sizePercent = ClusterSpeedometerOverlay.DEFAULT_SIZE_PERCENT
    private var opacityPercent = ClusterSpeedometerOverlay.DEFAULT_OPACITY_PERCENT

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val speedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 220, 220, 225)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private val rect = RectF()

    init {
        applyPalette()
        visibility = GONE
    }

    fun setLayout(xPercent: Int, yPercent: Int, sizePercent: Int) {
        this.xPercent = xPercent
        this.yPercent = yPercent
        this.sizePercent = sizePercent
        invalidate()
    }

    fun setOpacity(percent: Int) {
        val next = percent.coerceIn(20, 100)
        if (next == opacityPercent) return

        opacityPercent = next
        applyPalette()
        invalidate()
    }

    fun setSpeedKmh(speed: Int?) {
        val next = speed?.coerceIn(0, 300)
        if (next == speedKmh) return

        speedKmh = next
        visibility = if (next == null) GONE else VISIBLE
        if (next == null) motionClock.pause(android.os.SystemClock.elapsedRealtime())
        if (next != null) bringToFront()
        invalidate()
    }

    private fun stopArtwork() {
        stopObservingArtwork?.invoke()
        stopObservingArtwork = null
        albumArtwork = null
        albumColor = null
        playing = false
        motionClock.pause(android.os.SystemClock.elapsedRealtime())
        main.removeCallbacks(frame)
        removeCallbacks(frame)
        artworkFrom = 0f
        artworkTarget = 0f
        artworkBackground.setArtwork(null)
    }

    private fun backgroundColor(now: Long): Int = colorEvaluator.evaluate(
        ClusterMusicView.progress(now, backgroundChangedAt), previousBackgroundColor, targetBackgroundColor,
    ) as Int

    private fun updateBackgroundTarget() {
        val next = if (useAlbumColor && !animatedBackground) albumColor ?: Color.rgb(12, 14, 18) else Color.rgb(12, 14, 18)
        if (next == targetBackgroundColor) return
        val now = android.os.SystemClock.elapsedRealtime()
        previousBackgroundColor = backgroundColor(now)
        targetBackgroundColor = next
        backgroundChangedAt = now
        invalidate()
    }

    private fun applyPalette() {
        val alpha = (opacityPercent * 255 / 100).coerceIn(51, 255)

        backgroundPaint.color = Color.argb(alpha, 12, 14, 18)
        strokePaint.color =
            Color.argb((alpha * 0.16f).toInt().coerceAtLeast(10), 255, 255, 255)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1),
            MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val speed = speedKmh ?: return
        val card = ClusterSpeedometerOverlay.rect(
            width,
            height,
            xPercent,
            yPercent,
            sizePercent,
        )

        val w = card.width.toFloat()
        val h = card.height.toFloat()
        val centerX = card.left + w / 2f

        rect.set(
            card.left.toFloat(),
            card.top.toFloat(),
            card.left + w,
            card.top + h,
        )

        val now = android.os.SystemClock.elapsedRealtime()
        val color = backgroundColor(now)
        backgroundPaint.color = Color.argb((opacityPercent * 255 / 100).coerceIn(51, 255),
            Color.red(color), Color.green(color), Color.blue(color))
        main.removeCallbacks(frame)
        removeCallbacks(frame)
        val radius = h * 0.22f
        val artworkMode = animatedBackground
        val amount = if (artworkMode) artworkAmount(now) else 0f
        val motionActive = amount > 0f && motion && playing && outputVisible && isShown && windowVisibility == VISIBLE
        val phaseTime = motionClock.time(now, motionActive)
        if (artworkMode) {
            val save = canvas.saveLayerAlpha(rect, (opacityPercent * 255 / 100).coerceIn(51, 255))
            backgroundClip.reset()
            backgroundClip.addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW)
            canvas.clipPath(backgroundClip)
            canvas.translate(rect.left, rect.top)
            canvas.drawColor(Color.rgb(12, 14, 18))
            if (amount > 0f) {
                if (amount < 1f) {
                    val gradient = canvas.saveLayerAlpha(0f, 0f, w, h, (amount * 255).toInt())
                    artworkBackground.drawWideCrop(canvas, w, h, now, motion, phaseTime)
                    canvas.restoreToCount(gradient)
                } else {
                    artworkBackground.drawWideCrop(canvas, w, h, now, motion, phaseTime)
                }
            }
            canvas.restoreToCount(save)
        } else canvas.drawRoundRect(rect, radius, radius, backgroundPaint)
        if (outputVisible && isShown && windowVisibility == VISIBLE) {
            val artworkFade = artworkFrom != artworkTarget &&
                ClusterMusicView.progress(now, artworkChangedAt) < 1f
            if ((artworkMode && (artworkFade || (amount > 0f && artworkBackground.isTransitioning(now)))) ||
                (!artworkMode && ClusterMusicView.progress(now, backgroundChangedAt) < 1f)) postOnAnimation(frame)
            else if (motionActive) main.postDelayed(frame, 50)
        }
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        // Use the displayed (interpolated) album color, not the final fade target.
        // Never fade the text through low-contrast gray. The map behind this translucent
        // view is a separate surface, so contrast is measured against the card RGB.
        if (useAlbumColor && !artworkMode) {
            val luminance = ColorUtils.calculateLuminance(color)
            val blackContrast = (luminance + 0.05) / 0.05
            val whiteContrast = 1.05 / (luminance + 0.05)
            val foreground = if (blackContrast >= whiteContrast) Color.BLACK else Color.WHITE
            speedPaint.color = foreground
            unitPaint.color = foreground
        } else {
            speedPaint.color = Color.WHITE
            unitPaint.color = if (artworkMode) Color.rgb(220, 220, 225) else Color.argb(210, 220, 220, 225)
        }

        speedPaint.textSize = h * 0.48f
        unitPaint.textSize = h * 0.16f

        // Center the actual glyph bounds of the number and unit as one vertical block.
        val speedBounds = android.graphics.Rect()
        val unitBounds = android.graphics.Rect()
        val text = speed.toString()
        speedPaint.getTextBounds(text, 0, text.length, speedBounds)
        unitPaint.getTextBounds("km/h", 0, 4, unitBounds)
        val gap = h * 0.06f
        val blockHeight = speedBounds.height() + gap + unitBounds.height()
        val blockTop = card.top + (h - blockHeight) / 2f
        val speedY = blockTop - speedBounds.top
        val unitY = blockTop + speedBounds.height() + gap - unitBounds.top
        canvas.drawText(text, centerX, speedY, speedPaint)
        canvas.drawText("km/h", centerX, unitY, unitPaint)
    }
}
