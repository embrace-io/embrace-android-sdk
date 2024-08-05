package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.payload.LegacyExceptionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class LegacyExceptionInfoTest {

    @Test
    fun testOfThrowable() {
        val throwable = object : Throwable("UhOh.") {}
        val info = LegacyExceptionInfo.ofThrowable(throwable)
        assertNotNull(info)
        assertEquals("UhOh.", info.message)
        assertEquals("io.embrace.android.embracesdk.LegacyExceptionInfoTest\$testOfThrowable\$throwable\$1", info.name)
        assertEquals(
            "io.embrace.android.embracesdk.LegacyExceptionInfoTest.testOfThrowable(LegacyExceptionInfoTest.kt:13)",
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
