package com.shilapi.xcertplay

import android.net.Uri
import android.os.Looper
import android.view.Surface
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.shilapi.xcertplay.hud.BluetoothPauseEvidence
import com.shilapi.xcertplay.hud.BydCallPopupGuard
import com.shilapi.xcertplay.hud.BydOutputSettings
import com.shilapi.xcertplay.media.AndroidMediaSink
import com.shilapi.xcertplay.orchestration.CarPlayController
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * The evidence issues #1021, #1110, #1180, #1219 and #1237 were missing from saved reports: each new
 * line must reach the report as written, and none may carry a URL, a device name or an address.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], manifest = Config.NONE)
class DiagnosticReportEvidenceTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @After fun tearDown() {
        ReflectionHelpers.setField(CarPlayVideo, "diagnostic", { _: String -> })
        CarPlayVideo.onVideoSessionEnded()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun assertKept(line: String) = assertEquals(line, DiagnosticRedactor.redact(line))

    @Test fun theHeaderNamesCallPopupPipAndDashboardSongSettings() {
        AirPlayPersistence.saveCallEchoCancellation(context, true)
        BydOutputSettings.setPhoneAudioRoute(context, false)
        AirPlayPersistence.saveCallSpeakerphone(context, true)
        AirPlayPersistence.saveHideBydCallPopup(context, true)
        AirPlayPersistence.saveBtSuspendDuringCarplay(context, true)
        AirPlayPersistence.saveBtSuspendDelaySeconds(context, 15)
        AirPlayPersistence.saveAdaptPipResolution(context, true)
        BydOutputSettings.setClusterSong(context, true)
        BydOutputSettings.setClusterSongGate(context, false)
        BydOutputSettings.setClusterSongOnChange(context, true)

        val lines = DiagnosticSettingsReport.lines(context)
        assertEquals(
            "Call settings: call_echo_cancellation=true phone_audio_route=false call_speakerphone=true " +
                "hide_byd_call_popup=true bt_suspend_during_carplay=true bt_suspend_delay_s=15; " +
                "since app start: callPopupStatus=${BydCallPopupGuard.status}",
            lines[0],
        )
        assertEquals("Multi-window resolution: adapt_pip_resolution=true", lines[1])
        assertTrue(lines[2], lines[2].startsWith(
            "Dashboard song: clusterSong=true clusterSongGate=false clusterSongOnChange=true; " +
                "since app start: gate=off adbAccess="))
        assertTrue(lines[2], lines[2].contains(" lastWrite="))
        lines.forEach(::assertKept)
    }

    @Test fun aFailedBluetoothPauseKeepsItsEvidenceInTheReport() {
        val lines = BluetoothPauseEvidence.lines(true, "svc: unknown command 'bluetooth'", "ON",
            "Bluetooth Status\n  enabled: true\n  state: ON\n  address: C0:A6:00:29:58:0A\n  name: Jane's Car")
        assertEquals(listOf(
            "car Bluetooth pause evidence: svc bluetooth disable printed 'svc: unknown command bluetooth'",
            "car Bluetooth pause evidence: Android Bluetooth was ON when the wait for the disable ended",
            "car Bluetooth pause evidence: bluetooth_manager after DiPlay's restore step enabled=true state=ON",
        ), lines)
        lines.forEach(::assertKept)
        // A reply that names an address keeps the line; the redactor masks the address.
        val masked = DiagnosticRedactor.redact(BluetoothPauseEvidence.commandLine(true, "error C0:A6:00:29:58:0A busy"))
        assertEquals("car Bluetooth pause evidence: svc bluetooth disable printed 'error [address] busy'", masked)
    }

