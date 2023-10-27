package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.comms.delivery.DeliveryCacheManager
import io.embrace.android.embracesdk.comms.delivery.DeliveryFailedApiCall
import io.embrace.android.embracesdk.comms.delivery.DeliveryFailedApiCalls
import io.embrace.android.embracesdk.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.FakeAndroidMetadataService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal class EmbraceApiServiceTest {

    companion object {
        private val metadataService = FakeAndroidMetadataService()
        private val userService = FakeUserService()
        private val connectedNetworkStatuses =
            NetworkStatus.values().filter { it != NetworkStatus.NOT_REACHABLE }

        private lateinit var mockApiUrlBuilder: ApiUrlBuilder
        private lateinit var mockApiClient: ApiClient
        private lateinit var mockCacheManager: DeliveryCacheManager
        private lateinit var blockingScheduledExecutorService: BlockingScheduledExecutorService
        private lateinit var testScheduledExecutor: ScheduledExecutorService
        private lateinit var networkConnectivityService: NetworkConnectivityService
        private lateinit var failedApiCalls: DeliveryFailedApiCalls
        private lateinit var cachedConfig: CachedConfig
        private lateinit var apiService: EmbraceApiService

        @BeforeClass
        @JvmStatic
        fun setupBeforeAll() {
            mockApiUrlBuilder = mockk(relaxUnitFun = true) {
                every { getEmbraceUrlWithSuffix(any()) } returns "http://fake.url"
                every { getConfigUrl() } returns "https://config.url"
            }
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
        mockApiClient = mockk {
            every { post(any(), any()) } returns ""
        }
        cachedConfig = CachedConfig(
            config = null,
            eTag = null
        )
        failedApiCalls = DeliveryFailedApiCalls()
        clearApiPipeline()
        mockCacheManager = mockk(relaxUnitFun = true)
        every { mockCacheManager.loadPayload("cached_payload_1") } returns "{payload 1}".toByteArray()
        every { mockCacheManager.loadFailedApiCalls() } returns failedApiCalls
        every { mockCacheManager.savePayload(any()) } returns "fake_cache"
    }

    @After
    fun tearDown() {
        clearMocks(mockApiClient)
        clearMocks(mockCacheManager)
    }

    @Test
    fun `test getConfig returns correct values in Response`() {
        val json = ResourceReader.readResourceAsText("remote_config_response.json")
        every { mockApiClient.executeGet(any()) } returns ApiResponse(
            statusCode = 200,
            body = json,
            headers = emptyMap()
        )
        initApiService(
            status = NetworkStatus.NOT_REACHABLE,
            runRetryJobAfterScheduling = true
        )
        val remoteConfig = apiService.getConfig()

        // verify a few fields were serialized correctly.
        checkNotNull(remoteConfig)
        assertTrue(checkNotNull(remoteConfig.sessionConfig?.isEnabled))
        assertFalse(checkNotNull(remoteConfig.sessionConfig?.endAsync))
        assertEquals(100, remoteConfig.threshold)
    }

    @Test(expected = IllegalStateException::class)
    fun `test getConfig rethrows an exception thrown by apiClient`() {
        every { mockApiClient.executeGet(any()) } throws IllegalStateException("Test exception message")
        initApiService(
            status = NetworkStatus.NOT_REACHABLE,
            runRetryJobAfterScheduling = true
        )
        // exception will be thrown and caught by this test's annotation
        apiService.getConfig()
    }

    @Test
    fun testGetConfigWithMatchingEtag() {
        val cfg = RemoteConfig()
        cachedConfig = CachedConfig(cfg, "my_etag")
        every { mockApiClient.executeGet(any()) } returns ApiResponse(
            statusCode = 304,
            body = "",
            headers = emptyMap()
        )
        initApiService(
            status = NetworkStatus.NOT_REACHABLE,
            runRetryJobAfterScheduling = true
        )
        val remoteConfig = apiService.getConfig()
        assertSame(cfg, remoteConfig)
    }

    @Test
    fun `scheduled retry job active at init time`() {
        connectedNetworkStatuses.forEach { status ->
            initApiService(status = status, runRetryJobAfterScheduling = true)
            retryTaskActive(status)
            clearApiPipeline()
        }
    }

    @Test
    fun `retryTask is not active and doesn't run if there are no failed API requests`() {
        connectedNetworkStatuses.forEach { status ->
            initApiService(
                status = status,
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
            initApiService(status = status, runRetryJobAfterScheduling = true)
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
            every { mockApiClient.post(any(), any()) } throws Exception()
            initApiService(status = status, runRetryJobAfterScheduling = true)
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
            every { mockApiClient.post(any(), any()) } returns ""

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
        initApiService(
            status = NetworkStatus.NOT_REACHABLE,
            runRetryJobAfterScheduling = true
        )
        blockingScheduledExecutorService.runCurrentlyBlocked()
        retryTaskNotActive(NetworkStatus.NOT_REACHABLE)
        checkNoApiRequestSent()
    }

    @Test
    fun `retryTask isn't active and won't run if there are no failed requests after getting a connection before retry job is scheduled`() {
        connectedNetworkStatuses.forEach { status ->
            initApiService(
                status = NetworkStatus.NOT_REACHABLE,
                loadFailedRequest = false,
                runRetryJobAfterScheduling = true
            )
            blockingScheduledExecutorService.runCurrentlyBlocked()
            apiService.onNetworkConnectivityStatusChanged(status)
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
            initApiService(
                status = NetworkStatus.NOT_REACHABLE,
                runRetryJobAfterScheduling = true
            )
            blockingScheduledExecutorService.runCurrentlyBlocked()
            apiService.onNetworkConnectivityStatusChanged(status)
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
            initApiService(
                status = NetworkStatus.NOT_REACHABLE,
                loadFailedRequest = false
            )
            apiService.onNetworkConnectivityStatusChanged(status)
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
            initApiService(status = NetworkStatus.NOT_REACHABLE)
            apiService.onNetworkConnectivityStatusChanged(status)
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
            initApiService(status = status)
            apiService.onNetworkConnectivityStatusChanged(NetworkStatus.NOT_REACHABLE)
            retryTaskNotActive(status)
            blockingScheduledExecutorService.runCurrentlyBlocked()
            checkNoApiRequestSent()
        }
    }

    @Test
    fun `queue size should be bounded`() {
        initApiService(status = NetworkStatus.WIFI, loadFailedRequest = false)
        every { mockApiClient.post(any(), any()) } throws Exception()

        assertEquals(0, apiService.pendingRetriesCount())

        repeat(201) {
            apiService.sendSession("{ dummy_session }".toByteArray(), null)
            blockingScheduledExecutorService.runCurrentlyBlocked()
        }
        assertEquals(200, apiService.pendingRetriesCount())
    }

    private fun clearApiPipeline() {
        clearMocks(mockApiClient, answers = false)
        failedApiCalls.clear()
        blockingScheduledExecutorService = BlockingScheduledExecutorService()
        testScheduledExecutor =
            blockingScheduledExecutorService
    }

    private fun initApiService(
        status: NetworkStatus,
        loadFailedRequest: Boolean = true,
        runRetryJobAfterScheduling: Boolean = false
    ) {
        every { networkConnectivityService.getCurrentNetworkStatus() } returns status

        apiService = EmbraceApiService(
            apiClient = mockApiClient,
            urlBuilder = mockApiUrlBuilder,
            serializer = EmbraceSerializer(),
            cachedConfigProvider = { _, _ -> cachedConfig },
            logger = mockk(relaxed = true),
            metadataService = metadataService,
            userService = userService,
            scheduledExecutorService = testScheduledExecutor,
            networkConnectivityService = networkConnectivityService,
            cacheManager = mockCacheManager
        )

        failedApiCalls.clear()
        if (loadFailedRequest) {
            failedApiCalls.add(DeliveryFailedApiCall(mockk(), "cached_payload_1"))
        }

        if (runRetryJobAfterScheduling) {
            blockingScheduledExecutorService.runCurrentlyBlocked()
        }
    }

    private fun retryTaskActive(status: NetworkStatus) {
        assertTrue("Failed for network status = $status", apiService.isRetryTaskActive())
    }

    private fun retryTaskNotActive(status: NetworkStatus) {
        assertFalse(
            "Failed for network status = $status",
            apiService.isRetryTaskActive()
        )
    }

    private fun checkRequestSendAttempt(count: Int = 1) {
        verify(exactly = count) { mockApiClient.post(any(), "{payload 1}".toByteArray()) }
    }

    private fun checkNoApiRequestSent() {
        verify(exactly = 0) { mockApiClient.post(any(), any()) }
    }
}
