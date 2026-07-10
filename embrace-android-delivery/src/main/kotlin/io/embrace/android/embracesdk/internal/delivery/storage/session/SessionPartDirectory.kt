package io.embrace.android.embracesdk.internal.delivery.storage.session

import java.io.File

/**
 * Owns the on-disk layout of a single session-part directory and the handles to its constituent
 * files. Session-part data is split across multiple Wire-encoded files so that a mutation to one
 * concern (e.g. a completed span) does not require re-serializing the whole payload.
 *
 * Each `.wire` (Protobuf) file has a `.json` (kotlinx-serialization) twin written alongside it. The
 * JSON twins are never read back; they exist purely so the serialization cost of the two formats can
 * be compared against each other (and against the legacy monolithic gzipped-JSON payload) in traces
 * and microbenchmarks. See [SessionPartWriter].
 */
class SessionPartDirectory(val dir: File) {
    val manifestFile: File = File(dir, "manifest.wire")
    val metadataFile: File = File(dir, "metadata.wire")
    val completedSpansFile: File = File(dir, "completed_spans.wire")
    val snapshotsFile: File = File(dir, "span_snapshots.wire")
    val sessionSpanFile: File = File(dir, "session_span.wire")

    val manifestJsonFile: File = File(dir, "manifest.json")
    val metadataJsonFile: File = File(dir, "metadata.json")
    val completedSpansJsonFile: File = File(dir, "completed_spans.json")
    val snapshotsJsonFile: File = File(dir, "span_snapshots.json")
    val sessionSpanJsonFile: File = File(dir, "session_span.json")

    /** Written once the part has been finalized/delivered, so it is not treated as recoverable. */
    val completeMarkerFile: File = File(dir, "complete.marker")

    fun create() {
        dir.mkdirs()
    }
}
