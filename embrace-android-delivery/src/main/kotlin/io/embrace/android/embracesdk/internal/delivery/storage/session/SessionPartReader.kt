package io.embrace.android.embracesdk.internal.delivery.storage.session

import io.embrace.android.embracesdk.internal.delivery.storage.session.proto.CompletedSpanRecordProto
import io.embrace.android.embracesdk.internal.delivery.storage.session.proto.ManifestProto
import io.embrace.android.embracesdk.internal.delivery.storage.session.proto.MetadataFileProto
import io.embrace.android.embracesdk.internal.delivery.storage.session.proto.SessionSpanProto
import io.embrace.android.embracesdk.internal.delivery.storage.session.proto.SpanSnapshotsProto
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import java.io.DataInputStream
import java.io.EOFException
import java.io.File

/**
 * Reads a session-part directory back into a single [Envelope] equivalent to the one the monolithic
 * pipeline produces. Completed spans and the session span are merged into
 * [SessionPartPayload.spans]; snapshots become [SessionPartPayload.spanSnapshots].
 */
class SessionPartReader {

    fun reconstitute(directory: SessionPartDirectory): Envelope<SessionPartPayload>? {
        if (!directory.manifestFile.exists()) return null
        val manifest = ManifestProto.ADAPTER.decode(directory.manifestFile.readBytes())

        val metadata = directory.metadataFile.takeIf(File::exists)?.let { file ->
            MetadataFileProto.ADAPTER.decode(file.readBytes()).metadata?.toPayload()
        }

        val completedSpans = readCompletedSpans(directory.completedSpansFile)
        val sessionSpan = directory.sessionSpanFile.takeIf(File::exists)?.let { file ->
            SessionSpanProto.ADAPTER.decode(file.readBytes()).span?.toPayload()
        }
        val snapshots = directory.snapshotsFile.takeIf(File::exists)?.let { file ->
            SpanSnapshotsProto.ADAPTER.decode(file.readBytes()).spans.map { it.toPayload() }
        }.orEmpty()

        val spans = completedSpans + listOfNotNull(sessionSpan)
        val sharedLibSymbolMapping = manifest.shared_lib_symbol_mapping.ifEmpty { null }

        return Envelope(
            resource = manifest.resource?.toPayload(),
            metadata = metadata,
            version = manifest.version,
            type = manifest.type,
            data = SessionPartPayload(
                spans = spans.ifEmpty { null },
                spanSnapshots = snapshots.ifEmpty { null },
                sharedLibSymbolMapping = sharedLibSymbolMapping,
            ),
        )
    }

    private fun readCompletedSpans(file: File): List<Span> {
        if (!file.exists()) return emptyList()
        val spans = mutableListOf<Span>()
        DataInputStream(file.inputStream().buffered()).use { input ->
            while (true) {
                val length = try {
                    input.readInt()
                } catch (_: EOFException) {
                    break
                }
                val bytes = ByteArray(length)
                input.readFully(bytes)
                CompletedSpanRecordProto.ADAPTER.decode(bytes).span?.let { spans.add(it.toPayload()) }
            }
        }
        return spans
    }
}
