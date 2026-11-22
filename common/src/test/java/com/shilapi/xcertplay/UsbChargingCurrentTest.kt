package com.shilapi.xcertplay

import com.shilapi.xcertplay.orchestration.CarPlayRuntimeConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class UsbChargingCurrentTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun normalKeepsTheExistingChargingOffer() {
        assertEquals(CarPlayRuntimeConfig.DEFAULT_AVAILABLE_CURRENT_MILLI_AMPS, UsbChargingCurrent.NORMAL.milliAmps)
        assertEquals(listOf(2400, 1500, 1000, 500), UsbChargingCurrent.entries.map { it.milliAmps })
    }

    @Test fun storedValuesAreStableKeysAndUnknownOnesFallBackToNormal() {
        assertEquals(listOf("normal", "reduced", "medium", "low"), UsbChargingCurrent.entries.map { it.key })
        assertEquals(UsbChargingCurrent.MEDIUM, UsbChargingCurrent.fromKey("medium"))
        assertEquals(UsbChargingCurrent.NORMAL, UsbChargingCurrent.fromKey(null))
        assertEquals(UsbChargingCurrent.NORMAL, UsbChargingCurrent.fromKey("2"))
        // A value this build does not know, such as one restored from a newer build's backup, offers the default.
        assertEquals(UsbChargingCurrent.NORMAL, UsbChargingCurrent.fromKey("custom"))
    }

    @Test fun theChoiceIsSavedAndDefaultsToNormal() {
        assertEquals(UsbChargingCurrent.NORMAL, AirPlayPersistence.loadUsbChargingCurrent(context))
        AirPlayPersistence.saveUsbChargingCurrent(context, UsbChargingCurrent.MEDIUM)
        assertEquals(UsbChargingCurrent.MEDIUM, AirPlayPersistence.loadUsbChargingCurrent(context))
        AirPlayPersistence.saveUsbChargingCurrent(context, UsbChargingCurrent.LOW)
        assertEquals(UsbChargingCurrent.LOW, AirPlayPersistence.loadUsbChargingCurrent(context))
    }
}
