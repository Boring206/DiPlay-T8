package com.shilapi.xcertplay.airplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenShapeSafeAreaTest {
    private fun corners(width: Int, height: Int, radius: Int) = listOf(
        ScreenCorner(ScreenCorner.Position.TOP_LEFT, radius, radius, radius),
        ScreenCorner(ScreenCorner.Position.TOP_RIGHT, width - radius, radius, radius),
        ScreenCorner(ScreenCorner.Position.BOTTOM_RIGHT, width - radius, height - radius, radius),
        ScreenCorner(ScreenCorner.Position.BOTTOM_LEFT, radius, height - radius, radius),
    )

    /** Every corner of [rect] lies inside its screen corner's arc, or past the arc's centre on one axis. */
    private fun assertClearOfCorners(rect: SafeAreaRect, corners: List<ScreenCorner>) {
        for (corner in corners) {
            val x = if (corner.position == ScreenCorner.Position.TOP_LEFT ||
                corner.position == ScreenCorner.Position.BOTTOM_LEFT) rect.left else rect.right
            val y = if (corner.position == ScreenCorner.Position.TOP_LEFT ||
                corner.position == ScreenCorner.Position.TOP_RIGHT) rect.top else rect.bottom
            val dx = (corner.centerX - x).toLong()
            val dy = (corner.centerY - y).toLong()
            val pastCentre = when (corner.position) {
                ScreenCorner.Position.TOP_LEFT -> dx <= 0 || dy <= 0
                ScreenCorner.Position.TOP_RIGHT -> dx >= 0 || dy <= 0
                ScreenCorner.Position.BOTTOM_RIGHT -> dx >= 0 || dy >= 0
                ScreenCorner.Position.BOTTOM_LEFT -> dx <= 0 || dy >= 0
            }
            assertTrue("$rect clips ${corner.position}",
                pastCentre || dx * dx + dy * dy <= corner.radius.toLong() * corner.radius)
        }
    }

    @Test
    fun aPlainScreenNeedsNoSafeArea() {
        assertNull(ScreenShapeSafeArea.fit(ScreenShape(), 1920, 720))
        assertNull(ScreenShapeSafeArea.fit(ScreenShape(corners = corners(1920, 720, 0)), 1920, 720))
    }

    @Test
    fun landscapeNotchPushesItsEdgeAndTheFarCornersMoveTheirEdgesSlightly() {
        // A Pocophone F1 held landscape: the notch on the left, corners of radius 60 px.
        val shape = ScreenShape(
            cutouts = listOf(SafeAreaRect(0, 300, 89, 780)),
            corners = corners(2246, 1080, 60),
        )

        val rect = ScreenShapeSafeArea.fit(shape, 2246, 1080)!!

        // Past the notch the left corners are already clear; the right ones need 18 px from each edge.
        assertEquals(SafeAreaRect(89, 18, 2228, 1062), rect)
        assertClearOfCorners(rect, shape.corners)
    }

    @Test
    fun notchOnTheRightPushesTheRightEdge() {
        val shape = ScreenShape(cutouts = listOf(SafeAreaRect(2157, 300, 2246, 780)))

        assertEquals(SafeAreaRect(0, 0, 2157, 1080), ScreenShapeSafeArea.fit(shape, 2246, 1080))
    }

    @Test
    fun punchHoleInPortraitPushesTheTopEdge() {
        val shape = ScreenShape(cutouts = listOf(SafeAreaRect(500, 20, 580, 100)))

        assertEquals(SafeAreaRect(0, 100, 1080, 2340), ScreenShapeSafeArea.fit(shape, 1080, 2340))
    }

    @Test
    fun roundedCornersAloneMoveEveryEdgeToTheArc() {
        val shape = ScreenShape(corners = corners(1000, 500, 100))

        val rect = ScreenShapeSafeArea.fit(shape, 1000, 500)!!

        assertEquals(SafeAreaRect(30, 29, 970, 471), rect)
        assertClearOfCorners(rect, shape.corners)
    }

    @Test
    fun cornerOutsideTheActivityIsIgnored() {
        // The window is inset from the screen corner, so its arc's centre lies beyond the activity.
        val shape = ScreenShape(corners = listOf(ScreenCorner(ScreenCorner.Position.TOP_LEFT, -10, -10, 60)))

        assertNull(ScreenShapeSafeArea.fit(shape, 1920, 1080))
    }

    @Test
    fun largeCornersStayClearOnASmallActivity() {
        val shape = ScreenShape(corners = corners(400, 300, 140))

        val rect = ScreenShapeSafeArea.fit(shape, 400, 300)!!

        assertClearOfCorners(rect, shape.corners)
        assertTrue(rect.width > 0 && rect.height > 0)
    }
}
