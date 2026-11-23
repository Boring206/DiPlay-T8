package com.shilapi.xcertplay.airplay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The media HID device: Fast Forward and Rewind are declared only with the long-press seek setting (#1190). */
class AirPlayMediaHidTest {
    // The media descriptor of 0.2.22 and earlier, byte for byte.
    private val earlierDescriptor = intArrayOf(
        0x05, 0x0c, 0x09, 0x01, 0xa1, 0x01,
        0x15, 0x00, 0x25, 0x06, 0x05, 0x0c,
        0x0a, 0x00, 0x00, 0x0a, 0xb0, 0x00, 0x0a, 0xb1, 0x00, 0x0a, 0xcd, 0x00,
        0x0a, 0xb5, 0x00, 0x0a, 0xb6, 0x00, 0x0a, 0x9e, 0x02,
        0x75, 0x08, 0x95, 0x01, 0x81, 0x00, 0xc0,
    ).map { it.toByte() }.toByteArray()

    private val earlierUsages = listOf(0x00, 0xb0, 0xb1, 0xcd, 0xb5, 0xb6, 0x29e)

    @Test fun withoutSeekKeysTheDescriptorIsTheOneEveryEarlierVersionSent() {
        assertArrayEquals(earlierDescriptor, descriptor(AirPlayHid.mediaHidDevice("display")))
        assertArrayEquals(earlierDescriptor, descriptor(AirPlayHid.mediaHidDevice("display", seekKeys = false)))
        assertArrayEquals(earlierDescriptor, mediaDescriptor(config()))
    }

    @Test fun seekKeysAddFastForwardAndRewindAfterTheExistingPresses() {
        val field = mediaField(descriptor(AirPlayHid.mediaHidDevice("display", seekKeys = true)))
        assertEquals(earlierUsages + listOf(0xb3, 0xb4), field.usages)
        assertEquals(0xb3, field.usages[CarPlayMediaButton.FAST_FORWARD])
        assertEquals(0xb4, field.usages[CarPlayMediaButton.REWIND])
        // Every earlier press keeps its index.
        assertEquals(0xb0, field.usages[CarPlayMediaButton.PLAY])
        assertEquals(0xb1, field.usages[CarPlayMediaButton.PAUSE])
        assertEquals(0xcd, field.usages[CarPlayMediaButton.PLAY_PAUSE])
        assertEquals(0xb5, field.usages[CarPlayMediaButton.NEXT])
        assertEquals(0xb6, field.usages[CarPlayMediaButton.PREVIOUS])
        // The logical range covers the new indices and nothing more; the report stays one array byte.
        assertEquals(0, field.logicalMin)
        assertEquals(field.usages.lastIndex, field.logicalMax)
        assertEquals(8, field.size)
        assertEquals(1, field.count)
        assertEquals(0x00, field.flags)

        val earlier = mediaField(earlierDescriptor)
        assertEquals(earlierUsages, earlier.usages)
        assertEquals(earlier.usages.lastIndex, earlier.logicalMax)
    }

    @Test fun theInfoDeclaresSeekKeysOnlyWhenTheConfigAsks() {
        val on = config().copy(mediaSeekKeys = true)
        assertArrayEquals(descriptor(AirPlayHid.mediaHidDevice(AirPlayInfoPlist.MAIN_UUID, seekKeys = true)), mediaDescriptor(on))
        assertFalse(earlierDescriptor.contentEquals(mediaDescriptor(on)))
        // The other input devices do not change with it.
        val others = { config: AirPlayConfig ->
            hidDevices(config).filter { it["name"] != "xcertplay Media" }.map { descriptor(it).toList() }
        }
        assertEquals(others(config()), others(on))
        assertEquals(4, hidDevices(on).size)
    }

    @Test fun theMediaReportIsTheIndexAlone() {
        assertArrayEquals(byteArrayOf(7), AirPlayHid.mediaReport(CarPlayMediaButton.FAST_FORWARD))
        assertArrayEquals(byteArrayOf(8), AirPlayHid.mediaReport(CarPlayMediaButton.REWIND))
        assertArrayEquals(byteArrayOf(0), AirPlayHid.mediaReport(0))
    }

    private fun config() = AirPlayConfig(
        deviceName = "test", deviceId = "02:00:00:00:00:02", btMac = "02:00:00:00:00:02",
        sourceVersion = "366.0",
        main = AirPlayDisplayConfig(widthPixels = 1280, heightPixels = 720),
    )

    @Suppress("UNCHECKED_CAST")
    private fun hidDevices(config: AirPlayConfig) = AirPlayInfoPlist.build(config)["hidDevices"] as List<Map<String, Any?>>

    private fun mediaDescriptor(config: AirPlayConfig) = descriptor(hidDevices(config).single { it["name"] == "xcertplay Media" })

    private fun descriptor(device: Map<String, Any?>) = device["hidDescriptor"] as ByteArray

    private data class ArrayField(
        val usages: List<Int>, val logicalMin: Int, val logicalMax: Int, val size: Int, val count: Int, val flags: Int,
    )

    /** Decodes HID short items into the descriptor's one Input field. */
    private fun mediaField(descriptor: ByteArray): ArrayField {
        val fields = mutableListOf<ArrayField>()
        val usages = mutableListOf<Int>()
        var logicalMin = 0
        var logicalMax = 0
        var reportSize = 0
        var reportCount = 0
        var offset = 0
        while (offset < descriptor.size) {
            val prefix = descriptor[offset++].toInt() and 0xff
            val size = if ((prefix and 3) == 3) 4 else (prefix and 3)
            var value = 0
            repeat(size) { byte -> value = value or ((descriptor[offset++].toInt() and 0xff) shl (8 * byte)) }
            val type = (prefix shr 2) and 3
            val tag = prefix shr 4
            when (type) {
                1 -> when (tag) {
                    1 -> logicalMin = value
                    2 -> logicalMax = value
                    7 -> reportSize = value
                    9 -> reportCount = value
                }
                2 -> if (tag == 0) usages += value
                0 -> {
                    if (tag == 8) fields += ArrayField(usages.toList(), logicalMin, logicalMax, reportSize, reportCount, value)
                    usages.clear()
                }
            }
        }
        return fields.single()
    }
}
