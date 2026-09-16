package io.embrace.android.embracesdk.internal.capture.experiment

/**
 * Internal representation of a single tracked experiment or feature flag, differentiated by [kind], with the start time already resolved.
 */
data class TrackedData(
    val kind: ExperimentKind,
    val id: String,
    val startTimeMs: Long,
    val variant: String?,
) {
    companion object {
        fun experiment(id: String, startTimeMs: Long, variant: String?): TrackedData =
            TrackedData(ExperimentKind.EXPERIMENT, id, startTimeMs, variant)

        fun featureFlag(id: String, startTimeMs: Long, variant: String?): TrackedData =
            TrackedData(ExperimentKind.FEATURE_FLAG, id, startTimeMs, variant)
    }
}
