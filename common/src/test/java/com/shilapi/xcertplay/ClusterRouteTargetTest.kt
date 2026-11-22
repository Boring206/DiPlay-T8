package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import java.util.concurrent.ExecutorService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild
import org.robolectric.util.ReflectionHelpers

/**
 * #1327: a Geely head unit with the DiLink 4 ADB switch saved on asked its iPhone for a 1920x720 cluster
 * stream that no display showed, and a BYD unit whose probe finds no projection display did the same.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ClusterRouteTargetTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val hosts = mutableListOf<CarPlayHostActivity>()

    private val target = "mBaseDisplayInfo=DisplayInfo{\"built-in, displayId 0\", real 1920 x 1080}\n" +
        "mBaseDisplayInfo=DisplayInfo{\"${DiLink4ClusterDisplay.NAME}, displayId 7\", real 1920 x 720, " +
        "owner com.xdja.containerservice (uid 1000)}"
    private val noTarget = "mBaseDisplayInfo=DisplayInfo{\"built-in, displayId 0\", real 1920 x 1080}"

    @Before fun reset() {
        app.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        AdbClusterRouter.forgetMissingTarget()
    }

    @After fun tearDown() {
        AdbClusterRouter.forgetMissingTarget()
        MapMirrors.streamAspect = MapMirrors.PHYSICAL_STREAM_ASPECT
        hosts.forEach { host ->
            for (name in listOf("teardownExecutor", "airPlayCommandExecutor")) {
                (host.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(host) as ExecutorService)
                    .shutdownNow()
            }
        }
    }

    @Test fun savedDiLink4RoutesAreIgnoredOffBydHeadUnits() {
        assertFalse(AdbClusterRouter.bydHeadUnit(app))
        AirPlayPersistence.saveAdbClusterEnabled(app, true)
        assertTrue(AirPlayPersistence.loadAdbClusterEnabled(app))
        assertFalse(AdbClusterRouter.enabled(app))
        assertVirtualStream(config())

        AirPlayPersistence.saveLegacyClusterEnabled(app, true)
        assertFalse(AdbClusterRouter.enabled(app))
        assertVirtualStream(config())
    }

    @Test fun firmwareThatNamesItsDiLinkKeepsTheRoute() {
        ShadowBuild.setFingerprint("BYD-AUTO/DiLink4.0/DiLink4.0:9/PQ1A/1:user/release-keys")
        AirPlayPersistence.saveAdbClusterEnabled(app, true)
        assertTrue(AdbClusterRouter.bydHeadUnit(app))
        assertTrue(AdbClusterRouter.enabled(app))
    }

    @Test fun bydUnitAsksForTheNativeStreamUntilAProbeFindsNoTarget() {
        installBydCarSettings(app)
        AirPlayPersistence.saveAdbClusterEnabled(app, true)
        // The first connection must ask for the cluster stream before any probe has run.
        assertNativeStream(config())

        assertNull(AdbClusterRouter.probe(noTarget))
        assertTrue(AdbClusterRouter.targetMissing)
        assertTrue(AdbClusterRouter.enabled(app))
        assertVirtualStream(config())

        // A display that appears later is found by the next probe, and the next connection asks again.
        assertEquals(7, AdbClusterRouter.probe(target))
        assertFalse(AdbClusterRouter.targetMissing)
        assertNativeStream(config())
    }

    @Test fun aFailedDumpIsNotEvidenceThatTheDisplayIsMissing() {
        installBydCarSettings(app)
        AirPlayPersistence.saveAdbClusterEnabled(app, true)
        assertNull(AdbClusterRouter.probe(""))
        assertNull(AdbClusterRouter.probe("Can't find service: display"))
        assertFalse(AdbClusterRouter.targetMissing)
        assertNativeStream(config())
    }

    @Test fun changingTheRouteSettingsAsksForTheNativeStreamAgain() {
        installBydCarSettings(app)
        AirPlayPersistence.saveAdbClusterEnabled(app, true)
        AdbClusterRouter.probe(noTarget)
        assertVirtualStream(config())

        ClusterActivityOutput.stopForSettings()
        assertFalse(AdbClusterRouter.targetMissing)
        assertNativeStream(config())
    }

    @Test fun aMissingTargetDoesNotAddAStreamWhenTheClusterMapIsOff() {
        installBydCarSettings(app)
        AirPlayPersistence.saveAdbClusterEnabled(app, true)
        AdbClusterRouter.probe(noTarget)
        AirPlayPersistence.saveClusterMapEnabled(app, false)
        assertNull(config())
    }

    private fun assertNativeStream(config: AirPlayDisplayConfig?) {
        assertEquals(DiLink4ClusterDisplay.STREAM_WIDTH, config!!.widthPixels)
        assertEquals(DiLink4ClusterDisplay.STREAM_HEIGHT, config.heightPixels)
        assertTrue(ReflectionHelpers.getField<Boolean>(hosts.last(), "adbClusterConfigured"))
    }

    // The stream a unit with the cluster map on and no cluster display gets, for the centre card and launcher.
    private fun assertVirtualStream(config: AirPlayDisplayConfig?) {
        assertEquals(1280, config!!.widthPixels)
        assertEquals(720, config.heightPixels)
        assertFalse(ReflectionHelpers.getField<Boolean>(hosts.last(), "adbClusterConfigured"))
    }

    private fun config(): AirPlayDisplayConfig? {
        val host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get().also { hosts += it }
        return CarPlayHostActivity::class.java.getDeclaredMethod("clusterDisplayConfig")
            .apply { isAccessible = true }.invoke(host) as AirPlayDisplayConfig?
    }
}
