package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.transport.LockdownPairRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Each iPhone keeps its own wired pair record, so two drivers never make each other tap Trust again. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class LockdownRecordPersistenceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)

    @Before fun clearPreferences() {
        prefs.edit().clear().apply()
    }

    @Test fun eachIphoneLoadsOnlyItsOwnRecord() {
        save(record("a"))
        save(record("b"))
        assertEquals("HOST-a", load("a")?.hostId)
        assertEquals("HOST-b", load("b")?.hostId)
        assertNull(load("c"))
    }

    @Test fun clearingOneIphoneLeavesTheOther() {
        save(record("a"))
        save(record("b"))
        AirPlayPersistence.clearLockdownRecord(context, deviceKey("a"))
        assertNull(load("a"))
        assertEquals("HOST-b", load("b")?.hostId)
    }

    @Test fun clearingAnUnknownIphoneChangesNothing() {
        save(record("a"))
        AirPlayPersistence.clearLockdownRecord(context, deviceKey("never-paired"))
        assertEquals("HOST-a", load("a")?.hostId)
    }

    @Test fun savingAnIphoneAgainReplacesItsRecord() {
        save(record("a", hostId = "FIRST"))
        save(record("a", hostId = "SECOND"))
        assertEquals("SECOND", load("a")?.hostId)
        assertTrue(prefs.all.values.none { it == "FIRST" })
    }

    @Test fun legacySingleRecordKeepsLoadingForItsIphone() {
        writeLegacy(record("a"))
        assertEquals("HOST-a", load("a")?.hostId)
        assertNull(load("b"))
        assertTrue(LEGACY_KEYS.none(prefs::contains))
        assertEquals("HOST-a", load("a")?.hostId)
    }

    @Test fun legacySingleRecordSurvivesAnotherIphonePairing() {
        writeLegacy(record("a"))
        save(record("b"))
        assertEquals("HOST-a", load("a")?.hostId)
        assertEquals("HOST-b", load("b")?.hostId)
        assertTrue(LEGACY_KEYS.none(prefs::contains))
    }

    @Test fun incompleteLegacyRecordIsDroppedWithoutLoading() {
        prefs.edit().putString("lockdown_host_id", "HOST-a").putString("lockdown_wifi_mac", "aa:bb").apply()
        assertNull(load("a"))
        assertTrue(LEGACY_KEYS.none(prefs::contains))
    }

    @Test fun ninthIphoneEvictsTheLeastRecentlySaved() {
        for (phone in 1..8) save(record("p$phone"))
        save(record("p1"))
        save(record("p9"))
        assertNull(load("p2"))
        assertTrue(prefs.all.values.none { it == "HOST-p2" })
        for (phone in listOf(1, 3, 4, 5, 6, 7, 8, 9)) assertNotNull("p$phone", load("p$phone"))
    }

    @Test fun savingTheSameIphoneTwiceCountsOnce() {
        save(record("a", hostId = "FIRST"))
        save(record("a", hostId = "SECOND"))
        for (phone in 1..7) save(record("p$phone"))
        assertEquals("SECOND", load("a")?.hostId)
    }

    private fun save(record: LockdownPairRecord) = AirPlayPersistence.saveLockdownRecord(context, record)

    private fun load(phone: String) = AirPlayPersistence.loadLockdownRecord(context, deviceKey(phone))

    private fun deviceKey(phone: String) =
        "-----BEGIN RSA PUBLIC KEY-----\n$phone\n-----END RSA PUBLIC KEY-----\n".toByteArray()

    private fun record(phone: String, hostId: String = "HOST-$phone") = LockdownPairRecord.restore(
        hostId = hostId,
        systemBuid = "BUID-$phone",
        wifiMacAddress = "aa:bb:cc:dd:ee:ff",
        devicePublicKeyPem = deviceKey(phone),
        deviceCertificatePem = "device-cert-$phone".toByteArray(),
        hostPrivateKeyPem = "host-private-$phone".toByteArray(),
        hostCertificatePem = "host-cert-$phone".toByteArray(),
        rootPrivateKeyPem = "root-private-$phone".toByteArray(),
        rootCertificatePem = "root-cert-$phone".toByteArray(),
    )

    /** The nine flat keys releases up to 0.2.22 wrote for their single record. */
    private fun writeLegacy(record: LockdownPairRecord) {
        prefs.edit()
            .putString("lockdown_host_id", record.hostId)
            .putString("lockdown_system_buid", record.systemBuid)
            .putString("lockdown_wifi_mac", record.wifiMacAddress)
            .putString("lockdown_device_public", record.devicePublicKeyPem.toHex())
            .putString("lockdown_device_cert", record.deviceCertificatePem.toHex())
            .putString("lockdown_host_private", record.hostPrivateKeyPem.toHex())
            .putString("lockdown_host_cert", record.hostCertificatePem.toHex())
            .putString("lockdown_root_private", record.rootPrivateKeyPem.toHex())
            .putString("lockdown_root_cert", record.rootCertificatePem.toHex())
            .apply()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        val LEGACY_KEYS = listOf(
            "lockdown_host_id", "lockdown_system_buid", "lockdown_wifi_mac", "lockdown_device_public",
            "lockdown_device_cert", "lockdown_host_private", "lockdown_host_cert", "lockdown_root_private",
            "lockdown_root_cert",
        )
    }
}
