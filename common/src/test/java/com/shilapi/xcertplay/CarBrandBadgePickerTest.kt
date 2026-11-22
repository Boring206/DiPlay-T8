// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import android.app.AlertDialog
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import com.shilapi.xcertplay.host.R
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], qualifiers = "en", manifest = Config.NONE)
class CarBrandBadgePickerTest {

    @Test fun eachBadgeSendsItsPackagedIconToTheIphone() {
        val activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        fun defaultIconBytes(): ByteArray =
            CarPlayHostActivity::class.java.getDeclaredMethod("defaultAirPlayIconBytes")
                .apply { isAccessible = true }.invoke(activity) as ByteArray
        for (badge in CarBrandBadge.entries) {
            AirPlayPersistence.saveCarBrand(activity, badge.key)
            assertArrayEquals(
                activity.resources.openRawResource(badge.iconRawRes).use { it.readBytes() },
                defaultIconBytes(),
            )
        }
        // Unknown stored keys fall back to the default BYD icon.
        AirPlayPersistence.saveCarBrand(activity, "mystery")
        assertArrayEquals(
            activity.resources.openRawResource(CarBrandBadge.DEFAULT.iconRawRes).use { it.readBytes() },
            defaultIconBytes(),
        )
    }

    @Test fun pickingABadgeKeepsANameTheUserTyped() {
        val activity = newHome()
        AirPlayPersistence.saveCarBrand(activity, CarBrandBadge.DEFAULT.key)
        AirPlayPersistence.saveOemLabel(activity, "Seal")
        brandButton(activity).performClick()
        val dialog = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
        dialog.listView.performItemClick(
            null,
            CarBrandBadge.DENZA.ordinal,
            dialog.listView.adapter.getItemId(CarBrandBadge.DENZA.ordinal),
        )
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Seal", AirPlayPersistence.loadOemLabel(activity))
        assertEquals(CarBrandBadge.DENZA.key, AirPlayPersistence.loadCarBrand(activity))
    }

    @Test fun savingWithoutChangingTheSelectionKeepsTheCustomImage() {
        val activity = newHome()
        try {
            AirPlayPersistence.saveCarBrand(activity, CarBrandBadge.DEFAULT.key)
            AirPlayPersistence.saveCustomAirPlayIcon(activity, byteArrayOf(1, 2, 3, 4))
            brandButton(activity).performClick()
            val dialog = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
            // Nothing selected differently; just confirm.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            assertNotNull(AirPlayPersistence.loadCustomAirPlayIconFile(activity))
            assertEquals(CarBrandBadge.DEFAULT.key, AirPlayPersistence.loadCarBrand(activity))
        } finally {
            AirPlayPersistence.clearCustomAirPlayIcon(activity)
        }
    }

    @Test fun pickingABadgeUpdatesTheDefaultNameWithTheLocalizedBadgeName() {
        val activity = newHome()
        try {
            AirPlayPersistence.saveCarBrand(activity, CarBrandBadge.DEFAULT.key)
            AirPlayPersistence.saveOemLabel(activity, AirPlayPersistence.DEFAULT_OEM_LABEL)
            brandButton(activity).performClick()
            val dialog = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
            dialog.listView.performItemClick(
                null,
                CarBrandBadge.YANGWANG.ordinal,
                dialog.listView.adapter.getItemId(CarBrandBadge.YANGWANG.ordinal),
            )
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(
                activity.getString(R.string.settings_car_brand_yangwang),
                AirPlayPersistence.loadOemLabel(activity),
            )
            assertTrue(AirPlayPersistence.loadCustomAirPlayIconFile(activity) == null)
        } finally {
            AirPlayPersistence.saveOemLabel(activity, AirPlayPersistence.DEFAULT_OEM_LABEL)
        }
    }

    private fun newHome(): DiPlayActivity {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        return activity
    }

    private fun brandButton(activity: DiPlayActivity): Button {
        val controls = LinearLayout(activity)
        DiPlayActivity::class.java.getDeclaredMethod("carButtonControls", LinearLayout::class.java)
            .apply { isAccessible = true }.invoke(activity, controls)
        val title = activity.getString(R.string.settings_car_brand_title)
        return (0 until controls.childCount)
            .map { controls.getChildAt(it) }
            .filterIsInstance<Button>()
            .single { it.text.startsWith(title) }
    }
}
