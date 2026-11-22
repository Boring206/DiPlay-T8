package com.shilapi.xcertplay.network

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import com.shilapi.xcertplay.orchestration.ConnectionFailureCause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException

// #1256: the XPeng P7 E28 firmware (Android 9) has no wifip2p service, so Android gives no WifiP2pManager.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class WifiDirectSupportTest {
    private fun context(manager: Boolean, feature: Boolean): Context {
        shadowOf(RuntimeEnvironment.getApplication().packageManager)
            .setSystemFeature(PackageManager.FEATURE_WIFI_DIRECT, feature)
        return object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getSystemService(name: String): Any? =
                if (!manager && name == Context.WIFI_P2P_SERVICE) null else super.getSystemService(name)
        }
    }

    @Test fun onlyAMissingManagerMakesWifiDirectUnavailable() {
        assertTrue(WifiDirectSupport.available(context(manager = true, feature = true)))
        // A unit that runs Wi-Fi Direct without declaring the feature keeps it.
        assertTrue(WifiDirectSupport.available(context(manager = true, feature = false)))
        assertFalse(WifiDirectSupport.available(context(manager = false, feature = true)))
        assertFalse(WifiDirectSupport.available(context(manager = false, feature = false)))
    }

    @Test fun theConnectionLogShowsBothTheFeatureAndTheManager() {
        assertEquals("wifiDirect feature=false manager=true", WifiDirectSupport.diagnostic(context(manager = true, feature = false)))
        assertEquals("wifiDirect feature=true manager=false", WifiDirectSupport.diagnostic(context(manager = false, feature = true)))
    }

    @Test fun aHeadUnitWithoutWifiDirectIsNamedAndNeverRetried() {
        val failure = WifiDirectSupport.unsupported()
        assertEquals(WirelessStartupFailure.HOTSPOT_CONFIGURATION, failure.reason)
        assertFalse(failure.reason.retryable)
        assertEquals(ConnectionFailureCause.WIFI_DIRECT_UNSUPPORTED, failure.knownCause)
        assertEquals("This head unit does not support Wi-Fi Direct. " +
            "Choose Built-in car hotspot or Existing Wi-Fi / Same LAN in Connection setup.", failure.message)
        assertNull(failure.cause)
        val rejection = IOException("Wi-Fi P2P createGroup failed: Wi-Fi P2P is unsupported (code=1)")
        assertSame(rejection, WifiDirectSupport.unsupported(rejection).cause)
    }
}
