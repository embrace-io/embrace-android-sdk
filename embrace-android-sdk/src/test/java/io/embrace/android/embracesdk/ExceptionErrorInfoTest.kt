package io.embrace.android.embracesdk

import com.google.gson.Gson
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
            0, "STATE",
            listOf(
                info,
            )
        )

        val expectedInfo = ResourceReader.readResourceAsText("exception_error_info_expected.json")
            .filter { !it.isWhitespace() }

        val observed = Gson().toJson(exceptionErrorInfo)
        assertEquals(expectedInfo, observed)
    }

    @Test
    fun testExceptionErrorInfoDeserialization() {
        val json = ResourceReader.readResourceAsText("exception_error_info_expected.json")
        val obj = Gson().fromJson(json, ExceptionErrorInfo::class.java)
        assertEquals(0L, obj.timestamp)
        assertEquals("STATE", obj.state)
        val exceptionInfo = obj.exceptions?.get(0)
        assertEquals(info.message, exceptionInfo?.message)
        assertEquals(info.name, exceptionInfo?.name)
        assertEquals(info.lines, exceptionInfo?.lines)
    }
}
