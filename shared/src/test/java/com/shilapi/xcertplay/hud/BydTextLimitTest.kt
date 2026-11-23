package com.shilapi.xcertplay.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Both BYD text fields are capped in UTF-16LE bytes; neither should recopy the prefix per character. */
class BydTextLimitTest {
    @Test(timeout = 3_000L)
    fun aVeryLongTitleDoesNotRecopyItsPrefixForEveryCharacter() {
        val title = "x".repeat(100_000)
        val text = ClusterSongState.text(title, null)!!
        assertTrue(text.length <= ClusterSongState.MAX_TEXT_BYTES / 2)
        assertTrue(text.toByteArray(Charsets.UTF_16LE).size <= ClusterSongState.MAX_TEXT_BYTES)
    }

    @Test(timeout = 3_000L)
    fun aVeryLongCallerNameDoesNotRecopyItsPrefixForEveryCharacter() {
        val name = "x".repeat(100_000)
        val bytes = CarPlayCallState.nameBytes(name)
        assertTrue(bytes.size <= CarPlayCallState.MAX_NAME_BYTES)
    }

    @Test
    fun shortNamesAndSurrogatePairsKeepTheSameTruncation() {
        assertEquals(
            "Short title — Short artist",
            ClusterSongState.text("Short title", "Short artist"),
        )
        assertEquals("x".repeat(127), ClusterSongState.text("x".repeat(200), null))
        // High surrogate at the cut: the whole pair is dropped.
        assertEquals("aa" + "\uD83D\uDE00".repeat(62), ClusterSongState.text("aa" + "\uD83D\uDE00".repeat(100), null))
        // Longer than the new start (32), with the pair across the 60-byte cut.
        assertEquals(
            "A".repeat(29),
            String(
                CarPlayCallState.nameBytes("A".repeat(29) + "\uD83D\uDE00" + "B".repeat(10)),
                Charsets.UTF_16LE,
            ),
        )
    }
}
