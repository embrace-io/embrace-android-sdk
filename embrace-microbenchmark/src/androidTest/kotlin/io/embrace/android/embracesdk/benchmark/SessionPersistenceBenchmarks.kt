package io.embrace.android.embracesdk.benchmark

import android.util.Log
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.delivery.storage.session.Manifest
import io.embrace.android.embracesdk.internal.delivery.storage.session.SessionMetadata
import io.embrace.android.embracesdk.internal.delivery.storage.session.SessionPartSerializer
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Compares the CPU cost of *serializing* and *deserializing* session parts in-memory. This compares
 * four approaches for each direction:
 *
 * - Existing behavior (whole JSON payload with gzip)
 * - Whole JSON payload without gzip
 * - Multiple files (JSON)
 * - Multiple files (Protobuf)
 *
 * [reportOnDiskSizes] additionally logs the on-disk byte size of each approach (computed in-memory,
 * without touching disk) so payload size can be compared alongside the CPU measurements.
 */
@RunWith(AndroidJUnit4::class)
class SessionPersistenceBenchmarks {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val jsonSerializer = EmbraceSerializer()
    private val partSerializer = SessionPartSerializer()

    private val resource = buildResource()
    private val metadata = SessionMetadata(buildMetadata())
    private val manifest = Manifest(resource, VERSION, TYPE, sharedLibSymbolMapping = emptyMap())
    private val completedSpans = (1..COMPLETED_SPAN_COUNT).map { buildSpan("completed-span-$it") }
    private val snapshots = (1..SNAPSHOT_COUNT).map { buildSpan("open-span-$it") }
    private val sessionSpan = buildSessionSpan()
    private val envelope = Envelope(
        resource = resource,
        metadata = metadata.metadata,
        version = VERSION,
        type = TYPE,
        data = SessionPartPayload(
            spans = completedSpans + sessionSpan,
            spanSnapshots = snapshots,
            sharedLibSymbolMapping = emptyMap(),
        ),
    )

    // Pre-encoded bytes for the deserialization benchmarks, so those measure only decode cost.
    private val envelopeJsonGzipBytes = ByteArrayOutputStream(INITIAL_BUFFER_BYTES).apply {
        jsonSerializer.toJson(envelope, Envelope.sessionEnvelopeSerializer, GZIPOutputStream(this))
    }.toByteArray()
    private val envelopeJsonBytes = ByteArrayOutputStream(INITIAL_BUFFER_BYTES).apply {
        jsonSerializer.toJson(envelope, Envelope.sessionEnvelopeSerializer, this)
    }.toByteArray()

    private val manifestProtoBytes = partSerializer.manifestProto(manifest)
    private val metadataProtoBytes = partSerializer.metadataProto(metadata)
    private val completedSpansProtoBytes = partSerializer.completedSpansProto(completedSpans)
    private val snapshotsProtoBytes = partSerializer.snapshotsProto(snapshots)
    private val sessionSpanProtoBytes = partSerializer.sessionSpanProto(sessionSpan)

    private val manifestJsonBytes = partSerializer.manifestJson(manifest)
    private val metadataJsonBytes = partSerializer.metadataJson(metadata)
    private val completedSpansJsonBytes = partSerializer.completedSpansJson(completedSpans)
    private val snapshotsJsonBytes = partSerializer.snapshotsJson(snapshots)
    private val sessionSpanJsonBytes = partSerializer.sessionSpanJson(sessionSpan)

    // Accumulates output sizes so the encoded bytes cannot be optimized away as dead code.
    private var blackhole = 0

    @Test
    fun serializeWholeEnvelopeJsonWithCompression() {
        benchmarkRule.measureRepeated {
            EmbTrace.trace("persist-legacy") {
                val baos = ByteArrayOutputStream(INITIAL_BUFFER_BYTES)
                jsonSerializer.toJson(envelope, Envelope.sessionEnvelopeSerializer, GZIPOutputStream(baos))
                consume(baos.size())
            }
        }
    }

    @Test
    fun serializeWholeEnvelopeJsonNoCompression() {
        benchmarkRule.measureRepeated {
            EmbTrace.trace("persist-legacy") {
                val baos = ByteArrayOutputStream(INITIAL_BUFFER_BYTES)
                jsonSerializer.toJson(envelope, Envelope.sessionEnvelopeSerializer, baos)
                consume(baos.size())
            }
        }
    }

