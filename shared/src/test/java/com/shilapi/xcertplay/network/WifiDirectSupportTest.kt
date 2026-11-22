package com.shilapi.xcertplay.network

import com.shilapi.xcertplay.orchestration.ConnectionFailureCause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// #1256: the XPeng P7 E28 firmware (Android 9) has no wifip2p service and no wifi.direct feature.
class WifiDirectSupportTest {
    @Test fun wifiDirectNeedsBothTheFeatureAndTheService() {
        assertTrue(WifiDirectSupport.available(hasFeature = true, hasService = true))
        assertFalse(WifiDirectSupport.available(hasFeature = false, hasService = true))
        assertFalse(WifiDirectSupport.available(hasFeature = true, hasService = false))
        assertFalse(WifiDirectSupport.available(hasFeature = false, hasService = false))
    }

    @Test fun aHeadUnitWithoutWifiDirectIsNamedAndNeverRetried() {
        val failure = WifiDirectSupport.unsupported()
        assertEquals(WirelessStartupFailure.HOTSPOT_CONFIGURATION, failure.reason)
        assertFalse(failure.reason.retryable)
        assertEquals(ConnectionFailureCause.WIFI_DIRECT_UNSUPPORTED, failure.knownCause)
        assertEquals("This head unit does not support Wi-Fi Direct. " +
            "Choose Built-in car hotspot or Existing Wi-Fi / Same LAN in Connection setup.", failure.message)
    }
}
