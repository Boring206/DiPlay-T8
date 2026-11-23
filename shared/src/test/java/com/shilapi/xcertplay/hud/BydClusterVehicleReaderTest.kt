package com.shilapi.xcertplay.hud

import org.junit.Assert.*
import org.junit.Test

class BydClusterVehicleReaderTest {
    @Test fun decodesActualCanfdRepliesWithoutRequiringRemainingEnergy() {
        val calls = mutableListOf<String>()
        val result = BydClusterVehicleReader.readBattery { command ->
            calls += command
            when (command) {
                "getprop ro.car.protocol" -> "\n"
                "getprop sys.car.protocol" -> "CANFD\n"
                "service call autoservice 7 i32 1014 i32 1246777400" -> "Result: Parcel(00000000 421c0000 '.......B')"
                "service call autoservice 5 i32 1014 i32 1246765118" -> "Result: Parcel(00000000 0000000f '........')"
                else -> error("Unexpected call: $command")
            }
        }
        assertEquals(39.0, result.first!!, 0.0)
        assertEquals(15, result.second)
        assertTrue(calls.none { it.contains("1005") })
    }

    @Test fun missingRangeDoesNotEraseValidChargeAndUnknownProtocolDoesNotProbeAddresses() {
        val result = BydClusterVehicleReader.readBattery { command ->
            when {
                command.startsWith("getprop") -> "CAN"
                command.contains("1033543720") -> "Result: Parcel(00000000 421c0000 '.......B')"
                else -> "Result: Parcel(00000000 ffffd8e5 '........')"
            }
        }
        assertEquals(39.0, result.first!!, 0.0)
        assertNull(result.second)
        assertEquals(null to null, BydClusterVehicleReader.readBattery { command ->
            check(command.startsWith("getprop")); "UNKNOWN"
        })
    }

    @Test fun errorSentinelsAndNanAreNotVehicleReadings() {
        for (bits in listOf("bf800000", "7fc00000", "7f800000")) {
            val reading = BydClusterVehicleReader.readBattery { command ->
                when {
                    command.startsWith("getprop") -> "CANFD"
                    command.contains("1246777400") -> "Result: Parcel(00000000 $bits '........')"
                    else -> null
                }
            }
            assertEquals(null to null, reading)
        }
    }

    @Test fun EachReadExpiresIndependentlyAndFutureTimestampsAreRejected() {
        val reading = ClusterVehicleReading("P",39.0,15,gearAt=10_000,batteryAt=1_000)
        assertEquals("P", reading.fresh(12_000).gear)
        assertEquals(39.0, reading.fresh(12_000).batteryPercent!!, 0.0)
        assertNull(reading.fresh(13_001).gear)
        assertNull(reading.fresh(16_001).batteryPercent)
        assertNull(reading.fresh(16_001).rangeKm)
        assertNull(reading.fresh(0).gear)
    }
}
