package io.embrace.android.embracesdk.internal.instance

import android.util.Log
import io.embrace.android.embracesdk.EmbraceSdk
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.experiments.TrackedExperiment
import io.embrace.android.embracesdk.experiments.TrackedFeatureFlag
import io.embrace.android.embracesdk.internal.api.delegate.LateBindingOpenTelemetry
import io.embrace.android.embracesdk.internal.api.delegate.TrackedExperimentImpl
import io.embrace.android.embracesdk.internal.api.delegate.TrackedFeatureFlagImpl
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The [EmbraceSdk] that is current before `Embrace.start()` is called.
 *
 * It moves through three phases:
 *
 * 1. Before [attach]: calls that must survive start-up are buffered, and every other call is dropped.
 * 2. After [attach]: the real instance is under construction. Calls that configure it (OTel, experiments,
 *    pre-start spans, application init time) go to it directly. Calls that need a started SDK (user identifier,
 *    session properties) are still buffered.
 * 3. After [seal]: every call forwards to whatever instance is current, so a call that read this object as
 *    current just before the swap still reaches the started instance.
 */
internal class PreStartEmbrace(
    private val current: () -> EmbraceSdk,
    private val now: () -> Long = System::currentTimeMillis,
    private val warn: (String) -> Unit = { Log.w("Embrace", it) },
) : ForwardingEmbraceSdk() {

    private val lock = Any()
    private val beforeInit = ArrayList<(StartableEmbrace) -> Unit>()
    private val afterStart = ArrayList<(EmbraceSdk) -> Unit>()
    private var attached: StartableEmbrace? = null
    private val warnedDropped = AtomicBoolean(false)
    private val warnedOverflow = AtomicBoolean(false)

    @Volatile
    private var sealed = false

    private val openTelemetry = LateBindingOpenTelemetry {
        if (sealed) current().getOpenTelemetryKotlin() else NoopOpenTelemetry
    }

    override fun delegate(): EmbraceSdk {
        if (sealed) {
            return current()
        }
        if (warnedDropped.compareAndSet(false, true)) {
            warn("An Embrace API was called before Embrace.start(). Calls made before start are dropped.")
        }
        return DisabledEmbrace
    }

    /**
     * Replays the calls that configure [instance] and routes any later ones straight to it.
     */
    fun attach(instance: StartableEmbrace) {
        synchronized(lock) {
            attached = instance
            beforeInit.forEach { it(instance) }
            beforeInit.clear()
        }
    }

    /**
     * Replays the calls that need a started SDK into the current instance, then forwards everything to it.
     * Must be called after the current instance has been swapped away from this object.
     */
    fun seal() {
        synchronized(lock) {
            val target = current()
            sealed = true
            beforeInit.clear()
            afterStart.forEach { it(target) }
            afterStart.clear()
        }
    }

    override val isStarted: Boolean
        get() = sealed && current().isStarted

    override fun getOpenTelemetryKotlin(): OpenTelemetry = openTelemetry

    override fun createExperiment(id: String, variant: String?, startedAt: Long?): TrackedExperiment =
        TrackedExperimentImpl(id, variant, startedAt)

    override fun createFeatureFlag(id: String, variant: String?, startedAt: Long?): TrackedFeatureFlag =
        TrackedFeatureFlagImpl(id, variant, startedAt)

    override fun addSpanExporter(spanExporter: SpanExporter) = configure { it.addSpanExporter(spanExporter) }

    override fun addSpanProcessor(spanProcessor: SpanProcessor) = configure { it.addSpanProcessor(spanProcessor) }

    override fun addLogRecordExporter(logRecordExporter: LogRecordExporter) =
        configure { it.addLogRecordExporter(logRecordExporter) }

    override fun addLogRecordProcessor(logRecordProcessor: LogRecordProcessor) =
        configure { it.addLogRecordProcessor(logRecordProcessor) }

    override fun setResourceAttribute(key: String, value: String) = configure { it.setResourceAttribute(key, value) }

    override fun applicationInitStart() {
        val timeMs = now()
        configure { it.applicationInitStart(timeMs) }
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
        var result = true
        configure { result = it.recordCompletedSpan(name, startTimeMs, endTimeMs, errorCode, parent, attributes, events) }
        return result
    }

    // timestamps that default to "now" are resolved here so that buffering doesn't move them to start() time
    override fun trackExperiments(experiments: List<TrackedExperiment>) {
        val resolved = experiments.map { TrackedExperimentImpl(it.id, it.variant, it.startedAt ?: now()) }
        configure { it.trackExperiments(resolved) }
    }

    override fun untrackExperiments(ids: List<String>, endedAt: Long?) {
        val timeMs = endedAt ?: now()
        configure { it.untrackExperiments(ids, timeMs) }
    }

    override fun trackFeatureFlags(flags: List<TrackedFeatureFlag>) {
        val resolved = flags.map { TrackedFeatureFlagImpl(it.id, it.variant, it.startedAt ?: now()) }
        configure { it.trackFeatureFlags(resolved) }
    }

    override fun untrackFeatureFlags(ids: List<String>, endedAt: Long?) {
        val timeMs = endedAt ?: now()
        configure { it.untrackFeatureFlags(ids, timeMs) }
    }

    override fun setUserIdentifier(userId: String?) = whenStarted { it.setUserIdentifier(userId) }

    override fun clearUserIdentifier() = whenStarted { it.clearUserIdentifier() }

    /**
     * The result of a buffered call isn't known until start, so this optimistically returns true.
     */
    override fun addUserSessionProperty(key: String, value: String, scope: PropertyScope): Boolean {
        var result = true
        whenStarted { result = it.addUserSessionProperty(key, value, scope) }
        return result
    }

    /**
     * The result of a buffered call isn't known until start, so this returns false.
     */
    override fun removeUserSessionProperty(key: String): Boolean {
        var result = false
        whenStarted { result = it.removeUserSessionProperty(key) }
        return result
    }

    /**
     * Runs [call] against the instance being started, buffering it if there is no instance yet.
     */
    private fun configure(call: (StartableEmbrace) -> Unit) {
        val target = synchronized(lock) {
            when {
                sealed -> current() as? StartableEmbrace ?: return
                else -> attached ?: return buffer(beforeInit, call)
            }
        }
        call(target)
    }

    /**
     * Runs [call] against the started instance, buffering it until the SDK has started.
     */
    private fun whenStarted(call: (EmbraceSdk) -> Unit) {
        val target = synchronized(lock) {
            if (!sealed) {
                return buffer(afterStart, call)
            }
            current()
        }
        call(target)
    }

    private fun <T> buffer(queue: MutableList<T>, call: T) {
        if (queue.size < MAX_BUFFERED_CALLS) {
            queue.add(call)
        } else if (warnedOverflow.compareAndSet(false, true)) {
            warn("Too many Embrace API calls were made before Embrace.start(). Further calls are dropped.")
        }
    }

    private companion object {
        const val MAX_BUFFERED_CALLS = 500
    }
}
