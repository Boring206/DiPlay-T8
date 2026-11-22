package com.shilapi.xcertplay

import android.content.Context
import android.provider.Settings
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.hud.BydOutputSettings
import com.shilapi.xcertplay.network.WirelessStartupFailure
import com.shilapi.xcertplay.orchestration.CarPlayStatus
import com.shilapi.xcertplay.orchestration.ConnectionFailureCause
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** #1180: with "Video while parked" on and the gear unreadable, Home and the report say why video stays off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en")
class VideoGearProblemTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("diplay_last_problem", Context.MODE_PRIVATE)
    private val off = "Videos can’t play on the car screen: DiPlay can’t read the gear."
    private val stamp = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} [+-]\\d{4}"

    @Before fun setUp() {
        prefs.edit().clear().commit()
        BydOutputSettings.setVideoWhileParked(context, true)
    }

    @After fun tearDown() {
        prefs.edit().clear().commit()
        BydOutputSettings.setVideoWhileParked(context, false)
        ReflectionHelpers.setField(CarPlayVideo, "appContext", null)
    }

    private fun notPaired() = LastConnectionProblem.follow(context, CarPlayStatus.Failed("failed",
        startupFailure = WirelessStartupFailure.HOTSPOT_CONFIGURATION, knownCause = ConnectionFailureCause.CHOSEN_IPHONE_NOT_PAIRED))

    private fun notApproved() = LastConnectionProblem.videoGearUnreadable(context, LocalAdb.Access.NOT_APPROVED, replied = false)

    @Test fun theLineSaysVideosCannotPlayNotThatTheSettingIsOff() {
        assertEquals(off, context.getString(R.string.video_off_gear_unreadable))
        assertTrue(context.getString(R.string.video_while_parked) !in off)
    }

    @Test fun duringASessionHomeShowsItAloneAndOtherwiseWithTheTimeItWasRecorded() {
        notApproved()

        val hint = context.getString(R.string.adb_not_approved)
        assertEquals("$off $hint", LastConnectionProblem.homeLine(context, sessionActive = true))
        val idle = LastConnectionProblem.homeLine(context, sessionActive = false)!!
        assertTrue(idle, idle.matches(Regex("Last CarPlay connection \\([^()]*\\d{1,2}:\\d{2}[^()]*\\): " + Regex.escape("$off $hint"))))
        val report = LastConnectionProblem.report(context).lines()
        assertEquals("Last problem: none recorded", report[0])
        assertTrue(report[1], report[1].matches(Regex(
            Regex.escape("Video in car problem: $off $hint (video:gear:NOT_APPROVED) at ") + stamp)))
    }

    @Test fun eachReasonGivesItsOwnHint() {
        fun hint(adb: LocalAdb.Access?, replied: Boolean): String? {
            LastConnectionProblem.videoGearUnreadable(context, adb, replied)
            return LastConnectionProblem.homeLine(context, sessionActive = true)?.removePrefix("$off ")
        }
        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
        assertEquals(context.getString(R.string.adb_off), hint(LocalAdb.Access.UNREACHABLE, false))
        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 1)
        assertEquals(context.getString(R.string.adb_network_not_answering), hint(LocalAdb.Access.UNREACHABLE, false))
        assertEquals(context.getString(R.string.adb_pairing_only), hint(LocalAdb.Access.UNSUPPORTED, false))
        assertEquals(context.getString(R.string.adb_vehicle_gear_unreadable), hint(LocalAdb.Access.READY, true))
        assertEquals(context.getString(R.string.adb_not_checked), hint(null, false))
        assertEquals("video:gear:NOT_CHECKED", prefs.getString("video", null))
    }

    @Test fun aGearReadWithNoReplyDoesNotTellTheDriverTheirCheckFailed() {
        LastConnectionProblem.videoGearUnreadable(context, LocalAdb.Access.READY, replied = false)
        assertEquals("$off The head unit’s ADB did not answer the gear read.",
            LastConnectionProblem.homeLine(context, sessionActive = true))
    }

    @Test fun theUnreachableHintIsWhatWasTrueWhenRecordedNotAtExport() {
        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 1)
        LastConnectionProblem.videoGearUnreadable(context, LocalAdb.Access.UNREACHABLE, replied = false)
        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)

        val report = LastConnectionProblem.report(context)
        assertTrue(report, report.contains(context.getString(R.string.adb_network_not_answering) +
            " (video:gear:ADB_NETWORK_NOT_ANSWERING)"))
    }

    @Test fun aVideoRecordDoesNotReplaceAConnectionFailure() {
        notPaired()
        notApproved()

        assertEquals(context.getString(R.string.chosen_iphone_not_paired),
            LastConnectionProblem.load(context)?.problem?.line()?.let(context::getString))
        val report = LastConnectionProblem.report(context).lines()
        assertTrue(report[0], report[0].startsWith("Last problem: ${context.getString(R.string.chosen_iphone_not_paired)} " +
            "(cause:CHOSEN_IPHONE_NOT_PAIRED) at "))
        assertTrue(report[1], report[1].startsWith("Video in car problem: $off"))
        val home = LastConnectionProblem.homeLine(context)!!.lines()
        assertEquals(2, home.size)
        assertTrue(home[0], home[0].startsWith("Last connection attempt ("))
        assertTrue(home[1], home[1].startsWith("Last CarPlay connection ("))
        // While CarPlay runs only the running session's problem shows.
        assertEquals("$off ${context.getString(R.string.adb_not_approved)}",
            LastConnectionProblem.homeLine(context, sessionActive = true))
    }

    @Test fun aConnectionFailureOrClearDoesNotReplaceAVideoRecord() {
        notApproved()
        notPaired()
        assertEquals(VideoGearProblem.NOT_APPROVED, LastConnectionProblem.loadVideo(context)?.problem)

        LastConnectionProblem.follow(context, CarPlayStatus.IphoneNotListed(nonHubDeviceListed = false))
        assertEquals(VideoGearProblem.NOT_APPROVED, LastConnectionProblem.loadVideo(context)?.problem)

        // A failure with no known cause, and an active session, clear only the connection record.
        LastConnectionProblem.follow(context, CarPlayStatus.Failed("Wireless iAP2 tunnel closed"))
        LastConnectionProblem.clear(context)
        assertNull(LastConnectionProblem.load(context))
        assertEquals(VideoGearProblem.NOT_APPROVED, LastConnectionProblem.loadVideo(context)?.problem)
    }

    @Test fun aReadableGearDropsOnlyTheVideoRecord() {
        notPaired()
        notApproved()
        LastConnectionProblem.clearVideoGear(context)
        assertNull(LastConnectionProblem.loadVideo(context))
        assertEquals(context.getString(R.string.chosen_iphone_not_paired),
            LastConnectionProblem.load(context)?.problem?.line()?.let(context::getString))
    }

    @Test fun aRecordFoundWhileTheSettingIsOffIsForgottenSoTurningItOnDoesNotBringItBack() {
        notApproved()
        BydOutputSettings.setVideoWhileParked(context, false)
        assertNull(LastConnectionProblem.homeLine(context, sessionActive = true))
        assertNull(LastConnectionProblem.report(context).lines().getOrNull(1))

        BydOutputSettings.setVideoWhileParked(context, true)
        assertNull(LastConnectionProblem.homeLine(context, sessionActive = true))
        assertNull(prefs.getString("video", null))
    }

    @Test fun theSameReasonIsWrittenOnceSoItsTimeIsWhenItStarted() {
        notApproved()
        prefs.edit().putLong("video_at", 1_000L).commit()
        notApproved()
        assertEquals(1_000L, LastConnectionProblem.loadVideo(context)?.atMillis)
    }

    @Test fun aVideoValueThisVersionCannotReadIsRemoved() {
        for (token in listOf("video:gear:SOMETHING_NEW", "video:other", "video:", "cause:CHOSEN_IPHONE_NOT_PAIRED")) {
            prefs.edit().putString("video", token).putLong("video_at", 1_000L).commit()
            assertNull(token, LastConnectionProblem.loadVideo(context))
            assertNull(token, prefs.getString("video", null))
        }
        // A video value in the connection slot is not a connection problem either.
        prefs.edit().putString("problem", "video:gear:NOT_APPROVED").putLong("at", 1_000L).commit()
        assertNull(LastConnectionProblem.load(context))
    }

    @Test fun theVideoHostRecordsAndClearsItFromTheGate() {
        notPaired()
        ReflectionHelpers.setField(CarPlayVideo, "appContext", context)
        CarPlayVideo.onGearObserved(null) // no read has run here, so the ADB state is unknown
        assertEquals("video:gear:NOT_CHECKED", prefs.getString("video", null))
        CarPlayVideo.onGearObserved(false)
        assertNull(LastConnectionProblem.loadVideo(context))
        assertEquals(context.getString(R.string.chosen_iphone_not_paired),
            LastConnectionProblem.load(context)?.problem?.line()?.let(context::getString))
    }
}
