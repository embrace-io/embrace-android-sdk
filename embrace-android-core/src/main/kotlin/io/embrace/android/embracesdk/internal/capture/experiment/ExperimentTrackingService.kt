package io.embrace.android.embracesdk.internal.capture.experiment

import io.embrace.android.embracesdk.semconv.EmbCommonAttributes

/**
 * Attribute key under which the experiment records are attached to telemetry.
 */
const val EMB_EXPERIMENTS_ATTRIBUTE_KEY: String = EmbCommonAttributes.EMB_EXPERIMENTS

/**
 * Tracks the experiment and feature-flag state declared for the current process. Both kinds share
 * the same records and limits. State is held in memory only and persists for the lifetime of the
 * process: it ages out when a new process starts and the app does not re-track it, so the app is
 * expected to re-track active experiments and feature flags on every launch.
 */
interface ExperimentTrackingService {

    /**
     * Records the given experiments as tracked. Returns true if all were accepted.
     */
    fun trackExperiments(experiments: List<TrackedExperimentData>): Boolean

    /**
     * Records the given experiments as untracked at the given time by closing their tracked
     * records. IDs that are not actively tracked are rejected. Returns true if all the given
     * experiments were being tracked.
     */
    fun untrackExperiments(experimentIds: List<String>, endTimeMs: Long): Boolean

    /**
     * Records the given feature flags as tracked. Returns true if all were accepted.
     */
    fun trackFeatureFlags(flags: List<TrackedFeatureFlagData>): Boolean

    /**
     * Records the given feature flags as untracked at the given time by closing their tracked
     * records. IDs that are not actively tracked are rejected. Returns true if all the given
     * flags were being tracked.
     */
    fun untrackFeatureFlags(flagIds: List<String>, endTimeMs: Long): Boolean

    /**
     * Returns the serialized experiment records, or null if nothing is tracked.
     */
    fun getRecords(): String?
}

/**
 * Internal representation of a single tracked experiment.
 */
data class TrackedExperimentData(
    val experimentId: String,
    val variantName: String,
    val startTimeMs: Long,
)

/**
 * Internal representation of a single tracked feature flag.
 */
data class TrackedFeatureFlagData(
    val flagId: String,
    val startTimeMs: Long,
)
