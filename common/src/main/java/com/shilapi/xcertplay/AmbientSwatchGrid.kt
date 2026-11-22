package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.media.AmbientAlbumPalette
import kotlin.math.roundToInt

/**
 * The car's 31 ambient colors as tappable swatches, drawn with the stock settings' preview colors.
 * [single] picks one color; otherwise any number may be selected, but never none.
 */
internal class AmbientSwatchGrid(
    context: Context,
    private val accent: Int,
    initial: Set<Int>,
    var single: Boolean,
    private val onChanged: (Set<Int>) -> Unit,
    private val onLastDeselected: () -> Unit,
) : ViewGroup(context) {
    var selection: Set<Int> = initial
        private set
    private val swatches = HashMap<Int, FrameLayout>()
    private val density = context.resources.displayMetrics.density
    private var columns = 1
    private var rowHeight = 0

    init {
        for (index in 1..COLORS) {
            val swatch = swatchView(context, index)
            swatches[index] = swatch
            addView(swatch)
        }
        refresh()
    }

    // Use the whole row width, with equal space around each fixed-size swatch.
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val minimumCellWidth = dp(SWATCH_DP) + dp(MARGIN_DP) * 2
        val width = resolveSize(maxOf(MeasureSpec.getSize(widthSpec),
            minimumCellWidth + paddingLeft + paddingRight), widthSpec)
        val available = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        columns = (available / minimumCellWidth).coerceAtLeast(1)
        val swatchSize = minOf(dp(SWATCH_DP),
            (available / columns - dp(MARGIN_DP) * 2).coerceAtLeast(0))
        val childSpec = MeasureSpec.makeMeasureSpec(swatchSize, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) {
            getChildAt(i).measure(childSpec, childSpec)
        }
        rowHeight = swatchSize + dp(MARGIN_DP) * 2
        val rows = (childCount + columns - 1) / columns
        setMeasuredDimension(width, resolveSize(paddingTop + rows * rowHeight + paddingBottom, heightSpec))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val available = (right - left - paddingLeft - paddingRight).coerceAtLeast(0)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val logicalColumn = i % columns
            val column = if (layoutDirection == LAYOUT_DIRECTION_RTL) columns - 1 - logicalColumn else logicalColumn
            val cellLeft = paddingLeft + available * column / columns
            val cellRight = paddingLeft + available * (column + 1) / columns
            val x = cellLeft + (cellRight - cellLeft - child.measuredWidth) / 2
            val y = paddingTop + i / columns * rowHeight + dp(MARGIN_DP)
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams = MarginLayoutParams(-2, -2)
    override fun generateLayoutParams(attrs: android.util.AttributeSet?): LayoutParams = MarginLayoutParams(context, attrs)
    override fun generateLayoutParams(p: LayoutParams): LayoutParams = MarginLayoutParams(p)
    override fun checkLayoutParams(p: LayoutParams): Boolean = p is MarginLayoutParams

    fun setSelection(colors: Set<Int>) {
        selection = colors
        refresh()
    }

    fun setInteractive(enabled: Boolean) {
        isEnabled = enabled
        swatches.values.forEach { it.isEnabled = enabled }
        alpha = if (enabled) 1f else 0.4f
    }

    private fun swatchView(context: Context, index: Int): FrameLayout {
        val check = TextView(context).apply {
            text = "✓"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (isLight(AmbientAlbumPalette.previewRgb(index))) Color.BLACK else Color.WHITE)
            tag = CHECK
        }
        return FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            addView(check, FrameLayout.LayoutParams(-1, -1))
            setOnClickListener { toggle(index) }
        }
    }

    private fun toggle(index: Int) {
        if (!isEnabled) return
        val next = when {
            single -> setOf(index)
            index in selection && selection.size == 1 -> { onLastDeselected(); return }
            index in selection -> selection - index
            else -> selection + index
        }
        if (next == selection) return
        selection = next
        refresh()
        onChanged(next)
    }

    private fun refresh() {
        for ((index, swatch) in swatches) {
            val selected = index in selection
            swatch.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(AmbientAlbumPalette.previewRgb(index))
                if (selected) setStroke(dp(3), accent) else setStroke(dp(1), UNSELECTED_RING)
            }
            swatch.findViewWithTag<View>(CHECK).visibility = if (selected) VISIBLE else INVISIBLE
            swatch.contentDescription = context.getString(
                if (selected) R.string.settings_ambient_swatch_selected else R.string.settings_ambient_swatch, index,
            )
        }
    }

    private fun isLight(rgb: Int): Boolean =
        (0.299 * Color.red(rgb) + 0.587 * Color.green(rgb) + 0.114 * Color.blue(rgb)) > 150

    private fun dp(value: Int) = (value * density).roundToInt()

    internal companion object {
        const val COLORS = 31
        private const val SWATCH_DP = 44
        private const val MARGIN_DP = 5
        private const val CHECK = "check"
        private const val UNSELECTED_RING = 0x55808080
    }
}
