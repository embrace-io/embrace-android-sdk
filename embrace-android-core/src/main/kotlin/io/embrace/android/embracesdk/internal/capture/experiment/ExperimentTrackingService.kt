package io.embrace.android.embracesdk.internal.capture.experiment

/**
 * Tracks the experiment and feature-flag state declared for the current app instance. Invalid calls will be dropped silently.
 */
interface ExperimentTrackingService {

    /**
     * Records the given experiments and feature flags as tracked. Records are identified by kind + ID, so an experiment and a
     * feature flag may share an ID. Repeats of the same kind + ID are dropped, both in the same call and in subsequent calls.
     * Leading and trailing ASCII whitespace (U+0009-U+000D, U+0020) is stripped from IDs and variants before validation and
     * storage, and if there are no characters left after storage, it's treated as empty.
     */
    fun track(data: List<TrackedData>)

    /**
     * Marks the given experiments or feature flags as no longer enabled. IDs given here must match a previously tracked record of
     * the same kind after ASCII whitespace stripping. Otherwise, they are dropped.
     */
    fun untrack(data: List<UntrackedData>)

    /**
     * Returns the serialized experiment records as a blob, or null if nothing has been tracked.
     */
    fun getRecords(): String?
}

/**
 * Internal representation of a request to stop tracking a single experiment or feature flag.
 */
data class UntrackedData(
    val kind: ExperimentKind,
    val id: String,
    val endTimeMs: Long,
)

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
