package io.embrace.android.embracesdk.comms.delivery

import com.google.common.util.concurrent.MoreExecutors
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
import io.embrace.android.embracesdk.fakes.fakeCachedSessionEnvelopeWithHeartbeatTime
import io.embrace.android.embracesdk.fakes.fakeCachedSessionEnvelopeWithTerminationTime
import io.embrace.android.embracesdk.fakes.fakeSessionEnvelope
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.getSessionId
import io.embrace.android.embracesdk.getStartTime
import io.embrace.android.embracesdk.internal.EventType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.comms.delivery.CachedSession
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceDeliveryCacheManager
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceDeliveryService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType.NORMAL_END
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.opentelemetry.embCrashId
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NativeCrashData
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.SpanId
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
        deliveryService.sendSession(envelope, NORMAL_END)
        checkNotNull(apiService.sessionRequests.single())
        assertEquals(0, apiService.futureGetCount)
        assertNull(cacheService.loadObject(sessionFileName, Envelope.sessionEnvelopeType))
    }

    @Test
    fun `cache session successfully`() {
        deliveryService.sendSession(envelope, SessionSnapshotType.PERIODIC_CACHE)
        assertEquals(0, apiService.sessionRequests.size)
        assertNotNull(cacheService.loadObject(sessionFileName, Envelope.sessionEnvelopeType))
    }

    @Test
    fun `send session synchronously on crash successfully`() {
        deliveryService.sendSession(envelope, SessionSnapshotType.JVM_CRASH)
        checkNotNull(apiService.sessionRequests.single())
        assertEquals(1, apiService.futureGetCount)
        assertNull(cacheService.loadObject(sessionFileName, Envelope.sessionEnvelopeType))
    }

    @Test
    fun `if no previous cached session then send previous cached sessions should not send anything`() {
        deliveryService.sendCachedSessions({ fakeNativeCrashService }, sessionIdTracker)
        assertTrue(apiService.sessionRequests.isEmpty())
    }

    @Test
    fun `send previously cached sessions successfully`() {
        assertNotNull(cacheService.writeSession(sessionFileName, envelope))
        assertNotNull(cacheService.writeSession(anotherMessageFileName, anotherMessage))
        deliveryService.sendCachedSessions({ fakeNativeCrashService }, sessionIdTracker)
        assertEquals(2, apiService.sessionRequests.size)
    }

    @Test
    fun `fail previously cached snapshot when sending cached session`() {
        assertNotNull(cacheService.writeSession(sessionWithSnapshotFileName, sessionWithSnapshot))
        deliveryService.sendCachedSessions({ fakeNativeCrashService }, sessionIdTracker)
        val sentSession = apiService.sessionRequests.single()
        assertEquals(3, sentSession.data.spans?.size)
        assertEquals(0, sentSession.data.spanSnapshots?.size)
        val snapshot = checkNotNull(sessionWithSnapshot.data.spanSnapshots).single()
        val span = sentSession.data.spans?.single { it.spanId == snapshot.spanId }
        assertEmbraceSpanData(
            span = span,
            expectedStartTimeMs = checkNotNull(snapshot.startTimeNanos?.nanosToMillis()),
            expectedEndTimeMs = checkNotNull(sessionWithSnapshot.findSessionSpan().endTimeNanos?.nanosToMillis()),
            expectedParentId = SpanId.getInvalid(),
            expectedErrorCode = ErrorCode.FAILURE,
            expectedCustomAttributes = mapOf(
                EmbType.Performance.Default.toEmbraceKeyValuePair()
            )
        )
    }

    @Test
    fun `missing end time in session will find appropriate fallback for end time of failed span`() {
        assertNotNull(cacheService.writeSession(sessionWithTerminationTimeFileName, sessionWithTerminationTime))
        deliveryService.sendCachedSessions({ fakeNativeCrashService }, sessionIdTracker)
        val sentSessionWithTerminationTime = apiService.sessionRequests.single()
        assertNotNull(cacheService.writeSession(sessionWithLastHeartbeatTimeFileName, sessionWithLastHeartbeatTime))
        deliveryService.sendCachedSessions({ fakeNativeCrashService }, sessionIdTracker)
        val sentSessionWithLastHeartbeatTime = apiService.sessionRequests.last()

        checkNotNull(sessionWithTerminationTime.data.spanSnapshots).single().let { snapshot ->
            val span = sentSessionWithTerminationTime.data.spans?.single { it.spanId == snapshot.spanId }
            assertEmbraceSpanData(
                span = span,
                expectedStartTimeMs = checkNotNull(snapshot.startTimeNanos?.nanosToMillis()),
                expectedEndTimeMs = checkNotNull(sessionWithSnapshot.findSessionSpan().endTimeNanos?.nanosToMillis()),
                expectedParentId = SpanId.getInvalid(),
                expectedErrorCode = ErrorCode.FAILURE,
                expectedCustomAttributes = mapOf(
                    EmbType.Performance.Default.toEmbraceKeyValuePair()
                )
            )
        }

        checkNotNull(sessionWithLastHeartbeatTime.data.spanSnapshots).single().let { snapshot ->
            val span = sentSessionWithLastHeartbeatTime.data.spans?.single { it.spanId == snapshot.spanId }
            assertEmbraceSpanData(
                span = span,
                expectedStartTimeMs = checkNotNull(snapshot.startTimeNanos?.nanosToMillis()),
                expectedEndTimeMs = checkNotNull(sessionWithSnapshot.findSessionSpan().endTimeNanos?.nanosToMillis()),
                expectedParentId = SpanId.getInvalid(),
                expectedErrorCode = ErrorCode.FAILURE,
                expectedCustomAttributes = mapOf(
                    EmbType.Performance.Default.toEmbraceKeyValuePair()
                )
            )
        }
    }

    @Test
    fun `do not add failed span from a snapshot if a span with the same id is already in the payload`() {
        val startedSnapshot = EmbraceSpanData(perfSpanSnapshot)
        val completedSpan = startedSnapshot.copy(endTimeNanos = startedSnapshot.startTimeNanos + 10000000L)
        val snapshots = listOfNotNull(startedSnapshot)
        val base = fakeSessionEnvelope()
        val messedUpSession = base.copy(
            data = checkNotNull(base.data).copy(
                spans = base.data.spans?.plus(completedSpan.toNewPayload()),
                spanSnapshots = snapshots.map(EmbraceSpanData::toNewPayload)
            )
        )
        assertEquals(3, messedUpSession.data.spans?.size)
        assertEquals(1, messedUpSession.data.spanSnapshots?.size)
        val messedUpSessionFilename = CachedSession.create(
            messedUpSession.getSessionId(),
            messedUpSession.getStartTime(),
            false
        ).filename
        assertNotNull(cacheService.writeSession(messedUpSessionFilename, messedUpSession))
        deliveryService.sendCachedSessions({ fakeNativeCrashService }, sessionIdTracker)
        val sentSession = apiService.sessionRequests.single()
        assertEquals(3, sentSession.data.spans?.size)
        assertEquals(0, sentSession.data.spanSnapshots?.size)
    }

    @Test
    fun `crash during sending of cache session should preserve conversion of failed snapshot`() {
        assertNotNull(cacheService.writeSession(sessionWithSnapshotFileName, sessionWithSnapshot))
        assertEquals(2, sessionWithSnapshot.data.spans?.size)
        assertEquals(1, sessionWithSnapshot.data.spanSnapshots?.size)
        apiService.throwExceptionSendSession = true
        deliveryService.sendCachedSessions({ fakeNativeCrashService }, sessionIdTracker)
        assertTrue(apiService.sessionRequests.isEmpty())
        val transformedSession =
            checkNotNull(cacheService.loadObject<Envelope<SessionPayload>>(sessionWithSnapshotFileName, Envelope.sessionEnvelopeType))
        assertEquals(3, transformedSession.data.spans?.size)
        assertEquals(0, transformedSession.data.spanSnapshots?.size)
    }

    @Test
    fun `crash ID is added to previous session`() {
        assertNotNull(cacheService.writeSession(sessionWithSnapshotFileName, sessionWithSnapshot))
        apiService.throwExceptionSendSession = true
        fakeNativeCrashService.data = NativeCrashData(
            "my-crash-id",
            sessionWithSnapshot.getSessionId(),
            0L,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        )
        deliveryService.sendCachedSessions({ fakeNativeCrashService }, sessionIdTracker)
        assertTrue(apiService.sessionRequests.isEmpty())
        val transformedSession =
            checkNotNull(cacheService.loadObject<Envelope<SessionPayload>>(sessionWithSnapshotFileName, Envelope.sessionEnvelopeType))
        val attrs = checkNotNull(transformedSession.getSessionSpan()?.attributes)
        assertEquals("my-crash-id", attrs.findAttributeValue(embCrashId.name))
        assertEquals(3, transformedSession.data.spans?.size)
        assertEquals(0, transformedSession.data.spanSnapshots?.size)
    }

    @Test
    fun `ignore current session when sending previously cached sessions`() {
        assertNotNull(cacheService.writeSession(sessionFileName, envelope))
        assertNotNull(cacheService.writeSession(anotherMessageFileName, anotherMessage))
        sessionIdTracker.sessionId = anotherMessage.getSessionId()
        deliveryService.sendCachedSessions({ fakeNativeCrashService }, sessionIdTracker)
        assertEquals(1, apiService.sessionRequests.size)
    }

    @Test
    fun `if an exception is thrown while sending cached session then sendCachedSession should not crash`() {
        assertNotNull(cacheService.writeSession(sessionFileName, envelope))
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
        private val envelope = fakeSessionEnvelope()
        private val sessionFileName = CachedSession.create(
            envelope.getSessionId(),
            envelope.getStartTime(),
            true
        ).filename
        private val anotherMessage = fakeSessionEnvelope(sessionId = "session2")
        private val anotherMessageFileName = CachedSession.create(
            anotherMessage.getSessionId(),
            anotherMessage.getStartTime(),
            false
        ).filename
        private val sessionWithSnapshot = fakeSessionEnvelope()
        private val sessionWithSnapshotFileName = CachedSession.create(
            sessionWithSnapshot.getSessionId(),
            sessionWithSnapshot.getStartTime(),
            false
        ).filename
        private val sessionWithTerminationTime = fakeCachedSessionEnvelopeWithTerminationTime()
        private val sessionWithTerminationTimeFileName = CachedSession.create(
            sessionWithTerminationTime.getSessionId(),
            sessionWithTerminationTime.getStartTime(),
            false
        ).filename
        private val sessionWithLastHeartbeatTime = fakeCachedSessionEnvelopeWithHeartbeatTime()
        private val sessionWithLastHeartbeatTimeFileName = CachedSession.create(
            sessionWithLastHeartbeatTime.getSessionId(),
            sessionWithLastHeartbeatTime.getStartTime(),
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
