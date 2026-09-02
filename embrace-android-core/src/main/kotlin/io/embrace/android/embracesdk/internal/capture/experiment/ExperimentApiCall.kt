package io.embrace.android.embracesdk.internal.capture.experiment

/**
 * An experiment API call made before the SDK started
 */
sealed class ExperimentApiCall {

    data class Track(val data: List<TrackedData>) : ExperimentApiCall()

    data class Untrack(
        val kind: ExperimentKind,
        val ids: List<String>,
        val endTimeMs: Long,
    ) : ExperimentApiCall()
}
