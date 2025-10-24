package io.embrace.android.embracesdk.internal.otel.payload

import io.embrace.android.embracesdk.assertions.assertError
import io.embrace.android.embracesdk.assertions.assertIsTypePerformance
import io.embrace.android.embracesdk.assertions.assertNotPrivateSpan
import io.embrace.android.embracesdk.assertions.assertSuccessful
import io.embrace.android.embracesdk.fakes.FakeSpanData
import io.embrace.android.embracesdk.internal.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.otel.sdk.setEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.toEmbracePayload
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.toEmbraceSpanData
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class SpanMapperTest {

    @OptIn(ExperimentalApi::class)
    @Test
    fun toSpan() {
        val input = FakeSpanData.perfSpanCompleted.toEmbraceSpanData()
        val output = input.toEmbracePayload()

        assertEquals(input.traceId, output.traceId)
        assertEquals(input.spanId, output.spanId)
        assertEquals(input.parentSpanId, output.parentSpanId)
        assertEquals(input.name, output.name)
        assertEquals(input.startTimeNanos, output.startTimeNanos)
        assertEquals(input.endTimeNanos, output.endTimeNanos)
        assertEquals(input.status.toEmbracePayload().name, checkNotNull(output.status).name)

        // validate event copied
        val inputEvent = input.events.single()
        val outputEvent = checkNotNull(output.events).single()
        assertEquals(inputEvent, outputEvent.toEmbracePayload())

        // test attributes
        output.assertSuccessful()
        output.assertIsTypePerformance()
        output.assertNotPrivateSpan()
        checkNotNull(output.attributes).forEach {
            assertEquals(input.attributes[it.key], it.data)
        }
    }

    @Test
    fun `terminating span snapshot works as expected`() {
        val snapshot = FakeSpanData.perfSpanSnapshot.toEmbraceSpanData().toEmbracePayload()
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
        failedSpan.assertNotPrivateSpan()
        val attributesOfFailedSpan = failedSpan.attributes?.associate { it.key to it.data } ?: emptyMap()
        checkNotNull(snapshot.attributes).forEach {
            assertEquals(attributesOfFailedSpan[it.key], it.data)
        }
    }

    private fun Span.toFailedSpan(endTimeMs: Long): Span {
        val newAttributes = mutableMapOf<String, String>().apply {
            setEmbraceAttribute(ErrorCodeAttribute.Failure)
            if (hasEmbraceAttribute(EmbType.Ux.Session)) {
                setEmbraceAttribute(AppTerminationCause.Crash)
            }
        }

        return copy(
            endTimeNanos = endTimeMs.millisToNanos(),
            parentSpanId = parentSpanId ?: OtelIds.INVALID_SPAN_ID,
            status = Span.Status.ERROR,
            attributes = newAttributes.map { Attribute(it.key, it.value) }.plus(attributes ?: emptyList())
        )
    }
}
