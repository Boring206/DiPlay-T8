package com.shilapi.xcertplay

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

// #1224: on Android 9 to 14 the CarPlay window stayed out of the notch strip, which showed black even with
// "Fit around notch and rounded corners" on.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class CarPlayHostCutoutModeTest {
    @Test fun withTheFitOnAFullScreenWindowFillsTheNotchStrip() {
        val activity = activity(fit = true)
        applyFullscreenMode(activity)
        assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES, cutoutMode(activity))
    }

    @Test fun withTheFitOffTheWindowStaysOutOfTheNotch() {
        val activity = activity(fit = false)
        applyFullscreenMode(activity)
        assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT, cutoutMode(activity))
    }

    @Test fun aShownSystemBarKeepsTheWindowOutOfTheNotch() {
        val activity = activity(fit = true)
        set(activity, "hideTopBar", false)
        applyFullscreenMode(activity)
        assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT, cutoutMode(activity))
    }

    @Test fun turningTheFitOffTakesTheWindowOutOfTheNotchAgain() {
        val activity = activity(fit = true)
        applyFullscreenMode(activity)
        AirPlayPersistence.saveFitScreenShape(activity, false)
        applyFullscreenMode(activity)
        assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT, cutoutMode(activity))
    }

    private fun activity(fit: Boolean): CarPlayHostActivity =
        Robolectric.buildActivity(CarPlayHostActivity::class.java).get().also {
            AirPlayPersistence.saveFitScreenShape(it, fit)
        }

    private fun applyFullscreenMode(activity: CarPlayHostActivity) {
        CarPlayHostActivity::class.java.getDeclaredMethod("applyFullscreenMode").apply { isAccessible = true }
            .invoke(activity)
    }

    private fun cutoutMode(activity: CarPlayHostActivity): Int = activity.window.attributes.layoutInDisplayCutoutMode

    private fun set(activity: CarPlayHostActivity, name: String, value: Any) {
        CarPlayHostActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }
}
