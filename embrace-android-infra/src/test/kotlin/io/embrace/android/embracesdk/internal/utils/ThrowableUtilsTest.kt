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

    @Test
    fun `stacktrace text without causes`() {
        val exc = BigStackException(arrayOf(StackTraceElement("A", "b", "A.kt", 1)))
        assertEquals("A.b(A.kt:1)", exc.stacktraceTextWithCauses())
    }

    @Test
    fun `stacktrace text includes causes`() {
        val root = BigStackException(arrayOf(StackTraceElement("C", "d", "C.kt", 3)), message = null)
        val middle = BigStackException(arrayOf(StackTraceElement("B", "c", "B.kt", 2)), "middle", root)
        val exc = BigStackException(arrayOf(StackTraceElement("A", "b", "A.kt", 1)), "top", middle)
        val name = BigStackException::class.java.name
        val expected = listOf(
            "A.b(A.kt:1)",
            "Caused by: $name: middle",
            "B.c(B.kt:2)",
            "Caused by: $name",
            "C.d(C.kt:3)",
        ).joinToString("\n")
        assertEquals(expected, exc.stacktraceTextWithCauses())
    }

    @Test
    fun `stacktrace text caps causes and frames`() {
        var exc: Throwable = BigStackException(bigStack, "cause 5")
        for (k in 4 downTo 0) {
            exc = BigStackException(bigStack, "cause $k", exc)
        }
        val text = exc.stacktraceTextWithCauses()
        val causes = Regex("Caused by: .*: (cause \\d)").findAll(text).map { it.groupValues[1] }.toList()
        assertEquals(listOf("cause 1", "cause 2", "cause 3"), causes)
        assertEquals(800, text.lines().count { it == "A.B(C:1)" })
    }

    @Test
    fun `stacktrace text stops at a cause cycle`() {
        val first = CyclicException("first")
        val second = CyclicException("second", first)
        first.next = second
        val text = first.stacktraceTextWithCauses()
        assertEquals(1, Regex("Caused by:").findAll(text).count())
    }

    class DangerousException : Exception("DangerousException") {
        override fun getStackTrace(): Array<StackTraceElement> = error("lol")
    }

    private class BigStackException(
        private val customStacktrace: Array<StackTraceElement>,
        message: String? = null,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause) {
        override fun getStackTrace(): Array<StackTraceElement> = customStacktrace
    }

    private class CyclicException(message: String, var next: Throwable? = null) : RuntimeException(message) {
        override val cause: Throwable?
            get() = next
    }
}
