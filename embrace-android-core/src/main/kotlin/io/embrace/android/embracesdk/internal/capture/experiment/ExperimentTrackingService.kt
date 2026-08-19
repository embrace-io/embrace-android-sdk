package io.embrace.android.embracesdk.internal.capture.experiment

/**
 * Tracks the experiment and feature-flag state declared for the current app instance. Invalid calls will be dropped silently.
 */
interface ExperimentTrackingService {

    /**
     * Records the given experiments and feature flags as tracked. IDs share a single namespace across kinds: the first entry
     * tracked with a given ID persists irrespective of kind. Repeats are dropped, both in the same call and in subsequent
     * calls, even if the kind is different.
     */
    fun track(data: List<TrackedData>)

    /**
     * Marks the given experiments or feature flags as no longer enabled. IDs given here must have previously been tracked.
     * Otherwise, they are dropped.
     */
    fun untrack(ids: List<String>, endTimeMs: Long)

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
