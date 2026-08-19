package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.experiments.ExperimentTrackingScope
import io.embrace.android.embracesdk.experiments.ExperimentUntrackingScope
import io.embrace.android.embracesdk.internal.api.ExperimentApi
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentKind
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedData
import io.embrace.android.embracesdk.internal.capture.experiment.UntrackedData
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

    override fun trackExperiments(action: ExperimentTrackingScope.() -> Unit) {
        val scope = ExperimentTrackingScopeImpl(::now)
        try {
            scope.action()
        } finally {
            track(scope.drain())
        }
    }

    override fun untrackExperiments(action: ExperimentUntrackingScope.() -> Unit) {
        val scope = ExperimentUntrackingScopeImpl(::now)
        try {
            scope.action()
        } finally {
            untrack(scope.drain())
        }
    }

    fun flushPendingCalls() {
        // The buffer can contain more experiments than the configured cap, but we'll replay all of them and let the limit enforcer
        // log the overage and drop later calls after the cap has been reached.
        while (true) {
            val event = pendingEvents.poll() ?: break
            when (event) {
                is PendingEvent.Track -> trackNow(event.data)
                is PendingEvent.Untrack -> untrackNow(event.data)
            }
        }
    }

    private fun track(data: List<TrackedData>) {
        if (data.isEmpty()) {
            return
        }
        if (!sdkCallChecker.started.get()) {
            val admitted = admitEntries(data) ?: return
            pendingEvents.add(PendingEvent.Track(admitted))
        } else {
            trackNow(data)
        }
    }

    private fun untrack(data: List<UntrackedData>) {
        if (data.isEmpty()) {
            return
        }
        if (!sdkCallChecker.started.get()) {
            val admitted = admitEntries(data) ?: return
            pendingEvents.add(PendingEvent.Untrack(admitted))
        } else {
            untrackNow(data)
        }
    }

    private fun trackNow(data: List<TrackedData>) {
        val experiments = data.any { it is TrackedData.Experiment } && sdkCallChecker.check(TRACK_EXPERIMENT)
        val flags = data.any { it is TrackedData.FeatureFlag } && sdkCallChecker.check(TRACK_FEATURE_FLAG)
        if (experiments || flags) {
            experimentTrackingService?.track(data)
        }
    }

    private fun untrackNow(data: List<UntrackedData>) {
        val experiments = data.any { it.kind == ExperimentKind.EXPERIMENT } && sdkCallChecker.check(UNTRACK_EXPERIMENT)
        val flags = data.any { it.kind == ExperimentKind.FEATURE_FLAG } && sdkCallChecker.check(UNTRACK_FEATURE_FLAG)
        if (experiments || flags) {
            experimentTrackingService?.untrack(data)
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

    private sealed interface PendingEvent {
        class Track(val data: List<TrackedData>) : PendingEvent
        class Untrack(val data: List<UntrackedData>) : PendingEvent
    }

    private companion object {
        private const val TRACK_EXPERIMENT = "track_experiment"
        private const val TRACK_FEATURE_FLAG = "track_feature_flag"
        private const val UNTRACK_EXPERIMENT = "untrack_experiment"
        private const val UNTRACK_FEATURE_FLAG = "untrack_feature_flag"

        // The buffer stores entries up to the record cap's maximum settable value because it can't resolve the configured cap
        // until the SDK starts.
        private const val PENDING_ENTRY_LIMIT = ExperimentBehaviorImpl.MAX_EXPERIMENT_COUNT_LIMIT
    }
}
