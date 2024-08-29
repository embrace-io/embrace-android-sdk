package io.embrace.android.embracesdk.internal.anr.ndk

import io.embrace.android.embracesdk.internal.TypeUtils
import io.embrace.android.embracesdk.internal.arch.DataCaptureServiceOtelConverter
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.NativeThreadAnrInterval
import io.embrace.android.embracesdk.internal.payload.NativeThreadAnrSample
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.sdk.trace.IdGenerator
import io.opentelemetry.semconv.ExceptionAttributes
import io.opentelemetry.semconv.JvmAttributes
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes

public class NativeAnrOtelMapper(
    private val nativeThreadSamplerService: NativeThreadSamplerService?,
    private val serializer: PlatformSerializer,
    private val clock: Clock
) : DataCaptureServiceOtelConverter {

    override fun snapshot(isFinalPayload: Boolean): List<Span> {
        if (nativeThreadSamplerService == null) {
            return emptyList()
        }
        val intervals: List<NativeThreadAnrInterval> =
            nativeThreadSamplerService.getCapturedIntervals(isFinalPayload) ?: emptyList()

        return intervals.map { interval ->
            val attrs = mapIntervalToSpanAttributes(interval)
            val events = mapIntervalToSpanEvents(interval)
            Span(
                traceId = IdGenerator.random().generateTraceId(),
                spanId = IdGenerator.random().generateSpanId(),
                parentSpanId = SpanId.getInvalid(),
                name = "emb_native_thread_blockage",
                startTimeNanos = interval.threadBlockedTimestamp?.millisToNanos(),
                endTimeNanos = clock.now().millisToNanos(),
                status = Span.Status.UNSET,
                attributes = attrs,
                events = events
            )
        }
    }

    private fun mapIntervalToSpanAttributes(interval: NativeThreadAnrInterval): List<Attribute> {
        val attrs = mutableListOf<Attribute>()
        attrs.add(Attribute("emb.type", "perf.native_thread_blockage"))

        interval.id?.let {
            attrs.add(Attribute(ThreadIncubatingAttributes.THREAD_ID.key, it.toString()))
        }
        interval.name?.let {
            attrs.add(Attribute(ThreadIncubatingAttributes.THREAD_NAME.key, it))
        }
        interval.priority?.let {
            attrs.add(Attribute("thread_priority", it.toString()))
        }
        interval.state?.let {
            attrs.add(Attribute(JvmAttributes.JVM_THREAD_STATE.key, it.toString()))
        }
        interval.sampleOffsetMs?.let {
            attrs.add(Attribute("sampling_offset_ms", it.toString()))
        }
        interval.unwinder?.let {
            attrs.add(Attribute("stack_unwinder", it.toString()))
        }
        return attrs
    }

    private fun mapIntervalToSpanEvents(interval: NativeThreadAnrInterval): List<SpanEvent> {
        return interval.samples?.map(::mapSampleToSpanEvent) ?: emptyList()
    }

    private fun mapSampleToSpanEvent(sample: NativeThreadAnrSample): SpanEvent {
        val attrs = mutableListOf<Attribute>()
        attrs.add(Attribute("emb.type", "perf.native_thread_blockage_sample"))

        sample.result?.let {
            attrs.add(Attribute("result", it.toString()))
        }
        sample.sampleDurationMs?.let {
            attrs.add(Attribute("sample_overhead_ms", it.toString()))
        }
        val frames = sample.stackframes?.map { frame ->
            NativeAnrSampleFrame(
                programCounter = frame.pc,
                soLoadAddr = frame.soLoadAddr,
                soName = frame.soPath,
                result = frame.result
            )
        }
        frames?.let { stacktrace ->
            val json = serializer.toJson(stacktrace, TypeUtils.typedList(NativeAnrSampleFrame::class))
            attrs.add(Attribute(ExceptionAttributes.EXCEPTION_STACKTRACE.key, json))
        }
        return SpanEvent(
            name = "emb_native_thread_blockage_sample",
            timestampNanos = sample.sampleTimestamp?.millisToNanos(),
            attributes = attrs
        )
    }
}
