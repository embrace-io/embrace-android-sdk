package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.internal.logs.EmbraceLogRecordData
import org.junit.Assert.assertEquals
import org.junit.Test

internal class LogMapperTest {

    @Test
    fun `convert to new payload`() {
        val input = EmbraceLogRecordData(FakeLogRecordData())
        val output = input.toNewPayload()
        assertEquals(input.timeUnixNanos, output.timeUnixNano)
        assertEquals(input.severityNumber, output.severityNumber)
        assertEquals(input.severityText, output.severityText)
        assertEquals(input.body.message, checkNotNull(output.body).message)
        assertEquals(input.traceId, output.traceId)
        assertEquals(input.spanId, output.spanId)

        val inputMap = input.attributes.mapKeys { it.key }.mapValues { it.value.toString() }
        val outputMap = checkNotNull(output.attributes).associateBy { it.key }.mapValues { it.value.data }
        assertEquals(inputMap, outputMap)
    }
}
