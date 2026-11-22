package com.shilapi.xcertplay

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Looper
import android.os.ParcelUuid
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.network.*
import com.shilapi.xcertplay.orchestration.*
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class WirelessStartupControllerTest {
    private val statuses = mutableListOf<CarPlayStatus>()
    private fun controller(
        chosenIphone: String? = null,
        mode: WirelessHotspotMode = WirelessHotspotMode.WIFI_P2P,
        logs: MutableList<String>? = null,
        p2pService: Boolean = true,
    ) = CarPlayController(object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getApplicationContext(): Context = this
        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean = false
        override fun getSystemService(name: String): Any? =
            if (!p2pService && name == Context.WIFI_P2P_SERVICE) null else super.getSystemService(name)
    },
        CarPlayRuntimeConfig(mfiTarget = MfiTarget.LOCAL, transport = CarPlayTransport.WIRELESS,
            identification = Iap2IdentificationConfig(name = "test", modelIdentifier = "test", manufacturer = "test",
                serialNumber = "test", firmwareVersion = "1", hardwareVersion = "1", carPlayUsbInterfaceNumber = 3),
            wirelessBluetoothDeviceAddress = chosenIphone, wirelessHotspotMode = mode,
            manualHotspotSsid = "Car", manualHotspotPassphrase = "12345678"),
        AirPlayConfig("test", "test", "", "1", AirPlayDisplayConfig(800, 480)),
        AirPlayIdentity(ByteArray(32), ByteArray(32), "test"), PairingStore(),
        object : AirPlaySessionListener {
            override fun onDebugLog(message: String) { logs?.add(message) }
        }, object : AirPlayMediaHandler {}, statuses::add)

    private fun fail(controller: CarPlayController, generation: Int, error: Throwable) {
        controller.javaClass.getDeclaredMethod("fail", Throwable::class.java, Int::class.javaObjectType)
            .apply { isAccessible = true }.invoke(controller, error, generation)
    }

    @Test fun timeoutAndCleanupExceptionProduceOnlyOneTypedFailure() {
        val controller = controller()
        try {
            fail(controller, 0, WirelessStartupException(WirelessStartupFailure.FIRST_TCP_TIMEOUT, "timeout"))
            fail(controller, 0, IOException("Bluetooth socket closed"))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, statuses.size)
            assertEquals(WirelessStartupFailure.FIRST_TCP_TIMEOUT, (statuses.single() as CarPlayStatus.Failed).startupFailure)
        } finally { controller.close(); controller.awaitClosed(2_000) }
    }

    @Test fun queuedOldFailureAndOldCleanupDoNotAffectNewGeneration() {
        val controller = controller()
        var closed = 0
        val hotspot = object : WirelessHotspotManager {
            override fun start(timeoutMillis: Long): WirelessHotspotInfo = error("Not started")
            override fun close() { closed++ }
        }
        try {
            fail(controller, 0, WirelessStartupException(WirelessStartupFailure.FIRST_TCP_TIMEOUT, "timeout"))
            ReflectionHelpers.getField<AtomicInteger>(controller, "wirelessGeneration").set(1)
            ReflectionHelpers.setField(controller, "hotspot", hotspot)
            controller.javaClass.getDeclaredMethod("closeWirelessStack", CarPlayVpnService::class.java, Int::class.javaObjectType)
                .apply { isAccessible = true }.invoke(controller, null, 0)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(statuses.isEmpty())
            assertEquals(0, closed)
            assertSame(hotspot, ReflectionHelpers.getField(controller, "hotspot"))
            fail(controller, 0, IOException("late old failure"))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(statuses.isEmpty())
        } finally { controller.close(); controller.awaitClosed(2_000) }
    }

    @Test fun aKnownCauseInTheChainIsNamedWithoutBecomingAStartupFailure() {
        val controller = controller()
        try {
            fail(controller, 0, IOException("Could not establish WIFI_P2P hotspot: no channel",
                ConnectionFailureException(ConnectionFailureCause.WIFI_DIRECT_CHANNEL_UNAVAILABLE, "no channel")))
            shadowOf(Looper.getMainLooper()).idle()
            val failure = statuses.single() as CarPlayStatus.Failed
            assertEquals(ConnectionFailureCause.WIFI_DIRECT_CHANNEL_UNAVAILABLE, failure.knownCause)
            assertNull(failure.startupFailure)
            assertEquals("Could not establish WIFI_P2P hotspot: no channel", failure.message)
        } finally { controller.close(); controller.awaitClosed(2_000) }
    }

    @Test fun aStartupFailureCarriesItsKnownCause() {
        val controller = controller()
        try {
            fail(controller, 0, IOException("Could not establish MANUAL hotspot: Hotspot network is not ready",
                WirelessStartupException(WirelessStartupFailure.HOTSPOT_NOT_READY, "Hotspot network is not ready",
                    ConnectionFailureCause.HOTSPOT_INTERFACE_NOT_FOUND)))
            shadowOf(Looper.getMainLooper()).idle()
            val failure = statuses.single() as CarPlayStatus.Failed
            assertEquals(WirelessStartupFailure.HOTSPOT_NOT_READY, failure.startupFailure)
            assertEquals(ConnectionFailureCause.HOTSPOT_INTERFACE_NOT_FOUND, failure.knownCause)
            assertEquals("Could not establish MANUAL hotspot: Hotspot network is not ready", failure.message)
        } finally { controller.close(); controller.awaitClosed(2_000) }
    }

    private fun hotspotStartFailure(
        mode: WirelessHotspotMode = WirelessHotspotMode.MANUAL,
        p2pService: Boolean = true,
    ): WirelessStartupException {
        val controller = controller(mode = mode, p2pService = p2pService)
        try {
            return assertThrows(InvocationTargetException::class.java) {
                controller.javaClass.getDeclaredMethod("startWirelessHotspot", Int::class.javaPrimitiveType)
                    .apply { isAccessible = true }.invoke(controller, 0)
            }.cause as WirelessStartupException
        } finally { controller.close(); controller.awaitClosed(2_000) }
    }

    @Test @Config(shadows = [CarHotspotOffConnectTest.HotspotState::class])
    fun aCarHotspotReportedOffIsNamed() {
        CarHotspotOffConnectTest.HotspotState.on = false
        val failure = hotspotStartFailure()
        assertEquals(WirelessStartupFailure.HOTSPOT_CONFIGURATION, failure.reason)
        assertEquals("The car hotspot is off. Turn it on in the car settings and connect again.", failure.message)
        assertEquals(ConnectionFailureCause.CAR_HOTSPOT_OFF, failure.knownCause)
    }

    // #1256: firmware without Wi-Fi Direct used to fail with no reason, so it was retried forever unnamed.
    @Test fun aHeadUnitWithoutAWifiP2pManagerIsNamedAndNotRetried() {
        val packageManager = shadowOf(RuntimeEnvironment.getApplication().packageManager)
        for (feature in listOf(true, false)) {
            packageManager.setSystemFeature(PackageManager.FEATURE_WIFI_DIRECT, feature)
            val failure = hotspotStartFailure(WirelessHotspotMode.WIFI_P2P, p2pService = false)
            assertEquals(WirelessStartupFailure.HOTSPOT_CONFIGURATION, failure.reason)
            assertFalse(failure.reason.retryable)
            assertEquals(ConnectionFailureCause.WIFI_DIRECT_UNSUPPORTED, failure.knownCause)
        }
    }

    // Nothing shows that every unit where Wi-Fi Direct works declares the feature, so a missing flag alone never blocks.
    @Test fun aManagerWithoutTheDeclaredFeatureStillStartsWifiDirect() {
        shadowOf(RuntimeEnvironment.getApplication().packageManager).setSystemFeature(PackageManager.FEATURE_WIFI_DIRECT, false)
        // Location is not granted, so WifiP2pGroupManager's own preflight is what stops it.
        val error = wirelessHotspotError()
        assertTrue(error.message!!, error.message!!.contains("Allow precise Location"))
        assertNull(namedCause(error))
    }

    // Android 7 keeps a WifiP2pManager without Wi-Fi Direct; the framework answers createGroup with P2P_UNSUPPORTED.
    @Test @Config(shadows = [UnsupportedP2p::class, WifiP2pGroupManagerTest.P2pChannel::class])
    fun aFrameworkThatRefusesWifiDirectIsNamedAndNotRetried() {
        UnsupportedP2p.creates = 0
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION)
        app.getSystemService(android.net.wifi.WifiManager::class.java).isWifiEnabled = true
        val error = wirelessHotspotError()
        val failure = requireNotNull(namedCause(error)) { error.toString() }
        assertEquals(WirelessStartupFailure.HOTSPOT_CONFIGURATION, failure.reason)
        assertFalse(failure.reason.retryable)
        assertEquals(1, UnsupportedP2p.creates)
        val controller = controller()
        try {
            fail(controller, 0, error)
            shadowOf(Looper.getMainLooper()).idle()
            val status = statuses.single() as CarPlayStatus.Failed
            assertEquals(WirelessStartupFailure.HOTSPOT_CONFIGURATION, status.startupFailure)
            assertEquals(ConnectionFailureCause.WIFI_DIRECT_UNSUPPORTED, status.knownCause)
        } finally { controller.close(); controller.awaitClosed(2_000) }
    }

    /** startWirelessHotspot in WIFI_P2P mode past the cancelled-run check, so the manager really starts. */
    private fun wirelessHotspotError(): Throwable {
        val controller = controller()
        val phase = controller.javaClass.getDeclaredField("phase").apply { isAccessible = true }
        phase.set(controller, phase.type.enumConstants!!.first { it.toString() == "WIRELESS" })
        val start = controller.javaClass.getDeclaredMethod("startWirelessHotspot", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        // WifiP2pGroupManager.start refuses the main thread, as on the car.
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            return assertThrows(java.util.concurrent.ExecutionException::class.java) {
                executor.submit<Any?> { start.invoke(controller, 0) }.get(10, java.util.concurrent.TimeUnit.SECONDS)
            }.cause!!.let { if (it is InvocationTargetException) it.cause!! else it }
        } finally {
            executor.shutdownNow()
            controller.close(); controller.awaitClosed(2_000)
        }
    }

    private fun namedCause(error: Throwable): WirelessStartupException? =
        generateSequence(error) { it.cause }.filterIsInstance<WirelessStartupException>()
            .firstOrNull { it.knownCause == ConnectionFailureCause.WIFI_DIRECT_UNSUPPORTED }

    @Implements(android.net.wifi.p2p.WifiP2pManager::class)
    class UnsupportedP2p : org.robolectric.shadows.ShadowWifiP2pManager() {
        @Implementation protected fun requestP2pState(channel: android.net.wifi.p2p.WifiP2pManager.Channel,
                                                      listener: android.net.wifi.p2p.WifiP2pManager.P2pStateListener) {
            listener.onP2pStateAvailable(android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_DISABLED)
        }

        @Implementation override fun requestGroupInfo(channel: android.net.wifi.p2p.WifiP2pManager.Channel,
                                                      listener: android.net.wifi.p2p.WifiP2pManager.GroupInfoListener) {
            listener.onGroupInfoAvailable(null)
        }

        @Implementation override fun createGroup(channel: android.net.wifi.p2p.WifiP2pManager.Channel,
                                                 config: android.net.wifi.p2p.WifiP2pConfig?,
                                                 listener: android.net.wifi.p2p.WifiP2pManager.ActionListener) {
            creates++
            listener.onFailure(android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED)
        }

        companion object { var creates = 0 }
    }

    @Test fun theFailureOfAHeadUnitWithoutWifiDirectReachesTheScreenWithItsCause() {
        val controller = controller()
        try {
            fail(controller, 0, WifiDirectSupport.unsupported())
            shadowOf(Looper.getMainLooper()).idle()
            val failure = statuses.single() as CarPlayStatus.Failed
            assertEquals(WirelessStartupFailure.HOTSPOT_CONFIGURATION, failure.startupFailure)
            assertEquals(ConnectionFailureCause.WIFI_DIRECT_UNSUPPORTED, failure.knownCause)
        } finally { controller.close(); controller.awaitClosed(2_000) }
    }

    @Test @Config(shadows = [Tethering::class])
    fun aCarHotspotThatDidNotStartIsNamed() {
        CarHotspotSettings.setEnabled(RuntimeEnvironment.getApplication(), true)
        for ((result, reason, cause) in listOf(
            Triple(CarHotspotTethering.Result.TIMED_OUT, WirelessStartupFailure.HOTSPOT_NOT_READY,
                ConnectionFailureCause.CAR_HOTSPOT_START_TIMED_OUT),
            Triple(CarHotspotTethering.Result.FAILED, WirelessStartupFailure.HOTSPOT_NOT_READY,
                ConnectionFailureCause.CAR_HOTSPOT_START_FAILED),
            Triple(CarHotspotTethering.Result.PERMISSION_REQUIRED, WirelessStartupFailure.HOTSPOT_CONFIGURATION, null),
        )) {
            Tethering.result = result
            val failure = hotspotStartFailure()
            assertEquals(reason, failure.reason)
            assertEquals("${result.diagnostic}. Open the car hotspot settings and connect again.", failure.message)
            assertEquals(cause, failure.knownCause)
        }
    }

    @Implements(className = "com.shilapi.xcertplay.network.CarHotspotTethering", isInAndroidSdk = false)
    class Tethering {
        @Implementation fun enable(context: Context, isCancelled: () -> Boolean, timeoutMillis: Long,
            log: (String) -> Unit): CarHotspotTethering.Result = result

        companion object {
            @Volatile var result = CarHotspotTethering.Result.READY
        }
    }

    @Test fun theIphoneChoiceSaysWhyNoIphoneWasPicked() {
        val iPhone = "iPhone" to "00:11:22:33:44:01"
        assertCause(ConnectionFailureCause.CHOSEN_IPHONE_NOT_PAIRED,
            "The selected iPhone is no longer paired. Choose it again in DiPlay.", selectionFailure("00:11:22:33:44:99", iPhone))
        val noneBonded = "No unambiguous bonded iPhone found; pair one iPhone and retry"
        assertCause(ConnectionFailureCause.NO_PAIRED_IPHONE, noneBonded, selectionFailure(null))
        assertCause(ConnectionFailureCause.IPHONE_NOT_CHOSEN,
            "Multiple bonded iPhones found and none is currently connected; connect one iPhone and retry",
            selectionFailure(null, iPhone, "Work iPhone" to "00:11:22:33:44:02"))
        assertCause(ConnectionFailureCause.IPHONE_NOT_CHOSEN, noneBonded,
            selectionFailure(null, "Headset" to "00:11:22:33:44:03", "Pixel" to "00:11:22:33:44:04"))
    }

    // #1151: these counts went to logcat only, so a report could not tell "no iPhone paired" from "two iPhones".
    @Test fun theIphoneChoiceLeavesItsCountsInTheReport() {
        val logs = java.util.concurrent.CopyOnWriteArrayList<String>()
        selectionFailure(null, "Jane's iPhone" to "00:11:22:33:44:01", "Work iPhone" to "00:11:22:33:44:02",
            "Headset" to "00:11:22:33:44:03", logs = logs)
        selectionFailure("00:11:22:33:44:99", "Jane's iPhone" to "00:11:22:33:44:01", logs = logs)
        val choices = logs.filter { it.contains("wireless Bluetooth iPhone choice=") }
        assertEquals(listOf(
            ConnectionDiagnosticLines.automaticBluetoothIphone(bonded = 3, bondedIphones = 2, directlyConnected = 0, connected = 0),
            ConnectionDiagnosticLines.savedBluetoothIphone(bonded = 1, stillPaired = false),
        ), choices.map { it.substringAfter(" phase=").substringAfter(' ') })
        for (line in choices) {
            assertTrue(line, line.startsWith(CarPlayController.CONNECTION_DIAGNOSTIC_PREFIX + " "))
            assertEquals(line, DiagnosticRedactor.redact(line))
            assertFalse(line, line.contains("Jane") || line.contains("00:11:22"))
        }
    }

    // #1151: an inline "bondName=" made the redactor drop every snapshot; the controller must use the shared builder.
    @Test fun theBluetoothSnapshotIsTheSharedLineAndSurvivesTheRedactor() {
        val logs = java.util.concurrent.CopyOnWriteArrayList<String>()
        val adapter = BluetoothAdapter.getDefaultAdapter()
        shadowOf(adapter).setState(BluetoothAdapter.STATE_ON)
        val device = adapter.getRemoteDevice("00:11:22:33:44:05")
        shadowOf(device).setName("Jane's iPhone")
        shadowOf(device).setBondState(BluetoothDevice.BOND_BONDED)
        shadowOf(device).setUuids(arrayOf(ParcelUuid.fromString("00000000-deca-fade-deca-deafdecacafe")))
        val controller = controller(logs = logs)
        try {
            controller.javaClass.getDeclaredMethod("logBluetoothConnectionSnapshot", BluetoothDevice::class.java,
                String::class.java).apply { isAccessible = true }.invoke(controller, device, "test")
        } finally { controller.close(); controller.awaitClosed(2_000) }
        val line = logs.single { it.contains("Bluetooth snapshot") }
        assertEquals(ConnectionDiagnosticLines.bluetoothSnapshot(point = "test", adapterEnabled = true,
            bondState = BluetoothDevice.BOND_BONDED, cachedServicesReadable = true, cachedServiceCount = 1,
            cachedIap2Service = true), line.substringAfter(" phase=").substringAfter(' '))
        assertEquals(line, DiagnosticRedactor.redact(line))
        assertFalse(line, line.contains("Jane") || line.contains("00:11:22"))
    }

    private fun assertCause(cause: ConnectionFailureCause, message: String, failure: Throwable) {
        assertEquals(message, failure.message)
        assertEquals(cause, (failure as? ConnectionFailureException)?.reason)
    }

    private fun selectionFailure(chosenIphone: String?, vararg bonded: Pair<String, String>,
        logs: MutableList<String>? = null): Throwable {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        // No profile service answers in a test, so each profile query would wait out its timeout.
        // Setting any proxy, even null, makes Robolectric's getProfileProxy return false for every profile left unset.
        shadowOf(adapter).setProfileProxy(BluetoothProfile.HEADSET, null)
        shadowOf(adapter).setBondedDevices(bonded.mapTo(mutableSetOf()) { (name, address) ->
            adapter.getRemoteDevice(address).also { shadowOf(it).setName(name) }
        })
        val controller = controller(chosenIphone, logs = logs)
        try {
            return assertThrows(InvocationTargetException::class.java) {
                controller.javaClass.getDeclaredMethod("selectWirelessBluetoothDevice", BluetoothAdapter::class.java)
                    .apply { isAccessible = true }.invoke(controller, adapter)
            }.cause!!
        } finally { controller.close(); controller.awaitClosed(2_000) }
    }
}
