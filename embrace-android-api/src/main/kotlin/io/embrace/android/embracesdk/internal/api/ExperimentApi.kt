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
     * Creates a [TrackedExperiment] with an optional [variant] and a timestamp [startedAt] in milliseconds from epoch observed from
     * the client-side that denotes when the experiment variant was first applied.
     */
    public fun createExperiment(id: String, variant: String? = null, startedAt: Long? = null): TrackedExperiment =
        TrackedExperimentImpl(id, variant, startedAt)

    /**
     * Tracks a single experiment membership that this app instance has been bucketed into. A null [startedAt] means the time at which
     * this call is made will be used.
     */
    public fun trackExperiment(id: String, variant: String? = null, startedAt: Long? = null): Unit =
        trackExperiments(listOf(createExperiment(id, variant, startedAt)))

    /**
     * Tracks the given experiment memberships that this app instance has been bucketed into.
     */
    public fun trackExperiments(experiments: List<TrackedExperiment>)

    /**
     * Stops tracking a single experiment on this app instance. A null [endedAt] means the time at which this call is made will be used.
     */
    public fun untrackExperiment(id: String, endedAt: Long? = null): Unit =
        untrackExperiments(listOf(id), endedAt)

    /**
     * Stops tracking the given experiments on this app instance at the given time. An experiment can only be untracked after it has
     * first been tracked. Untracking an experiment that has not been tracked has no affect. A null [endedAt] means the time at which
     * this call is made will be used.
     */
    public fun untrackExperiments(ids: List<String>, endedAt: Long? = null)

    /**
     * Creates a [TrackedFeatureFlag] with an optional timestamp [startedAt] in milliseconds from epoch observed from the client-side
     * that denotes when the flag was first applied.
     */
    public fun createFeatureFlag(id: String, startedAt: Long? = null): TrackedFeatureFlag =
        TrackedFeatureFlagImpl(id, startedAt)

    /**
     * Tracks a single enabled feature flag that applies to this app instance. A null [startedAt] means the time at which this call
     * is made will be used.
     */
    public fun trackFeatureFlag(id: String, startedAt: Long? = null): Unit =
        trackFeatureFlags(listOf(createFeatureFlag(id, startedAt)))

    /**
     * Tracks the given enabled feature flags that apply to this app instance.
     */
    public fun trackFeatureFlags(flags: List<TrackedFeatureFlag>)

    /**
     * Stops tracking a single feature flag enablement on this app instance. A null [endedAt] means the time at which this call is made
     * will be used.
     */
    public fun untrackFeatureFlag(id: String, endedAt: Long? = null): Unit =
        untrackFeatureFlags(listOf(id), endedAt)

    /**
     * Stops tracking the given feature flag enablements on this app instance at the given time. A feature flag can only be untracked
     * after it has first been tracked. Untracking a feature flag that has not been previously enabled has no affect. A null [endedAt]
     * means the time at which this call is made will be used.
     */
    public fun untrackFeatureFlags(ids: List<String>, endedAt: Long? = null)
}
