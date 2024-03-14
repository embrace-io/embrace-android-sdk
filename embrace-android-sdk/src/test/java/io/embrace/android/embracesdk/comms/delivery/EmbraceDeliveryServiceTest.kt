package io.embrace.android.embracesdk.comms.delivery

import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.FakeNdkService
import io.embrace.android.embracesdk.fakes.FakeApiService
import io.embrace.android.embracesdk.fakes.FakeDeliveryCacheManager
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.fakes.fakeV1SessionMessage
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType.JVM_CRASH
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType.NORMAL_END
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType.PERIODIC_CACHE
import io.embrace.android.embracesdk.worker.BackgroundWorker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceDeliveryServiceTest {

    private val session = fakeSession()
    private val sessionMessage = fakeV1SessionMessage()
    private val anotherMessage =
        fakeV1SessionMessage().copy(session = session.copy(sessionId = "session2"))

    private lateinit var worker: BackgroundWorker
    private lateinit var deliveryCacheManager: FakeDeliveryCacheManager
    private lateinit var apiService: FakeApiService
    private lateinit var ndkService: FakeNdkService
    private lateinit var gatingService: FakeGatingService
    private lateinit var logger: InternalEmbraceLogger
    private lateinit var deliveryService: EmbraceDeliveryService
    private lateinit var sessionIdTracker: FakeSessionIdTracker

    @Before
    fun setUp() {
        worker = BackgroundWorker(MoreExecutors.newDirectExecutorService())
        deliveryCacheManager = FakeDeliveryCacheManager()
        apiService = FakeApiService()
        ndkService = FakeNdkService()
        gatingService = FakeGatingService()
        logger = InternalEmbraceLogger()
        sessionIdTracker = FakeSessionIdTracker()
    }

    @After
    fun after() {
        gatingService.sessionMessagesFiltered.clear()
    }

    private fun initializeDeliveryService() {
        deliveryService = EmbraceDeliveryService(
            deliveryCacheManager,
            apiService,
            worker,
            EmbraceSerializer(),
            logger
        )
    }

    @Test
    fun `cache current session successfully`() {
        initializeDeliveryService()
        deliveryService.sendSession(sessionMessage, NORMAL_END)

        val observed = deliveryCacheManager.saveSessionRequests.single()
        assertEquals(Pair(sessionMessage, NORMAL_END), observed)
    }

    @Test
    fun `cache periodic session successful`() {
        initializeDeliveryService()
        deliveryService.sendSession(sessionMessage, PERIODIC_CACHE)

        val observed = deliveryCacheManager.saveSessionRequests.last()
        assertEquals(Pair(sessionMessage, PERIODIC_CACHE), observed)
    }

    @Test
    fun `cache session on crash successful`() {
        initializeDeliveryService()
        deliveryService.sendSession(sessionMessage, JVM_CRASH)

        val observed = deliveryCacheManager.saveSessionRequests.last()
        assertEquals(Pair(sessionMessage, JVM_CRASH), observed)
    }

    @Test
    fun `if no previous cached session then send previous cached sessions should not send anything`() {
        initializeDeliveryService()
        deliveryService.sendCachedSessions(null, sessionIdTracker)
        assertTrue(apiService.sessionRequests.isEmpty())
    }

    @Test
    fun `send previously cached sessions successfully`() {
        initializeDeliveryService()
        deliveryCacheManager.addCachedSessions(sessionMessage, anotherMessage)

        deliveryService.sendCachedSessions(null, sessionIdTracker)
        assertEquals(listOf(sessionMessage, anotherMessage), apiService.sessionRequests)
    }

    @Test
    fun `ignore current session when sending previously cached sessions`() {
        initializeDeliveryService()
        deliveryCacheManager.addCachedSessions(sessionMessage, anotherMessage)
        sessionIdTracker.sessionId = anotherMessage.session.sessionId
        deliveryService.sendCachedSessions(null, sessionIdTracker)
        assertEquals(listOf(sessionMessage), apiService.sessionRequests)
    }

    @Test
    fun `if an exception is thrown while sending cached session then sendCachedSession should not crash`() {
        initializeDeliveryService()
        deliveryCacheManager.addCachedSessions(sessionMessage)
        apiService.throwExceptionSendSession = true
        deliveryService.sendCachedSessions(null, sessionIdTracker)
        assertTrue(apiService.sessionRequests.isEmpty())
    }

    @Test
    fun `send session end with crash`() {
        initializeDeliveryService()
        deliveryService.sendSession(sessionMessage, JVM_CRASH)
        assertEquals(sessionMessage, apiService.sessionRequests.last())
    }

    @Test
    fun `check for native crash info if ndk feature is enabled`() {
        initializeDeliveryService()
        deliveryService.sendCachedSessions(ndkService, sessionIdTracker)
        assertEquals(1, ndkService.checkForNativeCrashCount)
    }

    @Test
    fun testSendEventAsync() {
        initializeDeliveryService()
        val obj = EventMessage(Event(eventId = "abc", type = EventType.END))
        deliveryService.sendMoment(obj)
        assertEquals(obj, apiService.eventRequests.single())
    }

    @Test
    fun testSaveCrash() {
        initializeDeliveryService()
        val obj = EventMessage(Event(eventId = "abc", type = EventType.CRASH))
        deliveryService.sendCrash(obj, true)
        assertEquals(obj, deliveryCacheManager.saveCrashRequests.single())
        assertEquals(obj, apiService.crashRequests.single())
    }
}
