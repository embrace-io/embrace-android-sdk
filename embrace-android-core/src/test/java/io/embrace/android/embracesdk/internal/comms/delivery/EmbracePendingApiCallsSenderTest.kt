package io.embrace.android.embracesdk.internal.comms.delivery

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.comms.api.ApiRequest
import io.embrace.android.embracesdk.internal.comms.api.ApiRequestMapper
import io.embrace.android.embracesdk.internal.comms.api.ApiRequestUrl
import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.comms.api.EmbraceApiUrlBuilder
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.payload.Event
import io.embrace.android.embracesdk.internal.payload.EventMessage
import io.embrace.android.embracesdk.internal.payload.EventType
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
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
import org.junit.Test
import java.util.concurrent.TimeUnit

internal class EmbracePendingApiCallsSenderTest {

    companion object {
        private val connectedNetworkStatuses =
            NetworkStatus.values().filter { it.isReachable }

        private lateinit var blockingScheduledExecutorService: BlockingScheduledExecutorService
        private lateinit var mockCacheManager: DeliveryCacheManager
        private lateinit var worker: BackgroundWorker
        private lateinit var pendingApiCalls: PendingApiCalls
        private lateinit var queue: PendingApiCallQueue
        private lateinit var pendingApiCallsSender: EmbracePendingApiCallsSender
        private lateinit var mockRetryMethod: (request: ApiRequest, action: SerializationAction) -> ApiResponse

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
        worker = BackgroundWorker(
            blockingScheduledExecutorService
        )
        pendingApiCalls = PendingApiCalls()
        queue = PendingApiCallQueue(pendingApiCalls)
        mockRetryMethod = mockk(relaxUnitFun = true)
        clearApiPipeline()
        mockCacheManager = mockk(relaxUnitFun = true)
        every { mockCacheManager.loadPayloadAsAction("cached_payload_1") } returns { "{payload 1}".toByteArray() }
        every { mockCacheManager.savePayload(any()) } returns "fake_cache"
        every { mockCacheManager.loadPendingApiCallQueue() } returns PendingApiCallQueue(
            pendingApiCalls
        )
    }

    @After
    fun tearDown() {
        clearMocks(mockCacheManager)
    }

    @Test
    fun `scheduled retry job active at init time`() {
        connectedNetworkStatuses.forEach { status ->
            initPendingApiCallsSender(runRetryJobAfterScheduling = true)
            pendingApiCallsSender.onNetworkConnectivityStatusChanged(status)
            retryTaskActive(status)
            clearApiPipeline()
        }
    }

