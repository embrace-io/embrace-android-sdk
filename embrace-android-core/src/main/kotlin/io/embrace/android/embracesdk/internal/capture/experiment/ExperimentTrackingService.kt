package io.embrace.android.embracesdk.internal.capture.experiment

/**
 * Tracks the experiment and feature-flag state declared for the current app instance. Invalid calls will be dropped silently.
 */
interface ExperimentTrackingService {

    /**
     * Records the given experiments and feature flags as tracked. Records are identified by kind + ID, so an experiment and a
     * feature flag may share an ID. Repeats of the same kind + ID are dropped, both in the same call and in subsequent calls.
     */
    fun track(data: List<TrackedData>)

    /**
     * Marks the given experiments or feature flags of the given kind as no longer enabled. IDs given here must match a
     * previously tracked record of the same kind. Otherwise, they are dropped.
     */
    fun untrack(kind: ExperimentKind, ids: List<String>, endTimeMs: Long)

    /**
     * Returns the serialized experiment records as a blob, or null if nothing has been tracked.
     */
    fun getRecords(): String?
}

/**
 * Internal representation of a single tracked experiment or feature flag.
 */
sealed class TrackedData {
    abstract val id: String
    abstract val startTimeMs: Long

    /**
     * An association with an experiment, optionally including the variant in which this app instance is bucketed into.
     */
    data class Experiment(
        override val id: String,
        override val startTimeMs: Long,
        val variant: String?,
    ) : TrackedData()

    /**
     * An association with an enabled feature flag.
     */
    data class FeatureFlag(
        override val id: String,
        override val startTimeMs: Long,
    ) : TrackedData()
}
