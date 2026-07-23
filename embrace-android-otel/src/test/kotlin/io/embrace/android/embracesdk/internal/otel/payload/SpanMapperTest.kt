package io.embrace.android.embracesdk.internal.otel.payload

import io.embrace.android.embracesdk.assertions.assertError
import io.embrace.android.embracesdk.assertions.assertIsTypePerformance
import io.embrace.android.embracesdk.assertions.assertNotPrivateSpan
import io.embrace.android.embracesdk.assertions.assertSuccessful
import io.embrace.android.embracesdk.fakes.FakeTraceFlags
import io.embrace.android.embracesdk.fakes.FakeTraceState
import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.spans.toFailedSpan
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.kotlin.InstrumentationScopeInfo
import io.opentelemetry.kotlin.resource.Resource
import io.opentelemetry.kotlin.tracing.SpanContext
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.TraceFlags
import io.opentelemetry.kotlin.tracing.TraceState
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.data.SpanEventData
import io.opentelemetry.kotlin.tracing.data.SpanLinkData
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SpanMapperTest {

    @Test
    fun toSpan() {
        val input = testSpanData(endTimestamp = START_TIME_NANOS + 60_000_000_000L)
        val output = input.toEmbracePayload()

        assertEquals(input.spanContext.traceId, output.traceId)
        assertEquals(input.spanContext.spanId, output.spanId)
        assertEquals(input.parent.spanId, output.parentSpanId)
        assertEquals(input.name, output.name)
        assertEquals(input.startTimestamp, output.startTimeNanos)
        assertEquals(input.endTimestamp, output.endTimeNanos)
        assertEquals(Span.Status.UNSET, checkNotNull(output.status))

        // validate event copied
        val inputEvent = input.events.single()
        val outputEvent = checkNotNull(output.events).single()
        assertEquals(inputEvent.name, outputEvent.name)
        assertEquals(inputEvent.timestamp, outputEvent.timestampNanos)
        assertEquals(listOf(Attribute("event-key", "event-value")), outputEvent.attributes)

        // validate link copied
        val inputLink = input.links.single()
        val outputLink = checkNotNull(output.links).single()
        assertEquals(inputLink.spanContext.traceId, outputLink.traceId)
        assertEquals(inputLink.spanContext.spanId, outputLink.spanId)
        assertEquals(listOf(Attribute("link-key", "link-value")), outputLink.attributes)

        // test attributes
        output.assertSuccessful()
        output.assertIsTypePerformance()
        output.assertNotPrivateSpan()
        checkNotNull(output.attributes).forEach {
            assertEquals(input.attributes[it.key]?.toString(), it.data)
        }
    }

    @Test
    fun `terminating span snapshot works as expected`() {
        val snapshot = testSpanData(endTimestamp = null).toEmbracePayload()
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

    private fun testSpanData(endTimestamp: Long?): SpanData = TestSpanData(
        name = "test-span",
        spanContext = TestSpanContext(traceId = "19bb482ec1c7e6b2f10fb89e0ccc85fa", spanId = "342eb9c7f8cb54ff"),
        parent = TestSpanContext(traceId = "19bb482ec1c7e6b2f10fb89e0ccc85fa", spanId = "5b32a5a2b5a4fa27"),
        startTimestamp = START_TIME_NANOS,
        endTimestamp = endTimestamp,
        status = StatusData.Unset,
        attributes = mapOf(
            EmbType.Performance.Default.asPair(),
            Pair("my-key", "my-value"),
        ),
        events = listOf(
            object : SpanEventData {
                override val name: String = "test-event"
                override val timestamp: Long = START_TIME_NANOS + 1_000_000L
                override val attributes: Map<String, Any> = mapOf("event-key" to "event-value")
            },
        ),
        links = listOf(
            object : SpanLinkData {
                override val spanContext: SpanContext =
                    TestSpanContext(traceId = "032df1b0b1c1e0cbc0bd8b342a2a1f26", spanId = "b5a2c5c5b6b6f2f1")
                override val attributes: Map<String, Any> = mapOf("link-key" to "link-value")
            },
        ),
    )

    private class TestSpanContext(
        override val traceId: String,
        override val spanId: String,
    ) : SpanContext {
        override val isRemote: Boolean = false
        override val isValid: Boolean = true
        override val traceFlags: TraceFlags = FakeTraceFlags()
        override val traceState: TraceState = FakeTraceState()
        override val spanIdBytes: ByteArray = spanId.toByteArray()
        override val traceIdBytes: ByteArray = traceId.toByteArray()
    }

    private class TestSpanData(
        override val name: String,
        override val spanContext: SpanContext,
        override val parent: SpanContext,
        override val startTimestamp: Long,
        override val endTimestamp: Long?,
        override val status: StatusData,
        override val attributes: Map<String, Any>,
        override val events: List<SpanEventData>,
        override val links: List<SpanLinkData>,
    ) : SpanData {
        override val spanKind: SpanKind = SpanKind.INTERNAL
        override val hasEnded: Boolean = endTimestamp != null
        override val resource: Resource
            get() = throw UnsupportedOperationException()
        override val instrumentationScopeInfo: InstrumentationScopeInfo
            get() = throw UnsupportedOperationException()
    }

    private companion object {
        const val START_TIME_NANOS = 1681972471806000000L
    }
}
