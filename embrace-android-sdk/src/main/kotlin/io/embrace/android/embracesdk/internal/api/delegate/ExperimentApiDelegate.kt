package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.experiments.TrackedEntry
import io.embrace.android.embracesdk.experiments.TrackedExperiment
import io.embrace.android.embracesdk.experiments.TrackedFeatureFlag
import io.embrace.android.embracesdk.internal.api.ExperimentApi
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentApiCall
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentKind
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedData
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject

internal class ExperimentApiDelegate(
    lazyBootstrapper: Lazy<ModuleInitBootstrapper>,
    private val sdkCallChecker: SdkCallChecker,
) : ExperimentApi {

    private val bootstrapper by lazyBootstrapper

    private val clock by embraceImplInject(sdkCallChecker) {
        bootstrapper.initModule.clock
    }

    private val experimentTrackingService by embraceImplInject(sdkCallChecker) {
        bootstrapper.essentialServiceModule.experimentTrackingService
    }

    override fun createExperiment(id: String, variant: String?, startedAt: Long?): TrackedExperiment =
        TrackedExperimentImpl(id, variant, startedAt)

    override fun trackExperiments(experiments: List<TrackedExperiment>) {
        if (sdkCallChecker.check("track_experiment")) {
            experimentTrackingService?.track(experiments.toData(ExperimentKind.EXPERIMENT))
        }
    }

    override fun untrackExperiments(ids: List<String>, endedAt: Long?) {
        if (sdkCallChecker.check("untrack_experiment")) {
            experimentTrackingService?.untrack(ExperimentKind.EXPERIMENT, ids, endedAt ?: now())
        }
    }

    override fun createFeatureFlag(id: String, variant: String?, startedAt: Long?): TrackedFeatureFlag =
        TrackedFeatureFlagImpl(id, variant, startedAt)

    override fun trackFeatureFlags(flags: List<TrackedFeatureFlag>) {
        if (sdkCallChecker.check("track_feature_flag")) {
            experimentTrackingService?.track(flags.toData(ExperimentKind.FEATURE_FLAG))
        }
    }

    override fun untrackFeatureFlags(ids: List<String>, endedAt: Long?) {
        if (sdkCallChecker.check("untrack_feature_flag")) {
            experimentTrackingService?.untrack(ExperimentKind.FEATURE_FLAG, ids, endedAt ?: now())
        }
    }

    /**
     * Commits calls that were made before the SDK started in one go in the service.
     */
    fun replay(calls: List<ExperimentApiCall>) {
        if (calls.isNotEmpty()) {
            experimentTrackingService?.bulkModify(calls)
        }
    }

    private fun now(): Long = clock?.now() ?: System.currentTimeMillis()

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
