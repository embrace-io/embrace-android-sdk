package io.embrace.android.embracesdk.internal.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class ThrowableUtilsTest {

    private val bigStack = Array(201) { StackTraceElement("A", "B", "C", 1) }

    @Test
    fun `test safe stacktrace`() {
        assertNull(DangerousException().getSafeStackTrace())
    }

    @Test
    fun `verify truncation of stacktrace elements`() {
        assertEquals(200, bigStack.truncate().count())
    }

    @Test
    fun `verify truncated stacktrace text`() {
        val stacktraceText = BigStackException(bigStack).truncatedStacktraceText()
        assertEquals(200, stacktraceText.count { it == 'A' })
        assertEquals(200, stacktraceText.count { it == 'B' })
        assertEquals(200, stacktraceText.count { it == 'C' })
        assertEquals(200, stacktraceText.count { it == '1' })
        assertEquals(199, stacktraceText.count { it == '\n' })
    }

    class DangerousException : Exception("DangerousException") {
        override fun getStackTrace(): Array<StackTraceElement> = error("lol")
    }

    private class BigStackException(private val customStacktrace: Array<StackTraceElement>) : RuntimeException() {
        override fun getStackTrace(): Array<StackTraceElement> = customStacktrace
    }
}
