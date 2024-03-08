package io.embrace.android.embracesdk

import com.squareup.moshi.JsonDataException
import io.embrace.android.embracesdk.payload.LegacyExceptionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class LegacyExceptionInfoTest {

    private val info = LegacyExceptionInfo(
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
        val obj = deserializeJsonFromResource<LegacyExceptionInfo>("exception_info_expected.json")
        assertEquals("java.lang.IllegalStateException", obj.name)
        assertEquals("Whoops!", obj.message)
        assertEquals("java.base/java.lang.Thread.getStackTrace(Thread.java:1602)", obj.lines[0])
        assertEquals(
            "io.embrace.android.embracesdk.ThreadInfoTest.testThreadInfoSerialization(ThreadInfoTest.kt:18)",
            obj.lines[1]
        )
    }

    @Test(expected = JsonDataException::class)
    fun testExceptionInfoEmptyObject() {
        deserializeEmptyJsonString<LegacyExceptionInfo>()
    }

    @Test
    fun testOfThrowable() {
        val throwable = object : Throwable("UhOh.") {}
        val info = LegacyExceptionInfo.ofThrowable(throwable)
        assertNotNull(info)
        assertEquals("UhOh.", info.message)
        assertEquals("io.embrace.android.embracesdk.LegacyExceptionInfoTest\$testOfThrowable\$throwable\$1", info.name)
        assertEquals(
            "io.embrace.android.embracesdk.LegacyExceptionInfoTest.testOfThrowable(LegacyExceptionInfoTest.kt:45)",
            info.lines.first()
        )
        assertNull(info.originalLength)
    }

    @Test
    fun testMaxStacktraceLimit() {
        val limit = 200
        val len = limit + 100
        val obj = LegacyExceptionInfo(
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
