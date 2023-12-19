package io.embrace.android.embracesdk.comms.delivery

import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.EmbraceEvent
import io.embrace.android.embracesdk.comms.api.ApiService
import io.embrace.android.embracesdk.fakeBackgroundActivity
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.fakes.fakeSessionMessage
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.session.SessionSnapshotType.JVM_CRASH
import io.embrace.android.embracesdk.session.SessionSnapshotType.NORMAL_END
import io.embrace.android.embracesdk.session.SessionSnapshotType.PERIODIC_CACHE
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

internal class EmbraceDeliveryServiceTest {

    private lateinit var deliveryService: EmbraceDeliveryService
    private val executor = MoreExecutors.newDirectExecutorService()
    private val session = fakeSession()
    private val sessionMessage = fakeSessionMessage()

    companion object {
        private lateinit var mockDeliveryCacheManager: EmbraceDeliveryCacheManager
        private lateinit var apiService: ApiService
        private lateinit var gatingService: FakeGatingService
        private lateinit var logger: InternalEmbraceLogger

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockDeliveryCacheManager = mockk(relaxed = true)
            every { mockDeliveryCacheManager.loadCrash() } returns null
            every { mockDeliveryCacheManager.getAllCachedSessionIds() } returns emptyList()
            apiService = mockk(relaxed = true)
            gatingService = FakeGatingService()
            logger = InternalEmbraceLogger()
        }
    }

    @After
    fun after() {
        clearAllMocks()
        executor.shutdown()
        gatingService.sessionMessagesFiltered.clear()
    }

    private fun initializeDeliveryService() {
        deliveryService = EmbraceDeliveryService(
            mockDeliveryCacheManager,
            apiService,
            gatingService,
            executor,
            executor,
            EmbraceSerializer(),
            logger
        )
    }

    @Test
    fun `cache current session successfully`() {
        initializeDeliveryService()
        deliveryService.sendSession(sessionMessage, NORMAL_END)

        verify(exactly = 1) { mockDeliveryCacheManager.saveSession(sessionMessage, NORMAL_END) }
        assertEquals(1, gatingService.sessionMessagesFiltered.size)
    }

    @Test
    fun `cache periodic session successful`() {
        initializeDeliveryService()
        deliveryService.sendSession(sessionMessage, PERIODIC_CACHE)

        verify(exactly = 1) {
            mockDeliveryCacheManager.saveSession(
                sessionMessage,
                PERIODIC_CACHE
            )
        }
        assertEquals(1, gatingService.sessionMessagesFiltered.size)
    }

    @Test
    fun `cache session on crash successful`() {
        initializeDeliveryService()
        deliveryService.sendSession(sessionMessage, JVM_CRASH)

        verify(exactly = 1) { mockDeliveryCacheManager.saveSession(sessionMessage, JVM_CRASH) }
        assertEquals(1, gatingService.sessionMessagesFiltered.size)
    }

    @Test
    fun `if no previous cached session then send previous cached sessions should not send anything`() {
        initializeDeliveryService()
        every { mockDeliveryCacheManager.getAllCachedSessionIds() } returns emptyList()
        every { mockDeliveryCacheManager.loadCrash() } returns null

        deliveryService.sendCachedSessions(false, mockk(), null)
        executor.awaitTermination(1, TimeUnit.SECONDS)

        verify { apiService wasNot Called }
        verify(exactly = 0) { mockDeliveryCacheManager.deleteSession(any()) }
    }

    @Test
    fun `send previously cached sessions successfully`() {
        initializeDeliveryService()
        every { mockDeliveryCacheManager.getAllCachedSessionIds() } returns listOf(
            "session1",
            "session2"
        )
        every { mockDeliveryCacheManager.loadSessionAsAction("session1") } returns { it.write("cached_session_1".toByteArray()) }
        every { mockDeliveryCacheManager.loadSessionAsAction("session2") } returns { it.write("cached_session_2".toByteArray()) }
        every { mockDeliveryCacheManager.loadCrash() } returns null

        deliveryService.sendCachedSessions(false, mockk(), null)
        executor.awaitTermination(1, TimeUnit.SECONDS)

        verify { mockDeliveryCacheManager.loadSessionAsAction("session1") }
        verify { mockDeliveryCacheManager.loadSessionAsAction("session2") }
        verify(exactly = 2) { apiService.sendSession(any(), any()) }
    }

    @Test
    fun `ignore current session when sending previously cached sessions`() {
        initializeDeliveryService()
        every { mockDeliveryCacheManager.getAllCachedSessionIds() } returns listOf(
            "session1",
            "session2"
        )
        every { mockDeliveryCacheManager.loadSessionAsAction("session1") } returns { it.write("cached_session_1".toByteArray()) }
        every { mockDeliveryCacheManager.loadSessionAsAction("session2") } returns { it.write("cached_session_2".toByteArray()) }
        every { mockDeliveryCacheManager.loadCrash() } returns null

        deliveryService.sendCachedSessions(false, mockk(), "session2")
        executor.awaitTermination(1, TimeUnit.SECONDS)

        verify { mockDeliveryCacheManager.loadSessionAsAction("session1") }
        verify(exactly = 0) { mockDeliveryCacheManager.loadSessionAsAction("session2") }
        verify { apiService.sendSession(any(), any()) }
        verify(exactly = 0) {
            apiService.sendSession(
                { it.write("cached_session_2".toByteArray()) },
                any()
            )
        }
    }

    @Test
    fun `if an exception is thrown while sending cached session then sendCachedSession should not crash`() {
        initializeDeliveryService()
        every { mockDeliveryCacheManager.getAllCachedSessionIds() } returns listOf("session1")
        every { mockDeliveryCacheManager.loadSessionAsAction("session1") } returns { it.write("cached_session".toByteArray()) }
        every { apiService.sendSession(any(), any()) } throws Exception()
        every { mockDeliveryCacheManager.loadCrash() } returns null

        deliveryService.sendCachedSessions(false, mockk(), null)
        executor.awaitTermination(1, TimeUnit.SECONDS)

        verify { mockDeliveryCacheManager.loadSessionAsAction("session1") }
        verify { apiService.sendSession(any(), any()) }
    }

    @Test
    fun `send session end`() {
        initializeDeliveryService()

        val mockFuture: Future<Unit> = mockk()
        every { apiService.sendSession(any(), any()) } returns mockFuture

        deliveryService.sendSession(sessionMessage, NORMAL_END)

        verify(exactly = 1) {
            apiService.sendSession(
                any(),
                withArg {
                    assertNotNull(it)
                }
            )
        }
        verify { mockFuture wasNot Called }
        assertEquals(1, gatingService.sessionMessagesFiltered.size)
    }

    @Test
    fun `send session end with crash`() {
        initializeDeliveryService()

        val mockFuture: Future<Unit> = mockk()
        every { apiService.sendSession(any(), any()) } returns mockFuture

        deliveryService.sendSession(sessionMessage, JVM_CRASH)

        verify(exactly = 1) {
            apiService.sendSession(
                any(),
                withArg {
                    assertNotNull(it)
                }
            )
        }
        verify(exactly = 1) {
            mockFuture.get(1L, TimeUnit.SECONDS)
        }
        assertEquals(1, gatingService.sessionMessagesFiltered.size)
    }

    @Test
    fun `send session invalid`() {
        initializeDeliveryService()

        val mockFuture: Future<Unit> = mockk()
        every { apiService.sendSession(any(), any()) } returns mockFuture

        deliveryService.sendSession(sessionMessage, PERIODIC_CACHE)

        verify(exactly = 0) {
            apiService.sendSession(
                any(),
                withArg {
                    assertNotNull(it)
                }
            )
        }
    }

    @Test
    fun `check for native crash info if ndk feature is enabled`() {
        val mockNdkService: NdkService = mockk()
        initializeDeliveryService()
        deliveryService.sendCachedSessions(true, mockNdkService, "")
        verify(exactly = 1) { mockNdkService.checkForNativeCrash() }
    }

    @Test
    fun testSaveBackgroundActivity() {
        initializeDeliveryService()
        val obj = fakeBackgroundActivity()
        deliveryService.saveBackgroundActivity(obj)
        verify(exactly = 1) { mockDeliveryCacheManager.saveBackgroundActivity(obj) }
    }

    @Test
    fun testSendEventAsync() {
        initializeDeliveryService()
        val obj = EventMessage(Event(eventId = "abc", type = EmbraceEvent.Type.END))
        deliveryService.sendMoment(obj)
        verify(exactly = 1) { apiService.sendEvent(obj) }
    }

    @Test
    fun testSaveCrash() {
        initializeDeliveryService()
        val obj = EventMessage(Event(eventId = "abc", type = EmbraceEvent.Type.CRASH))
        deliveryService.sendCrash(obj, true)
        verify(exactly = 1) { mockDeliveryCacheManager.saveCrash(obj) }
        verify(exactly = 1) { apiService.sendCrash(obj) }
    }

    @Test
    fun testSendBackgroundActivity() {
        initializeDeliveryService()
        val obj = fakeBackgroundActivity()
        deliveryService.sendBackgroundActivity(obj)

        // cache the object first in case process terminates
        verify(exactly = 1) { mockDeliveryCacheManager.saveBackgroundActivity(obj) }
        verify(exactly = 1) { apiService.sendSession(any(), any()) }
    }

    @Test
    fun testSendBackgroundActivities() {
        val bytes = ByteArray(5)
        initializeDeliveryService()
        val obj = fakeBackgroundActivity()
        deliveryService.saveBackgroundActivity(obj)

        every { mockDeliveryCacheManager.loadBackgroundActivity(any()) } returns bytes
        deliveryService.sendBackgroundActivities()
        verify(exactly = 1) { apiService.sendSession(any(), any()) }
    }
}
