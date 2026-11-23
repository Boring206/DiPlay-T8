package com.shilapi.xcertplay

import android.app.AlertDialog
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.hud.BydOutputSettings
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VehicleDataModeDialogTest {
    @Test fun nativeChoicePreparationPreservesCheckedItemAndPendingSelection() {
        val screen = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        screen.setTheme(android.R.style.Theme_Material_NoActionBar)
        var pending = 1
        var saved = -1
        val dialog = screen.appDialogBuilder()
            .setSingleChoiceItems(arrayOf("First", "Second"), pending) { _, index -> pending = index }
            .setPositiveButton("Save") { _, _ -> saved = pending }
            .setNegativeButton("Cancel", null).create()
        try {
            dialog.prepareSingleChoiceRows()
            dialog.show()
            assertTrue(dialog.listView.isItemChecked(1))
            assertEquals(1, dialog.listView.checkedItemPosition)
            shadowOf(dialog).clickOnItem(0)
            assertEquals(0, pending)
            assertEquals(-1, saved)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, saved)
        } finally { dialog.dismiss(); screen.finish() }
    }

    @After fun reset() {
        RuntimeEnvironment.setFontScale(1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("diplay_byd_outputs", 0).edit().clear().commit()
    }

    @Test fun nativeChoicesFitLongTextAtLargeFontScale() {
        for (locale in listOf("en", "zh-rTW", "uk", "ar-ldrtl")) {
            for (widthDp in listOf(320, 1280)) {
                RuntimeEnvironment.setQualifiers("$locale-w${widthDp}dp-h960dp-${if (widthDp > 960) "land" else "port"}-xhdpi")
                RuntimeEnvironment.setFontScale(1.5f)
                val screen = Robolectric.buildActivity(DiPlayActivity::class.java).get()
                screen.applicationInfo.flags = screen.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
                screen.setTheme(android.R.style.Theme_Material_NoActionBar)
                BydOutputSettings.setLegacyVehicleProbe(screen, false)
                val card = LinearLayout(screen)
                ReflectionHelpers.callInstanceMethod<Unit>(screen, "advancedVehicleData",
                    ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, card))
                val control = (0 until card.childCount).map(card::getChildAt).filterIsInstance<TextView>()
                    .single { it.text.startsWith(screen.getString(R.string.vehicle_data_mode) + " · ") }
                control.performClick()
                val dialog = ShadowAlertDialog.getLatestAlertDialog()
                try {
                    val list = requireNotNull(dialog.listView)
                    assertEquals(ListView.CHOICE_MODE_SINGLE, list.choiceMode)
                    val decor = dialog.window!!.decorView
                    // This isolated fixture has no activity window hierarchy to supply direction.
                    decor.layoutDirection = screen.resources.configuration.layoutDirection
                    val density = screen.resources.displayMetrics.density
                    val dialogWidth = (minOf(widthDp, 560) * density).roundToInt()
                    val maxHeight = (960 * density).roundToInt()
                    fun layout() {
                        decor.measure(View.MeasureSpec.makeMeasureSpec(dialogWidth, View.MeasureSpec.AT_MOST),
                            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST))
                        decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
                    }
                    layout()
                    assertEquals("Native choices must follow the locale direction",
                        screen.resources.configuration.layoutDirection, list.layoutDirection)
                    val initialHeight = decor.height
                    shadowOf(Looper.getMainLooper()).idle()
                    layout()
                    assertEquals("Dialog dimensions must be ready on first layout", initialHeight, decor.height)
                    assertEquals(2, list.childCount)
                    assertTrue("The first option must be fully visible", list.getChildAt(0).top >= list.paddingTop)
                    assertTrue("Both options fit this window without scrolling",
                        list.getChildAt(1).bottom <= list.height - list.paddingBottom)
                    for (index in 0 until list.childCount) {
                        val row = list.getChildAt(index) as TextView
                        val textLayout = requireNotNull(row.layout)
                        for (line in 0 until textLayout.lineCount) {
                            assertEquals("Option text must not be ellipsized", 0, textLayout.getEllipsisCount(line))
                            assertTrue("Option text must fit horizontally", textLayout.getLineWidth(line) <= textLayout.width + 1f)
                        }
                        assertEquals("The full option must be laid out", row.text.length,
                            textLayout.getLineEnd(textLayout.lineCount - 1))
                        assertTrue("Option text must fit vertically",
                            textLayout.height <= row.height - row.compoundPaddingTop - row.compoundPaddingBottom)
                    }
                    assertTrue(list.isItemChecked(0))
                    list.performItemClick(list.getChildAt(1), 1, list.adapter.getItemId(1))
                    assertFalse("A pending choice must not save immediately", BydOutputSettings.legacyVehicleProbe(screen))
                    val output = Paths.get("build/reports/vehicle-data-mode-dialog/$locale-${widthDp}dp-150.png")
                    Files.createDirectories(output.parent)
                    val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
                    try {
                        decor.draw(Canvas(bitmap))
                        Files.newOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { bitmap.recycle() }
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                    assertFalse("Cancel must preserve the saved mode", BydOutputSettings.legacyVehicleProbe(screen))
                } finally { dialog.dismiss(); screen.finish() }
            }
        }
    }
}
