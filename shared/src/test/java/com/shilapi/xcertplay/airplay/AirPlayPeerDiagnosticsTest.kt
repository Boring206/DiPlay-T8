package com.shilapi.xcertplay.airplay

import org.junit.Assert.*
import org.junit.Test

class AirPlayPeerDiagnosticsTest {
    @Test fun productAndVersionDetailsAreAvailableWithoutIdentity() {
        assertEquals("Phone display peer model=iPhone11,2 ios=26.1 build=23B85 airplay=950.7.1",
            AirPlayPeerDiagnostics.summary("iPhone11,2", "26.1", "950.7.1", "23B85"))
    }

    // #1196: iPhones before iOS 26 send osBuildVersion only, so ios= alone read not_reported.
    @Test fun anOlderIphoneStillShowsItsIosBuild() {
        assertEquals("Phone display peer model=iPhone11,2 ios=not_reported build=22G86 airplay=870.14.1",
            AirPlayPeerDiagnostics.summary("iPhone11,2", "", "870.14.1", "22G86"))
    }

    @Test fun absentOrUnexpectedFieldsCannotLeakPersonalOrMultilineValues() {
        val summary = AirPlayPeerDiagnostics.summary("Jane's iPhone", "26.0\npassword=secret", "", "Jane's build")
        assertEquals("Phone display peer model=not_reported ios=not_reported build=not_reported airplay=not_reported", summary)
    }
}