    @Test fun callAudioAndPopupLinesSurviveTheSavedLog() {
        // The exact text MicrophoneUplink and BydCallPopupGuard write; their own tests pin it there.
        val lines = listOf(
            "Microphone: type=telephony platform effect=AEC enabled=false",
            "Microphone: type=telephony platform effect=NS could not be disabled status=-5",
            "Microphone: type=telephony echo canceller enabled=true tail=250ms",
            "Microphone: type=telephony echo canceller skipped rate=48000 channels=1",
            "Microphone: type=telephony effects left to the car's phone audio state",
            "Microphone: type=telephony echo canceller stopped; platform AEC restored",
            "BYD call popup: hide requested",
            "BYD call popup: status=HIDDEN previous=OFF",
            "BYD call popup: status=UNAVAILABLE_OR_RESTORE_UNCONFIRMED previous=HIDDEN reason=HELPER_TIMEOUT",
        )
        val folder = Files.createTempDirectory("diplay-call-evidence").toFile()
        try {
            val file = folder.resolve("diplay.log")
            SessionLogFile(file).use { log ->
                log.reset("header")
                lines.forEach { log.append("12:00:00.000  $it") }
                assertTrue(log.awaitIdle(10_000))
            }
            assertEquals(listOf("header") + lines.map { "12:00:00.000  $it" }, file.readLines())
        } finally { folder.deleteRecursively() }
    }

