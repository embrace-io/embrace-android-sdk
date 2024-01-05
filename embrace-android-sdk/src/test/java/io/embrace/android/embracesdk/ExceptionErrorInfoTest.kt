package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.payload.ExceptionErrorInfo
import io.embrace.android.embracesdk.payload.ExceptionInfo
import org.junit.Assert.assertEquals
import org.junit.Test

internal class ExceptionErrorInfoTest {

    private val info = ExceptionInfo(
        "java.lang.IllegalStateException",
        "Whoops!",
        listOf(
            "java.base/java.lang.Thread.getStackTrace(Thread.java:1602)",
            "io.embrace.android.embracesdk.ThreadInfoTest.testThreadInfoSerialization(ThreadInfoTest.kt:18)"
        )
    )

    @Test
    fun testExceptionErrorInfoSerialization() {
        val exceptionErrorInfo = ExceptionErrorInfo(
            0,
            "STATE",
            listOf(
                info,
            )
        )
        assertJsonMatchesGoldenFile("exception_error_info_expected.json", exceptionErrorInfo)
    }

    @Test
    fun testExceptionErrorInfoDeserialization() {
        val obj = deserializeJsonFromResource<ExceptionErrorInfo>("exception_error_info_expected.json")
        assertEquals(0L, obj.timestamp)
        assertEquals("STATE", obj.state)
        val exceptionInfo = obj.exceptions?.get(0)
        assertEquals(info.message, exceptionInfo?.message)
        assertEquals(info.name, exceptionInfo?.name)
        assertEquals(info.lines, exceptionInfo?.lines)
    }
}
