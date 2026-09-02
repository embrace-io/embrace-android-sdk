package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.experiments.TrackedExperiment
import io.embrace.android.embracesdk.experiments.TrackedFeatureFlag
import io.embrace.android.embracesdk.internal.api.ExperimentApi
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentKind
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedData
import io.embrace.android.embracesdk.internal.config.behavior.ExperimentBehaviorImpl
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

internal class ExperimentApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker,
) : ExperimentApi {

    private val clock by embraceImplInject(sdkCallChecker) {
        bootstrapper.initModule.clock
    }

    private val experimentTrackingService by embraceImplInject(sdkCallChecker) {
        bootstrapper.essentialServiceModule.experimentTrackingService
    }

    private val pendingEvents = ConcurrentLinkedQueue<PendingEvent>()
    private val bufferedEntryCount = AtomicInteger(0)

    override fun createExperiment(id: String, variant: String?, startedAt: Long?): TrackedExperiment =
        TrackedExperimentImpl(id, variant, startedAt)

    override fun trackExperiments(experiments: List<TrackedExperiment>) {
        track("track_experiment", experiments.map { it.toData() })
    }

    override fun untrackExperiments(ids: List<String>, endedAt: Long?) {
        untrack("untrack_experiment", ExperimentKind.EXPERIMENT, ids, endedAt ?: now())
    }

    override fun createFeatureFlag(id: String, startedAt: Long?): TrackedFeatureFlag =
        TrackedFeatureFlagImpl(id, startedAt)

    override fun trackFeatureFlags(flags: List<TrackedFeatureFlag>) {
        track("track_feature_flag", flags.map { it.toData() })
    }

    override fun untrackFeatureFlags(ids: List<String>, endedAt: Long?) {
        untrack("untrack_feature_flag", ExperimentKind.FEATURE_FLAG, ids, endedAt ?: now())
    }

    /**
     * Flush buffered experiment API calls by combining tracking and untracking into as few
     * calls down to the service as possible. The ordering of tracking calls will be preserved,
     * first writer still wins, but all untracking calls will be replayed after the tracking
     * calls in the order they were called. Later untracking calls using the same end
     * timestamp as a previous one will be hoisted to when that first untracking call with
     * the same timestamp was invoked.
     */
    fun flushPendingCalls() {
        val toTrack = mutableListOf<TrackedData>()
        val expToUntrack = linkedMapOf<Long, List<String>>()
        val flagsToUntrack = linkedMapOf<Long, List<String>>()
        val apiCalls = mutableListOf<String>()
        while (true) {
            val event = pendingEvents.poll() ?: break
            apiCalls.add(event.action)

            when (event) {
                is PendingEvent.Track -> {
                    toTrack += event.data
                }

                is PendingEvent.Untrack -> {
                    when (event.kind) {
                        ExperimentKind.EXPERIMENT -> {
                            val existing = expToUntrack[event.endTimeMs]
                            if (existing == null) {
                                expToUntrack[event.endTimeMs] = event.ids
                            } else {
                                expToUntrack[event.endTimeMs] = existing + event.ids
                            }
                        }

                        ExperimentKind.FEATURE_FLAG -> {
                            val existing = flagsToUntrack[event.endTimeMs]
                            if (existing == null) {
                                flagsToUntrack[event.endTimeMs] = event.ids
                            } else {
                                flagsToUntrack[event.endTimeMs] = existing + event.ids
                            }
                        }
                    }
                }
            }
        }

        // Reducing the update of the session part span to one, instead of once per service call, requires a lot more complexity
        // to be introduced to the service. But given the expected use cases, and the unlikely usage of untracking APIs pre-SDK init,
        // the net result will likely be one session part span update anyway. As such, this implementation is acceptable for now.

        experimentTrackingService?.track(toTrack)
        expToUntrack.entries.forEach {
            experimentTrackingService?.untrack(ExperimentKind.EXPERIMENT, it.value, it.key)
        }
        flagsToUntrack.entries.forEach {
            experimentTrackingService?.untrack(ExperimentKind.FEATURE_FLAG, it.value, it.key)
        }
        apiCalls.forEach {
            sdkCallChecker.recordApiCall(it)
        }
    }

    private fun track(action: String, data: List<TrackedData>) {
        if (!sdkCallChecker.started.get()) {
            val admitted = admitEntries(data) ?: return
            pendingEvents.add(PendingEvent.Track(action, admitted))
        } else {
            trackNow(action, data)
        }
    }

    private fun untrack(action: String, kind: ExperimentKind, ids: List<String>, endTimeMs: Long) {
        if (!sdkCallChecker.started.get()) {
            val admitted = admitEntries(ids) ?: return
            pendingEvents.add(PendingEvent.Untrack(action, kind, admitted, endTimeMs))
        } else {
            untrackNow(action, kind, ids, endTimeMs)
        }
    }

    private fun trackNow(action: String, data: List<TrackedData>) {
        if (sdkCallChecker.check(action)) {
            experimentTrackingService?.track(data)
        }
    }

    private fun untrackNow(action: String, kind: ExperimentKind, ids: List<String>, endTimeMs: Long) {
        if (sdkCallChecker.check(action)) {
            experimentTrackingService?.untrack(kind, ids, endTimeMs)
        }
    }

    // Use the system clock if the SDK hasn't been initialized and the SDK clock is unavailable.
    private fun now(): Long = clock?.now() ?: System.currentTimeMillis()

    /**
     * Return the entries to be allowed given the cap. Any entries that will put the total over the cap will be dropped.
     */
    private fun <T> admitEntries(entries: List<T>): List<T>? {
        if (entries.isEmpty()) {
            return null
        }
        val remaining = PENDING_ENTRY_LIMIT - bufferedEntryCount.get()
        if (remaining <= 0) {
            return null
        }
        val admitted = entries.take(remaining)
        bufferedEntryCount.addAndGet(admitted.size)
        return admitted
    }

    private fun TrackedExperiment.toData(): TrackedData =
        TrackedData.Experiment(
            id = id,
            startTimeMs = startedAt ?: now(),
            variant = variant,
        )

    private fun TrackedFeatureFlag.toData(): TrackedData =
        TrackedData.FeatureFlag(
            id = id,
            startTimeMs = startedAt ?: now(),
        )

    private sealed interface PendingEvent {
        val action: String

        class Track(override val action: String, val data: List<TrackedData>) : PendingEvent
        class Untrack(
            override val action: String,
            val kind: ExperimentKind,
            val ids: List<String>,
            val endTimeMs: Long,
        ) : PendingEvent
    }

    private companion object {
        // The buffer stores entries up to the record cap's maximum settable value because it can't resolve the configured cap
        // until the SDK starts.
        private const val PENDING_ENTRY_LIMIT = ExperimentBehaviorImpl.MAX_EXPERIMENT_COUNT_LIMIT
    }
}