    @Test fun aQueuedVideoReachesTheReportWithItsDomainButNotItsUrl() {
        val lines = CopyOnWriteArrayList<String>()
        ReflectionHelpers.setField(CarPlayVideo, "diagnostic", { line: String -> lines += line })
        val url = "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=1&signature=SECRET&ip=203.0.113.9"
        CarPlayVideo.onVideoMessage(7L, mapOf("type" to "insertPlayQueueItem", "item" to mapOf(
            "Content-Location" to url, "mediaType" to "streaming", "clientBundleID" to "com.example.Video", "uuid" to "u1")))
        CarPlayVideo.onVideoMessage(7L, mapOf("type" to "insertPlayQueueItem", "item" to mapOf(
            "Content-Location" to "file:///sdcard/private.mp4", "mediaType" to "file")))
        // An app's own scheme: what sits in the host is the app's to choose, here an account token.
        CarPlayVideo.onVideoMessage(7L, mapOf("type" to "insertPlayQueueItem", "item" to mapOf(
            "Content-Location" to "myapp://u83Kq2ZpLx0aTnQw7Rv/master.m3u8", "mediaType" to "streaming",
            "clientBundleID" to "com.example.Video", "uuid" to "u2")))
        CarPlayVideo.onVideoMessage(7L, mapOf("type" to "unhandledURL", "kind" to "response",
            "response" to mapOf("FCUP_Response_RequestID" to 3, "FCUP_Response_StatusCode" to 200)))
        CarPlayVideo.onVideoMessage(7L, mapOf("type" to "stop"))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(
            "Video: queue item app=com.example.Video scheme=https host=googlevideo.com mediaType=streaming",
            "Video: queue item this player cannot play mediaType=file",
            "Video: queue item app=com.example.Video scheme=myapp mediaType=streaming",
            "Video: iPhone unhandledURL answer request=3 keys=[type,kind,response] " +
                "responseKeys=[FCUP_Response_RequestID,FCUP_Response_StatusCode]",
        ), lines)
        for (line in lines) {
            assertKept(line)
            assertFalse(line, listOf("videoplayback", "SECRET", "signature", "203.0.113.9", "sdcard", "rr3", "sn-abc",
                "u83Kq2ZpLx0aTnQw7Rv", "master").any(line::contains))
        }
    }

    @Test fun onlyAWebUrlGivesADomainAndOnlyItsLastTwoLabels() {
        assertEquals("googlevideo.com", VideoDiagnostics.host("https://rr3---sn-abc.googlevideo.com/videoplayback"))
        assertEquals("example.com", VideoDiagnostics.host("HTTP://Example.COM./v.m3u8"))
        assertEquals("ip", VideoDiagnostics.host("http://203.0.113.9:8080/v.m3u8"))
        assertEquals("ip", VideoDiagnostics.host("http://[2001:db8::1]/v.m3u8"))
        assertNull(VideoDiagnostics.host("myapp://u83Kq2ZpLx0aTnQw7Rv/master.m3u8"))
        assertNull(VideoDiagnostics.host("skd://key-id-123"))
        assertNull(VideoDiagnostics.host("https:///no-host"))
    }

    // An app-scheme playlist can resolve every segment through the iPhone: three lines per load would flood the report.
    @Test fun loadsThroughTheIphoneWriteTheFirstFewInFullThenOneSummaryWhenTheItemStops() {
        val lines = CopyOnWriteArrayList<String>()
        ReflectionHelpers.setField(CarPlayVideo, "diagnostic", { line: String -> lines += line })
        ReflectionHelpers.setField(CarPlayVideo, "controller", null) // no replies, so no reply lines
        val app = "myapp://token/master.m3u8"
        CarPlayVideo.onVideoMessage(7L, mapOf("type" to "insertPlayQueueItem", "item" to mapOf(
            "Content-Location" to app, "mediaType" to "streaming", "uuid" to "u1")))
        shadowOf(Looper.getMainLooper()).idle()
        val pending = ReflectionHelpers.getField<Map<Long, *>>(CarPlayVideo, "pendingUrls")
        repeat(5) { load ->
            val result = java.util.concurrent.atomic.AtomicReference<CarPlayVideo.LoadedUrl?>()
            val loader = Thread { result.set(CarPlayVideo.resolveOnIphone("myapp://token/segment$load.ts")) }
            loader.start()
            val deadline = System.nanoTime() + 5_000_000_000L
            while (pending.isEmpty() && System.nanoTime() < deadline) Thread.sleep(2)
            val id = pending.keys.single()
            CarPlayVideo.onVideoMessage(7L, mapOf("type" to "unhandledURL", "kind" to "response", "response" to mapOf(
                "FCUP_Response_RequestID" to id, "FCUP_Response_StatusCode" to 200, "FCUP_Response_Data" to ByteArray(4))))
            shadowOf(Looper.getMainLooper()).idle()
            loader.join(5_000)
            assertEquals(200, result.get()?.status)
            CarPlayVideo.noteIphoneLoaded("myapp", 200, 4, redirect = false)
        }
        CarPlayVideo.noteIphoneLoaded("myapp", 404, 0, redirect = false)
        CarPlayVideo.onVideoMessage(7L, mapOf("type" to "stop"))
        shadowOf(Looper.getMainLooper()).idle()

        val perLoad = lines.drop(1)
        assertEquals(perLoad.toString(), 3, perLoad.count { it.startsWith("Video: asked the iPhone to load a myapp URL") })
        assertEquals(perLoad.toString(), 3, perLoad.count { it.startsWith("Video: iPhone unhandledURL answer") })
        assertEquals(perLoad.toString(), 3, perLoad.count { it.startsWith("Video: iPhone loaded myapp URL status=200") })
        // A failure has its own allowance, so it is not hidden behind the successful loads.
        assertTrue(perLoad.toString(), perLoad.contains("Video: iPhone loaded myapp URL status=404 bytes=0 redirect=false"))
        assertEquals("Video: iPhone loads for the item asked=5 answers=5 loaded=5 failed=1 noAnswer=0 " +
            "(first 3 of each kind written in full)", lines.last())
        assertEquals(lines.toString(), 12, lines.size)

        // The next item starts its own count; one that never passed the limit needs no summary.
        lines.clear()
        CarPlayVideo.onVideoMessage(7L, mapOf("type" to "insertPlayQueueItem", "item" to mapOf(
            "Content-Location" to app, "mediaType" to "streaming", "uuid" to "u2")))
        shadowOf(Looper.getMainLooper()).idle()
        CarPlayVideo.noteIphoneLoaded("myapp", 200, 4, redirect = false)
        CarPlayVideo.onVideoMessage(7L, mapOf("type" to "stop"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("Video: queue item app=none scheme=myapp mediaType=streaming",
            "Video: iPhone loaded myapp URL status=200 bytes=4 redirect=false"), lines)
    }

    // The host that started a session closes its log in onDestroy; a host that adopts it must take video lines over.
    @Test fun aHostThatAdoptsTheSessionReceivesTheVideoLines() {
        val controller = mock(CarPlayController::class.java)
        `when`(controller.videoListener).thenReturn(CarPlayVideo)
        val first = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        val replacement = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        val folder = Files.createTempDirectory("diplay-video-adopt").toFile()
        val log = SessionLogFile(folder.resolve("diplay.log"))
        try {
            CarPlayBackgroundSession.clear()
            CarPlayBackgroundSession.store(controller, mock(AndroidMediaSink::class.java), 800, 480, first,
                CarPlaySessionDisplay(800, 480, Surface.ROTATION_0, false, false, 800, 480)) {}
            ReflectionHelpers.setField(CarPlayVideo, "diagnostic", { _: String -> }) // the old host's closed log
            log.reset("header")
            ReflectionHelpers.setField(replacement, "sessionLog", log)
            ReflectionHelpers.setField(replacement, "smoothVideo", false)
            assertTrue(ReflectionHelpers.callInstanceMethod<Boolean>(replacement, "adoptBackgroundSession"))

            CarPlayVideo.note("prepared duration=1000 ms size=1280x720")
            assertTrue(AsyncDiagnosticLog.awaitIdle(10_000))
            assertTrue(log.awaitIdle(10_000))
            val written = folder.resolve("diplay.log").readLines()
            assertTrue(written.toString(), written.any { it.endsWith("  Video: prepared duration=1000 ms size=1280x720") })
        } finally {
            log.close()
            folder.deleteRecursively()
            ReflectionHelpers.setField(CarPlayVideo, "controller", null)
            for (host in listOf(first, replacement)) for (name in listOf("teardownExecutor", "airPlayCommandExecutor"))
                ReflectionHelpers.getField<ExecutorService>(host, name).shutdownNow()
            CarPlayBackgroundSession.clear()
        }
    }

    @Test fun videoErrorsEncryptionAndLoadsKeepCodesAndNamesOnly() {
        val dataSpec = DataSpec(Uri.parse("https://cdn.example.com/v.m3u8?token=abc"))
        val http = HttpDataSource.InvalidResponseCodeException(403, "Forbidden https://cdn.example.com/v.m3u8?token=abc",
            null, emptyMap(), dataSpec, ByteArray(0))
        val error = PlaybackException("Source error https://cdn.example.com/v.m3u8?token=abc", http,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
        val lines = listOf(
            VideoDiagnostics.playerError(error),
            VideoDiagnostics.loadingVideo("https://cdn.example.com/v.m3u8?token=abc",
                "network=wifi internet=true validated=false"),
            VideoDiagnostics.iphoneLoaded("myapp", 200, 1024, redirect = false),
            VideoDiagnostics.hlsEncryption("skd", "cbcs",
                listOf(UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"), UUID.randomUUID()),
                listOf("cbcs", null), 120),
        ).map { "Video: $it" }
        assertEquals(listOf(
            "Video: player error ERROR_CODE_IO_BAD_HTTP_STATUS code=2004 http=403 causes=InvalidResponseCodeException",
            "Video: loading video scheme=https network=wifi internet=true validated=false",
            "Video: iPhone loaded myapp URL status=200 bytes=1024 redirect=false",
            "Video: HLS encryption aes128KeyScheme=skd sampleAesSchemeType=cbcs drm=[widevine,other] " +
                "sessionKeys=[cbcs,none] segments=120",
        ), lines)
        for (line in lines) {
            assertKept(line)
            assertFalse(line, line.contains("token") || line.contains("cdn.example.com"))
        }
        assertEquals("none", VideoDiagnostics.word(null))
        assertEquals("[a,b,c,d,e,f,g,h,i,j,k,l,+2]", VideoDiagnostics.keys(("abcdefghijklmn").map { it.toString() }))
        assertFalse(VideoDiagnostics.word("x y\nz=\"secret\"").contains(' '))
        assertTrue(runCatching { VideoDiagnostics.playerError(PlaybackException(null, IOException(), 1000)) }.isSuccess)
    }
}