    @Test
    fun `retryTask is not active and doesn't run if there are no failed API requests`() {
        connectedNetworkStatuses.forEach { status ->
            initPendingApiCallsSender(
                loadFailedRequest = false,
                runRetryJobAfterScheduling = true
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
            initPendingApiCallsSender(runRetryJobAfterScheduling = true)
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
            every { mockRetryMethod(any(), any()) } returns ApiResponse.Incomplete(Throwable())
            initPendingApiCallsSender(runRetryJobAfterScheduling = true)
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
            every { mockRetryMethod(any(), any()) } returns ApiResponse.Success("", null)

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
        initPendingApiCallsSender(
            runRetryJobAfterScheduling = true
        )
        pendingApiCallsSender.onNetworkConnectivityStatusChanged(NetworkStatus.NOT_REACHABLE)
        blockingScheduledExecutorService.runCurrentlyBlocked()
        retryTaskNotActive(NetworkStatus.NOT_REACHABLE)
        checkNoApiRequestSent()
    }

    @Test
    fun `retryTask isn't active and won't run if there are no failed requests after getting a connection before retry job is scheduled`() {
        connectedNetworkStatuses.forEach { status ->
            initPendingApiCallsSender(
                loadFailedRequest = false,
                runRetryJobAfterScheduling = true
            )
            blockingScheduledExecutorService.runCurrentlyBlocked()
            pendingApiCallsSender.onNetworkConnectivityStatusChanged(status)
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
            initPendingApiCallsSender(
                runRetryJobAfterScheduling = true
            )
            pendingApiCallsSender.onNetworkConnectivityStatusChanged(NetworkStatus.NOT_REACHABLE)
            blockingScheduledExecutorService.runCurrentlyBlocked()
            pendingApiCallsSender.onNetworkConnectivityStatusChanged(status)
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
            initPendingApiCallsSender(
                loadFailedRequest = false
            )
            pendingApiCallsSender.onNetworkConnectivityStatusChanged(status)
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
            initPendingApiCallsSender()
            pendingApiCallsSender.onNetworkConnectivityStatusChanged(status)
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
            initPendingApiCallsSender()
            pendingApiCallsSender.onNetworkConnectivityStatusChanged(NetworkStatus.NOT_REACHABLE)
            retryTaskNotActive(status)
            blockingScheduledExecutorService.runCurrentlyBlocked()
            checkNoApiRequestSent()
        }
    }

    @Test
    fun `queue prioritises returning sessions over other api calls`() {
        initPendingApiCallsSender(loadFailedRequest = false)

        // populate queue with logs up to max of 100 items
        val mapper = ApiRequestMapper(
            EmbraceApiUrlBuilder(
                "https://data.emb-api.com",
                "https://config.emb-api.com",
                "appId",
                lazy { "deviceId" },
                lazy { "appVersionName" }
            ),
            lazy { "deviceId" },
            "appId"
        )
        repeat(105) { k ->
            val request = mapper.eventMessageRequest(
                EventMessage(
                    Event(
                        type = EventType.INFO_LOG,
                        eventId = "message_id_$k"
                    )
                )
            )
            pendingApiCallsSender.savePendingApiCall(request, {}, false)
            pendingApiCallsSender.scheduleRetry(
                ApiResponse.Incomplete(
                    Throwable()
                )
            )
        }

        // verify logs were added to the queue, and oldest added requests are dropped
        assertEquals("il:message_id_5", queue.pollNextPendingApiCall()?.apiRequest?.eventId)
        assertEquals("il:message_id_6", queue.pollNextPendingApiCall()?.apiRequest?.eventId)

        // now add some sessions for retry and verify they are returned first
        val sessionRequest = mapper.sessionRequest().copy(logId = "is:session_id_0")
        pendingApiCallsSender.savePendingApiCall(sessionRequest, {}, false)
        pendingApiCallsSender.scheduleRetry(ApiResponse.Incomplete(Throwable()))
        assertEquals(sessionRequest, queue.pollNextPendingApiCall()?.apiRequest)
    }

    @Test
    fun `no pending api calls remains if SendMethod is set`() {
        initPendingApiCallsSender(runRetryJobAfterScheduling = false)
        pendingApiCallsSender.onNetworkConnectivityStatusChanged(NetworkStatus.WIFI)
        retryTaskActive(NetworkStatus.WIFI)
        assertTrue(queue.hasPendingApiCallsToSend())
        blockingScheduledExecutorService.runCurrentlyBlocked()
        checkRequestSendAttempt()
        assertFalse(queue.hasPendingApiCallsToSend())
    }

    @Test
    fun `pending api calls remains if SendMethod is not set`() {
        initPendingApiCallsSender(
            runRetryJobAfterScheduling = false,
            sendMethod = null
        )
        pendingApiCallsSender.onNetworkConnectivityStatusChanged(NetworkStatus.WIFI)
        retryTaskActive(NetworkStatus.WIFI)
        assertTrue(queue.hasPendingApiCallsToSend())
        blockingScheduledExecutorService.runCurrentlyBlocked()
        assertTrue(queue.hasPendingApiCallsToSend())
    }

    private fun clearApiPipeline() {
        clearMocks(mockRetryMethod, answers = false)
        pendingApiCalls = PendingApiCalls()
        blockingScheduledExecutorService = BlockingScheduledExecutorService()
        worker = BackgroundWorker(
            blockingScheduledExecutorService
        )
    }

    private fun initPendingApiCallsSender(
        loadFailedRequest: Boolean = true,
        runRetryJobAfterScheduling: Boolean = false,
        sendMethod: SendMethod? = mockRetryMethod
    ) {
        pendingApiCalls = PendingApiCalls()
        every { mockCacheManager.loadPendingApiCallQueue() } returns queue

        pendingApiCallsSender = EmbracePendingApiCallsSender(
            worker = worker,
            cacheManager = mockCacheManager,
            clock = FakeClock(),
            logger = EmbLoggerImpl()
        )

        if (sendMethod != null) {
            pendingApiCallsSender.initializeRetrySchedule(mockRetryMethod)
        }

        if (loadFailedRequest) {
            val request = ApiRequest(
                url = ApiRequestUrl("https://fake.com/${Endpoint.SESSIONS.path}"),
                userAgent = ""
            )
            queue.add(PendingApiCall(request, "cached_payload_1"))
        }

        if (runRetryJobAfterScheduling) {
            blockingScheduledExecutorService.runCurrentlyBlocked()
        }
    }

    private fun retryTaskActive(status: NetworkStatus) {
        assertTrue(
            "Failed for network status = $status",
            pendingApiCallsSender.isDeliveryTaskActive()
        )
    }

    private fun retryTaskNotActive(status: NetworkStatus) {
        assertFalse(
            "Failed for network status = $status",
            pendingApiCallsSender.isDeliveryTaskActive()
        )
    }

    private fun checkRequestSendAttempt(count: Int = 1) {
        verify(exactly = count) {
            mockRetryMethod(any(), any())
        }
    }

    private fun checkNoApiRequestSent() {
        verify(exactly = 0) { mockRetryMethod(any(), any()) }
    }
}
