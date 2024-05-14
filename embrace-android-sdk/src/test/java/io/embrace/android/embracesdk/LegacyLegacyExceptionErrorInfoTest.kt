package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.payload.LegacyExceptionErrorInfo
import io.embrace.android.embracesdk.payload.LegacyExceptionInfo
import org.junit.Assert.assertEquals
import org.junit.Test

internal class LegacyLegacyExceptionErrorInfoTest {

    private val info = LegacyExceptionInfo(
        "java.lang.IllegalStateException",
        "Whoops!",
        listOf(
            "java.base/java.lang.Thread.getStackTrace(Thread.java:1602)",
            "io.embrace.android.embracesdk.ThreadInfoTest.testThreadInfoSerialization(ThreadInfoTest.kt:18)"
        )
    )

    @Test
    fun testExceptionErrorInfoSerialization() {
        val exceptionErrorInfo = LegacyExceptionErrorInfo(
            0,
            listOf(info)
        )
        assertJsonMatchesGoldenFile("exception_error_info_expected.json", exceptionErrorInfo)
    }

    @Test
    fun testExceptionErrorInfoDeserialization() {
        val obj = deserializeJsonFromResource<LegacyExceptionErrorInfo>("exception_error_info_expected.json")
        assertEquals(0L, obj.timestamp)
        val exceptionInfo = obj.exceptions?.get(0)
        assertEquals(info.message, exceptionInfo?.message)
        assertEquals(info.name, exceptionInfo?.name)
        assertEquals(info.lines, exceptionInfo?.lines)
    }
}
