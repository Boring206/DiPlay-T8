package com.shilapi.xcertplay.orchestration

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.hud.BydHudRouteState
import com.shilapi.xcertplay.hud.NavigationOutputLines
import com.shilapi.xcertplay.hud.NavigationOutputLog
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

/** The controller wires each new report line to the session log (#1196, #1180, #1233). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class CarPlayControllerReportEvidenceTest {
    private val app: Context = RuntimeEnvironment.getApplication()
    private val logs = CopyOnWriteArrayList<String>()
    private val controllers = mutableListOf<CarPlayController>()
    private val connectivity get() = shadowOf(app.getSystemService(ConnectivityManager::class.java))

    @Before fun setUp() = NavigationOutputLog.resetForTest()

    @After fun tearDown() {
        controllers.forEach { it.close(); assertTrue(it.awaitClosed(2_000)) }
        NavigationOutputLog.resetForTest()
    }

    @Test fun aWiredSessionLogsAndroidWifiStationChangesUntilItCloses() {
        val controller = controller(CarPlayTransport.WIRED)
        controller.start()
        val callback = connectivity.networkCallbacks.single()
        val hotspot = ShadowNetwork.newInstance(41)
        callback.onAvailable(hotspot)
        callback.onCapabilitiesChanged(hotspot, ShadowNetworkCapabilities.newInstance().also {
            shadowOf(it).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        })
        callback.onLost(hotspot)

        val wifi = logs.filter { it.contains("Android Wi-Fi station") }
        assertTrue(wifi.all { it.startsWith(CarPlayController.CONNECTION_DIAGNOSTIC_PREFIX + " ") })
        assertEquals(listOf(
            "Android Wi-Fi station watch on (wired session)",
            "Android Wi-Fi station #1 connected stations=1",
            "Android Wi-Fi station #1 validated=false metered=true internet=true iPhoneHotspotRange=unknown",
            "Android Wi-Fi station #1 disconnected after 0s stations=0",
        ), wifi.map { it.substringAfter(" phase=").substringAfter(' ') })

        controller.close()
        assertTrue(controller.awaitClosed(2_000))
        assertTrue(connectivity.networkCallbacks.isEmpty())
    }

    @Test fun aWirelessSessionDoesNotWatchTheWifiStation() {
        controller(CarPlayTransport.WIRELESS).start()
        assertTrue(connectivity.networkCallbacks.isEmpty())
    }

    @Test fun navigationOutputStateReachesTheSessionLogUntilTheControllerCloses() {
        NavigationOutputLog.note("test", "hud.bind", NavigationOutputLines.hudBindRefused(30))
        val controller = controller(CarPlayTransport.WIRED)
        controller.start()
        NavigationOutputLog.note("test", "hud.gateway", NavigationOutputLines.hudConnected(0, 0, true))
        controller.close()
        assertTrue(controller.awaitClosed(2_000))
        NavigationOutputLog.note("test", "hud.guidance", NavigationOutputLines.hudGuidanceAccepted())

        assertEquals(listOf(
            "Navigation output (current): HUD SOME/IP bind refused; retrying with backoff up to 30s",
            "Navigation output: HUD SOME/IP connected callback=0 startResult=0 started=true",
        ), logs.filter { it.startsWith("Navigation output") })
    }

    @Test fun anUnreadableGearNamesItsReasonAndTellsTheVideoHost() {
        val observed = CountDownLatch(1)
        val gear = CopyOnWriteArrayList<Boolean?>()
        val controller = controller(CarPlayTransport.WIRED)
        controller.videoListener = object : CarPlayVideoListener {
            override fun readParked(): Boolean? = null
            override fun gearUnreadableReason(): String = "adb=NOT_APPROVED"
            override fun onGearObserved(parked: Boolean?) { gear += parked; observed.countDown() }
            override fun onVideoAllowedChanged(allowed: Boolean) = Unit
            override fun onVideoMessage(streamId: Long, message: Map<String, Any?>) = Unit
            override fun onVideoUiRequested() = Unit
            override fun onVideoSessionEnded() = Unit
        }
        controller.start()
        assertTrue(observed.await(3, TimeUnit.SECONDS))

        assertEquals(listOf<Boolean?>(null), gear)
        assertTrue(logs.contains("video in car gear=unknown adb=NOT_APPROVED; video stays off until the gear reads P"))
    }

    @Test fun anEndedSessionSummarizesItsRouteGuidanceOnce() {
        val controller = controller(CarPlayTransport.WIRED)
        val session = AirPlaySession(Socket(), airPlayConfig(), AirPlayIdentity.generate(), PairingStore(), null,
            object : AirPlaySessionListener {}, object : AirPlayMediaHandler {})
        try {
            field<AtomicBoolean>(controller, "routeGuidanceSession").set(true) // what onSessionActive sets
            controller.javaClass.getDeclaredField("activeSession").apply { isAccessible = true }.set(controller, session)
            val onRouteFrame = controller.javaClass.getDeclaredMethod("onRouteFrame", Iap2Frame::class.java)
                .apply { isAccessible = true }
            onRouteFrame.invoke(controller, Iap2Frame(BydHudRouteState.ROUTE_GUIDANCE_UPDATE,
                byteArrayOf(0, 9, 0, 0x13, 'M'.code.toByte(), 'a'.code.toByte(), 'p'.code.toByte(), 's'.code.toByte(), 0)))
            onRouteFrame.invoke(controller, Iap2Frame(BydHudRouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE, byteArrayOf(0, 6, 0, 1, 0, 0)))
            field<AirPlaySessionListener>(controller, "sessionListener").onSessionEnded(session)
            controller.close()
            assertTrue(controller.awaitClosed(2_000))
        } finally {
            session.close()
        }

        val summaries = logs.filter { it.startsWith("Route guidance this session:") }
        assertEquals(1, summaries.size)
        assertTrue(summaries.single(), summaries.single().startsWith(
            "Route guidance this session: 0x5201=1 0x5202=1 source=\"Maps\" navigation_enabled=true receivers="))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(target: Any, name: String): T =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target) as T

    private fun airPlayConfig() = AirPlayConfig("test", "test", "", "1", AirPlayDisplayConfig(800, 480))

    private fun controller(transport: CarPlayTransport) = CarPlayController(
        app,
        CarPlayRuntimeConfig(
            mfiTarget = MfiTarget.LOCAL, transport = transport,
            identification = Iap2IdentificationConfig(name = "test", modelIdentifier = "test", manufacturer = "test",
                serialNumber = "test", firmwareVersion = "1", hardwareVersion = "1", carPlayUsbInterfaceNumber = 3),
        ),
        airPlayConfig(), AirPlayIdentity.generate(), PairingStore(),
        object : AirPlaySessionListener {
            override fun onDebugLog(message: String) { logs += message }
        },
        object : AirPlayMediaHandler {}, {},
    ).also(controllers::add)
}