    @Test
    fun serializeSplitEnvelopeWithProtobuf() {
        benchmarkRule.measureRepeated {
            EmbTrace.trace("persist-proto") {
                consume(EmbTrace.trace("manifest") { partSerializer.manifestProto(manifest) }.size)
                consume(EmbTrace.trace("metadata") { partSerializer.metadataProto(metadata) }.size)
                consume(EmbTrace.trace("completed-spans") { partSerializer.completedSpansProto(completedSpans) }.size)
                consume(EmbTrace.trace("snapshots") { partSerializer.snapshotsProto(snapshots) }.size)
                consume(EmbTrace.trace("session-span") { partSerializer.sessionSpanProto(sessionSpan) }.size)
            }
        }
    }

    @Test
    fun serializeSplitEnvelopeWithJson() {
        benchmarkRule.measureRepeated {
            EmbTrace.trace("persist-json") {
                consume(EmbTrace.trace("manifest") { partSerializer.manifestJson(manifest) }.size)
                consume(EmbTrace.trace("metadata") { partSerializer.metadataJson(metadata) }.size)
                consume(EmbTrace.trace("completed-spans") { partSerializer.completedSpansJson(completedSpans) }.size)
                consume(EmbTrace.trace("snapshots") { partSerializer.snapshotsJson(snapshots) }.size)
                consume(EmbTrace.trace("session-span") { partSerializer.sessionSpanJson(sessionSpan) }.size)
            }
        }
    }

    /**
     * Reports the number of bytes each approach would write to disk, computed in-memory from the
     * same encoded payloads used by the benchmarks (no file IO). Results are logged to logcat so
     * they appear alongside the benchmark output.
     */
    @Test
    fun reportOnDiskSizes() {
        val splitProtoBytes = manifestProtoBytes.size + metadataProtoBytes.size +
            completedSpansProtoBytes.size + snapshotsProtoBytes.size + sessionSpanProtoBytes.size
        val splitJsonBytes = manifestJsonBytes.size + metadataJsonBytes.size +
            completedSpansJsonBytes.size + snapshotsJsonBytes.size + sessionSpanJsonBytes.size

        Log.i(SIZE_TAG, "on-disk size: whole JSON with gzip = ${envelopeJsonGzipBytes.size} bytes")
        Log.i(SIZE_TAG, "on-disk size: whole JSON no gzip = ${envelopeJsonBytes.size} bytes")
        Log.i(SIZE_TAG, "on-disk size: split protobuf (total) = $splitProtoBytes bytes")
        Log.i(SIZE_TAG, "  manifest=${manifestProtoBytes.size} metadata=${metadataProtoBytes.size} " +
            "completed-spans=${completedSpansProtoBytes.size} snapshots=${snapshotsProtoBytes.size} " +
            "session-span=${sessionSpanProtoBytes.size}")
        Log.i(SIZE_TAG, "on-disk size: split JSON (total) = $splitJsonBytes bytes")
        Log.i(SIZE_TAG, "  manifest=${manifestJsonBytes.size} metadata=${metadataJsonBytes.size} " +
            "completed-spans=${completedSpansJsonBytes.size} snapshots=${snapshotsJsonBytes.size} " +
            "session-span=${sessionSpanJsonBytes.size}")
    }

    @Test
    fun deserializeWholeEnvelopeJsonWithCompression() {
        benchmarkRule.measureRepeated {
            EmbTrace.trace("read-legacy") {
                val input = GZIPInputStream(ByteArrayInputStream(envelopeJsonGzipBytes))
                consume(jsonSerializer.fromJson(input, Envelope.sessionEnvelopeSerializer))
            }
        }
    }

    @Test
    fun deserializeWholeEnvelopeJsonNoCompression() {
        benchmarkRule.measureRepeated {
            EmbTrace.trace("read-legacy") {
                val input = ByteArrayInputStream(envelopeJsonBytes)
                consume(jsonSerializer.fromJson(input, Envelope.sessionEnvelopeSerializer))
            }
        }
    }

