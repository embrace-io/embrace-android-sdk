package io.embrace.android.embracesdk.comms.delivery

import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.EmbraceEvent
import io.embrace.android.embracesdk.FakeNdkService
import io.embrace.android.embracesdk.fakeBackgroundActivityMessage
import io.embrace.android.embracesdk.fakes.FakeApiService
import io.embrace.android.embracesdk.fakes.FakeDeliveryCacheManager
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.fakes.fakeSessionMessage
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.session.SessionSnapshotType.JVM_CRASH
import io.embrace.android.embracesdk.session.SessionSnapshotType.NORMAL_END
import io.embrace.android.embracesdk.session.SessionSnapshotType.PERIODIC_CACHE
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutorService

internal class EmbraceDeliveryServiceTest {

    private val session = fakeSession()
    private val sessionMessage = fakeSessionMessage()
    private val anotherMessage =
        fakeSessionMessage().copy(session = session.copy(sessionId = "session2"))

    private lateinit var executor: ExecutorService
    private lateinit var deliveryCacheManager: FakeDeliveryCacheManager
    private lateinit var apiService: FakeApiService
    private lateinit var ndkService: FakeNdkService
    private lateinit var gatingService: FakeGatingService
    private lateinit var logger: InternalEmbraceLogger
    private lateinit var deliveryService: EmbraceDeliveryService

    @Before
    fun setUp() {
        executor = MoreExecutors.newDirectExecutorService()
        deliveryCacheManager = FakeDeliveryCacheManager()
        apiService = FakeApiService()
        ndkService = FakeNdkService()
        gatingService = FakeGatingService()
        logger = InternalEmbraceLogger()
    }

    @After
    fun after() {
        executor.shutdown()
        gatingService.sessionMessagesFiltered.clear()
    }

    private fun initializeDeliveryService() {
        deliveryService = EmbraceDeliveryService(
            deliveryCacheManager,
            apiService,
            gatingService,
            executor,
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
        assertEquals(1, gatingService.sessionMessagesFiltered.size)
    }

    @Test
    fun `cache periodic session successful`() {
        initializeDeliveryService()
        deliveryService.sendSession(sessionMessage, PERIODIC_CACHE)

        val observed = deliveryCacheManager.saveSessionRequests.last()
        assertEquals(Pair(sessionMessage, PERIODIC_CACHE), observed)
        assertEquals(1, gatingService.sessionMessagesFiltered.size)
    }

    @Test
    fun `cache session on crash successful`() {
        initializeDeliveryService()
        deliveryService.sendSession(sessionMessage, JVM_CRASH)

        val observed = deliveryCacheManager.saveSessionRequests.last()
        assertEquals(Pair(sessionMessage, JVM_CRASH), observed)
        assertEquals(1, gatingService.sessionMessagesFiltered.size)
    }

    @Test
    fun `if no previous cached session then send previous cached sessions should not send anything`() {
        initializeDeliveryService()

        deliveryService.sendCachedSessions(false, ndkService, null)
        assertTrue(apiService.sessionRequests.isEmpty())
    }

    @Test
    fun `send previously cached sessions successfully`() {
        initializeDeliveryService()
        deliveryCacheManager.addCachedSessions(sessionMessage, anotherMessage)

        deliveryService.sendCachedSessions(false, ndkService, null)
        assertEquals(listOf(sessionMessage, anotherMessage), apiService.sessionRequests)
    }

    @Test
    fun `ignore current session when sending previously cached sessions`() {
        initializeDeliveryService()
        deliveryCacheManager.addCachedSessions(sessionMessage, anotherMessage)
        deliveryService.sendCachedSessions(false, ndkService, anotherMessage.session.sessionId)
        assertEquals(listOf(sessionMessage), apiService.sessionRequests)
    }

    @Test
    fun `if an exception is thrown while sending cached session then sendCachedSession should not crash`() {
        initializeDeliveryService()
        deliveryCacheManager.addCachedSessions(sessionMessage)
        apiService.throwExceptionSendSession = true
        deliveryService.sendCachedSessions(false, ndkService, null)
        assertTrue(apiService.sessionRequests.isEmpty())
    }

    @Test
    fun `send session end with crash`() {
        initializeDeliveryService()
        deliveryService.sendSession(sessionMessage, JVM_CRASH)
        assertEquals(sessionMessage, apiService.sessionRequests.last())
        assertEquals(1, gatingService.sessionMessagesFiltered.size)
    }

    @Test
    fun `check for native crash info if ndk feature is enabled`() {
        initializeDeliveryService()
        deliveryService.sendCachedSessions(true, ndkService, "")
        assertEquals(1, ndkService.checkForNativeCrashCount)
    }

    @Test
    fun testSaveBackgroundActivity() {
        initializeDeliveryService()
        val obj = fakeBackgroundActivityMessage()
        deliveryService.saveBackgroundActivity(obj)
        assertEquals(obj, deliveryCacheManager.saveBgActivityRequests.last())
    }

    @Test
    fun testSendEventAsync() {
        initializeDeliveryService()
        val obj = EventMessage(Event(eventId = "abc", type = EmbraceEvent.Type.END))
        deliveryService.sendMoment(obj)
        assertEquals(obj, apiService.eventRequests.single())
    }

    @Test
    fun testSaveCrash() {
        initializeDeliveryService()
        val obj = EventMessage(Event(eventId = "abc", type = EmbraceEvent.Type.CRASH))
        deliveryService.sendCrash(obj, true)
        assertEquals(obj, deliveryCacheManager.saveCrashRequests.single())
        assertEquals(obj, apiService.crashRequests.single())
    }

    @Test
    fun testSendBackgroundActivity() {
        initializeDeliveryService()
        val obj = fakeBackgroundActivityMessage()
        deliveryService.sendBackgroundActivity(obj)

        // cache the object first in case process terminates
        assertEquals(obj, deliveryCacheManager.saveBgActivityRequests.last())
        assertEquals(1, apiService.sessionRequests.size)
    }

    @Test
    fun testSendBackgroundActivities() {
        initializeDeliveryService()
        val obj = fakeBackgroundActivityMessage()
        deliveryService.saveBackgroundActivity(obj)
        deliveryService.sendBackgroundActivities()
        assertEquals(1, apiService.sessionRequests.size)
    }
}
