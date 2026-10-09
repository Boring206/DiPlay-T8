package com.shilapi.xcertplay

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** The home screen of a 1024x600 head unit: what a stranger sees right after installing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "en-w1024dp-h552dp-land-mdpi")
class HomeHeadUnitVerdictTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private var controller: ActivityController<DiPlayActivity>? = null
    private val activity get() = requireNotNull(controller).get()
    private val phoneAddress = "AA:BB:CC:DD:EE:FF"

    @Before fun setUp() {
        // Robolectric lists no codecs; a real head unit has an H.264 decoder, and without one every
        // screen here would be the same "cannot" whatever the code under test does.
        ReflectionHelpers.setField(HeadUnitCheck, "avcDecoderFound", true)
    }

    @After fun tearDown() {
        controller?.let { ReflectionHelpers.getField<Handler>(it.get(), "handler").removeCallbacksAndMessages(null) }
        CarPlayBackgroundSession.clear()
        controller?.pause()?.stop()?.destroy()
        ReflectionHelpers.setField(HeadUnitCheck, "avcDecoderFound", null)
    }

    /** Opens the home page as on a unit where the CarPlay identity is installed. */
    private fun open() {
        controller = Robolectric.buildActivity(DiPlayActivity::class.java).create()
        setupError(null)
    }
    private fun setupError(message: String?) {
        ReflectionHelpers.setField(activity, "setupError", message)
        render()
    }
    private fun render() = ReflectionHelpers.callInstanceMethod<Unit>(activity, "render")
    private fun views(view: View = activity.findViewById(android.R.id.content)): List<View> =
        listOf(view) + ((view as? ViewGroup)?.let { group -> (0 until group.childCount).flatMap { views(group.getChildAt(it)) } }
            ?: emptyList())
    private fun texts() = views().filterIsInstance<TextView>().map { it.text.toString() }
    private fun buttons() = views().filterIsInstance<Button>().map { it.text.toString() }
    private fun button(title: Int) = views().filterIsInstance<Button>().single { it.text.toString() == text(title) }
    private fun filled(button: Button) = button.currentTextColor == Color.rgb(12, 17, 27)
    private fun text(id: Int, vararg arguments: Any) = activity.getString(id, *arguments)
    private val verdictTitles get() = listOf(R.string.verdict_cannot_title, R.string.verdict_action_title,
        R.string.verdict_ready_title, R.string.verdict_worked_title).map { text(it) }
    private fun verdictShown() = texts().filter { it in verdictTitles }

    private fun pairIphone(bondState: Int = BluetoothDevice.BOND_BONDED, address: String = phoneAddress) {
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter
        shadowOf(adapter).setEnabled(true)
        val phone = adapter.getRemoteDevice(address)
        shadowOf(phone).setName("iPhone")
        shadowOf(phone).setBondState(bondState)
        shadowOf(adapter).setBondedDevices(setOf(phone))
    }
    private fun choosePhone(address: String = phoneAddress) = DiPlayPreferences.savePhone(context, address, "iPhone")

    @Test fun passingChecksAreReadyToTryAndNothingMore() {
        pairIphone(); choosePhone()
        open()

        assertEquals(listOf(text(R.string.verdict_ready_title)), verdictShown())
        assertTrue(text(R.string.connect_phone) in buttons())
        assertTrue(filled(button(R.string.connect_phone)))
        assertTrue(texts().any { it.startsWith(text(R.string.status_ready_for_prefix)) })
    }

    @Test fun onlyAWirelessSessionThatRanMakesItWork() {
        pairIphone(); choosePhone()
        DiPlayPreferences.markCarPlayWorked(context) // a cable session proves nothing about Bluetooth
        open()
        assertEquals(listOf(text(R.string.verdict_ready_title)), verdictShown())

        DiPlayPreferences.markWirelessWorked(context)
        render()
        assertEquals(listOf(text(R.string.verdict_worked_title)), verdictShown())
    }

    @Test fun aSessionFromBeforeThisVersionCountsToo() {
        assertFalse(DiPlayPreferences.wirelessWorked(context))
        // Earlier builds saved this, and only at the first picture of a wireless session.
        context.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().putBoolean("hotspot_prefers_ipv4", true).commit()
        assertTrue(DiPlayPreferences.wirelessWorked(context))
    }

    @Test fun aRememberedBluetoothFindingRulesWirelessOut() {
        pairIphone(); choosePhone()
        open()
        assertFalse(text(R.string.verdict_cannot_title) in texts())

        DiPlayPreferences.saveBluetoothUnreal(context, true)
        render()

        val shown = texts()
        assertEquals(listOf(text(R.string.verdict_cannot_title)), verdictShown())
        assertTrue(text(R.string.check_bt_unreal) in shown)
        // The verdict comes before the finding it sums up, and nothing still vouches for Bluetooth.
        assertTrue(shown.indexOf(text(R.string.verdict_cannot_title)) < shown.indexOf(text(R.string.check_bt_unreal)))
        assertFalse(text(R.string.check_bt_on) in shown)
        assertFalse(text(R.string.check_phone_ok, "iPhone") in shown)
        // Connecting stays possible, as a way to test again, without being offered as the next step.
        assertTrue(text(R.string.status_wireless_ruled_out) in shown)
        assertFalse(shown.any { it.startsWith(text(R.string.status_ready_for_prefix)) })
        assertFalse(text(R.string.connect_phone) in buttons())
        assertFalse(filled(button(R.string.connect_anyway)))
    }

    @Config(sdk = [29]) // from Android 10 the car's own hotspot is the default, and none is saved here
    @Test fun besideWhatNoSettingChangesNothingAsksForASetting() {
        pairIphone(); choosePhone()
        open()
        assertTrue(text(R.string.check_hotspot_not_saved) in texts())
        assertEquals(listOf(text(R.string.verdict_action_title)), verdictShown())

        DiPlayPreferences.saveBluetoothUnreal(context, true)
        render()
        assertFalse(text(R.string.check_hotspot_not_saved) in texts())
        assertFalse(text(R.string.open_connection_setup) in buttons())
    }

    @Test fun whereTheCableIsSoundItBecomesTheWayIn() {
        DiPlayPreferences.saveBluetoothUnreal(context, true)
        open()

        assertTrue(text(R.string.verdict_cannot_body) in texts())
        assertTrue(filled(button(R.string.connect_with_usb)))
    }

    // This build's wired path has a known fault below Android 9.
    @Config(sdk = [27])
    @Test fun onAndroid8NobodyIsSentToThisBuildsCable() {
        DiPlayPreferences.saveBluetoothUnreal(context, true)
        open()

        assertTrue(text(R.string.verdict_cannot_body_android8, text(R.string.project_page)) in texts())
        assertFalse(text(R.string.verdict_cannot_body) in texts())
        assertFalse(filled(button(R.string.connect_with_usb)))
    }

    @Test fun aFindingSavedWhileHomeIsInFrontShowsUp() {
        pairIphone(); choosePhone()
        open()
        assertEquals(listOf(text(R.string.verdict_ready_title)), verdictShown())

        DiPlayPreferences.saveBluetoothUnreal(context, true) // the connecting screen does this, behind this page
        ReflectionHelpers.getField<Runnable>(activity, "tick").run()

        assertEquals(listOf(text(R.string.verdict_cannot_title)), verdictShown())
    }

    @Test fun anAttemptThatStoppedIsNotCalledConnecting() {
        pairIphone(); choosePhone()
        open()
        ReflectionHelpers.setField(CarPlayBackgroundSession, "stopAction", { done: () -> Unit -> done() })
        render()
        assertTrue(text(R.string.open_carplay) in buttons())
        assertTrue(text(R.string.connecting_to_your_iphone) in texts())

        CarPlayBackgroundSession.stopped = true
        render()
        assertFalse(text(R.string.connecting_to_your_iphone) in texts())
        assertTrue(text(R.string.connect_phone) in buttons())
        // The session it still holds can be let go.
        assertEquals(View.VISIBLE, button(R.string.disconnect).visibility)
    }

    @Test fun aLiveSessionIsTheFilledButtonEvenOnARuledOutUnit() {
        DiPlayPreferences.saveBluetoothUnreal(context, true)
        open()
        assertTrue(filled(button(R.string.connect_with_usb)))

        ReflectionHelpers.setField(CarPlayBackgroundSession, "stopAction", { done: () -> Unit -> done() })
        render()
        // "Connect with USB" would end the session that is running.
        assertTrue(filled(button(R.string.open_carplay)))
        assertFalse(filled(button(R.string.connect_with_usb)))
    }

    @Test fun aBondThatNeverFinishedIsFlaggedBeforeAnyConnection() {
        pairIphone(BluetoothDevice.BOND_BONDING)
        open()

        assertTrue(text(R.string.check_phone_bond_incomplete, "iPhone") in texts())
        assertFalse(text(R.string.check_phone_ok, "iPhone") in texts())
        assertTrue(text(R.string.open_bluetooth) in buttons())
        assertEquals(listOf(text(R.string.verdict_action_title)), verdictShown())
    }

    @Test fun anotherPairedIphoneDoesNotStandInForTheChosenOne() {
        pairIphone(address = "11:22:33:44:55:66")
        choosePhone(phoneAddress)
        open()

        assertTrue(text(R.string.check_phone_selected_gone, "iPhone") in texts())
        assertFalse(text(R.string.check_phone_ok, "iPhone") in texts())
        assertEquals(listOf(text(R.string.verdict_action_title)), verdictShown())
    }

    @Test fun theCardNamesTheUnitByItsRealApiLevelRightUnderTheVerdict() {
        pairIphone(); choosePhone()
        open()

        val shown = texts()
        assertTrue("API 28" in HeadUnitCheck.deviceLine())
        assertEquals(shown.indexOf(text(R.string.verdict_ready_body)) + 1, shown.indexOf(HeadUnitCheck.deviceLine()))
    }

    @Test fun whatStopsEverythingComesFirstAndNoVerdictContradictsIt() {
        pairIphone(); choosePhone()
        open()
        setupError("no identity")

        val shown = texts()
        assertEquals(1, shown.count { it == "no identity" })
        assertTrue(shown.indexOf("no identity") < shown.indexOf(text(R.string.wireless_carplay)))
        // "Ready to try" beside a disabled Connect would send the reader in a circle.
        assertEquals(emptyList<String>(), verdictShown())
        assertFalse(button(R.string.connect_phone).isEnabled)
    }

    @Test fun homeSaysWhichAppThisIsAndOffersEveryWay() {
        open()

        assertTrue(activity.applicationInfo.loadLabel(activity.packageManager).toString() in texts())
        assertTrue(text(R.string.project_page) in texts())
        val wanted = listOf(R.string.settings, R.string.connect_phone, R.string.choose_iphone, R.string.connect_with_usb)
            .map { text(it) }
        assertTrue("missing from ${buttons()}", buttons().containsAll(wanted))
        // The slogan that used to push the connect button below the fold is gone.
        assertFalse(text(R.string.a_familiar_drive) in texts())
    }

    private fun launchWithConnectWhenOpened(wireless: Boolean): android.content.Intent? {
        choosePhone()
        DiPlayPreferences.saveAutoConnect(context, true)
        AirPlayPersistence.saveWirelessEnabled(context, wireless)
        DiPlayPreferences.saveBluetoothUnreal(context, true)
        open()
        requireNotNull(controller).start().resume()
        shadowOf(Looper.getMainLooper()).idle()
        return generateSequence { shadowOf(activity).nextStartedActivity }
            .firstOrNull { it.component?.className == CarPlayHostActivity::class.java.name }
    }

    // Every wireless attempt restarts the hotspot before Bluetooth can fail again.
    @Test fun aRuledOutUnitDoesNotConnectWirelesslyByItself() {
        assertNull(launchWithConnectWhenOpened(wireless = true))
    }

    @Test fun theCableIsNotHeldBackByABluetoothFinding() {
        assertNotNull(launchWithConnectWhenOpened(wireless = false))
    }
}
