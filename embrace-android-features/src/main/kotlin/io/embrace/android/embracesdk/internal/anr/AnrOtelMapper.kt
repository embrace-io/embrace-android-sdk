package io.embrace.android.embracesdk.internal.anr

import io.embrace.android.embracesdk.internal.arch.DataCaptureServiceOtelConverter
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.payload.AnrInterval
import io.embrace.android.embracesdk.internal.payload.AnrSample
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.sdk.trace.IdGenerator
import io.opentelemetry.semconv.ExceptionAttributes
import io.opentelemetry.semconv.JvmAttributes

/**
 * Maps captured ANRs to OTel constructs.
 */
class AnrOtelMapper(
    private val anrService: AnrService,
    private val clock: Clock
) : DataCaptureServiceOtelConverter {

    override fun snapshot(isFinalPayload: Boolean): List<Span> {
        val intervals = anrService.getCapturedData()

        return intervals.map { interval ->
            val attrs = mapIntervalToSpanAttributes(interval)
            val events = mapIntervalToSpanEvents(interval)
            Span(
                traceId = IdGenerator.random().generateTraceId(),
                spanId = IdGenerator.random().generateSpanId(),
                parentSpanId = SpanId.getInvalid(),
                name = "emb-thread-blockage",
                startTimeNanos = interval.startTime.millisToNanos(),
                endTimeNanos = (interval.endTime ?: clock.now()).millisToNanos(),
                status = Span.Status.UNSET,
                attributes = attrs,
                events = events
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
            attrs.add(Attribute(JvmAttributes.JVM_THREAD_STATE.key, thread.state.toString()))
            attrs.add(Attribute("thread_priority", thread.priority.toString()))

            thread.lines?.let { lines ->
                attrs.add(Attribute("frame_count", lines.size.toString()))
                attrs.add(Attribute(ExceptionAttributes.EXCEPTION_STACKTRACE.key, lines.joinToString("\n")))
            }
        }
        return SpanEvent(
            name = "perf.thread_blockage_sample",
            timestampNanos = sample.timestamp.millisToNanos(),
            attributes = attrs
        )
    }
}
