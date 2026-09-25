package io.embrace.android.embracesdk.internal.delivery.intake

import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.FakeSchedulingService
import io.embrace.android.embracesdk.fakes.FakeSectionRecorder
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.SESSION
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

internal class IntakeServiceCountersTest {

    private companion object {
        private const val SERIALIZED_BYTES_COUNTER = "sf-bytes-serialized"
    }

    private val serializer = TestPlatformSerializer()
    private val clock = FakeClock()
    private val sessionEnvelope = Envelope(data = SessionPartPayload(spans = listOf(Span(name = "session-span"))))
    private val sessionMetadata = StoredTelemetryMetadata(clock.now(), "uuid", "pid", SESSION)

    private lateinit var recorder: FakeSectionRecorder
    private lateinit var executorService: BlockableExecutorService
    private lateinit var intakeService: IntakeService

    @Before
    fun setUp() {
        recorder = FakeSectionRecorder()
        SystemTrace.recorder = recorder
        executorService = BlockableExecutorService(blockingMode = true)
        intakeService = IntakeServiceImpl(
            FakeSchedulingService(),
            FakePayloadStorageService(),
            FakePayloadStorageService(),
            FakeInternalLogger(throwOnInternalError = false),
            serializer,
            PriorityWorker(executorService),
        )
    }

    @After
    fun tearDown() {
        SystemTrace.recorder = null
    }

    @Test
    fun `nothing is counted until a payload is taken`() {
        assertNull(recorder.latestCounter(SERIALIZED_BYTES_COUNTER))
    }

    @Test
    fun `the uncompressed size of a payload is counted`() {
        intakeService.take(sessionEnvelope, sessionMetadata)
        executorService.runCurrentlyBlocked()
        assertEquals(uncompressedSize(sessionEnvelope), recorder.latestCounter(SERIALIZED_BYTES_COUNTER))
    }

    @Test
    fun `the count accumulates over the payloads taken`() {
        intakeService.take(sessionEnvelope, sessionMetadata)
        intakeService.take(sessionEnvelope, sessionMetadata.copy(uuid = "uuid2"))
        executorService.runCurrentlyBlocked()
        assertEquals(2 * uncompressedSize(sessionEnvelope), recorder.latestCounter(SERIALIZED_BYTES_COUNTER))
    }

    private fun uncompressedSize(envelope: Envelope<SessionPartPayload>): Long {
        val stream = ByteArrayOutputStream()
        serializer.toJson(envelope, Envelope.sessionEnvelopeSerializer, stream)
        return stream.size().toLong()
    }
}
