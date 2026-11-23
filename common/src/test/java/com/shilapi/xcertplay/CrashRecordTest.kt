package com.shilapi.xcertplay

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class CrashRecordTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun theRecordNamesTheExceptionChainAndWhereItWasThrown() {
        val cause = IllegalStateException("surface released at 192.168.1.20")
        val crash = RuntimeException("Unable to start activity\nsecond line", cause)
        crash.stackTrace = Array(20) { StackTraceElement("com.shilapi.xcertplay.DiPlayActivity", "onCreate$it", "DiPlayActivity.kt", it) }

        val record = CrashRecord.format("main", crash, 1_000L)

        val lines = record.lines()
        assertEquals("crash atEpochMs=1000 thread=main", lines[0])
        assertEquals("java.lang.RuntimeException: Unable to start activity", lines[1])
        assertEquals("  at com.shilapi.xcertplay.DiPlayActivity.onCreate0(DiPlayActivity.kt:0)", lines[2])
        assertTrue(record.contains("  at com.shilapi.xcertplay.DiPlayActivity.onCreate17(DiPlayActivity.kt:17)"))
        assertTrue(record.contains("  ... 2 more"))
        assertTrue(record.contains("caused by java.lang.IllegalStateException: surface released at 192.168.1.20"))
        assertFalse(record.contains("second line"))
    }

    // #463: Android's startActivity frames filled the record and hid the DiPlay call that crashed.
    @Test fun ourFramesBelowTheFirstOnesAreKept() {
        val framework = Array(14) { StackTraceElement("android.app.Activity", "startActivity$it", "Activity.java", it) }
        val ours = Array(9) { StackTraceElement("com.shilapi.xcertplay.DiPlayActivity", "open$it", "DiPlayActivity.kt", it) }
        val crash = IllegalArgumentException("There is no running activity in rootTask")
        crash.stackTrace = framework + ours + StackTraceElement("android.os.Looper", "loop", "Looper.java", 1)

        val record = CrashRecord.format("main", crash, 0)

        assertTrue(record.contains("  at android.app.Activity.startActivity11(Activity.java:11)"))
        assertFalse(record.contains("startActivity12"))
        assertTrue(record.contains("  ... DiPlay frames further down:"))
        assertTrue(record.contains("  at com.shilapi.xcertplay.DiPlayActivity.open0(DiPlayActivity.kt:0)"))
        assertTrue(record.contains("  at com.shilapi.xcertplay.DiPlayActivity.open5(DiPlayActivity.kt:5)"))
        assertFalse(record.contains("open6"))
        assertTrue(record.contains("  ... 6 more"))
    }

    @Test fun aCauseLoopEndsTheChain() {
        val first = RuntimeException("first")
        val second = IllegalStateException("second", first)
        first.initCause(second)

        assertEquals(2, CrashRecord.format("worker", first, 0).lines().count { it.startsWith("java.") || it.startsWith("caused by") })
    }

    @Test fun theReportShowsTheRedactedRecordOrSaysThereIsNone() {
        val file = File(context.filesDir, "logs/last-crash.txt")
        file.delete()
        assertEquals("No crash recorded by this version.", CrashRecord.report(context))

        file.parentFile?.mkdirs()
        file.writeText(CrashRecord.format("main", IllegalStateException("surface released at 192.168.1.20"), 5L))
        val report = CrashRecord.report(context)
        assertTrue(report, report.startsWith("crash atEpochMs=5 thread=main\njava.lang.IllegalStateException: surface released at [ip]"))
    }

    @Test fun anOversizedRecordIsBoundedAndNeverReachesTheTail() {
        val file = File(context.filesDir, "logs/last-crash.txt")
        file.parentFile?.mkdirs()
        val sentinel = "SENTINEL_AT_TAIL_9f8e7d6c"
        file.writeText(buildString {
            repeat(10_000) { appendLine("record line $it ok") }
            appendLine(sentinel)
        })

        val report = CrashRecord.report(context)
        val lineCount = report.lines().size
        assertTrue("report had $lineCount lines", lineCount <= CrashRecord.MAX_REPORT_LINES)
        assertFalse("tail leaked into the bounded report", report.contains(sentinel))
    }

    @Test fun aNormalSizedRecordIsReturnedVerbatim() {
        val file = File(context.filesDir, "logs/last-crash.txt")
        file.parentFile?.mkdirs()
        val content = (1..60).joinToString("\n") { "record line $it ok" }
        file.writeText(content)

        assertEquals(content, CrashRecord.report(context))
    }

    @Test fun anOverlongLineIsTruncatedWhileTheLineCountStaysBounded() {
        val file = File(context.filesDir, "logs/last-crash.txt")
        file.parentFile?.mkdirs()
        val sentinel = "TAIL_SENTINEL_5e4d3c2b"
        file.writeText(buildString {
            appendLine("x".repeat(5_000))
            repeat(300) { appendLine("record line $it ok") }
            appendLine(sentinel)
        })

        val report = CrashRecord.report(context)
        val lines = report.lines()
        assertEquals(CrashRecord.MAX_REPORT_LINES, lines.size)
        assertEquals(700, lines.first().length)
        assertFalse("tail leaked into the bounded report", report.contains(sentinel))
    }

    // Pins the 85-line ceiling (1 header + 4 causes x 21 lines): the largest record format() can
    // emit must survive report() intact, so a later MAX_FRAMES/MAX_CAUSES bump cannot truncate it.
    @Test fun theLargestPossibleRecordIsReturnedIntactByReport() {
        // Each cause: >12 framework frames followed by at least 6 com.shilapi. frames.
        val frames = Array(13) { StackTraceElement("android.app.Activity", "frame$it", "Activity.java", it) } +
            Array(8) { StackTraceElement("com.shilapi.xcertplay.DiPlayActivity", "call$it", "DiPlayActivity.kt", it) }
        fun withFrames(error: Throwable) = error.apply { stackTrace = frames }
        val cause4 = withFrames(IllegalStateException("boom4"))
        val cause3 = withFrames(RuntimeException("boom3", cause4))
        val cause2 = withFrames(RuntimeException("boom2", cause3))
        val cause1 = withFrames(RuntimeException("boom1", cause2))

        val record = CrashRecord.format("main", cause1, 0)
        // format() ends with a newline; String.lines() counts that trailing empty element, while
        // useLines()/readLine() does not. Compare real content lines on both sides.
        val recordLines = record.trimEnd('\n').lines()
        assertEquals(85, recordLines.size)

        val file = File(context.filesDir, "logs/last-crash.txt")
        file.parentFile?.mkdirs()
        file.writeText(record)

        val report = CrashRecord.report(context)
        assertEquals(recordLines.size, report.lines().size)
        assertTrue(report, report.contains("caused by java.lang.IllegalStateException: boom4"))
    }
}
