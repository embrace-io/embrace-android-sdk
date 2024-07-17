package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.concurrency.ExecutionCoordinator
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeStorageService
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fixtures.testSessionEnvelope
import io.embrace.android.embracesdk.fixtures.testSessionEnvelope2
import io.embrace.android.embracesdk.fixtures.testSessionEnvelopeOneMinuteLater
import io.embrace.android.embracesdk.getSessionId
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class EmbraceCacheServiceConcurrentAccessTest {

    private lateinit var embraceCacheService: EmbraceCacheService
    private lateinit var storageService: FakeStorageService
    private lateinit var serializer: TestPlatformSerializer
    private lateinit var logger: FakeEmbLogger
    private lateinit var executionCoordinator: ExecutionCoordinator

    @Before
    fun setUp() {
        storageService = FakeStorageService()
        serializer = TestPlatformSerializer()
        logger = FakeEmbLogger()
        embraceCacheService = EmbraceCacheService(
            storageService,
            serializer,
            logger
        )
        executionCoordinator = ExecutionCoordinator(
            executionModifiers = serializer,
            errorLogsProvider = { logger.errorMessages.mapNotNull(FakeEmbLogger.LogMessage::throwable) }
        )
    }

    @Test
    fun `concurrent write attempts to the same non-existent session file should lead to last finished persist`() {
        val type = Envelope.sessionEnvelopeType
        executionCoordinator.executeOperations(
            first = { embraceCacheService.cacheObject(FILENAME, testSessionEnvelope, type) },
            second = {
                embraceCacheService.cacheObject(FILENAME, testSessionEnvelopeOneMinuteLater, type)
            },
            firstBlocksSecond = true
        )

        assertEquals(
            executionCoordinator.getErrorMessage(),
            testSessionEnvelopeOneMinuteLater.getSessionId(),
            embraceCacheService.loadObject<Envelope<SessionPayload>>(FILENAME, type)?.getSessionId()
        )
    }

    @Test
    fun `accessing sessions with different names should not block`() {
        val type = Envelope.sessionEnvelopeType
        embraceCacheService.cacheObject(FILENAME, testSessionEnvelope2, type)

        executionCoordinator.executeOperations(
            first = { embraceCacheService.cacheObject(FILENAME, testSessionEnvelope, type) },
            second = { embraceCacheService.loadObject<Envelope<SessionPayload>>(FILENAME_2, type) },
            firstBlocksSecond = false
        )
    }

    @Test
    fun `reading a session should not block other reads to same session`() {
        val type = Envelope.sessionEnvelopeType
        embraceCacheService.cacheObject(FILENAME, testSessionEnvelope, type)

        executionCoordinator.executeOperations(
            first = { embraceCacheService.loadObject<Envelope<SessionPayload>>(FILENAME, type) },
            second = { embraceCacheService.loadObject<Envelope<SessionPayload>>(FILENAME, type) },
            firstBlocksSecond = false
        )
    }

    @Test
    fun `reads should block writes`() {
        val type = Envelope.sessionEnvelopeType
        embraceCacheService.cacheObject(FILENAME, testSessionEnvelope, type)

        executionCoordinator.executeOperations(
            first = { embraceCacheService.loadObject<Envelope<SessionPayload>>(FILENAME, type) },
            second = {
                embraceCacheService.cacheObject(FILENAME, testSessionEnvelopeOneMinuteLater, type)
            },
            firstBlocksSecond = true
        )

        assertEquals(
            executionCoordinator.getErrorMessage(),
            testSessionEnvelopeOneMinuteLater.getSessionId(),
            embraceCacheService.loadObject<Envelope<SessionPayload>>(FILENAME, type)?.getSessionId()
        )
    }

    @Test
    fun `reading a file that is being written to should block and succeed`() {
        var readSession: Envelope<SessionPayload>? = null
        val type = Envelope.sessionEnvelopeType

        executionCoordinator.executeOperations(
            first = { embraceCacheService.cacheObject(FILENAME, testSessionEnvelope, type) },
            second = { readSession = embraceCacheService.loadObject(FILENAME, type) },
            firstBlocksSecond = true
        )

        assertEquals(executionCoordinator.getErrorMessage(), testSessionEnvelope.getSessionId(), readSession?.getSessionId())
    }

    @Test
    fun `reading a file that is being rewritten to should block and succeed`() {
        var readSession: Envelope<SessionPayload>? = null
        val type = Envelope.sessionEnvelopeType
        embraceCacheService.cacheObject(FILENAME, testSessionEnvelope, type)

        executionCoordinator.executeOperations(
            first = {
                embraceCacheService.cacheObject(FILENAME, testSessionEnvelopeOneMinuteLater, type)
            },
            second = { readSession = embraceCacheService.loadObject(FILENAME, type) },
            firstBlocksSecond = true
        )

        assertEquals(
            executionCoordinator.getErrorMessage(),
            testSessionEnvelopeOneMinuteLater.getSessionId(),
            readSession?.getSessionId()
        )
    }

    @Test
    fun `interrupting a session write should not leave a file`() {
        val type = Envelope.sessionEnvelopeType
        executionCoordinator.executeOperations(
            first = { embraceCacheService.cacheObject(FILENAME, testSessionEnvelope, type) },
            second = { executionCoordinator.shutdownFirstThread() },
            firstBlocksSecond = false,
            firstOperationFails = true
        )

        assertNull(embraceCacheService.loadObject(FILENAME, type))
    }

    @Test
    fun `interrupting a session rewrite should not overwrite the file`() {
        val type = Envelope.sessionEnvelopeType
        embraceCacheService.cacheObject(FILENAME, testSessionEnvelope, type)

        executionCoordinator.executeOperations(
            first = {
                embraceCacheService.cacheObject(FILENAME, testSessionEnvelopeOneMinuteLater, type)
            },
            second = { executionCoordinator.shutdownFirstThread() },
            firstBlocksSecond = false,
            firstOperationFails = true
        )

        assertEquals(
            executionCoordinator.getErrorMessage(),
            testSessionEnvelope.getSessionId(),
            embraceCacheService.loadObject<Envelope<SessionPayload>>(FILENAME, type)?.getSessionId()
        )
    }

    companion object {
        private const val FILENAME = "testfile-1"
        private const val FILENAME_2 = "testfile-2"
    }
}
