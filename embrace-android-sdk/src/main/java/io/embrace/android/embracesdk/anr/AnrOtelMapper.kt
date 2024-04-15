package io.embrace.android.embracesdk.anr

import io.embrace.android.embracesdk.arch.DataCaptureServiceOtelConverter
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.payload.AnrInterval
import io.embrace.android.embracesdk.payload.AnrSample
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.sdk.trace.IdGenerator

/**
 * Maps captured ANRs to OTel constructs.
 */
internal class AnrOtelMapper(
    private val anrService: AnrService
) : DataCaptureServiceOtelConverter {

    override fun snapshot(): List<Span> {
        val intervals = anrService.getCapturedData()

        return intervals.map { interval ->
            val attrs = mapIntervalToSpanAttributes(interval)
            val events = mapIntervalToSpanEvents(interval)
            Span(
                traceId = IdGenerator.random().generateTraceId(),
                spanId = IdGenerator.random().generateSpanId(),
                parentSpanId = SpanId.getInvalid(),
                name = "emb-thread-blockage",
                startTimeUnixNano = interval.startTime.millisToNanos(),
                endTimeUnixNano = interval.endTime?.millisToNanos(),
                status = Span.Status.OK,
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
            attrs.add(Attribute("thread_state", thread.state.toString()))
            attrs.add(Attribute("thread_priority", thread.priority.toString()))

            thread.lines?.let { lines ->
                attrs.add(Attribute("frame_count", lines.size.toString()))
                attrs.add(Attribute("stacktrace", lines.joinToString("\n")))
            }
        }
        return SpanEvent(
            name = "perf.thread_blockage_sample",
            timeUnixNano = sample.timestamp.millisToNanos(),
            attributes = attrs
        )
    }
}
