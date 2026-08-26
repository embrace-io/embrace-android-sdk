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

    fun flushPendingCalls() {
        // The buffer can contain more experiments than the configured cap, but we'll replay all of them and let the limit enforcer
        // log the overage and drop later calls after the cap has been reached.
        while (true) {
            val event = pendingEvents.poll() ?: break
            when (event) {
                is PendingEvent.Track -> trackNow(event.action, event.data)
                is PendingEvent.Untrack -> untrackNow(event.action, event.kind, event.ids, event.endTimeMs)
            }
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
            return entries
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
        class Track(val action: String, val data: List<TrackedData>) : PendingEvent
        class Untrack(
            val action: String,
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
