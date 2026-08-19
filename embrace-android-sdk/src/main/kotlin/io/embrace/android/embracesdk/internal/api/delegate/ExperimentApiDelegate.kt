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

    private val experimentTrackingService by embraceImplInject(sdkCallChecker) {
        bootstrapper.essentialServiceModule.experimentTrackingService
    }

    private val pendingEvents = ConcurrentLinkedQueue<PendingEvent>()

    override fun trackExperiment(vararg experiments: TrackedExperiment) {
        if (!sdkCallChecker.started.get()) {
            buffer(PendingEvent.TrackExperiments(experiments.toList()))
        } else {
            trackExperimentsNow(experiments.toList())
        }
    }

    override fun untrackExperiment(vararg experimentIds: String, endTimeMs: Long) {
        untrack("untrack_experiment", experimentIds.toList(), endTimeMs)
    }

    override fun trackFeatureFlag(vararg flags: TrackedFeatureFlag) {
        if (!sdkCallChecker.started.get()) {
            buffer(PendingEvent.TrackFlags(flags.toList()))
        } else {
            trackFeatureFlagsNow(flags.toList())
        }
    }

    override fun untrackFeatureFlag(vararg flagIds: String, endTimeMs: Long) {
        untrack("untrack_feature_flag", flagIds.toList(), endTimeMs)
    }

    fun flushPendingCalls() {
        while (true) {
            when (val event = pendingEvents.poll() ?: return) {
                is PendingEvent.TrackExperiments -> trackExperimentsNow(event.experiments)
                is PendingEvent.TrackFlags -> trackFeatureFlagsNow(event.flags)
                is PendingEvent.Untrack -> untrackNow(event.action, event.ids, event.endTimeMs)
            }
        }
    }

    private fun untrack(action: String, ids: List<String>, endTimeMs: Long) {
        if (!sdkCallChecker.started.get()) {
            buffer(PendingEvent.Untrack(action, ids, endTimeMs))
        } else {
            untrackNow(action, ids, endTimeMs)
        }
    }

    private fun trackExperimentsNow(experiments: List<TrackedExperiment>) {
        if (sdkCallChecker.check("track_experiment")) {
            experimentTrackingService?.track(experiments.map { it.toData() })
        }
    }

    private fun trackFeatureFlagsNow(flags: List<TrackedFeatureFlag>) {
        if (sdkCallChecker.check("track_feature_flag")) {
            experimentTrackingService?.track(flags.map { it.toData() })
        }
    }

    private fun untrackNow(action: String, ids: List<String>, endTimeMs: Long) {
        if (sdkCallChecker.check(action)) {
            experimentTrackingService?.untrack(ids, endTimeMs)
        }
    }

    private fun buffer(event: PendingEvent) {
        if (pendingEvents.size >= PENDING_EVENT_LIMIT) {
            pendingEvents.poll()
        }
        pendingEvents.add(event)
    }

    private fun TrackedExperiment.toData(): TrackedData =
        TrackedData.Experiment(
            id = id,
            startTimeMs = startTimeMs,
            variant = variant,
        )

    private fun TrackedFeatureFlag.toData(): TrackedData =
        TrackedData.FeatureFlag(
            id = id,
            startTimeMs = startTimeMs,
        )

    private sealed interface PendingEvent {
        class TrackExperiments(val experiments: List<TrackedExperiment>) : PendingEvent
        class TrackFlags(val flags: List<TrackedFeatureFlag>) : PendingEvent
        class Untrack(val action: String, val ids: List<String>, val endTimeMs: Long) : PendingEvent
    }

    private companion object {
        private const val PENDING_EVENT_LIMIT = 5000
    }
}
