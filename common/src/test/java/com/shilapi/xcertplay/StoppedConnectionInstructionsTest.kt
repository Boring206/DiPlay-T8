package com.shilapi.xcertplay

import android.os.Handler
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.network.WirelessStartupFailure
import com.shilapi.xcertplay.orchestration.CarPlayStatus
import com.shilapi.xcertplay.transport.BluetoothReadiness
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter.from

/** What the connecting screen says under the cause once an attempt has stopped. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en", manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class StoppedConnectionInstructionsTest {
    private lateinit var activity: CarPlayHostActivity
    private lateinit var instructions: TextView
    private lateinit var retry: Button

    @Before fun setup() {
        CarPlayBackgroundSession.clear()
        activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        ReflectionHelpers.setField(CarPlayBackgroundSession, "owner", activity)
        retry = Button(activity).apply { visibility = View.GONE }
        ReflectionHelpers.setField(activity, "startupRetryButton", retry)
        instructions = TextView(activity)
        ReflectionHelpers.setField(activity, "instructionsView", instructions)
        ReflectionHelpers.setField(activity, "wirelessEnabled", true)
    }

    @After fun cleanup() {
        ReflectionHelpers.getField<Handler>(activity, "mainHandler").removeCallbacksAndMessages(null)
        CarPlayBackgroundSession.clear()
    }

    private fun report(status: CarPlayStatus, generation: Int = 0) {
        ReflectionHelpers.callInstanceMethod<(CarPlayStatus) -> Unit>(activity, "createStatusReporter",
            from(Int::class.javaPrimitiveType, generation))(status)
    }
    private fun bluetooth(message: String) =
        CarPlayStatus.Failed(message, startupFailure = WirelessStartupFailure.BLUETOOTH_NOT_READY)
    private fun shown() = instructions.text.toString().takeIf { instructions.visibility == View.VISIBLE }
    private val keepPhoneNear get() = activity.getString(R.string.keep_your_iphone_nearby_with_bluetooth_and_wi_fi_on_allow)

    @Test fun aLimitOfTheUnitReplacesTheRequestToKeepThePhoneNear() {
        report(bluetooth("Bluetooth adapter is unavailable"))
        assertEquals(activity.getString(R.string.stopped_hardware_limit), shown())
        // "Trying again will not help" above a button called Retry would be two answers at once.
        assertEquals(activity.getString(R.string.connect_anyway), retry.text.toString())
        assertEquals(View.VISIBLE, retry.visibility)
    }

    @Test fun thePanelStartsByAskingForThePhone() {
        val build = CarPlayHostActivity::class.java.getDeclaredMethod("buildContentView").apply { isAccessible = true }
        build.invoke(activity)
        val line = ReflectionHelpers.getField<TextView>(activity, "instructionsView")
        assertNotSame(instructions, line)
        assertEquals(View.VISIBLE, line.visibility)
        assertEquals(keepPhoneNear, line.text.toString())
    }

    @Test fun aStoppedAttemptIsMarkedForTheHomeScreen() {
        assertFalse(CarPlayBackgroundSession.stopped)
        report(bluetooth("Bluetooth is not enabled"))
        assertTrue(CarPlayBackgroundSession.stopped)
        CarPlayBackgroundSession.clear()
        assertFalse(CarPlayBackgroundSession.stopped)
    }

    // This build's wired path has a known fault below Android 9, so the cable is not offered there.
    @Config(sdk = [27])
    @Test fun onAndroid8TheLimitDoesNotSendAnyoneToThisBuildsCable() {
        report(bluetooth("Bluetooth adapter is unavailable"))
        assertEquals(activity.getString(R.string.stopped_hardware_limit_android8, activity.getString(R.string.project_page)), shown())
    }

    @Test fun unrealBluetoothIsRememberedUntilThePhoneAnswers() {
        assertFalse(DiPlayPreferences.bluetoothUnreal(activity))
        report(bluetooth("Android Bluetooth on this head unit reports ${BluetoothReadiness.UNREAL_MARK}: " +
            "a connection to a service no phone offers also succeeded in 6ms"))
        assertTrue(DiPlayPreferences.bluetoothUnreal(activity))
        assertEquals(activity.getString(R.string.stopped_hardware_limit), shown())

        report(CarPlayStatus.WirelessActive)
        assertFalse(DiPlayPreferences.bluetoothUnreal(activity))
    }

    @Test fun anOrdinaryFailureLeavesTheBluetoothFindingAlone() {
        report(bluetooth("Bluetooth is not enabled"))
        assertFalse(DiPlayPreferences.bluetoothUnreal(activity))
    }

    @Test fun aStopThatNamesItsOwnNextStepShowsNoSecondInstruction() {
        report(bluetooth("Bluetooth is not enabled"))
        assertNull(shown())
        assertEquals(activity.getString(R.string.open_bluetooth_settings), retry.text.toString())
    }

    @Test fun exhaustedRetriesStillAskForThePhone() {
        val timeout = CarPlayStatus.Failed("timeout", startupFailure = WirelessStartupFailure.FIRST_TCP_TIMEOUT)
        repeat(6) { generation ->
            ReflectionHelpers.setField(activity, "restartGeneration", generation)
            ReflectionHelpers.setField(activity, "reconnectScheduled", false)
            report(timeout, generation)
        }
        assertTrue(ReflectionHelpers.getField(activity, "startupRetryStopped"))
        assertEquals(keepPhoneNear, shown())
        assertEquals(activity.getString(R.string.retry_carplay_connection), retry.text.toString())
    }
}
