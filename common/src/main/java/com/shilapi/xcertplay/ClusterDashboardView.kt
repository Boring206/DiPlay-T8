package com.shilapi.xcertplay

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.shilapi.xcertplay.airplay.ClusterPlayerSettings

/** Switches entire pages. SurfaceView releases its surface when GONE; TextureView retains it. The host reattaches on return. */
internal class ClusterDashboardView(context: Context, private val map: View,
    playerParams: LayoutParams = LayoutParams(-1, -1), private val onPlayerShown: () -> Unit = {},
    private val legacyBounds: Boolean = false,
) : FrameLayout(context) {
    private val player = ClusterPlayerView(context)
    private var outputVisible = true
    private var stopSettings: (() -> Unit)? = null
    init {
        addView(map, LayoutParams(-1, -1))
        addView(player, playerParams)
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (legacyBounds) LegacyClusterLayout.plan(w, h)?.let { plan ->
            player.layoutParams = LayoutParams(plan.width, plan.height).apply {
                leftMargin = plan.left; topMargin = plan.top
            }
        }
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        stopSettings = ClusterPlayerSettings.observe(context) { refresh() }
        refresh()
    }
    fun setOutputVisible(visible: Boolean) { outputVisible = visible; player.setOutputVisible(visible); refresh() }
    private fun refresh() {
        val selected = ClusterPlayerSettings.enabled(context)
        visibility = if (selected && !outputVisible) INVISIBLE else VISIBLE
        map.visibility = if (selected) GONE else VISIBLE
        player.visibility = if (selected) VISIBLE else GONE
        if (selected) post { if (isAttachedToWindow && player.isShown) onPlayerShown() }
    }
    override fun onDetachedFromWindow() {
        stopSettings?.invoke(); stopSettings = null
        super.onDetachedFromWindow()
    }
}
