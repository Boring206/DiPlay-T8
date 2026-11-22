package com.shilapi.xcertplay

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import com.shilapi.xcertplay.host.R
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsButtonSizingTest {
    @After fun resetFontScale() {
        RuntimeEnvironment.setFontScale(1f)
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("diplay", 0).edit().clear().commit()
        context.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
    }

    @Test @Config(qualifiers = "uk-w320dp-h960dp-xhdpi")
    fun ukrainianSettingsButtonsFitLongLabelsAtLargeFontScale() = verifySettingsButtonsFit()

    @Test @Config(qualifiers = "ar-ldrtl-w320dp-h960dp-xhdpi")
    fun arabicSettingsButtonsFitLongLabelsAtLargeFontScale() = verifySettingsButtonsFit()

    @Test @Config(qualifiers = "zh-rTW-w1280dp-h960dp-xxxhdpi")
    fun expandedSettingsButtonsFitAtHighDensityAndLargeFontScale() = verifySettingsButtonsFit()

    @Test @Config(qualifiers = "uk-w320dp-h960dp-xhdpi")
    fun compactOverviewButtonsGrowAndKeepTheirColors() = verifyOverviewButtons()

    @Test @Config(qualifiers = "en-w1280dp-h960dp-land-xxxhdpi")
    fun expandedOverviewButtonsShareTheMinimumHeightAndKeepTheirColors() = verifyOverviewButtons()

    @Test @Config(qualifiers = "ar-ldrtl-w320dp-h960dp-xhdpi")
    fun rtlOverviewButtonsGrowAndKeepTheirColors() = verifyOverviewButtons()

    @Test @Config(qualifiers = "uk-w320dp-h960dp-xhdpi")
    fun newSettingsButtonsReceiveThePolicyWithoutTheirOwnHeightInstructions() {
        RuntimeEnvironment.setFontScale(1.5f)
        val screen = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        screen.setTheme(android.R.style.Theme_Material_NoActionBar)
        ReflectionHelpers.setField(screen, "page", "settings")
        val content = LinearLayout(screen).apply { orientation = LinearLayout.VERTICAL }
        val control = Button(screen).apply {
            text = screen.getString(R.string.vehicle_data_mode_default)
            textSize = 18f
        }
        val horizontalControl = Button(screen).apply { text = "Action" }
        val density = screen.resources.displayMetrics.density
        val horizontalHeight = (52 * density).roundToInt()
        val build: (LinearLayout) -> Unit = { card ->
            // No Settings-specific button helper or size is needed for a new vertical control.
            card.addView(control)
            card.addView(LinearLayout(screen).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(horizontalControl, LinearLayout.LayoutParams(0, horizontalHeight, 1f))
            })
        }
        try {
            DiPlayActivity::class.java.getDeclaredMethod("section", LinearLayout::class.java,
                String::class.java, Integer::class.java, Function1::class.java)
                .apply { isAccessible = true }.invoke(screen, content, "Settings sizing", null, build)
            val width = (320 * density).roundToInt()
            content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            content.layout(0, 0, width, content.measuredHeight)
            assertTrue(control.height >= (60 * density).roundToInt())
            assertTrue(control.layout.height <= control.height - control.compoundPaddingTop - control.compoundPaddingBottom)
            assertEquals(horizontalHeight, horizontalControl.height)
        } finally { screen.finish() }
    }

    private fun verifyOverviewButtons() {
        RuntimeEnvironment.setFontScale(1.5f)
        val screen = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        screen.setTheme(android.R.style.Theme_Material_NoActionBar)
        ReflectionHelpers.setField(screen, "page", "settings")
        AirPlayPersistence.saveWirelessEnabled(screen, true)
        try {
            for (appearance in listOf(AppAppearance.DARK, AppAppearance.LIGHT)) {
                AirPlayPersistence.saveAppAppearance(screen, appearance)
                ReflectionHelpers.callInstanceMethod<Boolean>(screen, "refreshAppearance")
                verifyOverviewButtonsInPalette(screen, DiPlayPalette.of(appearance == AppAppearance.DARK))
            }
        } finally { screen.finish() }
    }

    private fun verifyOverviewButtonsInPalette(screen: DiPlayActivity, palette: DiPlayPalette) {
        for (setupError in listOf(null, screen.getString(R.string.setup_needs_attention))) {
            ReflectionHelpers.setField(screen, "setupError", setupError)
            val content = LinearLayout(screen).apply { orientation = LinearLayout.VERTICAL }
            ReflectionHelpers.callInstanceMethod<Unit>(screen, "settingsOverview",
                ReflectionHelpers.ClassParameter(LinearLayout::class.java, content))
            val metrics = screen.resources.displayMetrics
            val width = (screen.resources.configuration.screenWidthDp * metrics.density).roundToInt()
            content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            content.layout(0, 0, width, content.measuredHeight)
            val buttons = descendants(content).filterIsInstance<Button>().toList()
            val actionTitle = screen.getString(if (setupError == null)
                R.string.settings_choose_iphone_action else R.string.open_connection_setup)
            val action = buttons.single { it.text.toString() == actionTitle }
            val guide = buttons.single { it.text.toString() == screen.getString(R.string.setup_guide) }
            for (button in listOf(action, guide)) {
                assertTrue(button.text.toString(), button.height >= (60 * metrics.density).roundToInt())
                assertTrue(button.text.toString(), button.layout.height <=
                    button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
            }
            assertEquals(palette.onAccent, action.currentTextColor)
            assertEquals(palette.primaryText, guide.currentTextColor)
            assertEquals(palette.accent, buttonFill(action))
            assertEquals(palette.button, buttonFill(guide))
            assertEquals((12 * metrics.density).roundToInt(), (action.layoutParams as LinearLayout.LayoutParams).topMargin)
            assertEquals((6 * metrics.density).roundToInt(), (guide.layoutParams as LinearLayout.LayoutParams).bottomMargin)
        }
    }

    private fun buttonFill(button: Button): Int {
        val background = button.background as RippleDrawable
        val shape = background.getDrawable(0) as GradientDrawable
        return shape.color!!.defaultColor
    }

    private fun verifySettingsButtonsFit() {
        RuntimeEnvironment.setFontScale(1.5f)
        val screen = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        screen.setTheme(android.R.style.Theme_Material_NoActionBar)
        // section() only runs the shared normalization on the real Settings page.
        ReflectionHelpers.setField(screen, "page", "settings")
        val content = LinearLayout(screen).apply { orientation = LinearLayout.VERTICAL }
        try {
            DiPlayActivity::class.java.getDeclaredMethod("renderSections", LinearLayout::class.java, Set::class.java)
                .apply { isAccessible = true }.invoke(screen, content, SettingsSection.entries.toSet())
            val metrics = screen.resources.displayMetrics
            val width = (screen.resources.configuration.screenWidthDp * metrics.density).roundToInt()
            content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            content.layout(0, 0, width, content.measuredHeight)
            val buttons = descendants(content).filterIsInstance<Button>()
                .filter { it !is android.widget.CompoundButton }
                .filter { it.width > 0 && it.height > 0 }.toList()
            assertTrue("Must check settings controls", buttons.size > 10)
            val minimumPadding = (8 * metrics.density).roundToInt()
            for (button in buttons) {
                val message = "Label must fit: ${button.text}"
                assertTrue(message, button.height >= (60 * metrics.density).roundToInt())
                assertTrue(message, button.layout.height <= button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
                assertTrue("Wrapped label needs vertical padding: ${button.text}", button.lineCount <= 1 ||
                    (button.paddingTop >= minimumPadding && button.paddingBottom >= minimumPadding))
            }
        } finally { screen.finish() }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
