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
     * Marks the given experiments or feature flags of the given kind as no longer enabled. IDs given here must match a
     * previously tracked record of the same kind after ASCII whitespace stripping. Otherwise, they are dropped.
     */
    fun untrack(kind: ExperimentKind, ids: List<String>, endTimeMs: Long)

    /**
     * Applies experiment API calls that were buffered before the SDK started, in the order given, exactly as if each had
     * been made via [track] or [untrack] after startup. The only difference is that they are applied as a single update.
     */
    fun bulkModify(events: List<ExperimentApiCall>)

    /**
     * Returns the serialized experiment records as a blob, or null if nothing has been tracked.
     */
    fun getRecords(): String?
}
