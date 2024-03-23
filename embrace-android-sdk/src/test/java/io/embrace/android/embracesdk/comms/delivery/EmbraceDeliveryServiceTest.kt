package io.embrace.android.embracesdk.comms.delivery

import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.FakeNdkService
import io.embrace.android.embracesdk.fakes.FakeApiService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeStorageService
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.fakes.fakeV1SessionMessage
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType.NORMAL_END
import io.embrace.android.embracesdk.worker.BackgroundWorker
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
    private lateinit var ndkService: FakeNdkService
    private lateinit var gatingService: FakeGatingService
    private lateinit var testPlatformSerializer: TestPlatformSerializer
    private lateinit var fakeStorageService: FakeStorageService
    private lateinit var cacheService: EmbraceCacheService
    private lateinit var logger: InternalEmbraceLogger
    private lateinit var deliveryService: EmbraceDeliveryService
    private lateinit var sessionIdTracker: FakeSessionIdTracker

    @Before
    fun setUp() {
        fakeClock = FakeClock()
        worker = BackgroundWorker(MoreExecutors.newDirectExecutorService())
        apiService = FakeApiService()
        ndkService = FakeNdkService()
        gatingService = FakeGatingService()
        logger = InternalEmbraceLogger()
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
        deliveryService.sendCachedSessions(null, sessionIdTracker)
        assertTrue(apiService.sessionRequests.isEmpty())
    }

    @Test
    fun `send previously cached sessions successfully`() {
        assertNotNull(cacheService.writeSession(sessionFileName, sessionMessage))
        assertNotNull(cacheService.writeSession(anotherMessageFileName, anotherMessage))
        deliveryService.sendCachedSessions(null, sessionIdTracker)
        assertTrue(apiService.sessionRequests.contains(sessionMessage))
        assertTrue(apiService.sessionRequests.contains(anotherMessage))
        assertEquals(2, apiService.sessionRequests.size)
        val sessionMap = apiService.sessionRequests.associateBy { it.session.sessionId }
        assertEquals(sessionMessage, sessionMap[sessionMessage.session.sessionId])
        assertEquals(anotherMessage, sessionMap[anotherMessage.session.sessionId])
    }

    @Test
    fun `ignore current session when sending previously cached sessions`() {
        assertNotNull(cacheService.writeSession(sessionFileName, sessionMessage))
        assertNotNull(cacheService.writeSession(anotherMessageFileName, anotherMessage))
        sessionIdTracker.sessionId = anotherMessage.session.sessionId
        deliveryService.sendCachedSessions(null, sessionIdTracker)
        assertEquals(listOf(sessionMessage), apiService.sessionRequests)
    }

    @Test
    fun `if an exception is thrown while sending cached session then sendCachedSession should not crash`() {
        assertNotNull(cacheService.writeSession(sessionFileName, sessionMessage))
        apiService.throwExceptionSendSession = true
        deliveryService.sendCachedSessions(null, sessionIdTracker)
        assertTrue(apiService.sessionRequests.isEmpty())
    }

    @Test
    fun `check for native crash info if ndk feature is enabled`() {
        deliveryService.sendCachedSessions(ndkService, sessionIdTracker)
        assertEquals(1, ndkService.checkForNativeCrashCount)
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

    companion object {
        private val sessionMessage = fakeV1SessionMessage()
        private val sessionFileName = CachedSession.create(
            sessionMessage.session.sessionId,
            sessionMessage.session.startTime,
            false
        ).filename
        private val anotherMessage =
            fakeV1SessionMessage().copy(session = fakeSession().copy(sessionId = "session2"))
        private val anotherMessageFileName = CachedSession.create(
            anotherMessage.session.sessionId,
            anotherMessage.session.startTime,
            false
        ).filename
    }
}
