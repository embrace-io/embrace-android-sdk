package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.experiments.TrackedExperiment
import io.embrace.android.embracesdk.experiments.TrackedFeatureFlag
import io.embrace.android.embracesdk.internal.api.ExperimentApi
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedExperimentData
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedFeatureFlagData
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

    override fun trackExperiment(vararg experiments: TrackedExperiment): Boolean {
        if (!sdkCallChecker.started.get()) {
            // buffered path: bypass check() so no "SDK not initialized" error is logged;
            // API-usage telemetry is emitted at flush time instead
            return buffer(PendingEvent.Track(experiments.toList()))
        }
        if (sdkCallChecker.check("track_experiment")) {
            return experimentTrackingService?.trackExperiments(experiments.map { it.toData() }) ?: false
        }
        return false
    }

    override fun untrackExperiment(vararg experimentIds: String, endTimeMs: Long): Boolean {
        if (!sdkCallChecker.started.get()) {
            return buffer(PendingEvent.Untrack(experimentIds.toList(), endTimeMs))
        }
        if (sdkCallChecker.check("untrack_experiment")) {
            return experimentTrackingService?.untrackExperiments(experimentIds.toList(), endTimeMs) ?: false
        }
        return false
    }

    override fun trackFeatureFlag(vararg flags: TrackedFeatureFlag): Boolean {
        if (!sdkCallChecker.started.get()) {
            return buffer(PendingEvent.TrackFlag(flags.toList()))
        }
        if (sdkCallChecker.check("track_feature_flag")) {
            return experimentTrackingService?.trackFeatureFlags(flags.map { it.toData() }) ?: false
        }
        return false
    }

    override fun untrackFeatureFlag(vararg flagIds: String, endTimeMs: Long): Boolean {
        if (!sdkCallChecker.started.get()) {
            return buffer(PendingEvent.UntrackFlag(flagIds.toList(), endTimeMs))
        }
        if (sdkCallChecker.check("untrack_feature_flag")) {
            return experimentTrackingService?.untrackFeatureFlags(flagIds.toList(), endTimeMs) ?: false
        }
        return false
    }

    /**
     * Drains calls buffered before the SDK started into the service. Invoked from
     * EmbraceImpl.start() once the SDK is considered started.
     */
    fun flushPendingCalls() {
        while (true) {
            when (val event = pendingEvents.poll() ?: return) {
                is PendingEvent.Track -> {
                    sdkCallChecker.check("track_experiment")
                    experimentTrackingService?.trackExperiments(event.experiments.map { it.toData() })
                }
                is PendingEvent.Untrack -> {
                    sdkCallChecker.check("untrack_experiment")
                    experimentTrackingService?.untrackExperiments(event.ids, event.endTimeMs)
                }
                is PendingEvent.TrackFlag -> {
                    sdkCallChecker.check("track_feature_flag")
                    experimentTrackingService?.trackFeatureFlags(event.flags.map { it.toData() })
                }
                is PendingEvent.UntrackFlag -> {
                    sdkCallChecker.check("untrack_feature_flag")
                    experimentTrackingService?.untrackFeatureFlags(event.ids, event.endTimeMs)
                }
            }
        }
    }

    private fun buffer(event: PendingEvent): Boolean {
        if (pendingEvents.size >= PENDING_EVENT_LIMIT) {
            pendingEvents.poll()
        }
        pendingEvents.add(event)
        return true
    }

    private fun TrackedExperiment.toData(): TrackedExperimentData =
        TrackedExperimentData(
            experimentId = experimentId,
            variantName = variantName,
            startTimeMs = startTimeMs,
        )

    private fun TrackedFeatureFlag.toData(): TrackedFeatureFlagData =
        TrackedFeatureFlagData(
            flagId = flagId,
            startTimeMs = startTimeMs,
        )

    private sealed interface PendingEvent {
        class Track(val experiments: List<TrackedExperiment>) : PendingEvent
        class Untrack(val ids: List<String>, val endTimeMs: Long) : PendingEvent
        class TrackFlag(val flags: List<TrackedFeatureFlag>) : PendingEvent
        class UntrackFlag(val ids: List<String>, val endTimeMs: Long) : PendingEvent
    }

    private companion object {
        private const val PENDING_EVENT_LIMIT = 100
    }
}
