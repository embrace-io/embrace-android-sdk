package io.embrace.android.embracesdk.comms.delivery

import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.EmbraceEvent
import io.embrace.android.embracesdk.comms.api.ApiService
import io.embrace.android.embracesdk.fakeBackgroundActivity
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.SessionMessage
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
            logger
        )
    }

    @Test
    fun `cache current session successfully`() {
        initializeDeliveryService()
        val mockSessionMessage: SessionMessage = mockk()
        every {
            mockDeliveryCacheManager.saveSession(mockSessionMessage, NORMAL_END)
        } returns "cached_session".toByteArray()

        deliveryService.saveSession(mockSessionMessage, NORMAL_END)

        verify(exactly = 1) { mockDeliveryCacheManager.saveSession(mockSessionMessage, NORMAL_END) }
        assertEquals(1, gatingService.sessionMessagesFiltered.size)
    }

    @Test
    fun `cache periodic session successful`() {
        initializeDeliveryService()
        val mockSessionMessage: SessionMessage = mockk()

        deliveryService.saveSession(mockSessionMessage, PERIODIC_CACHE)

        verify(exactly = 1) { mockDeliveryCacheManager.saveSession(mockSessionMessage, PERIODIC_CACHE) }
        assertEquals(1, gatingService.sessionMessagesFiltered.size)
    }

    @Test
    fun `cache session on crash successful`() {
        initializeDeliveryService()
        val mockSessionMessage: SessionMessage = mockk()

        deliveryService.saveSession(mockSessionMessage, JVM_CRASH)

        verify(exactly = 1) { mockDeliveryCacheManager.saveSession(mockSessionMessage, JVM_CRASH) }
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
        every { mockDeliveryCacheManager.loadSessionBytes("session1") } returns "cached_session_1".toByteArray()
        every { mockDeliveryCacheManager.loadSessionBytes("session2") } returns "cached_session_2".toByteArray()
        every { mockDeliveryCacheManager.loadCrash() } returns null

        deliveryService.sendCachedSessions(false, mockk(), null)
        executor.awaitTermination(1, TimeUnit.SECONDS)

        verify { mockDeliveryCacheManager.loadSessionBytes("session1") }
        verify { mockDeliveryCacheManager.loadSessionBytes("session2") }
        verify { apiService.sendSession("cached_session_1".toByteArray(), any()) }
        verify { apiService.sendSession("cached_session_2".toByteArray(), any()) }
    }

    @Test
    fun `ignore current session when sending previously cached sessions`() {
        initializeDeliveryService()
        every { mockDeliveryCacheManager.getAllCachedSessionIds() } returns listOf(
            "session1",
            "session2"
        )
        every { mockDeliveryCacheManager.loadSessionBytes("session1") } returns "cached_session_1".toByteArray()
        every { mockDeliveryCacheManager.loadSessionBytes("session2") } returns "cached_session_2".toByteArray()
        every { mockDeliveryCacheManager.loadCrash() } returns null

        deliveryService.sendCachedSessions(false, mockk(), "session2")
        executor.awaitTermination(1, TimeUnit.SECONDS)

        verify { mockDeliveryCacheManager.loadSessionBytes("session1") }
        verify(exactly = 0) { mockDeliveryCacheManager.loadSessionBytes("session2") }
        verify { apiService.sendSession("cached_session_1".toByteArray(), any()) }
        verify(exactly = 0) {
            apiService.sendSession(
                "cached_session_2".toByteArray(),
                any()
            )
        }
    }

    @Test
    fun `if an exception is thrown while sending cached session then sendCachedSession should not crash`() {
        initializeDeliveryService()
        every { mockDeliveryCacheManager.getAllCachedSessionIds() } returns listOf("session1")
        every { mockDeliveryCacheManager.loadSessionBytes("session1") } returns "cached_session".toByteArray()
        every { apiService.sendSession(any(), any()) } throws Exception()
        every { mockDeliveryCacheManager.loadCrash() } returns null

        deliveryService.sendCachedSessions(false, mockk(), null)
        executor.awaitTermination(1, TimeUnit.SECONDS)

        verify { mockDeliveryCacheManager.loadSessionBytes("session1") }
        verify { apiService.sendSession("cached_session".toByteArray(), any()) }
    }

    @Test
    fun `send session end`() {
        initializeDeliveryService()

        every {
            mockDeliveryCacheManager.saveSession(any(), NORMAL_END)
        } returns "cached_session".toByteArray()

        val mockFuture: Future<Unit> = mockk()
        every { apiService.sendSession(any(), any()) } returns mockFuture

        deliveryService.sendSession(mockk(), SessionMessageState.END)

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

        every {
            mockDeliveryCacheManager.saveSession(any(), JVM_CRASH)
        } returns "cached_session".toByteArray()

        val mockFuture: Future<Unit> = mockk()
        every { apiService.sendSession(any(), any()) } returns mockFuture

        deliveryService.sendSession(mockk(), SessionMessageState.END_WITH_CRASH)

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
        deliveryService.sendEventAsync(obj)
        verify(exactly = 1) { apiService.sendEvent(obj) }
    }

    @Test
    fun testSaveCrash() {
        initializeDeliveryService()
        val obj = EventMessage(Event(eventId = "abc", type = EmbraceEvent.Type.CRASH))
        deliveryService.saveCrash(obj)
        verify(exactly = 1) { mockDeliveryCacheManager.saveCrash(obj) }
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
        verify(exactly = 1) { apiService.sendSession(bytes, any()) }
    }
}
