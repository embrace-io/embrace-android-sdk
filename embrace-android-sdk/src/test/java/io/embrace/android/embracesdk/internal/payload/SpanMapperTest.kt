package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.arch.assertHasEmbraceAttribute
import io.embrace.android.embracesdk.arch.assertIsKeySpan
import io.embrace.android.embracesdk.arch.assertIsTypePerformance
import io.embrace.android.embracesdk.arch.assertNotPrivateSpan
import io.embrace.android.embracesdk.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.fakes.FakeSpanData
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SpanMapperTest {

    @Test
    fun toSpan() {
        val input = EmbraceSpanData(FakeSpanData())
        val output = input.toNewPayload()

        assertEquals(input.traceId, output.traceId)
        assertEquals(input.spanId, output.spanId)
        assertEquals(input.parentSpanId, output.parentSpanId)
        assertEquals(input.name, output.name)
        assertEquals(input.startTimeNanos, output.startTimeUnixNano)
        assertEquals(input.endTimeNanos, output.endTimeUnixNano)
        assertEquals(input.status.name, checkNotNull(output.status).name)

        // validate event copied
        val inputEvent = input.events.single()
        val outputEvent = checkNotNull(output.events).single()
        assertEquals(inputEvent.name, outputEvent.name)
        assertEquals(inputEvent.timestampNanos, outputEvent.timeUnixNano)

        // test event attributes
        val inputEventAttrs = checkNotNull(inputEvent.attributes)
        val outputEventAttrs = checkNotNull(outputEvent.attributes?.single())
        assertEquals(inputEventAttrs.keys.single(), outputEventAttrs.key)
        assertEquals(inputEventAttrs.values.single(), outputEventAttrs.data)

        // test attributes
        val inputAttrs = checkNotNull(input.attributes)
        val outputAttrs = checkNotNull(output.attributes?.single())
        assertEquals(inputAttrs.keys.single(), outputAttrs.key)
        assertEquals(inputAttrs.values.single(), outputAttrs.data)
    }

    @Test
    fun `terminating span snapshot works as expected`() {
        val snapshot = FakeSpanData.snapshot.toNewPayload()
        val terminationTimeMs = snapshot.startTimeUnixNano!!.nanosToMillis() + 60000L
        val failedSpan = snapshot.toFailedSpan(terminationTimeMs)

        assertEquals(snapshot.traceId, failedSpan.traceId)
        assertEquals(snapshot.spanId, failedSpan.spanId)
        assertEquals(snapshot.parentSpanId, failedSpan.parentSpanId)
        assertEquals(snapshot.name, failedSpan.name)
        assertEquals(snapshot.startTimeUnixNano, failedSpan.startTimeNanos)
        assertEquals(terminationTimeMs, failedSpan.endTimeNanos.nanosToMillis())
        assertEquals(StatusCode.ERROR, checkNotNull(failedSpan.status))
        assertEquals(snapshot.events?.single(), failedSpan.events.single().toNewPayload())
        failedSpan.assertHasEmbraceAttribute(ErrorCodeAttribute.Failure)
        failedSpan.assertIsTypePerformance()
        failedSpan.assertIsKeySpan()
        failedSpan.assertNotPrivateSpan()
        checkNotNull(snapshot.attributes).forEach {
            assertEquals(it.data, failedSpan.attributes[it.key])
        }
    }
}
