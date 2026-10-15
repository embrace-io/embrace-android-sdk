package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.experiments.TrackedEntry
import io.embrace.android.embracesdk.experiments.TrackedExperiment
import io.embrace.android.embracesdk.experiments.TrackedFeatureFlag
import io.embrace.android.embracesdk.internal.api.ExperimentApi
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentApiCall
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentKind
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentTrackingService
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedData
import io.embrace.android.embracesdk.internal.clock.Clock

internal class ExperimentApiDelegate(
    private val clock: Clock,
    private val experimentTrackingService: Lazy<ExperimentTrackingService>,
) : ExperimentApi {

    override fun createExperiment(id: String, variant: String?, startedAt: Long?): TrackedExperiment =
        TrackedExperimentImpl(id, variant, startedAt)

    override fun trackExperiments(experiments: List<TrackedExperiment>) {
        experimentTrackingService.value.track(experiments.toData(ExperimentKind.EXPERIMENT))
    }

    override fun untrackExperiments(ids: List<String>, endedAt: Long?) {
        experimentTrackingService.value.untrack(ExperimentKind.EXPERIMENT, ids, endedAt ?: now())
    }

    override fun createFeatureFlag(id: String, variant: String?, startedAt: Long?): TrackedFeatureFlag =
        TrackedFeatureFlagImpl(id, variant, startedAt)

    override fun trackFeatureFlags(flags: List<TrackedFeatureFlag>) {
        experimentTrackingService.value.track(flags.toData(ExperimentKind.FEATURE_FLAG))
    }

    override fun untrackFeatureFlags(ids: List<String>, endedAt: Long?) {
        experimentTrackingService.value.untrack(ExperimentKind.FEATURE_FLAG, ids, endedAt ?: now())
    }

    /**
     * Commits calls that were made before the SDK started in one go in the service.
     */
    fun replay(calls: List<ExperimentApiCall>) {
        if (calls.isNotEmpty()) {
            experimentTrackingService.value.bulkModify(calls)
        }
    }

    private fun now(): Long = clock.now()

    private fun List<TrackedEntry>.toData(kind: ExperimentKind): List<TrackedData> =
        map { entry ->
            TrackedData(
                kind = kind,
                id = entry.id,
                startTimeMs = entry.startedAt ?: now(),
                variant = entry.variant,
            )
        }
}
