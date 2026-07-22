package io.embrace.android.embracesdk.internal.delivery.storage.session

import io.embrace.android.embracesdk.internal.delivery.storage.session.proto.CompletedSpanRecordProto
import io.embrace.android.embracesdk.internal.delivery.storage.session.proto.ManifestProto
import io.embrace.android.embracesdk.internal.delivery.storage.session.proto.MetadataFileProto
import io.embrace.android.embracesdk.internal.delivery.storage.session.proto.SessionSpanProto
import io.embrace.android.embracesdk.internal.delivery.storage.session.proto.SpanSnapshotsProto
import io.embrace.android.embracesdk.internal.payload.Span
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

/**
 * Encodes the constituent parts of a session part to bytes, in both Protobuf (via Wire) and JSON
 * (via kotlinx-serialization). Serialization is separated from the file I/O in [SessionPartWriter]
 * so the encoding cost of the two formats can be measured in isolation (see the microbenchmarks),
 * and so both the writer and the benchmarks encode identical bytes from one place.
 */
class SessionPartSerializer(private val json: Json = defaultJson) {

    fun manifestProto(manifest: Manifest): ByteArray = ManifestProto(
        resource = manifest.resource.toProto(),
        version = manifest.version,
        type = manifest.type,
        shared_lib_symbol_mapping = manifest.sharedLibSymbolMapping.orEmpty(),
    ).encode()

    fun manifestJson(manifest: Manifest): ByteArray = json.encodeToString(manifest).encodeToByteArray()

    fun decodeManifestProto(bytes: ByteArray): Manifest = ManifestProto.ADAPTER.decode(bytes).let { proto ->
        Manifest(
            resource = proto.resource?.toPayload() ?: error("Manifest is missing a resource"),
            version = proto.version,
            type = proto.type,
            sharedLibSymbolMapping = proto.shared_lib_symbol_mapping.ifEmpty { null },
        )
    }

    fun decodeManifestJson(bytes: ByteArray): Manifest = json.decodeFromString(bytes.decodeToString())

    fun metadataProto(metadata: SessionMetadata): ByteArray =
        MetadataFileProto(metadata = metadata.metadata.toProto()).encode()

    fun metadataJson(metadata: SessionMetadata): ByteArray = json.encodeToString(metadata).encodeToByteArray()

    fun decodeMetadataProto(bytes: ByteArray): SessionMetadata = SessionMetadata(
        metadata = MetadataFileProto.ADAPTER.decode(bytes).metadata?.toPayload()
            ?: error("Metadata file is missing metadata"),
    )

    fun decodeMetadataJson(bytes: ByteArray): SessionMetadata = json.decodeFromString(bytes.decodeToString())

    /**
     * Encodes completed spans as individual length-framed records, the append-friendly layout the
     * writer flushes so an ended span costs one append rather than re-serializing the payload.
     */
    fun completedSpansProto(spans: List<Span>): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            spans.forEach { span ->
                val bytes = CompletedSpanRecordProto(span = span.toProto()).encode()
                out.writeInt(bytes.size)
                out.write(bytes)
            }
        }
        return buffer.toByteArray()
    }

    /** Newline-delimited JSON: the append-friendly analogue of the length-framed Protobuf log. */
    fun completedSpansJson(spans: List<Span>): ByteArray {
        val buffer = ByteArrayOutputStream()
        spans.forEach { span ->
            buffer.write(json.encodeToString(span).encodeToByteArray())
            buffer.write('\n'.code)
        }
        return buffer.toByteArray()
    }

    /** Reverses [completedSpansProto], reading each length-framed record back into a [Span]. */
    fun decodeCompletedSpansProto(bytes: ByteArray): List<Span> {
        val spans = mutableListOf<Span>()
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            while (true) {
                val length = try {
                    input.readInt()
                } catch (_: EOFException) {
                    break
                }
                val record = ByteArray(length)
                input.readFully(record)
                CompletedSpanRecordProto.ADAPTER.decode(record).span?.let { spans.add(it.toPayload()) }
            }
        }
        return spans
    }

    /** Reverses [completedSpansJson], decoding each newline-delimited record into a [Span]. */
    fun decodeCompletedSpansJson(bytes: ByteArray): List<Span> =
        bytes.decodeToString().lineSequence()
            .filter(String::isNotEmpty)
            .map { json.decodeFromString<Span>(it) }
            .toList()

    fun snapshotsProto(snapshots: List<Span>): ByteArray =
        SpanSnapshotsProto(spans = snapshots.map(Span::toProto)).encode()

    fun snapshotsJson(snapshots: List<Span>): ByteArray = json.encodeToString(snapshots).encodeToByteArray()

    fun decodeSnapshotsProto(bytes: ByteArray): List<Span> =
        SpanSnapshotsProto.ADAPTER.decode(bytes).spans.map { it.toPayload() }

    fun decodeSnapshotsJson(bytes: ByteArray): List<Span> = json.decodeFromString(bytes.decodeToString())

    fun sessionSpanProto(sessionSpan: Span): ByteArray = SessionSpanProto(span = sessionSpan.toProto()).encode()

    fun sessionSpanJson(sessionSpan: Span): ByteArray = json.encodeToString(sessionSpan).encodeToByteArray()

    fun decodeSessionSpanProto(bytes: ByteArray): Span =
        SessionSpanProto.ADAPTER.decode(bytes).span?.toPayload() ?: error("Session span file is missing a span")

    fun decodeSessionSpanJson(bytes: ByteArray): Span = json.decodeFromString(bytes.decodeToString())

    private companion object {
        /** Mirrors the config of the legacy `embraceJson` instance so the JSON comparison is fair. */
        private val defaultJson: Json = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
        }
    }
}
