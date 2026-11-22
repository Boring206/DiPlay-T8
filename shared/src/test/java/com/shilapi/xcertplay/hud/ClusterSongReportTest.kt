package com.shilapi.xcertplay.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** #1110: the report had no dashboard song settings, gate, ADB access or write result. */
class ClusterSongReportTest {
    @Test fun theLineCarriesSettingsGateAccessAndTheLastWrite() {
        assertEquals(
            "Dashboard song: clusterSong=true clusterSongGate=true clusterSongOnChange=false; " +
                "since app start: gate=opened, back to CarPlay adbAccess=READY lastWrite=rejected (source=0 state=0 text=-1) 12s ago " +
                "writes accepted=3 rejected=1 unanswered=0",
            ClusterSongReport.line(clusterSong = true, clusterSongGate = true, clusterSongOnChange = false,
                gate = "opened, back to CarPlay", adbAccess = "READY",
                lastWrite = "rejected (source=0 state=0 text=-1)", lastWriteAgeSeconds = 12,
                accepted = 3, rejected = 1, unanswered = 0),
        )
        assertEquals(
            "Dashboard song: clusterSong=false clusterSongGate=false clusterSongOnChange=false; " +
                "since app start: gate=off adbAccess=not tried lastWrite=none writes accepted=0 rejected=0 unanswered=0",
            ClusterSongReport.line(false, false, false, "not tried", null, "none", null, 0, 0, 0),
        )
    }

    @Test fun aRejectedWriteKeepsNamesNumbersAndErrorTypesButNoSongText() {
        assertEquals("source=0 state=0 text=-1",
            ClusterSongWriteResult.summary("source=0\nstate=0\ntext=-1\n"))
        assertEquals("write=ERR ClassNotFoundException",
            ClusterSongWriteResult.summary("write=ERR java.lang.ClassNotFoundException: android.hardware.bydauto.Foo"))
        assertEquals("source=0 state=0 text=ERR",
            ClusterSongWriteResult.summary("source=0\nstate=0\ntext=ERR too long"))
        assertEquals("no result lines", ClusterSongWriteResult.summary("Killed"))
        val leaked = ClusterSongWriteResult.summary("Bohemian Rhapsody=Queen\nsource=0")
        assertEquals("other=ERR source=0", leaked)
        assertFalse(leaked.contains("Queen") || leaked.contains("Bohemian"))
    }
}
