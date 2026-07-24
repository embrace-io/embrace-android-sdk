package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentTrackingService
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedExperimentData
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedFeatureFlagData

class FakeExperimentTrackingService : ExperimentTrackingService {

    val trackedExperiments: MutableList<TrackedExperimentData> = mutableListOf()
    val untrackedExperiments: MutableList<String> = mutableListOf()
    val trackedFeatureFlags: MutableList<TrackedFeatureFlagData> = mutableListOf()
    val untrackedFeatureFlags: MutableList<String> = mutableListOf()
    var fakeRecords: String? = null

    override fun trackExperiments(experiments: List<TrackedExperimentData>): Boolean {
        trackedExperiments.addAll(experiments)
        return true
    }

    override fun untrackExperiments(experimentIds: List<String>, endTimeMs: Long): Boolean {
        untrackedExperiments.addAll(experimentIds)
        return true
    }

    override fun trackFeatureFlags(flags: List<TrackedFeatureFlagData>): Boolean {
        trackedFeatureFlags.addAll(flags)
        return true
    }

    override fun untrackFeatureFlags(flagIds: List<String>, endTimeMs: Long): Boolean {
        untrackedFeatureFlags.addAll(flagIds)
        return true
    }

    override fun getRecords(): String? = fakeRecords
}
