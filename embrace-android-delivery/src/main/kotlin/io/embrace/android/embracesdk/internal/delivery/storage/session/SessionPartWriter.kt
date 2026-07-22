package io.embrace.android.embracesdk.internal.delivery.storage.session

import io.embrace.android.embracesdk.internal.payload.Span
import java.io.File
import java.io.FileOutputStream

/**
 * Format a session-part file can be serialized to. Each concern is written once per requested
 * format, allowing the Protobuf and JSON encodings to be measured independently.
 */
enum class SessionPartFormat { PROTO, JSON }

/**
 * Writes the constituent files of a session part. Overwrites are performed atomically (temp file +
 * rename, mirroring [io.embrace.android.embracesdk.internal.delivery.storage.FileStorageServiceImpl]),
 * while completed spans are appended as length-framed records to avoid re-serializing the set.
 *
 * Every concern is serialized to Protobuf (via Wire) and, immediately afterwards, to an identical
 * JSON file (via kotlinx-serialization) named `*.json` instead of `*.wire`. The JSON twins are never
 * read back; they exist so the two serialization formats can be compared against each other, and
 * against the legacy monolithic gzipped-JSON payload. Pass a restricted [formats] set to isolate a
 * single format (used by the microbenchmarks). Encoding is delegated to [SessionPartSerializer].
 */
class SessionPartWriter(
    private val directory: SessionPartDirectory,
    private val formats: Set<SessionPartFormat> = setOf(SessionPartFormat.PROTO, SessionPartFormat.JSON),
    private val serializer: SessionPartSerializer = SessionPartSerializer(),
) {

    private val writeProto = SessionPartFormat.PROTO in formats
    private val writeJson = SessionPartFormat.JSON in formats

    fun writeManifest(manifest: Manifest) {
        if (writeProto) writeAtomically(directory.manifestFile, serializer.manifestProto(manifest))
        if (writeJson) writeAtomically(directory.manifestJsonFile, serializer.manifestJson(manifest))
    }

    fun writeMetadata(metadata: SessionMetadata) {
        if (writeProto) writeAtomically(directory.metadataFile, serializer.metadataProto(metadata))
        if (writeJson) writeAtomically(directory.metadataJsonFile, serializer.metadataJson(metadata))
    }

    /**
     * Appends completed spans as individual length-framed records so that an ended span costs one
     * append rather than a full re-serialization of the payload. The JSON twin appends one span per
     * line (newline-delimited JSON), the append-friendly analogue of the length-framed Protobuf log.
     */
    fun appendCompletedSpans(spans: List<Span>) {
        if (spans.isEmpty()) return
        if (writeProto) appendBytes(directory.completedSpansFile, serializer.completedSpansProto(spans))
        if (writeJson) appendBytes(directory.completedSpansJsonFile, serializer.completedSpansJson(spans))
    }

    fun writeSnapshots(snapshots: List<Span>) {
        if (writeProto) writeAtomically(directory.snapshotsFile, serializer.snapshotsProto(snapshots))
        if (writeJson) writeAtomically(directory.snapshotsJsonFile, serializer.snapshotsJson(snapshots))
    }

    fun writeSessionSpan(sessionSpan: Span) {
        if (writeProto) writeAtomically(directory.sessionSpanFile, serializer.sessionSpanProto(sessionSpan))
        if (writeJson) writeAtomically(directory.sessionSpanJsonFile, serializer.sessionSpanJson(sessionSpan))
    }

    private fun appendBytes(dst: File, bytes: ByteArray) {
        // second constructor arg opens the file in append mode
        FileOutputStream(dst, true).buffered().use { it.write(bytes) }
    }

    private fun writeAtomically(dst: File, bytes: ByteArray) {
        val tmp = File.createTempFile(dst.name, ".tmp", dst.parentFile)
        tmp.outputStream().buffered().use { it.write(bytes) }
        if (!tmp.renameTo(dst)) {
            tmp.copyTo(dst, overwrite = true)
            tmp.delete()
        }
    }
}
