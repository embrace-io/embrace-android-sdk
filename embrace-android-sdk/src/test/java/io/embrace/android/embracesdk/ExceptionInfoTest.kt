package io.embrace.android.embracesdk

import com.google.gson.Gson
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
        val data = ResourceReader.readResourceAsText("exception_info_expected.json")
            .filter { !it.isWhitespace() }
        val observed = Gson().toJson(info)
        assertEquals(data, observed)
    }

    @Test
    fun testExceptionInfoDeserialization() {
        val json = ResourceReader.readResourceAsText("exception_info_expected.json")
        val obj = Gson().fromJson(json, ExceptionInfo::class.java)
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
        val info = Gson().fromJson("{}", ExceptionInfo::class.java)
        assertNotNull(info)
        info.name
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
