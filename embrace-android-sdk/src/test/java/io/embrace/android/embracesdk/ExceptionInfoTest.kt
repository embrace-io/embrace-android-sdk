package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.payload.ExceptionInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class ExceptionInfoTest {

    private val info = ExceptionInfo(
        "java.lang.IllegalStateException",
        "Whoops!",
        listOf(
            "java.base/java.lang.Thread.getStackTrace(Thread.java:1602)",
            "io.embrace.android.embracesdk.ThreadInfoTest.testThreadInfoSerialization(ThreadInfoTest.kt:18)"
        )
    )

    @Test
    fun testExceptionInfoSerialization() {
        assertJsonMatchesGoldenFile("exception_info_expected.json", info)
    }

    @Test
    fun testExceptionInfoDeserialization() {
        val obj = deserializeJsonFromResource<ExceptionInfo>("exception_info_expected.json")
        assertEquals("java.lang.IllegalStateException", obj.name)
        assertEquals("Whoops!", obj.message)
        assertEquals("java.base/java.lang.Thread.getStackTrace(Thread.java:1602)", obj.lines[0])
        assertEquals(
            "io.embrace.android.embracesdk.ThreadInfoTest.testThreadInfoSerialization(ThreadInfoTest.kt:18)",
            obj.lines[1]
        )
    }

    @Test
    fun testExceptionInfoEmptyObject() {
        val obj = deserializeEmptyJsonString<ExceptionInfo>()
        assertNotNull(obj)
    }

    @Test
    fun testOfThrowable() {
        val info = ExceptionInfo.ofThrowable(
            mockk {
                every { message } returns "UhOh."
                every { stackTrace } returns arrayOf(
                    StackTraceElement("Foo", "bar", "Foo.kt", 5)
                )
            }
        )
        assertNotNull(info)
        assertEquals("UhOh.", info.message)
        assertEquals("java.lang.Throwable", info.name)
        assertEquals("Foo.bar(Foo.kt:5)", info.lines.single())
        assertNull(info.originalLength)
    }

    @Test
    fun testMaxStacktraceLimit() {
        val limit = 200
        val len = limit + 100
        val obj = ExceptionInfo(
            "java.lang.IllegalStateException",
            "Whoops!",
            (0 until len).map { "line $it" }
        )
        assertEquals(limit, obj.lines.size)
        val expected = (0 until limit).map { "line $it" }
        assertEquals(expected, obj.lines)
        assertEquals(len, obj.originalLength)
    }
}
