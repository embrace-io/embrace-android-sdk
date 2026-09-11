@file:OptIn(ExperimentalSemconv::class)

package io.embrace.android.embracesdk.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.persistence.CompletedSpans
import io.embrace.android.embracesdk.internal.session.persistence.SessionMetadata
import io.embrace.android.embracesdk.internal.session.persistence.SpanSnapshots
import io.embrace.android.embracesdk.internal.session.persistence.buildCompletedSpans
import io.embrace.android.embracesdk.internal.session.persistence.buildSessionMetadata
import io.embrace.android.embracesdk.internal.session.persistence.buildSpanSnapshots
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.zip.GZIPOutputStream

/**
 * Measures what each persistence layer costs to turn one session into the bytes it stores, without I/O.
 *
 * [serializeSessionSingleFile] measures JSON encoding **and gzip**.
 * [serializeSessionMultiFile] measures encoding to uncompressed protobuf.
 * [serializeCacheTickMultiFile] measures only what one periodic cache window rewrites.
 */
@RunWith(Parameterized::class)
class PersistenceSerializationBenchmarks(
    private val completedSpanCount: Int,
    private val attributesPerSpan: Int,
) {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val serializer: PlatformSerializer = EmbraceSerializer()
    private val sink = CountingOutputStream()
    private lateinit var fixture: SimpleSessionFixture
    private lateinit var windowSpans: List<Span>

    @Before
    fun setup() {
        fixture = SimpleSessionFixture(completedSpanCount, attributesPerSpan)
        windowSpans = fixture.completedSpans.takeLast(WINDOW_SPAN_COUNT)
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

    /**
     * Measures what one periodic cache window costs the multi-file layer: the snapshots file is
     * rewritten in full, which is where the session span is held with a fresh heartbeat until it
     * ends, and the spans that ended during the window were appended as they ended.
     */
    @Test
    fun serializeCacheTickMultiFile() {
        benchmarkRule.measureRepeated {
            runWithMeasurementDisabled { sink.reset() }
            serializeCacheTick()
        }
    }

    private fun serializeSingleFile() {
        serializer.toJson(fixture.envelope, Envelope.sessionEnvelopeSerializer, GZIPOutputStream(sink))
    }

    private fun serializeMultiFile(): Long {
        val metadata = buildSessionMetadata(
            metadata = fixture.metadata,
            resource = fixture.resource,
            directory = fixture.directory,
            envelopeVersion = fixture.envelopeVersion,
            envelopeType = fixture.envelopeType,
            sharedLibSymbolMapping = null,
        )
        SessionMetadata.ADAPTER.encode(sink, metadata)

        val snapshots = buildSpanSnapshots(fixture.spanSnapshots)
        SpanSnapshots.ADAPTER.encode(sink, snapshots)

        // the session span is logged as a completed span once it ends
        val completedSpans = CompletedSpans.ADAPTER.encode(
            buildCompletedSpans(fixture.completedSpans + fixture.sessionSpan),
        )
        return sink.bytesWritten + completedSpans.size
    }

    private fun serializeCacheTick() {
        val snapshots = fixture.spanSnapshots + fixture.sessionSpan.withHeartbeat()
        SpanSnapshots.ADAPTER.encode(sink, buildSpanSnapshots(snapshots))
        CompletedSpans.ADAPTER.encode(sink, buildCompletedSpans(windowSpans))
    }

    private fun Span.withHeartbeat(): Span {
        val heartbeat = Attribute(EmbSessionAttributes.EMB_HEARTBEAT_TIME_UNIX_NANO, (endTimeNanos ?: 0L).toString())
        val existing = attributes.orEmpty().filterNot { it.key == heartbeat.key }
        return copy(attributes = existing + heartbeat)
    }

    companion object {
        private const val WINDOW_SPAN_COUNT: Int = 3

        @JvmStatic
        @Parameterized.Parameters(name = "{0,number,#}-spans-{1}-attrs")
        fun shapes(): List<Array<Any>> = SESSION_SHAPES
    }
}
