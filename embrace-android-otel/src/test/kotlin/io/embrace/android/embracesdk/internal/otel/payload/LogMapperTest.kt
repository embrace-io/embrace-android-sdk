package io.embrace.android.embracesdk.internal.otel.payload

import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.internal.otel.sdk.toStringMap
import org.junit.Assert.assertEquals
import org.junit.Test

internal class LogMapperTest {

    @Suppress("DEPRECATION")
    @Test
    fun `convert to new payload`() {
        val input = FakeLogRecordData()
        val output = input.toEmbracePayload()
        assertEquals(input.timestampEpochNanos, output.timeUnixNano)
        assertEquals(input.severity.severityNumber, output.severityNumber)
        assertEquals(input.severityText, output.severityText)
        assertEquals(input.body.asString(), checkNotNull(output.body))
        assertEquals(input.spanContext.traceId, output.traceId)
        assertEquals(input.spanContext.spanId, output.spanId)

        val inputMap = input.attributes.toStringMap()
        val outputMap = checkNotNull(output.attributes).associateBy { it.key }.mapValues { it.value.data }
        assertEquals(inputMap, outputMap)
    }
}
