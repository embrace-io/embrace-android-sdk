package io.embrace.android.embracesdk.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.persistence.CompletedSpans
import io.embrace.android.embracesdk.internal.session.persistence.EnvelopeMetadataProto
import io.embrace.android.embracesdk.internal.session.persistence.SessionManifest
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartSpan
import io.embrace.android.embracesdk.internal.session.persistence.SpanSnapshots
import io.embrace.android.embracesdk.internal.session.persistence.buildCompletedSpans
import io.embrace.android.embracesdk.internal.session.persistence.buildEnvelopeMetadata
import io.embrace.android.embracesdk.internal.session.persistence.buildSessionManifest
import io.embrace.android.embracesdk.internal.session.persistence.buildSessionPartSpan
import io.embrace.android.embracesdk.internal.session.persistence.buildSpanSnapshots
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.GZIPOutputStream

/**
 * Measures what each persistence layer costs to turn one session into the bytes it stores, without I/O.
 *
 * [serializeSessionSingleFile] measures JSON encoding **and gzip**.
 * [serializeSessionMultiFile] measures encoding to uncompressed protobuf.
 */
@RunWith(AndroidJUnit4::class)
class PersistenceSerializationBenchmarks {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val serializer: PlatformSerializer = EmbraceSerializer()
    private val sink = CountingOutputStream()
    private lateinit var fixture: SimpleSessionFixture

    @Before
    fun setup() {
        fixture = SimpleSessionFixture()
    }

    @Test
    fun serializeSessionSingleFile() {
        benchmarkRule.measureRepeated {
            runWithMeasurementDisabled { sink.reset() }
            serializeSingleFile()
        }
    }

    @Test
    fun serializeSessionMultiFile() {
        benchmarkRule.measureRepeated {
            runWithMeasurementDisabled { sink.reset() }
            serializeMultiFile()
        }
    }

    private fun serializeSingleFile() {
        serializer.toJson(fixture.envelope, Envelope.sessionEnvelopeSerializer, GZIPOutputStream(sink))
    }

    private fun serializeMultiFile(): Long {
        val manifest = buildSessionManifest(
            resource = fixture.resource,
            directory = fixture.directory,
            envelopeVersion = fixture.envelopeVersion,
            envelopeType = fixture.envelopeType,
            sharedLibSymbolMapping = null,
        )
        SessionManifest.ADAPTER.encode(sink, manifest)

        val metadata = buildEnvelopeMetadata(fixture.metadata, fixture.resource)
        EnvelopeMetadataProto.ADAPTER.encode(sink, metadata)

        val sessionSpan = buildSessionPartSpan(fixture.sessionSpan)
        SessionPartSpan.ADAPTER.encode(sink, sessionSpan)

        val snapshots = buildSpanSnapshots(fixture.spanSnapshots)
        SpanSnapshots.ADAPTER.encode(sink, snapshots)

        val completedSpans = CompletedSpans.ADAPTER.encode(buildCompletedSpans(fixture.completedSpans))
        return sink.bytesWritten + completedSpans.size
    }
}
