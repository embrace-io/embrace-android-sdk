package io.embrace.android.embracesdk.internal.capture.experiment

/**
 * Internal representation of an association with an experiment or feature flag
 */
internal data class ExperimentRecord(
    val kind: ExperimentKind,
    val id: String,
    val variant: String?,
    val startTimeMs: Long,
    val endTimeMs: Long?,
) {

    fun serialize(): String =
        "${kind.code}:${id.escape()}:${variant?.escape().orEmpty()}:$startTimeMs" +
            (endTimeMs?.let { ":$it" }.orEmpty())

    private fun String.escape(): String = replace("%", "%25")
        .replace(":", "%3A")
        .replace(";", "%3B")
}
