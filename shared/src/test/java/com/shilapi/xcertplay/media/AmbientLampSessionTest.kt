package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.hud.BydAmbientLightPolicy
import java.util.concurrent.CompletableFuture
import org.junit.Assert.*
import org.junit.Test

class AmbientLampSessionTest {
    private val original = BydAmbientLightPolicy.Snapshot(7, 23, 2, 5, 2)
    private val desired = Triple(3, 15, 6)
    private val WRITTEN = AmbientLampSession.Outcome.WRITTEN
    // What the car might show during its blind-spot warning (#1332); any value DiPlay did not set.
    private val warning = AmbientLampSession.Outcome(held = true, lamp = BydAmbientLightPolicy.Snapshot(1, 15, 6, 6, 3))
    private class Fake : AmbientLampSession.Client {
        var failed = false
        var seed: BydAmbientLightPolicy.Snapshot? = null
        val begin = CompletableFuture<BydAmbientLightPolicy.Snapshot>()
        val stopped = CompletableFuture<Unit>()
        val applies = mutableListOf<Pair<Triple<Int, Int, Int>, CompletableFuture<AmbientLampSession.Outcome>>>()
        var stops = 0
        var closed = false
        val rebases = mutableListOf<CompletableFuture<Unit>>()
        override fun unhealthy() = failed
        override fun begin(seed: BydAmbientLightPolicy.Snapshot?): CompletableFuture<BydAmbientLightPolicy.Snapshot> { this.seed = seed; return begin }
        override fun apply(desired: Triple<Int, Int, Int>) = CompletableFuture<AmbientLampSession.Outcome>().also { applies.add(desired to it) }
        override fun rebase() = CompletableFuture<Unit>().also { rebases.add(it) }
        override fun stop(): CompletableFuture<Unit> { stops++; return stopped }
        override fun close() { closed = true }
    }
    private class Harness {
        var time = 0L
        val callbacks = ArrayDeque<() -> Unit>()
        val clients = mutableListOf<Fake>()
        var applies = 0
        val lines = mutableListOf<String>()
        val engine = AmbientLampSession(
            factory = { Fake().also { clients.add(it) } },
            dispatch = { callbacks.addLast(it) }, now = { time }, applied = { applies++ },
            diagnostic = { lines.add(it) },
        )
        fun drain() { while (callbacks.isNotEmpty()) callbacks.removeFirst()() }
    }
    private fun start(h: Harness): Fake {
        h.engine.setEnabled(true); h.engine.update(desired)
        return h.clients.last().also { it.begin.complete(original); h.drain(); h.engine.update(desired) }
    }
    @Test fun applyAndStopFailureRecoverWithoutSettingsSaveAndPreserveOriginalSnapshot() {
        val h = Harness(); val first = start(h)
        first.applies.last().second.completeExceptionally(IllegalStateException("transport")); h.drain()
        assertEquals(1, first.stops)
        h.time = 60_000; h.engine.update(desired) // Even past retry time: stop has not completed.
        assertEquals(1, h.clients.size)
        first.stopped.completeExceptionally(IllegalStateException("EOF")); h.drain()
        assertTrue(first.closed)
        h.time += 15_999; h.engine.update(desired); assertEquals(1, h.clients.size)
        h.time++; h.engine.update(desired)
        val second = h.clients.last()
        assertEquals(original, second.seed)
        second.begin.complete(original); h.drain(); h.engine.update(desired)
        second.applies.last().second.complete(WRITTEN); h.drain()
        assertEquals(1, h.applies)
    }
    @Test fun unchangedDesiredStillChecksFailedClientBeforeDeduplication() {
        val h = Harness(); val first = start(h)
        first.applies.last().second.complete(WRITTEN); h.drain()
        first.failed = true
        h.time = 500; h.engine.update(desired)
        assertEquals(1, first.stops)
        first.stopped.complete(Unit); h.drain()
        h.time += 4_999; h.engine.update(desired); assertEquals(1, h.clients.size)
        h.time++; h.engine.update(desired); assertEquals(2, h.clients.size)
    }
    @Test fun consecutiveBeginFailuresBackOffToThirtySecondsAndSuccessfulApplyResetsBackoff() {
        val h = Harness(); h.engine.setEnabled(true)
        for (delay in listOf(5_000L, 10_000L, 20_000L, 30_000L, 30_000L)) {
            h.engine.update(desired)
            val client = h.clients.last(); val count = h.clients.size
            client.begin.completeExceptionally(IllegalStateException("unavailable")); h.drain()
            client.stopped.complete(Unit); h.drain()
            h.time += delay - 1; h.engine.update(desired); assertEquals(count, h.clients.size)
            h.time++
        }
        h.engine.update(desired)
        val good = h.clients.last(); good.begin.complete(original); h.drain(); h.engine.update(desired)
        good.applies.last().second.complete(WRITTEN); h.drain()
        good.failed = true; h.engine.update(desired); good.stopped.complete(Unit); h.drain()
        val count = h.clients.size
        h.time += 4_999; h.engine.update(desired); assertEquals(count, h.clients.size)
        h.time++; h.engine.update(desired); assertEquals(count + 1, h.clients.size)
    }
    @Test fun disablingCancelsRecoveryAndUnconfirmedSnapshotIsNotLost() {
        val h = Harness(); val first = start(h)
        h.engine.setEnabled(false)
        first.stopped.completeExceptionally(IllegalStateException("restore pending")); h.drain()
        h.time = 100_000; h.engine.update(desired)
        assertEquals(1, h.clients.size)
        h.engine.setEnabled(true); h.engine.update(desired)
        assertEquals(original, h.clients.last().seed)
    }
    @Test fun reenableCannotBypassUncertainStopLeaseGrace() {
        val h = Harness(); val first = start(h)
        h.engine.setEnabled(false)
        first.stopped.completeExceptionally(IllegalStateException("restore pending")); h.drain()
        h.engine.setEnabled(true)
        h.time = 15_999; h.engine.update(desired); assertEquals(1, h.clients.size)
        h.time = 16_000; h.engine.update(desired); assertEquals(2, h.clients.size)
    }
    @Test fun disabledWhileOldStopPendingNeverBeginsAndLateApplyDoesNotTouchNewOwner() {
        val h = Harness(); val first = start(h)
        val staleApply = first.applies.last().second
        first.failed = true; h.engine.update(desired)
        h.engine.setEnabled(false)
        first.stopped.complete(Unit); h.drain()
        h.time = 100_000; h.engine.update(desired); assertEquals(1, h.clients.size)
        h.engine.setEnabled(true); h.engine.update(desired)
        val second = h.clients.last(); second.begin.complete(original); h.drain(); h.engine.update(desired)
        staleApply.completeExceptionally(IllegalStateException("stale callback")); h.drain()
        assertEquals(0, second.stops)
        second.applies.last().second.complete(WRITTEN); h.drain()
        assertEquals(1, h.applies)
    }
    @Test fun changedDesiredAndPauseMinimumStayPendingUntilSuccessfulApply() {
        val h = Harness(); val client = start(h)
        val minimum = Triple(3, 7, 1)
        h.time = 250; h.engine.update(minimum)
        assertEquals(1, client.applies.size) // Never overlap a device apply.
        client.applies.first().second.complete(WRITTEN); h.drain()
        h.engine.update(minimum)
        assertEquals(minimum, client.applies.last().first)
    }
    @Test fun disableAfterConfirmedRestoreReleasesSnapshotForNextOemSession() {
        val h = Harness(); val first = start(h)
        first.failed = true; h.engine.update(desired)
        first.stopped.complete(Unit); h.drain()
        h.engine.setEnabled(false) // No active worker; OEM now owns lamp state.
        h.time = 1_000
        h.engine.setEnabled(true); h.engine.update(desired)
        val second = h.clients.last()
        assertNull(second.seed) // A later OEM change must be freshly snapshotted.
        val changedOem = original.copy(frontColor = 12, frontBrightness = 4)
        second.begin.complete(changedOem); h.drain(); h.engine.update(desired)
        second.failed = true; h.engine.update(desired); second.stopped.complete(Unit); h.drain()
        h.time += 5_000; h.engine.update(desired)
        assertEquals(changedOem, h.clients.last().seed)
    }
    @Test fun staleBeginCompletionCannotReplaceNewWorkerSnapshot() {
        val h = Harness(); h.engine.setEnabled(true); h.engine.update(desired)
        val old = h.clients.last()
        h.engine.setEnabled(false); old.stopped.complete(Unit); h.drain()
        h.engine.setEnabled(true); h.engine.update(desired)
        val fresh = h.clients.last()
        val freshOem = original.copy(backColor = 11)
        fresh.begin.complete(freshOem); h.drain(); h.engine.update(desired)
        old.begin.complete(original); h.drain()
        fresh.failed = true; h.engine.update(desired); fresh.stopped.complete(Unit); h.drain()
        h.time += 5_000; h.engine.update(desired)
        assertEquals(freshOem, h.clients.last().seed)
    }
    @Test fun heldApplyIsAskedAgainAndLogsOnePauseAndOneResume() {
        val h = Harness(); val client = start(h)
        client.applies.last().second.complete(warning); h.drain()
        assertEquals(0, h.applies)
        assertEquals(1, h.lines.size)
        assertTrue(h.lines[0], h.lines[0].startsWith("Ambient: lamp writes paused"))
        assertTrue(h.lines[0], "lamp=front 1/6 back 15/6 area 3" in h.lines[0])
        // The same value is sent again, every RETRY_MILLIS: the worker reads the lamp before each one.
        h.time = 2_000; h.engine.update(desired)
        assertEquals(2, client.applies.size)
        client.applies.last().second.complete(warning); h.drain()
        assertEquals(1, h.lines.size)
        h.time = 4_000; h.engine.update(desired)
        assertEquals(3, client.applies.size)
        client.applies.last().second.complete(WRITTEN); h.drain()
        assertEquals(listOf("Ambient: lamp writes resumed after 4000 ms"), h.lines.drop(1))
        assertEquals(1, h.applies)
        h.time = 4_200; h.engine.update(desired)
        assertEquals(3, client.applies.size) // Written now, so deduplicated again.
    }
    @Test fun heldValueIsAskedForAgainOnlyEveryTwoSecondsButANewValueAtOnce() {
        val h = Harness(); val client = start(h)
        client.applies.last().second.complete(warning); h.drain()
        for (t in 200L until 2_000L step 200) { h.time = t; h.engine.update(desired) }
        assertEquals(1, client.applies.size) // Not 9 more worker round trips while the lamp stays changed.
        val next = Triple(3, 22, 4)
        h.time = 2_000 - 1; h.engine.update(next)
        assertEquals(next, client.applies.last().first)
        client.applies.last().second.complete(warning); h.drain()
        h.time = 2_000 + 1_998; h.engine.update(next)
        assertEquals(2, client.applies.size)
        h.time = 2_000 + 1_999; h.engine.update(next)
        assertEquals(3, client.applies.size)
    }
    @Test fun rebaseClearsAHoldAndTheNextApplyIsWritten() {
        val h = Harness(); val client = start(h)
        client.applies.last().second.complete(warning); h.drain()
        h.time = 700; h.engine.rebase("DiPlay settings changed")
        assertEquals(1, client.rebases.size)
        assertEquals("Ambient: lamp hold cleared (DiPlay settings changed) after 700 ms", h.lines.last())
        client.rebases.last().complete(Unit); h.drain()
        // Not throttled: the same value goes at once, and the worker now compares it with the lamp as read.
        h.time = 900; h.engine.update(desired)
        assertEquals(2, client.applies.size)
        client.applies.last().second.complete(WRITTEN); h.drain()
        assertEquals(1, h.applies)
        assertEquals(2, h.lines.size) // No "resumed" line: the hold was already reported as cleared.
        assertEquals(0, client.stops)
    }
    @Test fun rebaseWithoutAHoldStillResetsTheWorkerButLogsNothing() {
        val h = Harness(); val client = start(h)
        client.applies.last().second.complete(WRITTEN); h.drain()
        h.engine.rebase("new CarPlay session")
        assertEquals(1, client.rebases.size)
        assertEquals(emptyList<String>(), h.lines)
    }
    @Test fun rebaseBeforeTheLampIsReadyIsLeftToBegin() {
        val h = Harness()
        h.engine.setEnabled(true); h.engine.update(desired)
        h.engine.rebase("new CarPlay session")
        assertEquals(0, h.clients.last().rebases.size) // begin takes a fresh baseline anyway.
    }
    @Test fun heldReplyToAnApplySentBeforeARebaseStartsNoHold() {
        val h = Harness(); val client = start(h)
        h.engine.rebase("DiPlay settings changed")
        client.applies.last().second.complete(warning); h.drain()
        assertEquals(emptyList<String>(), h.lines)
        h.time = 200; h.engine.update(desired)
        assertEquals(2, client.applies.size)
    }
    @Test fun failedRebaseRecoversTheLampSession() {
        val h = Harness(); val client = start(h)
        client.applies.last().second.complete(WRITTEN); h.drain()
        h.engine.rebase("DiPlay settings changed")
        client.rebases.last().completeExceptionally(IllegalStateException("worker-rebase-failed")); h.drain()
        assertEquals(1, client.stops)
    }
    @Test fun writtenApplyLogsNothing() {
        val h = Harness(); val client = start(h)
        client.applies.last().second.complete(WRITTEN); h.drain()
        assertEquals(emptyList<String>(), h.lines)
    }
    @Test fun stoppingWhileHeldEndsThePauseInTheLog() {
        val h = Harness(); val client = start(h)
        client.applies.last().second.complete(warning); h.drain()
        h.time = 1_000; h.engine.setEnabled(false)
        assertEquals(1, client.stops)
        assertEquals("Ambient: lamp hold ended after 1000 ms, lamp session stopping", h.lines.last())
        client.stopped.complete(Unit); h.drain()
        assertEquals(2, h.lines.size)
    }
}
