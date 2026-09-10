package io.embrace.android.embracesdk.internal.session.persistence

/**
 * A file that makes up part of the telemetry persisted for one session part.
 */
enum class SessionPartFile(
    internal val fileName: String,
    internal val traceSection: String,
) {
    MANIFEST(MANIFEST_FILE_NAME, "mf-read-manifest"),
    METADATA(METADATA_FILE_NAME, "mf-read-metadata"),
    SESSION_SPAN(SESSION_SPAN_FILE_NAME, "mf-read-session-span"),
    COMPLETED_SPANS(COMPLETED_SPANS_FILE_NAME, "mf-read-completed-spans"),
    SPAN_SNAPSHOTS(SPAN_SNAPSHOTS_FILE_NAME, "mf-read-span-snapshots"),
}
