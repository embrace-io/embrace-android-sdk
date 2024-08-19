package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.arch.assertError
import io.embrace.android.embracesdk.arch.assertIsKeySpan
import io.embrace.android.embracesdk.arch.assertIsTypePerformance
import io.embrace.android.embracesdk.arch.assertNotPrivateSpan
import io.embrace.android.embracesdk.arch.assertSuccessful
import io.embrace.android.embracesdk.fakes.FakeSpanData
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.spans.toEmbraceSpanData
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SpanMapperTest {

    @Test
    fun toSpan() {
        val input = FakeSpanData.perfSpanCompleted.toEmbraceSpanData()
        val output = input.toNewPayload()

        assertEquals(input.traceId, output.traceId)
        assertEquals(input.spanId, output.spanId)
        assertEquals(input.parentSpanId, output.parentSpanId)
        assertEquals(input.name, output.name)
        assertEquals(input.startTimeNanos, output.startTimeNanos)
        assertEquals(input.endTimeNanos, output.endTimeNanos)
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
        val snapshot = FakeSpanData.perfSpanSnapshot.toEmbraceSpanData().toNewPayload()
        val terminationTimeMs = snapshot.startTimeNanos!!.nanosToMillis() + 60000L
        val failedSpan = snapshot.toFailedSpan(terminationTimeMs)

        assertEquals(snapshot.traceId, failedSpan.traceId)
        assertEquals(snapshot.spanId, failedSpan.spanId)
        assertEquals(snapshot.parentSpanId, failedSpan.parentSpanId)
        assertEquals(snapshot.name, failedSpan.name)
        assertEquals(snapshot.startTimeNanos, failedSpan.startTimeNanos)
        assertEquals(terminationTimeMs, failedSpan.endTimeNanos?.nanosToMillis())
        assertEquals(Span.Status.ERROR, checkNotNull(failedSpan.status))
        assertEquals(snapshot.events?.single(), failedSpan.events?.single())
        failedSpan.assertError(ErrorCode.FAILURE)
        failedSpan.assertIsTypePerformance()
        failedSpan.assertIsKeySpan()
        failedSpan.assertNotPrivateSpan()
        val attributesOfFailedSpan = failedSpan.attributes?.associate { it.key to it.data } ?: emptyMap()
        checkNotNull(snapshot.attributes).forEach {
            assertEquals(attributesOfFailedSpan[it.key], it.data)
        }
    }

    @Test
    fun `terminating span snapshot as old payload works as expected`() {
        val snapshot = FakeSpanData.perfSpanSnapshot.toEmbraceSpanData()
        val terminationTimeMs = snapshot.startTimeNanos.nanosToMillis() + 60000L
        val failedSpan = snapshot.toFailedSpan(terminationTimeMs)

        assertEquals(snapshot.traceId, failedSpan.traceId)
        assertEquals(snapshot.spanId, failedSpan.spanId)
        assertEquals(snapshot.parentSpanId, failedSpan.parentSpanId)
        assertEquals(snapshot.name, failedSpan.name)
        assertEquals(snapshot.startTimeNanos, failedSpan.startTimeNanos)
        assertEquals(terminationTimeMs, failedSpan.endTimeNanos.nanosToMillis())
        assertEquals(StatusCode.ERROR, checkNotNull(failedSpan.status))
        assertEquals(snapshot.events.single(), failedSpan.events.single())
        failedSpan.assertError(ErrorCode.FAILURE)
        failedSpan.assertIsTypePerformance()
        failedSpan.assertIsKeySpan()
        failedSpan.assertNotPrivateSpan()
        checkNotNull(snapshot.attributes).forEach {
            assertEquals(failedSpan.attributes[it.key], it.value)
        }
    }
}
