package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.experiments.TrackedExperiment
import io.embrace.android.embracesdk.experiments.TrackedFeatureFlag
import io.embrace.android.embracesdk.internal.api.ExperimentApi
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedData
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject
import java.util.concurrent.ConcurrentLinkedQueue

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

    override fun trackExperiments(experiments: List<TrackedExperiment>) {
        track("track_experiment", experiments.map { it.toData() })
    }

    override fun untrackExperiments(ids: List<String>, endedAt: Long?) {
        untrack("untrack_experiment", ids, endedAt ?: now())
    }

    override fun trackFeatureFlags(flags: List<TrackedFeatureFlag>) {
        track("track_feature_flag", flags.map { it.toData() })
    }

    override fun untrackFeatureFlags(ids: List<String>, endedAt: Long?) {
        untrack("untrack_feature_flag", ids, endedAt ?: now())
    }

    fun flushPendingCalls() {
        while (true) {
            when (val event = pendingEvents.poll() ?: return) {
                is PendingEvent.Track -> trackNow(event.action, event.data)
                is PendingEvent.Untrack -> untrackNow(event.action, event.ids, event.endTimeMs)
            }
        }
    }

    private fun track(action: String, data: List<TrackedData>) {
        if (!sdkCallChecker.started.get()) {
            buffer(PendingEvent.Track(action, data))
        } else {
            trackNow(action, data)
        }
    }

    private fun untrack(action: String, ids: List<String>, endTimeMs: Long) {
        if (!sdkCallChecker.started.get()) {
            buffer(PendingEvent.Untrack(action, ids, endTimeMs))
        } else {
            untrackNow(action, ids, endTimeMs)
        }
    }

    private fun trackNow(action: String, data: List<TrackedData>) {
        if (sdkCallChecker.check(action)) {
            experimentTrackingService?.track(data)
        }
    }

    private fun untrackNow(action: String, ids: List<String>, endTimeMs: Long) {
        if (sdkCallChecker.check(action)) {
            experimentTrackingService?.untrack(ids, endTimeMs)
        }
    }

    // Use the system clock if the SDK hasn't been initialized and the SDK clock is unavailable.
    private fun now(): Long = clock?.now() ?: System.currentTimeMillis()

    private fun buffer(event: PendingEvent) {
        if (pendingEvents.size >= PENDING_EVENT_LIMIT) {
            pendingEvents.poll()
        }
        pendingEvents.add(event)
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
        class Untrack(val action: String, val ids: List<String>, val endTimeMs: Long) : PendingEvent
    }

    private companion object {
        private const val PENDING_EVENT_LIMIT = 5000
    }
}
