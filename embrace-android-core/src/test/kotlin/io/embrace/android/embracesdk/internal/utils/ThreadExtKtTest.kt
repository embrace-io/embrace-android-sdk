package io.embrace.android.embracesdk.internal.utils

import io.embrace.android.embracesdk.internal.arch.stacktrace.compatThreadId
import io.embrace.android.embracesdk.internal.arch.stacktrace.truncateStacktrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.Thread.currentThread

class ThreadExtKtTest {

    private val fakeBigStackTrace = Array(700) {
        StackTraceElement("a", "b", "c", 1)
    }

    @Test
    fun `correct threadInfo created`() {
        val targetThread = currentThread()
        val threadInfo = truncateStacktrace(
            thread = targetThread,
            stackTraceElements = targetThread.stackTrace,
            maxStacktraceSize = 4,
        )
        with(threadInfo) {
            assertEquals(targetThread.name, name)
            assertEquals(Thread.State.RUNNABLE, state)
            assertTrue(priority > 0)
            assertEquals(4, lines?.size)
            assertEquals(targetThread.stackTrace.size, frameCount)
        }
    }

    @Test
    fun `verify default stacktrace size is 200`() {
        val threadInfo = truncateStacktrace(
            thread = currentThread(),
            stackTraceElements = fakeBigStackTrace,
        )
        assertEquals(200, threadInfo.lines?.size)
    }

    @Test
    fun `verify max stacktrace size capped at 500`() {
        val threadInfo = truncateStacktrace(
            thread = currentThread(),
            stackTraceElements = fakeBigStackTrace,
            maxStacktraceSize = 600,
        )
        assertEquals(500, threadInfo.lines?.size)
    }

    @Test
    fun `valid threadId retrieved`() {
        assertTrue(currentThread().compatThreadId() > 0)
    }
}
