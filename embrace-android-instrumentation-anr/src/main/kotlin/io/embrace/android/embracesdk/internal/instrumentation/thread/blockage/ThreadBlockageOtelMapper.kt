package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import io.embrace.android.embracesdk.internal.arch.datasource.SpanEventImpl
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.embrace.opentelemetry.kotlin.semconv.JvmAttributes
import kotlin.random.Random

private const val INVALID_SPAN_ID: String = "0000000000000000"

internal fun mapIntervalToSpan(interval: ThreadBlockageInterval, clock: Clock, random: Random): Span {
    val attrs = mapIntervalToSpanAttributes(interval)
    val events = mapIntervalToSpanEvents(interval)
    return Span(
        traceId = generateId(16, random),
        spanId = generateId(8, random),
        parentSpanId = INVALID_SPAN_ID,
        name = "emb-thread-blockage",
        startTimeNanos = interval.startTime.millisToNanos(),
        endTimeNanos = (interval.endTime ?: clock.now()).millisToNanos(),
        status = Span.Status.UNSET,
        attributes = attrs,
        events = events
    )
}

internal fun mapIntervalToSpanAttributes(interval: ThreadBlockageInterval): List<Attribute> {
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

private fun mapIntervalToSpanEvents(interval: ThreadBlockageInterval): List<SpanEvent> {
    return interval.samples?.map(::mapSampleToSpanEvent) ?: emptyList()
}

internal fun mapSampleToSpanEvent(sample: ThreadBlockageSample): SpanEvent {
    val attrs = mutableListOf<Attribute>()
    attrs.add(Attribute("emb.type", "perf.thread_blockage_sample"))

    sample.sampleOverheadMs?.let {
        attrs.add(Attribute("sample_overhead", it.millisToNanos().toString()))
    }
    sample.code?.let {
        attrs.add(Attribute("sample_code", it.toString()))
    }
    sample.threadSample?.let { threadTrace ->
        attrs.add(Attribute(JvmAttributes.JVM_THREAD_STATE, threadTrace.state.toString()))
        attrs.add(Attribute("thread_priority", threadTrace.priority.toString()))
        attrs.add(Attribute("frame_count", threadTrace.frameCount.toString()))

        threadTrace.lines?.let { lines ->
            attrs.add(
                Attribute(
                    ExceptionAttributes.EXCEPTION_STACKTRACE,
                    lines.joinToString("\n")
                )
            )
        }
    }
    return SpanEvent(
        name = "perf.thread_blockage_sample",
        timestampNanos = sample.timestamp.millisToNanos(),
        attributes = attrs
    )
}

internal fun generateId(numBytes: Int, random: Random): String {
    val bytes = ByteArray(numBytes)
    do {
        random.nextBytes(bytes)
    } while (bytes.all { it == 0.toByte() })
    return bytes.joinToString("") { "%02x".format(it) }
}

internal fun List<Attribute>.toEmbracePayload(): Map<String, String> =
    associate { Pair(it.key ?: "", it.data ?: "") }.filterKeys { it.isNotBlank() }

internal fun SpanEvent.toArchSpanEvent(): io.embrace.android.embracesdk.internal.arch.datasource.SpanEvent =
    SpanEventImpl(
        name ?: "",
        timestampNanos ?: 0,
        attributes?.toEmbracePayload() ?: emptyMap()
    )
