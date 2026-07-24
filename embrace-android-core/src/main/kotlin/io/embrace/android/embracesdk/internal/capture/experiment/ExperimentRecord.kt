package io.embrace.android.embracesdk.internal.capture.experiment

/**
 * Discriminates the kind of entry a record describes.
 */
internal enum class ExperimentRecordType(val code: String) {
    EXPERIMENT("e"),
    FEATURE_FLAG("f"),
}

/**
 * A single record in the serialized experiment records. Serialized as
 * `type:id:variant:startTime` while tracked, gaining a trailing `:endTime` once untracked —
 * i.e. a record always ends in one or two numbers: the first is the start time, and the second,
 * if present, is the end time. Records are joined with `;`. Feature flags never have a variant,
 * so that field is blank for them. The ID and variant are percent-escaped so that any string
 * round-trips through the delimited format. Identity is (type, id): the same ID may be tracked as
 * an experiment and a feature flag simultaneously.
 */
internal data class ExperimentRecord(
    val type: ExperimentRecordType,
    val id: String,
    val variant: String?,
    val startTimeMs: Long,
    val endTimeMs: Long?,
) {

    fun key(): String = "${type.code}:$id"

    fun serialize(): String =
        "${type.code}:${id.escape()}:${variant?.escape().orEmpty()}:$startTimeMs" +
            (endTimeMs?.let { ":$it" }.orEmpty())

    // '%' must be escaped first so that escaped delimiters round-trip on decode
    private fun String.escape(): String = replace("%", "%25")
        .replace(":", "%3A")
        .replace(";", "%3B")
}
