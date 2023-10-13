package io.embrace.android.embracesdk.comms.delivery

import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.fakeBackgroundActivity
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.SessionMessage
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

internal class EmbraceDeliveryServiceTest {

    private lateinit var deliveryService: EmbraceDeliveryService
    private val executor = MoreExecutors.newDirectExecutorService()

    companion object {
        private lateinit var mockDeliveryCacheManager: DeliveryCacheManager
        private lateinit var mockDeliveryNetworkManager: DeliveryNetworkManager
        private lateinit var logger: InternalEmbraceLogger

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockDeliveryCacheManager = mockk(relaxed = true)
            every { mockDeliveryCacheManager.loadCrash() } returns null
            every { mockDeliveryCacheManager.getAllCachedSessionIds() } returns emptyList()
            mockDeliveryNetworkManager = mockk(relaxed = true)
            logger = InternalEmbraceLogger()
        }
    }

    @After
    fun after() {
        clearAllMocks()
        executor.shutdown()
    }

    private fun initializeDeliveryService() {
        deliveryService = EmbraceDeliveryService(
            mockDeliveryCacheManager,
            mockDeliveryNetworkManager,
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
            mockDeliveryCacheManager.saveSession(mockSessionMessage)
        } returns "cached_session".toByteArray()

        deliveryService.saveSession(mockSessionMessage)

        verify { mockDeliveryCacheManager.saveSession(mockSessionMessage) }
    }

    @Test
    fun `if no previous cached session then send previous cached sessions should not send anything`() {
        initializeDeliveryService()
        every { mockDeliveryCacheManager.getAllCachedSessionIds() } returns emptyList()
        every { mockDeliveryCacheManager.loadCrash() } returns null

        deliveryService.sendCachedSessions(false, mockk(), null)
        executor.awaitTermination(1, TimeUnit.SECONDS)

        verify { mockDeliveryNetworkManager wasNot Called }
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
        verify { mockDeliveryNetworkManager.sendSession("cached_session_1".toByteArray(), any()) }
        verify { mockDeliveryNetworkManager.sendSession("cached_session_2".toByteArray(), any()) }
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
        verify { mockDeliveryNetworkManager.sendSession("cached_session_1".toByteArray(), any()) }
        verify(exactly = 0) {
            mockDeliveryNetworkManager.sendSession(
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
        every { mockDeliveryNetworkManager.sendSession(any(), any()) } throws Exception()
        every { mockDeliveryCacheManager.loadCrash() } returns null

        deliveryService.sendCachedSessions(false, mockk(), null)
        executor.awaitTermination(1, TimeUnit.SECONDS)

        verify { mockDeliveryCacheManager.loadSessionBytes("session1") }
        verify { mockDeliveryNetworkManager.sendSession("cached_session".toByteArray(), any()) }
    }

    @Test
    fun `send session start`() {
        initializeDeliveryService()

        every {
            mockDeliveryCacheManager.saveSession(any())
        } returns "cached_session".toByteArray()

        val mockFuture: Future<Unit> = mockk()
        every { mockDeliveryNetworkManager.sendSession(any(), any()) } returns mockFuture

        deliveryService.sendSession(mockk(), SessionMessageState.START)

        verify(exactly = 1) {
            mockDeliveryNetworkManager.sendSession(
                any(),
                null
            )
        }
        verify { mockFuture wasNot Called }
    }

    @Test
    fun `send session end`() {
        initializeDeliveryService()

        every {
            mockDeliveryCacheManager.saveSession(any())
        } returns "cached_session".toByteArray()

        val mockFuture: Future<Unit> = mockk()
        every { mockDeliveryNetworkManager.sendSession(any(), any()) } returns mockFuture

        deliveryService.sendSession(mockk(), SessionMessageState.END)

        verify(exactly = 1) {
            mockDeliveryNetworkManager.sendSession(
                any(),
                withArg {
                    assertNotNull(it)
                }
            )
        }
        verify { mockFuture wasNot Called }
    }

    @Test
    fun `send session end with crash`() {
        initializeDeliveryService()

        every {
            mockDeliveryCacheManager.saveSession(any())
        } returns "cached_session".toByteArray()

        val mockFuture: Future<Unit> = mockk()
        every { mockDeliveryNetworkManager.sendSession(any(), any()) } returns mockFuture

        deliveryService.sendSession(mockk(), SessionMessageState.END_WITH_CRASH)

        verify(exactly = 1) {
            mockDeliveryNetworkManager.sendSession(
                any(),
                withArg {
                    assertNotNull(it)
                }
            )
        }
        verify(exactly = 1) {
            mockFuture.get(1L, TimeUnit.SECONDS)
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
        val obj = EventMessage(Event())
        deliveryService.sendEventAsync(obj)
        verify(exactly = 1) { mockDeliveryNetworkManager.sendEvent(obj) }
    }

    @Test
    fun testSaveCrash() {
        initializeDeliveryService()
        val obj = EventMessage(Event())
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
        verify(exactly = 1) { mockDeliveryNetworkManager.sendSession(any(), any()) }
    }

    @Test
    fun testSendBackgroundActivities() {
        val bytes = ByteArray(5)
        initializeDeliveryService()
        val obj = fakeBackgroundActivity()
        deliveryService.saveBackgroundActivity(obj)

        every { mockDeliveryCacheManager.loadBackgroundActivity(any()) } returns bytes
        deliveryService.sendBackgroundActivities()
        verify(exactly = 1) { mockDeliveryNetworkManager.sendSession(bytes, any()) }
    }
}
