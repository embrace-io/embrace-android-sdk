package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.experiments.TrackedExperiment
import io.embrace.android.embracesdk.experiments.TrackedFeatureFlag

/**
 * The public API used to track experiment and feature-flag state. The state of all tracked
 * experiments and feature flags is attached as metadata to telemetry from the point of tracking
 * onwards.
 *
 * The SDK does not persist this state: the app must re-track every still-active experiment and
 * feature flag on each app launch (this may be done before the SDK starts).
 */
public interface ExperimentApi {

    /**
     * Tracks the given experiments that the device has been bucketed into. Returns true if all
     * were accepted.
     */
    public fun trackExperiment(vararg experiments: TrackedExperiment): Boolean

    /**
     * Stops tracking the given experiments as of the given end time. An experiment can only be
     * untracked while it is actively tracked; other IDs are rejected. The closed record is
     * retained on telemetry for the rest of the process; state ages out when a new process
     * starts and the experiment is not re-tracked. Returns true if all given experiments were
     * being tracked.
     */
    public fun untrackExperiment(vararg experimentIds: String, endTimeMs: Long): Boolean

    /**
     * Tracks the given feature flags that apply to the device. Feature flags share the experiment
     * records and limits. Returns true if all were accepted.
     */
    public fun trackFeatureFlag(vararg flags: TrackedFeatureFlag): Boolean

    /**
     * Stops tracking the given feature flags as of the given end time. A flag can only be
     * untracked while it is actively tracked; other IDs are rejected. The closed record is
     * retained on telemetry for the rest of the process; state ages out when a new process
     * starts and the flag is not re-tracked. Returns true if all given flags were being tracked.
     */
    public fun untrackFeatureFlag(vararg flagIds: String, endTimeMs: Long): Boolean
}
