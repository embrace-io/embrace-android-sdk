package io.embrace.android.embracesdk.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.logging.InternalLoggerImpl
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDecoder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Measures what each persistence layer costs to turn the bytes it stored back into one session,
 * without I/O. The counterpart to [PersistenceSerializationBenchmarks], over the same session.
 *
 * [deserializeSessionSingleFile] measures **gunzip** and JSON decoding.
 * [deserializeSessionMultiFile] measures decoding uncompressed protobuf, mapping it to the payload
 * types, and assembling the envelope.
 */
@RunWith(AndroidJUnit4::class)
class PersistenceDeserializationBenchmarks {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val serializer: PlatformSerializer = EmbraceSerializer()
    private val decoder = SessionPartDecoder(InternalLoggerImpl())
    private lateinit var fixture: StoredSessionFixture

    @Before
    fun setup() {
        fixture = StoredSessionFixture()
    }

    @Test
    fun deserializeSessionSingleFile() {
        benchmarkRule.measureRepeated {
            val stored = runWithMeasurementDisabled { ByteArrayInputStream(fixture.singleFileBytes) }
            val envelope = deserializeSingleFile(stored)
            runWithMeasurementDisabled { checkSpanCount(envelope) }
        }
    }

    @Test
    fun deserializeSessionMultiFile() {
        benchmarkRule.measureRepeated {
            val stored = runWithMeasurementDisabled { fixture.newPartSource() }
            val envelope = decoder.decode(stored)
            runWithMeasurementDisabled { checkSpanCount(checkNotNull(envelope) { "session part did not decode" }) }
        }
    }

    private fun deserializeSingleFile(stored: ByteArrayInputStream): Envelope<SessionPartPayload> =
        serializer.fromJson(GZIPInputStream(stored), Envelope.sessionEnvelopeSerializer)

    private fun checkSpanCount(envelope: Envelope<SessionPartPayload>) {
        val payload = checkNotNull(envelope.data) { "envelope has no payload" }
        val observed = payload.spans.orEmpty().size + payload.spanSnapshots.orEmpty().size
        check(observed == fixture.expectedSpanCount) {
            "expected ${fixture.expectedSpanCount} spans but read back $observed"
        }
    }
}
