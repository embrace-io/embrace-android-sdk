package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.payload.toNewPayload
import org.junit.Assert.assertEquals
import org.junit.Test

internal class LogMapperTest {

    @Test
    fun `convert to new payload`() {
        val input = FakeLogRecordData()
        val output = input.toNewPayload()
        assertEquals(input.timestampEpochNanos, output.timeUnixNano)
        assertEquals(input.severity.severityNumber, output.severityNumber)
        assertEquals(input.severityText, output.severityText)
        assertEquals(input.body.asString(), checkNotNull(output.body).message)
        assertEquals(input.spanContext.traceId, output.traceId)
        assertEquals(input.spanContext.spanId, output.spanId)

        val inputMap = input.attributes.asMap().entries.associate { it.key.key to it.value }
        val outputMap = checkNotNull(output.attributes).associateBy { it.key }.mapValues { it.value.data }
        assertEquals(inputMap, outputMap)
    }
}
