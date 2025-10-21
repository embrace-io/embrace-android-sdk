package io.embrace.android.embracesdk.internal.anr

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.payload.AnrInterval
import io.embrace.android.embracesdk.internal.payload.AnrSample
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.factory.TracingIdFactory
import io.embrace.opentelemetry.kotlin.factory.toHexString
import io.embrace.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.embrace.opentelemetry.kotlin.semconv.JvmAttributes

/**
 * Maps captured ANRs to OTel constructs.
 */
@OptIn(ExperimentalApi::class)
class AnrOtelMapper(
    private val anrService: AnrService,
    private val clock: Clock,
    private val spanService: SpanService,
    private val tracingIdFactory: TracingIdFactory,
) {
    fun snapshot(): List<Span> = EmbTrace.trace("anr-snapshot") {
        anrService.getCapturedData().map { interval ->
            val attrs = mapIntervalToSpanAttributes(interval)
            val events = mapIntervalToSpanEvents(interval)
            Span(
                traceId = tracingIdFactory.generateTraceIdBytes().toHexString(),
                spanId = tracingIdFactory.generateSpanIdBytes().toHexString(),
                parentSpanId = OtelIds.INVALID_SPAN_ID,
                name = "emb-thread-blockage",
                startTimeNanos = interval.startTime.millisToNanos(),
                endTimeNanos = (interval.endTime ?: clock.now()).millisToNanos(),
                status = Span.Status.UNSET,
                attributes = attrs,
                events = events
            )
        }
    }

    fun record() = EmbTrace.trace("anr-record") {
        anrService.getCapturedData().forEach { interval ->
            val attributes = mapIntervalToSpanAttributes(interval).toEmbracePayload()
            val events = interval.anrSampleList?.samples?.mapNotNull { mapSampleToSpanEvent(it).toEmbracePayload() }
            spanService.recordCompletedSpan(
                name = "thread-blockage",
                startTimeMs = interval.startTime,
                endTimeMs = interval.endTime ?: clock.now(),
                type = EmbType.Performance.ThreadBlockage,
                attributes = attributes,
                events = events ?: emptyList(),
            )
        }
    }

    private fun mapIntervalToSpanAttributes(interval: AnrInterval): List<Attribute> {
        val attrs = mutableListOf<Attribute>()
        attrs.add(Attribute("emb.type", "perf.thread_blockage"))

        interval.code?.let {
            attrs.add(Attribute("interval_code", it.toString()))
        }
        interval.lastKnownTime?.let {
            attrs.add(Attribute("last_known_time_unix_nano", it.millisToNanos().toString()))
        }
        return attrs
    }

    private fun mapIntervalToSpanEvents(interval: AnrInterval): List<SpanEvent> {
        return interval.anrSampleList?.samples?.map(::mapSampleToSpanEvent) ?: emptyList()
    }

    private fun mapSampleToSpanEvent(sample: AnrSample): SpanEvent {
        val attrs = mutableListOf<Attribute>()
        attrs.add(Attribute("emb.type", "perf.thread_blockage_sample"))

        sample.sampleOverheadMs?.let {
            attrs.add(Attribute("sample_overhead", it.millisToNanos().toString()))
        }
        sample.code?.let {
            attrs.add(Attribute("sample_code", it.toString()))
        }
        sample.threads?.singleOrNull()?.let { thread ->
            attrs.add(Attribute(JvmAttributes.JVM_THREAD_STATE, thread.state.toString()))
            attrs.add(Attribute("thread_priority", thread.priority.toString()))
            attrs.add(Attribute("frame_count", thread.frameCount.toString()))

            thread.lines?.let { lines ->
                attrs.add(Attribute(ExceptionAttributes.EXCEPTION_STACKTRACE, lines.joinToString("\n")))
            }
        }
        return SpanEvent(
            name = "perf.thread_blockage_sample",
            timestampNanos = sample.timestamp.millisToNanos(),
            attributes = attrs
        )
    }
}
