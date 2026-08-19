package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.experiments.TrackedExperiment
import io.embrace.android.embracesdk.experiments.TrackedExperimentImpl
import io.embrace.android.embracesdk.experiments.TrackedFeatureFlag
import io.embrace.android.embracesdk.experiments.TrackedFeatureFlagImpl

/**
 * The public API used to track experiment and feature-flag state on a given device for the current app instance. IDs are unique across
 * experiments and feature flags. The SDK does not persist this state, and users need to call this each time the app starts up.
 */
public interface ExperimentApi {

    /**
     * Creates a [TrackedExperiment]
     */
    public fun createExperiment(id: String, startTimeMs: Long, variant: String? = null): TrackedExperiment =
        TrackedExperimentImpl(id, startTimeMs, variant)

    /**
     * Tracks the given experiment memberships that this app instance has been bucketed into.
     */
    public fun trackExperiment(vararg experiments: TrackedExperiment)

    /**
     * Stops tracking the given experiments on this app instance at the given time. An experiment can only be untracked after it has
     * first been tracked. Untracking an experiment that has not been tracked has no affect.
     */
    public fun untrackExperiment(vararg experimentIds: String, endTimeMs: Long)

    /**
     * Creates a [TrackedFeatureFlag]
     */
    public fun createFeatureFlag(id: String, startTimeMs: Long): TrackedFeatureFlag =
        TrackedFeatureFlagImpl(id, startTimeMs)

    /**
     * Tracks the given enabled feature flags that apply to this app instance.
     */
    public fun trackFeatureFlag(vararg flags: TrackedFeatureFlag)

    /**
     * Stops tracking the given feature flag enablements on this app instance at the given time. A feature flag can only be untracked after
     * it has first been tracked. Untracking a feature flag that has not been previously enabled has no affect.
     */
    public fun untrackFeatureFlag(vararg flagIds: String, endTimeMs: Long)
}
