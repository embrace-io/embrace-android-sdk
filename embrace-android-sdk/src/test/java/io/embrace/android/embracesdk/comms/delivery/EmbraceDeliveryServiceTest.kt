package io.embrace.android.embracesdk.comms.delivery

import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.fakes.FakeApiService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakeNativeCrashService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeSpanData.Companion.perfSpanSnapshot
import io.embrace.android.embracesdk.fakes.FakeStorageService
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.fakes.fakeV1EndedSessionMessage
import io.embrace.android.embracesdk.fakes.fakeV1EndedSessionMessageWithSnapshot
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType.NORMAL_END
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceDeliveryServiceTest {

    private lateinit var fakeClock: FakeClock
    private lateinit var worker: BackgroundWorker
    private lateinit var deliveryCacheManager: EmbraceDeliveryCacheManager
    private lateinit var apiService: FakeApiService
    private lateinit var fakeNativeCrashService: FakeNativeCrashService
    private lateinit var gatingService: FakeGatingService
    private lateinit var testPlatformSerializer: TestPlatformSerializer
    private lateinit var fakeStorageService: FakeStorageService
    private lateinit var cacheService: EmbraceCacheService
    private lateinit var logger: EmbLogger
    private lateinit var deliveryService: EmbraceDeliveryService
    private lateinit var sessionIdTracker: FakeSessionIdTracker

    @Before
    fun setUp() {
        fakeClock = FakeClock()
        worker = BackgroundWorker(MoreExecutors.newDirectExecutorService())
        apiService = FakeApiService()
        fakeNativeCrashService = FakeNativeCrashService()
        gatingService = FakeGatingService()
        logger = EmbLoggerImpl()
        sessionIdTracker = FakeSessionIdTracker()
        fakeStorageService = FakeStorageService()
        testPlatformSerializer = TestPlatformSerializer()
        cacheService = EmbraceCacheService(
            storageService = fakeStorageService,
            serializer = testPlatformSerializer,
            logger = logger
        )
        deliveryCacheManager = EmbraceDeliveryCacheManager(
            cacheService = cacheService,
            backgroundWorker = worker,
            logger = logger,
        )
        deliveryService = EmbraceDeliveryService(
            deliveryCacheManager,
            apiService,
            worker,
            testPlatformSerializer,
            logger
        )
    }

    @Test
    fun `send session successfully`() {
        deliveryService.sendSession(sessionMessage, NORMAL_END)
        assertTrue(apiService.sessionRequests.contains(sessionMessage))
        assertEquals(0, apiService.futureGetCount)
        assertNull(cacheService.loadObject(sessionFileName, SessionMessage::class.java))
    }

    @Test
    fun `cache session successfully`() {
        deliveryService.sendSession(sessionMessage, SessionSnapshotType.PERIODIC_CACHE)
        assertEquals(0, apiService.sessionRequests.size)
        assertNotNull(cacheService.loadObject(sessionFileName, SessionMessage::class.java))
    }

    @Test
    fun `send session synchronously on crash successfully`() {
        deliveryService.sendSession(sessionMessage, SessionSnapshotType.JVM_CRASH)
        assertTrue(apiService.sessionRequests.contains(sessionMessage))
        assertEquals(1, apiService.futureGetCount)
        assertNull(cacheService.loadObject(sessionFileName, SessionMessage::class.java))
    }

    @Test
    fun `if no previous cached session then send previous cached sessions should not send anything`() {
        deliveryService.sendCachedSessions({ fakeNativeCrashService }, sessionIdTracker)
        assertTrue(apiService.sessionRequests.isEmpty())
    }

    @Test
    fun `send previously cached sessions successfully`() {
        assertNotNull(cacheService.writeSession(sessionFileName, sessionMessage))
        assertNotNull(cacheService.writeSession(anotherMessageFileName, anotherMessage))
        deliveryService.sendCachedSessions({ fakeNativeCrashService }, sessionIdTracker)
        assertTrue(apiService.sessionRequests.contains(sessionMessage))
        assertTrue(apiService.sessionRequests.contains(anotherMessage))
        assertEquals(2, apiService.sessionRequests.size)
        val sessionMap = apiService.sessionRequests.associateBy { it.session.sessionId }
        assertEquals(sessionMessage, sessionMap[sessionMessage.session.sessionId])
        assertEquals(anotherMessage, sessionMap[anotherMessage.session.sessionId])
    }

    @Test
    fun `fail previously cached snapshot when sending cached session`() {
        assertNotNull(cacheService.writeSession(sessionWithSnapshotFileName, sessionWithSnapshot))
        deliveryService.sendCachedSessions({ fakeNativeCrashService }, sessionIdTracker)
        val sentSession = apiService.sessionRequests.single()
        assertEquals(2, sentSession.spans?.size)
        assertEquals(0, sentSession.spanSnapshots?.size)
        val snapshot = checkNotNull(sessionWithSnapshot.spanSnapshots).single()
        assertEmbraceSpanData(
            span = sentSession.spans?.single { it.spanId == snapshot.spanId },
            expectedStartTimeMs = checkNotNull(snapshot.startTimeNanos.nanosToMillis()),
            expectedEndTimeMs = checkNotNull(sessionWithSnapshot.session.endTime),
            expectedParentId = SpanId.getInvalid(),
            expectedErrorCode = ErrorCode.FAILURE,
            expectedCustomAttributes = mapOf(
                EmbType.Performance.Default.toEmbraceKeyValuePair()
            )
        )
    }

    @Test
    fun `do not add failed span from a snapshot if a span with the same id is already in the payload`() {
        val startedSnapshot = EmbraceSpanData(perfSpanSnapshot)
        val completedSpan = startedSnapshot.copy(status = StatusCode.OK, endTimeNanos = startedSnapshot.startTimeNanos + 10000000L)
        val snapshots = listOfNotNull(startedSnapshot)
        val spans = listOf(completedSpan)
        val messedUpSession = fakeV1EndedSessionMessage().copy(spans = spans, spanSnapshots = snapshots)
        assertEquals(1, messedUpSession.spans?.size)
        assertEquals(1, messedUpSession.spanSnapshots?.size)
        val messedUpSessionFilename = CachedSession.create(
            messedUpSession.session.sessionId,
            messedUpSession.session.startTime,
            false
        ).filename
        assertNotNull(cacheService.writeSession(messedUpSessionFilename, messedUpSession))
        deliveryService.sendCachedSessions({ fakeNativeCrashService }, sessionIdTracker)
        val sentSession = apiService.sessionRequests.single()
        assertEquals(1, sentSession.spans?.size)
        assertEquals(0, sentSession.spanSnapshots?.size)
        assertEquals(completedSpan, sentSession.spans?.single())
    }

    @Test
    fun `crash during sending of cache session should preserve conversion of failed snapshot`() {
        assertNotNull(cacheService.writeSession(sessionWithSnapshotFileName, sessionWithSnapshot))
        assertEquals(1, sessionWithSnapshot.spans?.size)
        assertEquals(1, sessionWithSnapshot.spanSnapshots?.size)
        apiService.throwExceptionSendSession = true
        deliveryService.sendCachedSessions({ fakeNativeCrashService }, sessionIdTracker)
        assertTrue(apiService.sessionRequests.isEmpty())
        val transformedSession =
            checkNotNull(cacheService.loadObject(sessionWithSnapshotFileName, SessionMessage::class.java))
        assertEquals(2, transformedSession.spans?.size)
        assertEquals(0, transformedSession.spanSnapshots?.size)
    }

    @Test
    fun `ignore current session when sending previously cached sessions`() {
        assertNotNull(cacheService.writeSession(sessionFileName, sessionMessage))
        assertNotNull(cacheService.writeSession(anotherMessageFileName, anotherMessage))
        sessionIdTracker.sessionId = anotherMessage.session.sessionId
        deliveryService.sendCachedSessions({ fakeNativeCrashService }, sessionIdTracker)
        assertEquals(listOf(sessionMessage), apiService.sessionRequests)
    }

    @Test
    fun `if an exception is thrown while sending cached session then sendCachedSession should not crash`() {
        assertNotNull(cacheService.writeSession(sessionFileName, sessionMessage))
        apiService.throwExceptionSendSession = true
        deliveryService.sendCachedSessions({ fakeNativeCrashService }, sessionIdTracker)
        assertTrue(apiService.sessionRequests.isEmpty())
    }

    @Test
    fun `check for native crash info if native crash service found`() {
        deliveryService.sendCachedSessions({ fakeNativeCrashService }, sessionIdTracker)
        assertEquals(1, fakeNativeCrashService.checkAndSendNativeCrashInvocation)
    }

    @Test
    fun testSendEventAsync() {
        val obj = EventMessage(Event(eventId = "abc", type = EventType.END))
        deliveryService.sendMoment(obj)
        assertEquals(obj, apiService.eventRequests.single())
    }

    @Test
    fun testSaveCrash() {
        val obj = EventMessage(Event(eventId = "abc", type = EventType.CRASH))
        deliveryService.sendCrash(obj, true)
        assertEquals(obj, apiService.crashRequests.single())
    }

    @Test
    fun testSendLogs() {
        deliveryService.sendLogs(logsEnvelope)
        assertEquals(logsEnvelope.data, apiService.sentLogPayloads.single())
        assertEquals(0, apiService.savedLogPayloads.size)
    }

    @Test
    fun testSaveLogs() {
        deliveryService.saveLogs(logsEnvelope)
        assertEquals(logsEnvelope.data, apiService.savedLogPayloads.single())
        assertEquals(0, apiService.sentLogPayloads.size)
    }

    companion object {
        private val sessionMessage = fakeV1EndedSessionMessage()
        private val sessionFileName = CachedSession.create(
            sessionMessage.session.sessionId,
            sessionMessage.session.startTime,
            false
        ).filename
        private val anotherMessage = sessionMessage.copy(session = fakeSession().copy(sessionId = "session2"))
        private val anotherMessageFileName = CachedSession.create(
            anotherMessage.session.sessionId,
            anotherMessage.session.startTime,
            false
        ).filename
        private val sessionWithSnapshot = fakeV1EndedSessionMessageWithSnapshot()
        private val sessionWithSnapshotFileName = CachedSession.create(
            sessionWithSnapshot.session.sessionId,
            sessionWithSnapshot.session.startTime,
            false
        ).filename
        private val logsEnvelope = Envelope(
            resource = FakeEnvelopeResourceSource().resource,
            metadata = FakeEnvelopeMetadataSource().metadata,
            version = "0.1.0",
            type = "logs",
            data = LogPayload(
                logs = listOf(
                    Log(),
                    Log()
                )
            )
        )
    }
}
