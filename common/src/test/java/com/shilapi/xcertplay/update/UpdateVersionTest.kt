package com.shilapi.xcertplay.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {
    @Test
    fun newerTagIsAnUpdate() {
        assertTrue(UpdateVersion.isNewer("v0.2.15", "0.2.14"))
    }

    @Test
    fun sameVersionIsNotAnUpdate() {
        assertFalse(UpdateVersion.isNewer("0.2.14", "0.2.14"))
    }

    @Test
    fun olderTagIsIgnored() {
        assertFalse(UpdateVersion.isNewer("v0.2.13", "0.2.14"))
    }

    @Test
    fun numericFieldsCompareLeftToRight() {
        assertTrue(UpdateVersion.isNewer("0.3.0", "0.2.14"))
        assertFalse(UpdateVersion.isNewer("0.2.9", "0.2.14"))
    }

    @Test
    fun preReleaseSuffixStillComparesByNumbers() {
        assertTrue(UpdateVersion.isNewer("0.2.15-beta.1", "0.2.14"))
        assertFalse(UpdateVersion.isNewer("0.2.14-rc.1", "0.2.14"))
    }

    @Test
    fun unparseableRemoteVersionIsNeverAnUpdate() {
        assertFalse(UpdateVersion.isNewer("public-preview", "0.2.14"))
    }

    @Test
    fun unparseableInstalledVersionAcceptsAnyParseableRemote() {
        assertTrue(UpdateVersion.isNewer("0.2.15", "psa.2"))
        assertFalse(UpdateVersion.isNewer("public-preview", "psa.2"))
    }

@Test
    fun overflowingNumericFieldsAreTreatedAsUnparseable() {
        assertNull(UpdateVersion.parse("v99999999999999999999.0.0"))
        assertFalse(UpdateVersion.isNewer("v99999999999999999999.0.0", "0.2.22"))
        assertFalse(UpdateVersion.isNewer("v0.99999999999999999999.0", "0.2.22"))
        assertFalse(UpdateVersion.isNewer("v0.2.99999999999999999999", "0.2.22"))
        assertTrue(UpdateVersion.isNewer("v0.2.23", "0.2.22"))
    }
}
