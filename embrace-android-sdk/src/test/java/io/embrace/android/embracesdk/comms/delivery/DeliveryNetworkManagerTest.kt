package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.EmbraceEvent
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.fakes.FakeAndroidMetadataService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeSdkModeBehavior
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal class DeliveryNetworkManagerTest {

    private lateinit var networkManager: DeliveryNetworkManager

    companion object {
        private val metadataService = FakeAndroidMetadataService()
        private val connectedNetworkStatuses =
            NetworkStatus.values().filter { it != NetworkStatus.NOT_REACHABLE }

        private lateinit var mockApiUrlBuilder: ApiUrlBuilder
        private lateinit var mockApiClient: ApiClient
        private lateinit var configService: ConfigService
        private lateinit var cfg: LocalConfig
        private lateinit var mockCacheManager: DeliveryCacheManager
        private lateinit var blockingScheduledExecutorService: BlockingScheduledExecutorService
        private lateinit var testScheduledExecutor: ScheduledExecutorService
        private lateinit var networkConnectivityService: NetworkConnectivityService
        private lateinit var failedApiCalls: DeliveryFailedApiCalls
        private lateinit var mockUserService: UserService

        @BeforeClass
        @JvmStatic
        fun setupBeforeAll() {
            cfg = LocalConfig("", false, SdkLocalConfig())
            mockApiUrlBuilder = mockk(relaxUnitFun = true)
            every { mockApiUrlBuilder.getEmbraceUrlWithSuffix(any()) } returns "http://fake.url"

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
    fun setup() {
        configService = FakeConfigService(
            sdkModeBehavior = fakeSdkModeBehavior(
                localCfg = { cfg }
            )
        )
        mockApiClient = mockk()
        every { mockApiClient.post(any(), any()) } returns ""
        failedApiCalls = DeliveryFailedApiCalls()
        clearApiPipeline()
        mockCacheManager = mockk(relaxUnitFun = true)
        every { mockCacheManager.loadPayload("cached_payload_1") } returns "{payload 1}".toByteArray()
        every { mockCacheManager.loadFailedApiCalls() } returns failedApiCalls
        every { mockCacheManager.savePayload(any()) } returns "fake_cache"
        mockUserService = mockk()
    }

    @After
    fun tearDown() {
        clearMocks(mockApiClient)
        clearMocks(mockCacheManager)
    }

    @Test
    fun `scheduled retry job active at init time`() {
        connectedNetworkStatuses.forEach { status ->
            initDeliveryNetworkManager(status = status, runRetryJobAfterScheduling = true)
            retryTaskActive(status)
            clearApiPipeline()
        }
    }

    @Test
    fun `retryTask is not active and doesn't run if there are no failed API requests`() {
        connectedNetworkStatuses.forEach { status ->
            initDeliveryNetworkManager(
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
            initDeliveryNetworkManager(status = status, runRetryJobAfterScheduling = true)
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
            initDeliveryNetworkManager(status = status, runRetryJobAfterScheduling = true)
            retryTaskActive(status)
            blockingScheduledExecutorService.runCurrentlyBlocked()
            checkRequestSendAttempt()
            retryTaskNotActive(status)
            // Previous failed attempt will queue another retry. Let it run so a new retry task is active
            blockingScheduledExecutorService.runCurrentlyBlocked()
            retryTaskActive(status)

            // First failure will result in another retry in 120 seconds
            // Go most of the way to check it didn't run
            blockingScheduledExecutorService.moveForwardAndRunBlocked(TimeUnit.SECONDS.toMillis(119L))
            retryTaskActive(status)
            checkRequestSendAttempt()

            // Go the full 120 seconds and check that the retry runs and fails
            blockingScheduledExecutorService.moveForwardAndRunBlocked(TimeUnit.SECONDS.toMillis(1L))
            checkRequestSendAttempt(count = 2)

            // Previous failed attempt will queue another retry. Let it run
            blockingScheduledExecutorService.runCurrentlyBlocked()

            // Let the next retry succeed
            every { mockApiClient.post(any(), any()) } returns ""

            // Second failure will result in another retry in double the last time, 240 seconds
            // Go most of the way to check it didn't run, then go all the way to check that it did.
            blockingScheduledExecutorService.moveForwardAndRunBlocked(TimeUnit.SECONDS.toMillis(239L))
            retryTaskActive(status)
            checkRequestSendAttempt(count = 2)
            blockingScheduledExecutorService.moveForwardAndRunBlocked(TimeUnit.SECONDS.toMillis(1L))
            retryTaskNotActive(status)
            checkRequestSendAttempt(count = 3)
            clearApiPipeline()
        }
    }

    @Test
    fun `retryTask is not active and doesn't run after init if network not reachable`() {
        initDeliveryNetworkManager(
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
            initDeliveryNetworkManager(
                status = NetworkStatus.NOT_REACHABLE,
                loadFailedRequest = false,
                runRetryJobAfterScheduling = true
            )
            blockingScheduledExecutorService.runCurrentlyBlocked()
            networkManager.onNetworkConnectivityStatusChanged(status)
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
            initDeliveryNetworkManager(
                status = NetworkStatus.NOT_REACHABLE,
                runRetryJobAfterScheduling = true
            )
            blockingScheduledExecutorService.runCurrentlyBlocked()
            networkManager.onNetworkConnectivityStatusChanged(status)
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
            initDeliveryNetworkManager(
                status = NetworkStatus.NOT_REACHABLE,
                loadFailedRequest = false
            )
            networkManager.onNetworkConnectivityStatusChanged(status)
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
            initDeliveryNetworkManager(status = NetworkStatus.NOT_REACHABLE)
            networkManager.onNetworkConnectivityStatusChanged(status)
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
            initDeliveryNetworkManager(status = status)
            networkManager.onNetworkConnectivityStatusChanged(NetworkStatus.NOT_REACHABLE)
            retryTaskNotActive(status)
            blockingScheduledExecutorService.runCurrentlyBlocked()
            checkNoApiRequestSent()
        }
    }

    @Test
    fun `queue size should be bounded`() {
        initDeliveryNetworkManager(status = NetworkStatus.WIFI, loadFailedRequest = false)
        every { mockApiClient.post(any(), any()) } throws Exception()

        Assert.assertEquals(0, networkManager.pendingRetriesCount())

        repeat(201) {
            networkManager.sendSession("{ dummy_session }".toByteArray(), null)
            blockingScheduledExecutorService.runCurrentlyBlocked()
        }
        Assert.assertEquals(200, networkManager.pendingRetriesCount())
    }

    @Test
    fun `test app and device info verification is not executed if integrationMode is false`() {
        initDeliveryNetworkManager(status = NetworkStatus.WIFI)
        val mocked = spyk(networkManager, recordPrivateCalls = true)
        val eventMessage = mockk<EventMessage>(relaxed = true)
        val event = Event(
            eventId = Uuid.getEmbUuid(),
            timestamp = 100L,
            type = EmbraceEvent.Type.CRASH
        )
        every { eventMessage.event } returns event
        mocked.sendEvent(eventMessage)
        verify(exactly = 0) { mocked["verifyDeviceInfo"](eventMessage) }
        verify(exactly = 0) { mocked["verifyAppInfo"](eventMessage) }
    }

    @Test
    fun `test app and device info verification is executed if integrationMode is true`() {
        initDeliveryNetworkManager(status = NetworkStatus.WIFI, integrationMode = true)

        val mocked = spyk(networkManager, recordPrivateCalls = true)
        val event = Event(
            eventId = Uuid.getEmbUuid(),
            timestamp = 100L,
            type = EmbraceEvent.Type.CRASH
        )
        val eventMessage = mockk<EventMessage>(relaxed = true)
        every { eventMessage.event } returns event
        mocked.sendEvent(eventMessage)
        verify { mocked["verifyDeviceInfo"](eventMessage) }
        verify { mocked["verifyAppInfo"](eventMessage) }
    }

    private fun clearApiPipeline() {
        clearMocks(mockApiClient, answers = false)
        failedApiCalls.clear()
        blockingScheduledExecutorService = BlockingScheduledExecutorService()
        testScheduledExecutor = blockingScheduledExecutorService
    }

    private fun initDeliveryNetworkManager(
        status: NetworkStatus,
        loadFailedRequest: Boolean = true,
        runRetryJobAfterScheduling: Boolean = false,
        integrationMode: Boolean = false,
    ) {
        cfg = LocalConfig("", false, SdkLocalConfig(integrationModeEnabled = integrationMode))
        every { networkConnectivityService.getCurrentNetworkStatus() } returns status

        networkManager = DeliveryNetworkManager(
            metadataService,
            mockApiUrlBuilder,
            mockApiClient,
            mockCacheManager,
            mockk(relaxUnitFun = true),
            configService,
            testScheduledExecutor,
            networkConnectivityService,
            EmbraceSerializer(),
            mockUserService
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
        Assert.assertTrue("Failed for network status = $status", networkManager.isRetryTaskActive())
    }

    private fun retryTaskNotActive(status: NetworkStatus) {
        Assert.assertFalse(
            "Failed for network status = $status",
            networkManager.isRetryTaskActive()
        )
    }

    private fun checkRequestSendAttempt(count: Int = 1) {
        verify(exactly = count) { mockApiClient.post(any(), "{payload 1}".toByteArray()) }
    }

    private fun checkNoApiRequestSent() {
        verify(exactly = 0) { mockApiClient.post(any(), any()) }
    }
}