    @Test
    fun deserializeSplitEnvelopeWithProtobuf() {
        benchmarkRule.measureRepeated {
            EmbTrace.trace("read-proto") {
                consume(EmbTrace.trace("manifest") { partSerializer.decodeManifestProto(manifestProtoBytes) })
                consume(EmbTrace.trace("metadata") { partSerializer.decodeMetadataProto(metadataProtoBytes) })
                consume(EmbTrace.trace("completed-spans") { partSerializer.decodeCompletedSpansProto(completedSpansProtoBytes) })
                consume(EmbTrace.trace("snapshots") { partSerializer.decodeSnapshotsProto(snapshotsProtoBytes) })
                consume(EmbTrace.trace("session-span") { partSerializer.decodeSessionSpanProto(sessionSpanProtoBytes) })
            }
        }
    }

    @Test
    fun deserializeSplitEnvelopeWithJson() {
        benchmarkRule.measureRepeated {
            EmbTrace.trace("read-json") {
                consume(EmbTrace.trace("manifest") { partSerializer.decodeManifestJson(manifestJsonBytes) })
                consume(EmbTrace.trace("metadata") { partSerializer.decodeMetadataJson(metadataJsonBytes) })
                consume(EmbTrace.trace("completed-spans") { partSerializer.decodeCompletedSpansJson(completedSpansJsonBytes) })
                consume(EmbTrace.trace("snapshots") { partSerializer.decodeSnapshotsJson(snapshotsJsonBytes) })
                consume(EmbTrace.trace("session-span") { partSerializer.decodeSessionSpanJson(sessionSpanJsonBytes) })
            }
        }
    }

    private fun consume(size: Int) {
        blackhole += size
    }

    // Folds a decoded object into the blackhole so decode results cannot be optimized away as dead
    // code. identityHashCode is cheap and constant-cost, so it does not distort the measurement.
    private fun consume(value: Any?) {
        blackhole += System.identityHashCode(value)
    }

    private fun buildSpan(name: String): Span {
        val rnd = Random(name.hashCode())
        return Span(
            traceId = rnd.hex(32),
            spanId = rnd.hex(16),
            parentSpanId = rnd.hex(16),
            name = name,
            startTimeNanos = 1_700_000_000_000_000_000L,
            endTimeNanos = 1_700_000_000_500_000_000L,
            status = Span.Status.OK,
            attributes = (1..ATTRIBUTES_PER_SPAN).map { Attribute("attribute.key.$it", rnd.hex(32)) },
            events = (1..EVENTS_PER_SPAN).map { i ->
                SpanEvent(
                    name = "span-event-$i",
                    timestampNanos = 1_700_000_000_100_000_000L + i,
                    attributes = listOf(Attribute("event.key.$i", rnd.hex(32))),
                )
            },
        )
    }

    private fun buildSessionSpan(): Span {
        val rnd = Random("emb-session".hashCode())
        return Span(
            traceId = rnd.hex(32),
            spanId = rnd.hex(16),
            name = "emb-session",
            startTimeNanos = 1_700_000_000_000_000_000L,
            endTimeNanos = 1_700_000_030_000_000_000L,
            status = Span.Status.UNSET,
            attributes = (1..SESSION_ATTRIBUTE_COUNT).map { Attribute("emb.session.attr.$it", rnd.hex(32)) },
        )
    }

    private fun Random.hex(length: Int): String {
        val chars = "0123456789abcdef"
        return buildString(length) { repeat(length) { append(chars[nextInt(chars.length)]) } }
    }

    private fun buildResource() = EnvelopeResource(
        appVersion = "1.2.3",
        buildId = "build-id-123",
        environment = "prod",
        sdkVersion = "6.0.0",
        deviceManufacturer = "Google",
        deviceModel = "Pixel 8",
        deviceArchitecture = "arm64-v8a",
        osName = "Android",
        osVersion = "14",
        osCode = "34",
        screenResolution = "1080x2400",
        numCores = 8,
    )

    private fun buildMetadata() = EnvelopeMetadata(
        userId = "user-abc-123",
        email = "user@example.com",
        username = "benchmark-user",
        personas = setOf("power_user", "beta"),
        timezoneDescription = "Europe/London",
        locale = "en_GB",
    )

    private companion object {
        private const val VERSION = "0.1.0"
        private const val TYPE = "spans"
        private const val COMPLETED_SPAN_COUNT = 50
        private const val SNAPSHOT_COUNT = 5
        private const val ATTRIBUTES_PER_SPAN = 8
        private const val EVENTS_PER_SPAN = 3
        private const val SESSION_ATTRIBUTE_COUNT = 25
        private const val INITIAL_BUFFER_BYTES = 32 * 1024
        private const val SIZE_TAG = "SessionPersistenceSize"
    }
}
