package com.shilapi.xcertplay

import com.shilapi.xcertplay.HeadUnitCheck.Item
import com.shilapi.xcertplay.HeadUnitCheck.Level
import com.shilapi.xcertplay.HeadUnitCheck.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

class HeadUnitVerdictTest {
    private fun item(level: Level, permanent: Boolean = false) = Item(level, "finding", permanent = permanent)
    private val passing = listOf(item(Level.OK), item(Level.INFO))

    @Test fun whatNoSettingChangesOutranksEverythingElse() {
        val items = passing + item(Level.BLOCKED) + item(Level.BLOCKED, permanent = true)
        assertEquals(Verdict.CANNOT, HeadUnitCheck.verdict(items, wirelessWorked = false))
        // A wired session, or a module swapped since, must not turn the finding into a promise.
        assertEquals(Verdict.CANNOT, HeadUnitCheck.verdict(items, wirelessWorked = true))
    }

    @Test fun anythingLeftToFixComesBeforeReady() {
        assertEquals(Verdict.NEEDS_ACTION, HeadUnitCheck.verdict(passing + item(Level.BLOCKED), wirelessWorked = false))
        assertEquals(Verdict.NEEDS_ACTION, HeadUnitCheck.verdict(passing + item(Level.WARNING), wirelessWorked = false))
        assertEquals(Verdict.NEEDS_ACTION, HeadUnitCheck.verdict(passing + item(Level.WARNING), wirelessWorked = true))
    }

    @Test fun besideWhatNoSettingChangesOnlyWhatNeedsNothingDoneRemains() {
        val permanent = item(Level.BLOCKED, permanent = true)
        val fixable = Item(Level.BLOCKED, "turn something off, then connect", HeadUnitCheck.Action.HOTSPOT_SETTINGS)
        assertEquals(passing + permanent, HeadUnitCheck.decisive(passing + fixable + item(Level.WARNING) + permanent))
        // Without such a finding the list is left as it is.
        assertEquals(passing + fixable, HeadUnitCheck.decisive(passing + fixable))
    }

    @Test fun passingChecksPromiseNothingUntilAWirelessSessionRan() {
        assertEquals(Verdict.READY, HeadUnitCheck.verdict(passing, wirelessWorked = false))
        assertEquals(Verdict.WORKED, HeadUnitCheck.verdict(passing, wirelessWorked = true))
    }
}
