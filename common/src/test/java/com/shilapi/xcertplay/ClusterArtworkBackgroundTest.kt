package com.shilapi.xcertplay

import android.graphics.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClusterArtworkBackgroundTest {
    private fun art(color: Int) = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    private fun frame(background: ClusterArtworkBackground, time: Long, crop: Boolean = false): Bitmap =
        Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888).also {
            if (crop) background.drawWideCrop(Canvas(it), 240f, 240f, time)
            else background.draw(Canvas(it), 240f, 240f, time)
        }

    @Test fun tinyAccentDoesNotBecomeAWholeLayerAndNeutralSleevesStayNeutral() {
        for (base in listOf(Color.rgb(120, 150, 180), Color.LTGRAY)) {
            val cover = art(base)
            Canvas(cover).drawRect(0f, 0f, 6f, 120f, Paint().apply { color = Color.MAGENTA })
            for (color in ClusterArtworkBackground.palette(cover)) {
                val hsv = FloatArray(3); Color.colorToHSV(color, hsv)
                if (base == Color.LTGRAY) assertTrue(hsv[1] < .01f)
                else assertTrue(hsv[0] in 200f..220f)
            }
            cover.recycle()
        }
        val black = art(Color.BLACK)
        assertTrue(ClusterArtworkBackground.palette(black).all { Color.red(it) < 20 && Color.red(it) == Color.blue(it) })
        black.recycle()
    }

    @Test fun whiteAndYellowBackgroundsKeepEvenDimMetadataAboveMinimumContrast() {
        for (color in listOf(Color.WHITE,Color.YELLOW)) {
            val cover=art(color)
            val background=ClusterArtworkBackground().apply { setArtwork(cover,0) }
            val image=Bitmap.createBitmap(320,120,Bitmap.Config.ARGB_8888)
            for (time in 0L..45000L step 1500L) {
                background.draw(Canvas(image),320f,120f,time,true)
                for (y in 0 until 120 step 4) for (x in 0 until 320 step 4) {
                    val underText=image.getPixel(x,y)
                    val text=androidx.core.graphics.ColorUtils.compositeColors(Color.argb(205,255,255,255),underText)
                    assertTrue("Contrast at $x,$y time=$time",androidx.core.graphics.ColorUtils.calculateContrast(text,underText)>=4.5)
                }
            }
            image.recycle();cover.recycle()
        }
    }

    @Test fun busySleevesAlwaysHaveADominantFallback() {
        val cover = art(Color.BLACK)
        for (y in 0 until 120) for (x in 0 until 120)
            cover.setPixel(x,y,Color.HSVToColor(floatArrayOf((x * 3).toFloat(), .8f, .8f)))
        assertEquals(3, ClusterArtworkBackground.palette(cover).size)
        cover.recycle()
    }

    @Test fun crossfadeStartsAtOldFrameAndInterruptedChangesRemainContinuous() {
        val red = art(Color.RED); val blue = art(Color.BLUE); val green = art(Color.GREEN)
        val background = ClusterArtworkBackground()
        background.setArtwork(red, 1000)
        val old = frame(background, 2000)
        background.setArtwork(blue, 2000)
        assertTrue(old.sameAs(frame(background, 2000)))
        assertTrue(background.isTransitioning(2000))
        val middle = frame(background, 2200)
        assertFalse(old.sameAs(middle))
        background.setArtwork(green, 2200)
        assertTrue(middle.sameAs(frame(background, 2200)))
        assertFalse(background.isTransitioning(2600))
        val settled = frame(background, 2600)
        val expected = ClusterArtworkBackground().apply { setArtwork(green, 2600) }
        assertTrue(settled.sameAs(frame(expected, 2600)))
        old.recycle(); middle.recycle(); settled.recycle(); red.recycle(); blue.recycle(); green.recycle()
    }

    @Test fun speedometerUsesCenterOfWideComposition() {
        val cover = art(Color.rgb(190, 150, 45))
        val background = ClusterArtworkBackground().apply { setArtwork(cover, 0) }
        val wide = Bitmap.createBitmap(640,240,Bitmap.Config.ARGB_8888)
        background.draw(Canvas(wide),640f,240f,5000)
        val square = frame(background,5000,true)
        for (y in 0 until 240 step 8) for (x in 0 until 240 step 8)
            assertEquals(wide.getPixel(x+200,y),square.getPixel(x,y))
        cover.recycle(); wide.recycle(); square.recycle()
    }
}
