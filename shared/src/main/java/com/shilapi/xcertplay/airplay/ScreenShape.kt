package com.shilapi.xcertplay.airplay

import kotlin.math.ceil
import kotlin.math.sqrt

/** One rounded corner of the screen in the activity's pixels: the centre of its arc and its radius. */
data class ScreenCorner(
    val position: Position,
    val centerX: Int,
    val centerY: Int,
    val radius: Int,
) {
    enum class Position { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT }
}

/**
 * The parts of a phone or tablet screen that CarPlay's controls should keep clear of, in the activity's
 * pixels: the camera notch or hole ([cutouts], clipped to the activity) and the rounded [corners].
 */
data class ScreenShape(
    val cutouts: List<SafeAreaRect> = emptyList(),
    val corners: List<ScreenCorner> = emptyList(),
)

/**
 * A safe area clear of a screen's notch and rounded corners. CarPlay takes one rectangle, so each notch
 * pushes in whichever edge loses the least area, and each corner moves the two edges beside it just far
 * enough for the safe area's corner to sit on the arc. CarPlay keeps its buttons and dock inside; maps
 * and wallpaper still fill the screen while "Draw outside safe area" is on.
 */
object ScreenShapeSafeArea {
    /** The safe area of a [width] x [height] activity clear of [shape], or null when nothing is in the way. */
    fun fit(shape: ScreenShape, width: Int, height: Int): SafeAreaRect? {
        require(width > 0 && height > 0) { "Activity dimensions must be positive" }
        var left = 0
        var top = 0
        var right = width
        var bottom = height
        for (cutout in shape.cutouts) {
            if (cutout.right <= left || cutout.left >= right || cutout.bottom <= top || cutout.top >= bottom) continue
            val spanX = (right - left).toLong()
            val spanY = (bottom - top).toLong()
            val losses = listOf(
                (cutout.right - left) * spanY,
                (cutout.bottom - top) * spanX,
                (right - cutout.left) * spanY,
                (bottom - cutout.top) * spanX,
            )
            when (losses.indices.minByOrNull { losses[it] }) {
                0 -> left = cutout.right
                1 -> top = cutout.bottom
                2 -> right = cutout.left
                else -> bottom = cutout.top
            }
        }
        for (corner in shape.corners) {
            if (corner.radius <= 0) continue
            val atLeft = corner.position == ScreenCorner.Position.TOP_LEFT || corner.position == ScreenCorner.Position.BOTTOM_LEFT
            val atTop = corner.position == ScreenCorner.Position.TOP_LEFT || corner.position == ScreenCorner.Position.TOP_RIGHT
            // Measured in from the corner's own two edges, so every corner is solved as a top-left one.
            val (x, y) = clearCorner(
                insetX = if (atLeft) left else width - right,
                insetY = if (atTop) top else height - bottom,
                centerX = if (atLeft) corner.centerX else width - corner.centerX,
                centerY = if (atTop) corner.centerY else height - corner.centerY,
                radius = corner.radius,
                spanX = (right - left).toLong(),
                spanY = (bottom - top).toLong(),
            )
            if (atLeft) left = x else right = width - x
            if (atTop) top = y else bottom = height - y
        }
        if (right <= left || bottom <= top) return null
        if (left == 0 && top == 0 && right == width && bottom == height) return null
        return SafeAreaRect(left, top, right, bottom)
    }

    /**
     * Insets from a corner's two edges that put the safe area's corner on or inside the arc. One edge moves
     * to at least the arc's 45° point and the other only as far as the arc there; the cheaper order wins.
     */
    private fun clearCorner(
        insetX: Int,
        insetY: Int,
        centerX: Int,
        centerY: Int,
        radius: Int,
        spanX: Long,
        spanY: Long,
    ): Pair<Int, Int> {
        val dx = (centerX - insetX).toLong()
        val dy = (centerY - insetY).toLong()
        if (dx <= 0 || dy <= 0 || dx * dx + dy * dy <= radius.toLong() * radius) return insetX to insetY
        val diagonal = radius / sqrt(2.0)
        val xFirst = maxOf(insetX, ceil(centerX - diagonal).toInt()).let { x ->
            x to maxOf(insetY, ceil(centerY - along(centerX - x, radius)).toInt())
        }
        val yFirst = maxOf(insetY, ceil(centerY - diagonal).toInt()).let { y ->
            maxOf(insetX, ceil(centerX - along(centerY - y, radius)).toInt()) to y
        }
        fun cost(insets: Pair<Int, Int>) = (insets.first - insetX) * spanY + (insets.second - insetY) * spanX
        return if (cost(yFirst) < cost(xFirst)) yFirst else xFirst
    }

    /** How far the arc reaches along one axis at [offset] from its centre on the other. */
    private fun along(offset: Int, radius: Int): Double =
        sqrt((radius.toDouble() * radius - offset.toDouble() * offset).coerceAtLeast(0.0))
}
