package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.payload.LegacyExceptionError
import io.embrace.android.embracesdk.payload.LegacyExceptionErrorInfo
import io.embrace.android.embracesdk.payload.LegacyExceptionInfo
import org.junit.Assert.assertEquals
import org.junit.Test

internal class MapInternalErrorTest {

    @Test
    fun `convert to model`() {
        val input = LegacyExceptionError()
        input.occurrences = 1
        input.exceptionErrors.add(
            LegacyExceptionErrorInfo(
                timestamp = 0,
                exceptions = listOf(
                    LegacyExceptionInfo(
                        name = "name",
                        message = "message",
                        lines = listOf("line1", "line2")
                    )
                )
            )
        )

        // validate transform
        val output = input.toNewPayload()
        assertEquals(1, output.count)

        val error = checkNotNull(output.errors).single()
        assertEquals(0L, error.timestamp)

        val exception = checkNotNull(error.exceptions).single()
        assertEquals("name", exception.name)
        assertEquals("message", exception.message)
        assertEquals(listOf("line1", "line2"), exception.stacktrace)
    }
}
