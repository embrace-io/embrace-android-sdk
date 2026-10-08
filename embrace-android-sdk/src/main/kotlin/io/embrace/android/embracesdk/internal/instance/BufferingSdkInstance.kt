@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.experiments.TrackedEntry
import io.embrace.android.embracesdk.experiments.TrackedExperiment
import io.embrace.android.embracesdk.experiments.TrackedFeatureFlag
import io.embrace.android.embracesdk.internal.api.ExperimentApi
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentApiCall
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentKind
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedData
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.resolved.ExperimentConfig
import io.embrace.android.embracesdk.internal.logging.InternalLogger

/**
 * An [SdkApi] implementation for use before the SDK has started. Calls whose effect can be applied later are buffered
 * so they can be drained into the started SDK.
 *
 * Once a group is drained subsequent calls in that group are forwarded straight to the real SDK instance.
 */
internal class BufferingSdkInstance(
    private val clock: Clock,
    logger: InternalLogger,
) : SdkApi by NoopSdkInstance(logger, NoopSdkInstance.SDK_NOT_INITIALIZED) {

    private val experimentCalls = CallBuffer<ExperimentApi, ExperimentApiCall>()

    private var bufferedExperimentEntryCount = 0

    /**
     * Passes all the buffered experiment and feature flag calls.
     */
    fun drainExperimentCalls(target: ExperimentApi, replay: (List<ExperimentApiCall>) -> Unit) {
        experimentCalls.drain(target, replay)
    }

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
        private const val MAX_BUFFERED_EXPERIMENT_ENTRIES = ExperimentConfig.MAX_COUNT_LIMIT
    }
}
