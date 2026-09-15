package io.embrace.android.embracesdk.internal.session.persistence

/**
 * A file that makes up part of the telemetry persisted for one session part.
 */
enum class SessionPartFile(
    internal val fileName: String,
    internal val traceSection: String,
) {
    METADATA(METADATA_FILE_NAME, "mf-read-metadata"),
    COMPLETED_SPANS(COMPLETED_SPANS_FILE_NAME, "mf-read-completed-spans"),
    SPAN_SNAPSHOTS(SPAN_SNAPSHOTS_FILE_NAME, "mf-read-span-snapshots"),
}
