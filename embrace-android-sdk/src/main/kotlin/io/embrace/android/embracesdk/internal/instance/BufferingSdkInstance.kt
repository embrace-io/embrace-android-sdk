@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.experiments.TrackedEntry
import io.embrace.android.embracesdk.experiments.TrackedExperiment
import io.embrace.android.embracesdk.experiments.TrackedFeatureFlag
import io.embrace.android.embracesdk.internal.api.ExperimentApi
import io.embrace.android.embracesdk.internal.api.OTelApi
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentApiCall
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentKind
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedData
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.resolved.ExperimentConfig
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.TracingApi
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import java.util.concurrent.TimeUnit

/**
 * An [SdkApi] implementation for use before the SDK has started. Calls whose effect can be applied later are buffered
 * so they can be drained into the started SDK.
 *
 * Once a group is drained subsequent calls in that group are forwarded straight to the real SDK instance.
 */
internal class BufferingSdkInstance(
    private val clock: Clock,
    private val logger: InternalLogger,
) : SdkApi by NoopSdkInstance(logger, NoopSdkInstance.SDK_NOT_INITIALIZED) {

    private val otelConfigCalls = CallBuffer<OTelApi, OTelConfigCall>()
    private val completedSpanCalls = CallBuffer<TracingApi, CompletedSpanCall>()
    private val experimentCalls = CallBuffer<ExperimentApi, ExperimentApiCall>()

    private var bufferedOTelConfigCount = 0
    private var bufferedSpanCount = 0
    private var bufferedExperimentEntryCount = 0

    @Volatile
    var applicationInitStartMs: Long? = null
        private set

    fun drainOTelConfig(target: OTelApi) {
        otelConfigCalls.drain(target) { calls ->
            calls.forEach { call ->
                guard {
                    when (call) {
                        is OTelConfigCall.AddSpanExporter -> target.addSpanExporter(call.exporter)
                        is OTelConfigCall.AddSpanProcessor -> target.addSpanProcessor(call.processor)
                        is OTelConfigCall.AddLogRecordExporter -> target.addLogRecordExporter(call.exporter)
                        is OTelConfigCall.AddLogRecordProcessor -> target.addLogRecordProcessor(call.processor)
                        is OTelConfigCall.SetResourceAttribute -> target.setResourceAttribute(call.key, call.value)
                    }
                }
            }
        }
    }

    fun drainCompletedSpans(target: TracingApi) {
        completedSpanCalls.drain(target) { calls ->
            calls.forEach { call ->
                guard {
                    target.recordCompletedSpan(
                        call.name,
                        call.startTimeMs,
                        call.endTimeMs,
                        call.errorCode,
                        call.parent,
                        call.attributes,
                        call.events,
                    )
                }
            }
        }
    }

    fun drainExperimentCalls(target: ExperimentApi, replay: (List<ExperimentApiCall>) -> Unit) {
        experimentCalls.drain(target) { calls -> guard { replay(calls) } }
    }

    override fun addSpanExporter(spanExporter: SpanExporter) {
        otelConfigCalls.submit(
            buffer = { calls -> bufferOTelConfig(calls, OTelConfigCall.AddSpanExporter(spanExporter)) },
            forward = { it.addSpanExporter(spanExporter) },
        )
    }

    override fun addSpanProcessor(spanProcessor: SpanProcessor) {
        otelConfigCalls.submit(
            buffer = { calls -> bufferOTelConfig(calls, OTelConfigCall.AddSpanProcessor(spanProcessor)) },
            forward = { it.addSpanProcessor(spanProcessor) },
        )
    }

    override fun addLogRecordExporter(logRecordExporter: LogRecordExporter) {
        otelConfigCalls.submit(
            buffer = { calls -> bufferOTelConfig(calls, OTelConfigCall.AddLogRecordExporter(logRecordExporter)) },
            forward = { it.addLogRecordExporter(logRecordExporter) },
        )
    }

    override fun addLogRecordProcessor(logRecordProcessor: LogRecordProcessor) {
        otelConfigCalls.submit(
            buffer = { calls -> bufferOTelConfig(calls, OTelConfigCall.AddLogRecordProcessor(logRecordProcessor)) },
            forward = { it.addLogRecordProcessor(logRecordProcessor) },
        )
    }

    override fun setResourceAttribute(key: String, value: String) {
        otelConfigCalls.submit(
            buffer = { calls -> bufferOTelConfig(calls, OTelConfigCall.SetResourceAttribute(key, value)) },
            forward = { it.setResourceAttribute(key, value) },
        )
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
    ): Boolean {
        return completedSpanCalls.submit(
            buffer = { calls ->
                if (bufferedSpanCount >= MAX_BUFFERED_SPAN_CALLS) {
                    false
                } else {
                    val call = CompletedSpanCall(
                        name = name,
                        startTimeMs = startTimeMs,
                        endTimeMs = endTimeMs,
                        errorCode = errorCode,
                        parent = parent,
                        attributes = attributes.toMap(),
                        events = events.map(::copyEvent),
                    )
                    bufferedSpanCount++
                    calls.add(call)
                    true
                }
            },
            forward = { it.recordCompletedSpan(name, startTimeMs, endTimeMs, errorCode, parent, attributes, events) },
        )
    }

    override fun applicationInitStart() {
        if (applicationInitStartMs == null) {
            applicationInitStartMs = clock.now()
        }
    }

    override fun getSdkCurrentTimeMs(): Long = 0

    // avoid default implementations getting delegate to the no-op instance - route explicitly

    override fun trackExperiment(id: String, variant: String?, startedAt: Long?) {
        trackExperiments(listOf(createExperiment(id, variant, startedAt)))
    }

    override fun untrackExperiment(id: String, endedAt: Long?) {
        untrackExperiments(listOf(id), endedAt)
    }

    override fun trackFeatureFlag(id: String, variant: String?, startedAt: Long?) {
        trackFeatureFlags(listOf(createFeatureFlag(id, variant, startedAt)))
    }

    override fun untrackFeatureFlag(id: String, endedAt: Long?) {
        untrackFeatureFlags(listOf(id), endedAt)
    }

    override fun trackExperiments(experiments: List<TrackedExperiment>) {
        experimentCalls.submit(
            buffer = { calls -> track(calls, experiments, ExperimentKind.EXPERIMENT) },
            forward = { it.trackExperiments(experiments) },
        )
    }

    override fun untrackExperiments(ids: List<String>, endedAt: Long?) {
        experimentCalls.submit(
            buffer = { calls -> untrack(calls, ids, ExperimentKind.EXPERIMENT, endedAt) },
            forward = { it.untrackExperiments(ids, endedAt) },
        )
    }

    override fun trackFeatureFlags(flags: List<TrackedFeatureFlag>) {
        experimentCalls.submit(
            buffer = { calls -> track(calls, flags, ExperimentKind.FEATURE_FLAG) },
            forward = { it.trackFeatureFlags(flags) },
        )
    }

    override fun untrackFeatureFlags(ids: List<String>, endedAt: Long?) {
        experimentCalls.submit(
            buffer = { calls -> untrack(calls, ids, ExperimentKind.FEATURE_FLAG, endedAt) },
            forward = { it.untrackFeatureFlags(ids, endedAt) },
        )
    }

    private inline fun guard(action: () -> Unit) {
        try {
            action()
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.PublicApiFail, exc)
        }
    }

    private fun bufferOTelConfig(calls: MutableList<OTelConfigCall>, call: OTelConfigCall) {
        if (bufferedOTelConfigCount < MAX_BUFFERED_OTEL_CONFIG_CALLS) {
            bufferedOTelConfigCount++
            calls.add(call)
        }
    }

    private fun copyEvent(event: EmbraceSpanEvent): EmbraceSpanEvent = checkNotNull(
        EmbraceSpanEvent.create(
            name = event.name,
            timestampMs = TimeUnit.NANOSECONDS.toMillis(event.timestampNanos),
            attributes = event.attributes.toMap(),
        ),
    )

    private fun track(calls: MutableList<ExperimentApiCall>, entries: List<TrackedEntry>, kind: ExperimentKind) {
        val admitted = admitExperimentEntries(entries) ?: return
        val call = ExperimentApiCall.Track(
            admitted.map { entry ->
                TrackedData(
                    kind = kind,
                    id = entry.id,
                    startTimeMs = entry.startedAt ?: clock.now(),
                    variant = entry.variant,
                )
            },
        )
        bufferedExperimentEntryCount += admitted.size
        calls.add(call)
    }

    private fun untrack(calls: MutableList<ExperimentApiCall>, ids: List<String>, kind: ExperimentKind, endedAt: Long?) {
        val admitted = admitExperimentEntries(ids) ?: return
        val call = ExperimentApiCall.Untrack(kind, admitted, endedAt ?: clock.now())
        bufferedExperimentEntryCount += admitted.size
        calls.add(call)
    }

    /**
     * Returns a copy of the entries that fit under the buffer's cap, or null if none do.
     */
    private fun <T> admitExperimentEntries(entries: List<T>): List<T>? {
        val remaining = MAX_BUFFERED_EXPERIMENT_ENTRIES - bufferedExperimentEntryCount
        if (entries.isEmpty() || remaining <= 0) {
            return null
        }
        return entries.take(remaining)
    }

    private sealed class OTelConfigCall {
        class AddSpanExporter(val exporter: SpanExporter) : OTelConfigCall()
        class AddSpanProcessor(val processor: SpanProcessor) : OTelConfigCall()
        class AddLogRecordExporter(val exporter: LogRecordExporter) : OTelConfigCall()
        class AddLogRecordProcessor(val processor: LogRecordProcessor) : OTelConfigCall()
        class SetResourceAttribute(val key: String, val value: String) : OTelConfigCall()
    }

    private class CompletedSpanCall(
        val name: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val errorCode: ErrorCode?,
        val parent: EmbraceSpan?,
        val attributes: Map<String, String>,
        val events: List<EmbraceSpanEvent>,
    )

    /**
     * Buffers calls of type [C] until [drain] supplies a target of type [T], after which calls are forwarded to that
     * instead.
     */
    private class CallBuffer<T : Any, C : Any> {
        // properties not private to avoid synthetic accessors
        val lock = Any()

        var calls = mutableListOf<C>()
            private set

        @Volatile
        var target: T? = null
            private set

        /**
         * Calls [forward] with the target if one has been set. Otherwise, calls [buffer] with the buffered calls.
         */
        inline fun <R> submit(buffer: (MutableList<C>) -> R, forward: (T) -> R): R {
            val current = target ?: synchronized(lock) {
                target ?: return buffer(calls)
            }
            return forward(current)
        }

        /**
         * Passes the buffered calls to [replay], then forwards all later calls to [target].
         */
        fun drain(target: T, replay: (List<C>) -> Unit) {
            while (true) {
                val batch = synchronized(lock) {
                    if (calls.isEmpty()) {
                        this.target = target
                        return
                    }
                    calls.also { calls = mutableListOf() }
                }
                replay(batch)
            }
        }
    }

    private companion object {
        private const val MAX_BUFFERED_OTEL_CONFIG_CALLS = 1000
        private const val MAX_BUFFERED_SPAN_CALLS = 1000
        private const val MAX_BUFFERED_EXPERIMENT_ENTRIES = ExperimentConfig.MAX_COUNT_LIMIT
    }
}
