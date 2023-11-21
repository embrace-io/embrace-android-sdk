package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.EmbraceEvent
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.ApiRequestMapper
import io.embrace.android.embracesdk.comms.api.EmbraceApiService.Companion.Endpoint
import io.embrace.android.embracesdk.comms.api.EmbraceApiUrlBuilder
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal class EmbraceDeliveryRetryManagerTest {

    companion object {
        private val connectedNetworkStatuses =
            NetworkStatus.values().filter { it != NetworkStatus.NOT_REACHABLE }

        private lateinit var networkConnectivityService: NetworkConnectivityService
        private lateinit var blockingScheduledExecutorService: BlockingScheduledExecutorService
        private lateinit var mockCacheManager: DeliveryCacheManager
        private lateinit var testScheduledExecutor: ScheduledExecutorService
        private lateinit var failedApiCalls: FailedApiCallsPerEndpoint
        private lateinit var deliveryRetryManager: EmbraceDeliveryRetryManager
        private lateinit var mockRetryMethod: (request: ApiRequest, payload: ByteArray) -> Unit

        @BeforeClass
        @JvmStatic
        fun setupBeforeAll() {
            networkConnectivityService = mockk(relaxUnitFun = true)
        }

        /**
         * Setup after all tests get executed. Un-mock all here.
         */
        @AfterClass
        @JvmStatic
        fun tearDownAfterAll() {
            unmockkAll()
        }
    }

    @Before
    fun setUp() {
        blockingScheduledExecutorService = BlockingScheduledExecutorService()
        testScheduledExecutor = blockingScheduledExecutorService
        failedApiCalls = FailedApiCallsPerEndpoint()
        mockRetryMethod = mockk(relaxUnitFun = true)
        clearApiPipeline()
        mockCacheManager = mockk(relaxUnitFun = true)
        every { mockCacheManager.loadPayload("cached_payload_1") } returns "{payload 1}".toByteArray()
        every { mockCacheManager.loadFailedApiCalls() } returns failedApiCalls
        every { mockCacheManager.savePayload(any()) } returns "fake_cache"
    }

    @After
    fun tearDown() {
        clearMocks(mockCacheManager)
    }

    @Test
    fun `scheduled retry job active at init time`() {
        connectedNetworkStatuses.forEach { status ->
            initDeliveryRetryManager(status = status, runRetryJobAfterScheduling = true)
            retryTaskActive(status)
            clearApiPipeline()
        }
    }

    @Test
    fun `retryTask is not active and doesn't run if there are no failed API requests`() {
        connectedNetworkStatuses.forEach { status ->
            initDeliveryRetryManager(
                status = status, loadFailedRequest = false, runRetryJobAfterScheduling = true
            )
            retryTaskNotActive(status)
            blockingScheduledExecutorService.runCurrentlyBlocked()
            checkNoApiRequestSent()
            retryTaskNotActive(status)
            clearApiPipeline()
        }
    }

    @Test
    fun `retryTask is active and runs after init if network is connected`() {
        connectedNetworkStatuses.forEach { status ->
            initDeliveryRetryManager(status = status, runRetryJobAfterScheduling = true)
            retryTaskActive(status)
            blockingScheduledExecutorService.runCurrentlyBlocked()
            checkRequestSendAttempt()
            retryTaskNotActive(status)
            clearApiPipeline()
        }
    }

    @Test
    fun `retryTask will be scheduled again if retry fails`() {
        connectedNetworkStatuses.forEach { status ->
            every { mockRetryMethod(any(), any()) } throws Exception()
            initDeliveryRetryManager(status = status, runRetryJobAfterScheduling = true)
            retryTaskActive(status)
            blockingScheduledExecutorService.runCurrentlyBlocked()
            checkRequestSendAttempt()
            retryTaskNotActive(status)
            // Previous failed attempt will queue another retry. Let it run so a new retry task is active
            blockingScheduledExecutorService.runCurrentlyBlocked()
            retryTaskActive(status)

            // First failure will result in another retry in 120 seconds
            // Go most of the way to check it didn't run
            blockingScheduledExecutorService.moveForwardAndRunBlocked(
                TimeUnit.SECONDS.toMillis(119L)
            )
            retryTaskActive(status)
            checkRequestSendAttempt()

            // Go the full 120 seconds and check that the retry runs and fails
            blockingScheduledExecutorService.moveForwardAndRunBlocked(
                TimeUnit.SECONDS.toMillis(1L)
            )
            checkRequestSendAttempt(count = 2)

            // Previous failed attempt will queue another retry. Let it run
            blockingScheduledExecutorService.runCurrentlyBlocked()

            // Let the next retry succeed
            every { mockRetryMethod(any(), any()) } returns Unit

            // Second failure will result in another retry in double the last time, 240 seconds
            // Go most of the way to check it didn't run, then go all the way to check that it did.
            blockingScheduledExecutorService.moveForwardAndRunBlocked(
                TimeUnit.SECONDS.toMillis(239L)
            )
            retryTaskActive(status)
            checkRequestSendAttempt(count = 2)
            blockingScheduledExecutorService.moveForwardAndRunBlocked(
                TimeUnit.SECONDS.toMillis(1L)
            )
            retryTaskNotActive(status)
            checkRequestSendAttempt(count = 3)
            clearApiPipeline()
        }
    }

    @Test
    fun `retryTask is not active and doesn't run after init if network not reachable`() {
        initDeliveryRetryManager(
            status = NetworkStatus.NOT_REACHABLE, runRetryJobAfterScheduling = true
        )
        blockingScheduledExecutorService.runCurrentlyBlocked()
        retryTaskNotActive(NetworkStatus.NOT_REACHABLE)
        checkNoApiRequestSent()
    }

    @Test
    fun `retryTask isn't active and won't run if there are no failed requests after getting a connection before retry job is scheduled`() {
        connectedNetworkStatuses.forEach { status ->
            initDeliveryRetryManager(
                status = NetworkStatus.NOT_REACHABLE,
                loadFailedRequest = false,
                runRetryJobAfterScheduling = true
            )
            blockingScheduledExecutorService.runCurrentlyBlocked()
            deliveryRetryManager.onNetworkConnectivityStatusChanged(status)
            retryTaskNotActive(status)
            blockingScheduledExecutorService.runCurrentlyBlocked()
            checkNoApiRequestSent()
            retryTaskNotActive(status)
            clearApiPipeline()
        }
    }

    @Test
    fun `retryTask is active and runs after connection changes from not reachable to connected after retry job runs`() {
        connectedNetworkStatuses.forEach { status ->
            initDeliveryRetryManager(
                status = NetworkStatus.NOT_REACHABLE, runRetryJobAfterScheduling = true
            )
            blockingScheduledExecutorService.runCurrentlyBlocked()
            deliveryRetryManager.onNetworkConnectivityStatusChanged(status)
            retryTaskActive(status)
            blockingScheduledExecutorService.runCurrentlyBlocked()
            checkRequestSendAttempt()
            retryTaskNotActive(status)
            clearApiPipeline()
        }
    }

    @Test
    fun `retryTask isn't active and doesn't run if there are no failed request after getting a connection before retry job is scheduled`() {
        connectedNetworkStatuses.forEach { status ->
            initDeliveryRetryManager(
                status = NetworkStatus.NOT_REACHABLE, loadFailedRequest = false
            )
            deliveryRetryManager.onNetworkConnectivityStatusChanged(status)
            retryTaskNotActive(status)
            blockingScheduledExecutorService.runCurrentlyBlocked()
            checkNoApiRequestSent()
            retryTaskNotActive(status)
            clearApiPipeline()
        }
    }

    @Test
    fun `retryTask is active and runs after connection changes from not reachable to connected before retry job is scheduled`() {
        connectedNetworkStatuses.forEach { status ->
            initDeliveryRetryManager(status = NetworkStatus.NOT_REACHABLE)
            deliveryRetryManager.onNetworkConnectivityStatusChanged(status)
            retryTaskActive(status)
            blockingScheduledExecutorService.runCurrentlyBlocked()
            checkRequestSendAttempt()
            retryTaskNotActive(status)
            clearApiPipeline()
        }
    }

    @Test
    fun `retryTask is not active and doesn't run after connection changes from connected to not reachable before retry job is scheduled`() {
        connectedNetworkStatuses.forEach { status ->
            initDeliveryRetryManager(status = status)
            deliveryRetryManager.onNetworkConnectivityStatusChanged(NetworkStatus.NOT_REACHABLE)
            retryTaskNotActive(status)
            blockingScheduledExecutorService.runCurrentlyBlocked()
            checkNoApiRequestSent()
        }
    }

    @Test
    fun `queue size should be bounded`() {
        initDeliveryRetryManager(status = NetworkStatus.WIFI, loadFailedRequest = false)
        every { mockRetryMethod(any(), any()) } throws Exception()

        assertTrue(failedApiCalls.hasNoFailedApiCalls())

        val mockApiRequestSessions = mockk<ApiRequest>(relaxed = true) {
            every { url.url.path } returns "https://mytesturl.com/sessions"
        }
        val mockApiRequestEvents = mockk<ApiRequest>(relaxed = true) {
            every { url.url.path } returns "https://mytesturl.com/events"
        }
        val mockApiRequestLogging = mockk<ApiRequest>(relaxed = true) {
            every { url.url.path } returns "https://mytesturl.com/logging"
        }
        val mockApiRequestBlobs = mockk<ApiRequest>(relaxed = true) {
            every { url.url.path } returns "https://mytesturl.com/blobs"
        }
        val mockApiRequestNetwork = mockk<ApiRequest>(relaxed = true) {
            every { url.url.path } returns "https://mytesturl.com/network"
        }

        repeat(201) {
            deliveryRetryManager.scheduleForRetry(mockApiRequestSessions, "{ dummy_payload }".toByteArray())
            deliveryRetryManager.scheduleForRetry(mockApiRequestEvents, "{ dummy_payload }".toByteArray())
            deliveryRetryManager.scheduleForRetry(mockApiRequestBlobs, "{ dummy_payload }".toByteArray())
            deliveryRetryManager.scheduleForRetry(mockApiRequestLogging, "{ dummy_payload }".toByteArray())
            deliveryRetryManager.scheduleForRetry(mockApiRequestNetwork, "{ dummy_payload }".toByteArray())
            blockingScheduledExecutorService.runCurrentlyBlocked()
        }
        assertEquals(100, failedApiCalls.failedApiCallsCount(Endpoint.SESSIONS))
        assertEquals(100, failedApiCalls.failedApiCallsCount(Endpoint.EVENTS))
        assertEquals(100, failedApiCalls.failedApiCallsCount(Endpoint.LOGGING))
        assertEquals(50, failedApiCalls.failedApiCallsCount(Endpoint.BLOBS))
        assertEquals(50, failedApiCalls.failedApiCallsCount(Endpoint.NETWORK))
    }

    @Test
    fun `queue prioritises keeping sessions when saturated`() {
        initDeliveryRetryManager(status = NetworkStatus.WIFI, loadFailedRequest = false)

        // populate queue with logs up to max of 200 items
        val mapper = ApiRequestMapper(
            EmbraceApiUrlBuilder(
                "https://data.emb-api.com",
                "https://config.emb-api.com",
                "appId",
                lazy { "deviceId" },
                lazy { "appVersionName" }
            ),
            lazy { "deviceId" }, "appId"
        )
        repeat(105) { k ->
            val request = mapper.logRequest(
                EventMessage(
                    Event(
                        type = EmbraceEvent.Type.INFO_LOG,
                        eventId = "eventId",
                        messageId = "message_id_$k"
                    )
                )
            )
            deliveryRetryManager.scheduleForRetry(request, ByteArray(0))
        }

        // verify logs were added to the queue, and that most recently added requests are dropped
        assertEquals(100, failedApiCalls[Endpoint.LOGGING]?.size)
        assertEquals("il:message_id_0", failedApiCalls[Endpoint.LOGGING]?.first()?.apiRequest?.logId)
        assertEquals("il:message_id_99", failedApiCalls[Endpoint.LOGGING]?.last()?.apiRequest?.logId)

        // now add some sessions for retry
        val sessionRequest = mapper.sessionRequest().copy(logId = "is:session_id_0")
        deliveryRetryManager.scheduleForRetry(sessionRequest, ByteArray(0))
        assertEquals(1, failedApiCalls[Endpoint.SESSIONS]?.size)
        assertEquals("is:session_id_0", failedApiCalls[Endpoint.SESSIONS]?.first()?.apiRequest?.logId)
        val request = failedApiCalls[Endpoint.SESSIONS]?.last()?.apiRequest
        assertTrue(request?.url.toString().endsWith("/sessions"))
    }

    private fun clearApiPipeline() {
        clearMocks(mockRetryMethod, answers = false)
        failedApiCalls.clear()
        blockingScheduledExecutorService = BlockingScheduledExecutorService()
        testScheduledExecutor = blockingScheduledExecutorService
    }

    private fun initDeliveryRetryManager(
        status: NetworkStatus,
        loadFailedRequest: Boolean = true,
        runRetryJobAfterScheduling: Boolean = false,
    ) {
        every { networkConnectivityService.getCurrentNetworkStatus() } returns status

        deliveryRetryManager = EmbraceDeliveryRetryManager(
            scheduledExecutorService = testScheduledExecutor,
            networkConnectivityService = networkConnectivityService,
            cacheManager = mockCacheManager,
            clock = FakeClock()
        )

        deliveryRetryManager.setRetryMethod(mockRetryMethod)

        failedApiCalls.clear()

        if (loadFailedRequest) {
            failedApiCalls.add(Endpoint.SESSIONS, DeliveryFailedApiCall(mockk(), "cached_payload_1"))
        }

        if (runRetryJobAfterScheduling) {
            blockingScheduledExecutorService.runCurrentlyBlocked()
        }
    }

    private fun retryTaskActive(status: NetworkStatus) {
        assertTrue("Failed for network status = $status", deliveryRetryManager.isRetryTaskActive())
    }

    private fun retryTaskNotActive(status: NetworkStatus) {
        assertFalse(
            "Failed for network status = $status", deliveryRetryManager.isRetryTaskActive()
        )
    }

    private fun checkRequestSendAttempt(count: Int = 1) {
        verify(exactly = count) { mockRetryMethod(any(), "{payload 1}".toByteArray()) }
    }

    private fun checkNoApiRequestSent() {
        verify(exactly = 0) { mockRetryMethod(any(), any()) }
    }
}
