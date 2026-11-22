package com.shilapi.xcertplay.hud

import android.content.Context
import com.shilapi.xcertplay.adb.LocalAdb
import java.io.IOException
import java.security.KeyPair
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 32], manifest = Config.NONE)
class BydCallPopupChannelTest {
    private class Fixture : BydCallPopupLease.Ops {
        var mode = 3
        var status = "READY"
        var afterStart: () -> Unit = {}
        var loseStartReply = false
        var loseStopReply = false
        val commands = mutableListOf<String>()
        val lease = BydCallPopupLease(this, { 0L }, { status = it })
        override fun query() = mode
        override fun set(value: Int) { mode = value }
        fun shell(command: String): String? {
            if ("app_process" in command) return "0 READY"
            if (command.startsWith("umask")) {
                val control = Regex("'([0-9]+ (?:START|PING|STOP))'").find(command)!!.groupValues[1]
                commands += control.substringAfter(' ')
                lease.accept(control)
                if (control.endsWith("START")) afterStart()
                if (control.endsWith("STOP") && loseStopReply) return null
                return "OK"
            }
            if (command.startsWith("cat")) {
                if (loseStartReply && commands.last() == "START") { loseStartReply = false; return null }
                return "${lease.sequence()} $status"
            }
            return "" // Terminal cleanup; no protocol or media payloads.
        }
        fun channel() = BydCallPopupChannel.prepare("/data/app/a'b/base.apk", ::shell)
    }

    @Test fun ambiguousStartReplyStillSendsStopAndRestores() {
        val f = Fixture().apply { loseStartReply = true }
        val result = runCatching { BydCallPopupGuard.runSession(f.channel(), { true }, {}, {}) }
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(listOf("START", "STOP"), f.commands)
        assertEquals(3, f.mode)
    }

    @Test fun lostStopReplyIsNotReportedAsConfirmedRestoration() {
        val f = Fixture().apply { loseStopReply = true }
        var current = true
        val reports = mutableListOf<String>()
        val result = runCatching {
            BydCallPopupGuard.runSession(f.channel(), { current }, { current = false }, { reports += it })
        }
        assertTrue(result.isFailure)
        assertEquals(listOf("HIDDEN"), reports)
    }

    @Test fun cancelledBeforeStartSendsOnlyStopWithoutMutation() {
        val f = Fixture()
        BydCallPopupGuard.runSession(f.channel(), { false }, {}, {})
        assertEquals(listOf("STOP"), f.commands)
        assertEquals(3, f.mode)
    }

    @Test fun foregroundLossDuringStartKeepsTheConnectionForImmediateStop() {
        val f = Fixture()
        val guard = BydCallPopupGuard
        val context: Context = RuntimeEnvironment.getApplication()
        val owner = Any()
        val client = LocalAdb(KeyPair(null, null))
        ReflectionHelpers.setField(guard, "running", true)
        try {
            guard.update(context, owner, true) { true }
            ReflectionHelpers.setField(guard, "adb", client)
            f.afterStart = { guard.update(context, owner, false) { true } }
            val generation = ReflectionHelpers.getField<Long>(guard, "generation")
            guard.runPrepared(generation, f.channel())
            assertFalse("Do not cancel the ADB connection between IGNORE and START acknowledgement",
                ReflectionHelpers.getField<java.util.concurrent.atomic.AtomicBoolean>(client, "cancelled").get())
            assertEquals(listOf("START", "STOP"), f.commands)
            assertEquals(3, f.mode)
        } finally {
            guard.update(context, owner, false) { true }
            ReflectionHelpers.setField(guard, "leased", false)
            ReflectionHelpers.setField(guard, "adb", null)
            ReflectionHelpers.setField(guard, "running", false)
            client.close()
        }
    }

    // #1237: the guard kept HIDDEN, RESTORED and its failures in memory, so a report could not show them.
    @Test fun hideRequestsAndStatusChangesReachTheReport() {
        val f = Fixture()
        val guard = BydCallPopupGuard
        val context: Context = RuntimeEnvironment.getApplication()
        val owner = Any()
        val lines = java.util.concurrent.CopyOnWriteArrayList<String>()
        ReflectionHelpers.setField(guard, "running", true)
        ReflectionHelpers.setField(guard, "status", "OFF")
        try {
            guard.update(context, owner, true, log = { lines += it }) { true }
            f.afterStart = { guard.update(context, owner, false, log = { lines += it }) { true } }
            guard.runPrepared(ReflectionHelpers.getField(guard, "generation"), f.channel())
            // The helper answers every heartbeat with HIDDEN; only a change is a line.
            repeat(3) {
                ReflectionHelpers.callInstanceMethod<Unit>(guard, "setStatus",
                    ReflectionHelpers.ClassParameter.from(String::class.java, "RESTORED"),
                    ReflectionHelpers.ClassParameter.from(String::class.java, null))
            }
            assertEquals(listOf(
                "BYD call popup: hide requested",
                "BYD call popup: hide no longer requested",
                "BYD call popup: status=HIDDEN previous=OFF",
                "BYD call popup: status=RESTORED previous=HIDDEN",
            ), lines)
            assertEquals("RESTORED", guard.status)
        } finally {
            guard.update(context, owner, false) { true }
            ReflectionHelpers.setField(guard, "leased", false)
            ReflectionHelpers.setField(guard, "running", false)
            // The owner already let go above, so update() leaves these alone: reset them for later tests.
            ReflectionHelpers.setField(guard, "diagnostic", { _: String -> })
            ReflectionHelpers.setField(guard, "status", "OFF")
        }
    }

    @Test fun aFailureReasonIsTheHelperCodeOrTheExceptionTypeOnly() {
        assertEquals("HELPER_TIMEOUT", BydCallPopupGuard.reason(IOException("HELPER_TIMEOUT")))
        assertEquals("ERROR_BUSY", BydCallPopupGuard.reason(IOException("ERROR BUSY")))
        assertEquals("IOException", BydCallPopupGuard.reason(IOException("/data/local/tmp/diplay/0a1b failed")))
        assertEquals("IOException", BydCallPopupGuard.reason(IOException()))
        assertEquals("SecurityException", BydCallPopupGuard.reason(SecurityException("uid 10123 is not allowed")))
    }

    @Test fun malformedTerminalAcknowledgementIsRejected() {
        val channel = BydCallPopupChannel.prepare("/data/app/base.apk") {
            when {
                "app_process" in it -> "0 READY"
                it.startsWith("umask") -> "OK"
                else -> "invalid RESTORED"
            }
        }
        assertTrue(runCatching { channel.exchange("STOP") }.isFailure)
    }
}
