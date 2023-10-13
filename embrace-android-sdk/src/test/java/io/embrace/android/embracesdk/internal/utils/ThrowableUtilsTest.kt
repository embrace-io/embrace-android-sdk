package io.embrace.android.embracesdk.internal.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class ThrowableUtilsTest {

    @Test
    fun `test throwableName`() {
        assertEquals("name should be empty string if the Throwable is null", causeName(null), "")
        assertEquals(
            "name should be empty string if the Throwable's cause is null",
            causeName(RuntimeException("message", null)),
            ""
        )
        assertEquals(
            "name is unexpected",
            causeName(
                RuntimeException("message", IllegalArgumentException())
            ),
            IllegalArgumentException::class.qualifiedName
        )
    }

    @Test
    fun `test throwableMessage`() {
        assertEquals(
            "message should be empty string if Throwable is null",
            causeMessage(null),
            ""
        )
        assertEquals(
            "message should be empty string if the Throwable's cause is null",
            causeMessage(RuntimeException("message", null)),
            ""
        )
        assertEquals(
            "message should be empty string if the Throwable's cause's message is null",
            causeMessage(RuntimeException("message", IllegalArgumentException())),
            ""
        )
        val message = "this is a message"
        assertEquals(
            "message is unexpected",
            causeMessage(RuntimeException("message", IllegalArgumentException(message))),
            message
        )
    }

    @Test
    fun `test safe stacktrace`() {
        assertNull(DangerousException().getSafeStackTrace())
    }

    class DangerousException : Exception("DangerousException") {
        override fun getStackTrace(): Array<StackTraceElement> = error("lol")
    }
}
