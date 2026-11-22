package com.shilapi.xcertplay

import android.content.Context
import android.os.Build
import android.view.Display
import android.view.RoundedCorner
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import com.shilapi.xcertplay.airplay.SafeAreaRect
import com.shilapi.xcertplay.airplay.ScreenCorner
import com.shilapi.xcertplay.airplay.ScreenShape

/**
 * The notch (Android 9+) and rounded corners (Android 12+) a phone or tablet used as a head unit reports.
 * Older Android versions report neither, so they get no fitted safe area.
 */
internal object AndroidScreenShape {
    private val CORNERS = listOf(
        RoundedCorner.POSITION_TOP_LEFT to ScreenCorner.Position.TOP_LEFT,
        RoundedCorner.POSITION_TOP_RIGHT to ScreenCorner.Position.TOP_RIGHT,
        RoundedCorner.POSITION_BOTTOM_RIGHT to ScreenCorner.Position.BOTTOM_RIGHT,
        RoundedCorner.POSITION_BOTTOM_LEFT to ScreenCorner.Position.BOTTOM_LEFT,
    )

    /** The notch and corners over [view], in its pixels, or null before it is laid out in a window. */
    fun over(view: View): ScreenShape? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val insets = view.rootWindowInsets ?: return null
        if (view.width <= 0 || view.height <= 0) return null
        // Insets are in the window's pixels; the video view can sit below a shown status bar.
        val origin = IntArray(2).also { view.getLocationInWindow(it) }
        val cutouts = insets.displayCutout?.boundingRects.orEmpty().mapNotNull { rect ->
            val left = (rect.left - origin[0]).coerceIn(0, view.width)
            val top = (rect.top - origin[1]).coerceIn(0, view.height)
            val right = (rect.right - origin[0]).coerceIn(0, view.width)
            val bottom = (rect.bottom - origin[1]).coerceIn(0, view.height)
            if (right > left && bottom > top) SafeAreaRect(left, top, right, bottom) else null
        }
        val corners = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) roundedCorners(insets, origin) else emptyList()
        return ScreenShape(cutouts, corners)
    }

    private fun roundedCorners(insets: WindowInsets, origin: IntArray): List<ScreenCorner> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()
        return CORNERS.mapNotNull { (position, ours) ->
            insets.getRoundedCorner(position)?.takeIf { it.radius > 0 }?.let {
                ScreenCorner(ours, it.center.x - origin[0], it.center.y - origin[1], it.radius)
            }
        }
    }

    /** True when the screen has a notch or rounded corners, so fitting CarPlay around them can help. */
    fun present(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        @Suppress("DEPRECATION")
        val display = (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
            ?: return false
        if (display.cutout?.boundingRects?.isNotEmpty() == true) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasRoundedCorner(display)
    }

    private fun hasRoundedCorner(display: Display): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return CORNERS.any { (position, _) -> (display.getRoundedCorner(position)?.radius ?: 0) > 0 }
    }
}
