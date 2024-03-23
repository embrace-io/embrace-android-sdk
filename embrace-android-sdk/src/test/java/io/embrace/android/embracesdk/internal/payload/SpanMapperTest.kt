package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.arch.assertError
import io.embrace.android.embracesdk.arch.assertIsKeySpan
import io.embrace.android.embracesdk.arch.assertIsTypePerformance
import io.embrace.android.embracesdk.arch.assertNotPrivateSpan
import io.embrace.android.embracesdk.arch.assertSuccessful
import io.embrace.android.embracesdk.fakes.FakeSpanData
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SpanMapperTest {

    @Test
    fun toSpan() {
        val input = EmbraceSpanData(FakeSpanData.perfSpanCompleted)
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
        assertEquals(inputEvent, outputEvent.toOldPayload())

        // test attributes
        output.assertSuccessful()
        output.assertIsTypePerformance()
        output.assertIsKeySpan()
        output.assertNotPrivateSpan()
        checkNotNull(output.attributes).forEach {
            assertEquals(input.attributes[it.key], it.data)
        }
    }

    @Test
    fun `terminating span snapshot works as expected`() {
        val snapshot = EmbraceSpanData(FakeSpanData.perfSpanSnapshot).toNewPayload()
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
        failedSpan.assertError(ErrorCode.FAILURE)
        failedSpan.assertIsTypePerformance()
        failedSpan.assertIsKeySpan()
        failedSpan.assertNotPrivateSpan()
        checkNotNull(snapshot.attributes).forEach {
            assertEquals(failedSpan.attributes[it.key], it.data)
        }
    }
}
