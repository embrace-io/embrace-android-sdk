package io.embrace.android.embracesdk.internal.capture.experiment

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
