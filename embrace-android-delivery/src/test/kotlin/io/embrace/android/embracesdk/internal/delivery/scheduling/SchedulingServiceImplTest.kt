package io.embrace.android.embracesdk.internal.delivery.scheduling

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.FakeRequestExecutionService
import io.embrace.android.embracesdk.fixtures.fakeLogStoredTelemetryMetadata
import io.embrace.android.embracesdk.fixtures.fakeSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.fixtures.fakeSessionStoredTelemetryMetadata2
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.delivery.execution.ExecutionResult
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingServiceImpl.Companion.INITIAL_DELAY_MS
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.RejectedExecutionException

internal class SchedulingServiceImplTest {

    private lateinit var storageService: FakePayloadStorageService
    private lateinit var executionService: FakeRequestExecutionService
    private lateinit var schedulingExecutor: BlockingScheduledExecutorService
    private lateinit var deliveryExecutor: BlockingScheduledExecutorService
    private lateinit var networkConnectivityService: FakeNetworkConnectivityService
    private lateinit var logger: FakeInternalLogger
    private lateinit var schedulingService: SchedulingServiceImpl

    @Volatile
    private lateinit var clock: FakeClock

    @Before
    fun setup() {
        clock = FakeClock()
        schedulingExecutor = BlockingScheduledExecutorService(clock, blockingMode = false)
        deliveryExecutor = BlockingScheduledExecutorService(clock, blockingMode = false)
        networkConnectivityService = FakeNetworkConnectivityService()
        storageService = FakePayloadStorageService().apply {
            addFakePayload(fakeLogStoredTelemetryMetadata)
            addFakePayload(fakeSessionStoredTelemetryMetadata)
        }
        executionService = FakeRequestExecutionService()
        logger = FakeInternalLogger()
        allSendsSucceed()
        schedulingService = SchedulingServiceImpl(
            storageService = storageService,
            executionService = executionService,
            schedulingWorker = BackgroundWorker(schedulingExecutor),
            deliveryWorker = BackgroundWorker(deliveryExecutor),
            clock = clock,
            logger = logger,
        )
        networkConnectivityService.addNetworkConnectivityListener(schedulingService)
    }

    @Test
    fun `new payload will trigger new delivery loop if the previous one is done`() {
        schedulingExecutor.blockingMode = true
        schedulingService.onPayloadIntake()
        assertEquals(1, schedulingExecutor.submitCount)
        schedulingExecutor.awaitExecutionCompletion()
        schedulingService.onPayloadIntake()
        assertEquals(3, schedulingExecutor.submitCount)
    }

    @Test
    fun `new payload will not trigger new delivery loop job if one is running`() {
        schedulingExecutor.blockingMode = true
        schedulingService.onPayloadIntake()
        schedulingService.onPayloadIntake()
        assertEquals(2, schedulingExecutor.submitCount)
    }

    @Test
    fun `all payloads ready to be sent are sent in priority order`() {
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(2, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
        assertTrue(executionService.attemptedHttpRequests.first().data is SessionPayload)
    }

    @Test
    fun `payloads being sent will not be resent when a new payload arrives`() {
        deliveryExecutor.blockingMode = true
        waitForOnPayloadIntakeTaskCompletion()
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.blockingMode = false
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(3, executionService.sendAttempts())
    }

    @Test
    fun `payloads that fail to send will not be ready to be resent immediately`() {
        allSendsFail()
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(2, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(2, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
    }

    @Test
    fun `payloads that fail to send will be retried with exponential back off`() {
        allSendsFail()
        schedulingExecutor.blockingMode = true
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        var delay = INITIAL_DELAY_MS
        repeat(10) { iteration ->
            schedulingExecutor.moveForwardAndRunBlocked(delay + 1)
            schedulingExecutor.awaitExecutionCompletion()
            deliveryExecutor.awaitExecutionCompletion()
            assertEquals(
                "Send attempt ${iteration + 1} did not result in the right number of sends after $delay ms",
                2 * (iteration + 2),
                executionService.sendAttempts()
            )
            assertEquals("Send attempt $iteration failed", 2, storageService.storedPayloadCount())
            delay *= 2
        }
    }

    @Test
    fun `payloads remaining in storage will resent if retry period has ended`() {
        allSendsFail()
        schedulingExecutor.blockingMode = true
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        clock.tick(INITIAL_DELAY_MS + 1)
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(4, executionService.sendAttempts())
    }

    @Test
    fun `new payload arrival during delivery loop will be picked up and sent without delay`() {
        schedulingExecutor.blockingMode = true
        deliveryExecutor.blockingMode = true
        schedulingService.onPayloadIntake()
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.blockingMode = false
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(3, executionService.sendAttempts())
    }

    @Test
    fun `payloads to blocked endpoint will not be sent or retried until duration lapses`() {
        val longBlockedDuration = 90_000L
        storageService.clearStorage()
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata)
        executionService.constantResponse = ExecutionResult.TooManyRequests(
            endpoint = Endpoint.SESSIONS,
            retryAfterMs = longBlockedDuration
        )
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(1, executionService.sendAttempts())
        allSendsSucceed()
        schedulingExecutor.moveForwardAndRunBlocked(INITIAL_DELAY_MS + 1)
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(1, executionService.sendAttempts())
        schedulingExecutor.moveForwardAndRunBlocked(longBlockedDuration - INITIAL_DELAY_MS)
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(2, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `payloads that fail to deliver because of a 429 will be retried before the default delay if endpoint is unblocked earlier`() {
        storageService.clearStorage()
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata)
        executionService.constantResponse = ExecutionResult.TooManyRequests(
            endpoint = Endpoint.SESSIONS,
            retryAfterMs = SHORT_BLOCKED_DURATION
        )
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        allSendsSucceed()
        schedulingExecutor.moveForwardAndRunBlocked(SHORT_BLOCKED_DURATION + 1)
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(2, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `payloads to unblocked endpoint will not affect other endpoints`() {
        storageService.clearStorage()
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata)
        executionService.constantResponse = ExecutionResult.TooManyRequests(
            endpoint = Endpoint.SESSIONS,
            retryAfterMs = SHORT_BLOCKED_DURATION
        )
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(1, executionService.sendAttempts())
        allSendsSucceed()
        storageService.addFakePayload(fakeLogStoredTelemetryMetadata)
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(2, executionService.sendAttempts())
        assertEquals(1, storageService.storedPayloadCount())
    }

    @Test
    fun `concurrent payload sending to the same endpoint will result in only one delivery attempt`() {
        storageService.clearStorage()
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata)
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)
        executionService.constantResponse = ExecutionResult.TooManyRequests(
            endpoint = Endpoint.SESSIONS,
            retryAfterMs = SHORT_BLOCKED_DURATION
        )
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(1, executionService.sendAttempts())
        schedulingExecutor.moveForwardAndRunBlocked(SHORT_BLOCKED_DURATION + 1)
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(2, executionService.sendAttempts())
    }

    @Test
    fun `payloads to already blocked endpoint will not be sent`() {
        storageService.clearStorage()
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata)
        executionService.constantResponse = ExecutionResult.TooManyRequests(
            endpoint = Endpoint.SESSIONS,
            retryAfterMs = SHORT_BLOCKED_DURATION
        )
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(1, executionService.sendAttempts())
    }

    @Test
    fun `no sent attempt will be made if a payload cannot be found on disk`() {
        deliveryExecutor.blockingMode = true
        waitForOnPayloadIntakeTaskCompletion()
        storageService.delete(fakeLogStoredTelemetryMetadata)
        deliveryExecutor.blockingMode = false
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(1, executionService.sendAttempts())
    }

    @Test
    fun `ready payloads will not be sent if there's no network but will be sent when network comes back online`() {
        networkConnectivityService.networkStatus = NetworkStatus.NOT_REACHABLE
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(0, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
        networkConnectivityService.networkStatus = NetworkStatus.WIFI
        schedulingExecutor.awaitExecutionCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(2, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `losing network will cause payload sends to stop and reconnection makes it start again`() {
        schedulingExecutor.blockingMode = true
        allSendsFail()
        networkConnectivityService.networkStatus = NetworkStatus.WAN
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(2, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
        networkConnectivityService.networkStatus = NetworkStatus.NOT_REACHABLE
        schedulingExecutor.moveForwardAndRunBlocked(INITIAL_DELAY_MS + 1)
        schedulingExecutor.awaitExecutionCompletion()
        assertEquals(2, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
        allSendsSucceed()
        networkConnectivityService.networkStatus = NetworkStatus.UNKNOWN
        schedulingExecutor.moveForwardAndRunBlocked((INITIAL_DELAY_MS * 2) + 1)
        schedulingExecutor.awaitExecutionCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(4, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `burst of already queued payloads when there is no network will not be sent`() {
        networkConnectivityService.networkStatus = NetworkStatus.NOT_REACHABLE
        queuePayloadsWithoutExecution()
        waitForOnPayloadIntakeAndDeliveryCompletion()
        assertEquals(0, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
    }

    @Test
    fun `unhandled exception during request sending will not be retried`() {
        logger.throwOnInternalError = false
        executionService.exceptionOnExecution = RuntimeException("die")
        schedulingExecutor.blockingMode = true
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(0, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
        assertEquals(2, logger.internalErrorMessages.size)
    }

    @Test
    fun `connection failure blocks further delivery attempts`() {
        blockConnection()
        waitForOnPayloadIntakeAndDeliveryCompletion()
        assertEquals(1, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)
        waitForOnPayloadIntakeAndDeliveryCompletion()
        assertEquals(1, executionService.sendAttempts())
        assertEquals(3, storageService.storedPayloadCount())
    }

    @Test
    fun `connection timeout doesn't further delivery attempts`() {
        allSendsTimeout()
        waitForOnPayloadIntakeAndDeliveryCompletion()
        assertEquals(2, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)
        waitForOnPayloadIntakeAndDeliveryCompletion()
        assertEquals(3, executionService.sendAttempts())
        assertEquals(3, storageService.storedPayloadCount())
    }

    @Test
    fun `burst of already queued payloads will result in only one request attempt if it results in connection blockage`() {
        // Blocking the delivery executor means all the cached payloads will be queued by the scheduling executor
        // without a chance for the first request to trigger a block. This simulates a burst of undelivered payloads
        // being queued up at SDK startup, which should result in just one failed request
        queuePayloadsWithoutExecution(::blockConnection)
        waitForOnPayloadIntakeAndDeliveryCompletion()
        assertEquals(1, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
    }

    @Test
    fun `connection attempt retried in an exponentially backed off manner`() {
        queuePayloadsWithoutExecution(::disconnect)
        waitForOnPayloadIntakeAndDeliveryCompletion()
        var delay = INITIAL_DELAY_MS
        repeat(10) { iteration ->
            schedulingExecutor.moveForwardAndRunBlocked(delay)
            schedulingExecutor.awaitExecutionCompletion()
            deliveryExecutor.awaitExecutionCompletion()
            assertEquals(
                "Connection retry attempt ${iteration + 1} did not result in the right number of sends after $delay ms",
                iteration + 2,
                executionService.sendAttempts()
            )
            delay *= 2
        }
        assertEquals(2, storageService.storedPayloadCount())
    }

    @Test
    fun `payload will be delivered at first retry attempt if unblocked`() {
        queuePayloadsWithoutExecution(::disconnect)
        waitForOnPayloadIntakeAndDeliveryCompletion()
        allSendsSucceed()
        schedulingExecutor.moveForwardAndRunBlocked(INITIAL_DELAY_MS - 1)
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(1, executionService.sendAttempts())
        schedulingExecutor.moveForwardAndRunBlocked(1)
        schedulingExecutor.awaitExecutionCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(3, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `new payload will not result in connection attempt if paused period has not elapsed`() {
        queuePayloadsWithoutExecution(::blockConnection)
        waitForOnPayloadIntakeAndDeliveryCompletion()
        schedulingExecutor.moveForwardAndRunBlocked(INITIAL_DELAY_MS - 1)
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)
        waitForOnPayloadIntakeTaskCompletion()
        assertEquals(1, executionService.sendAttempts())
        assertEquals(3, storageService.storedPayloadCount())
    }

    @Test
    fun `network change after connection retry pause period elapse will trigger send`() {
        queuePayloadsWithoutExecution(::allSendsTimeout)

        // After this returns, both stored payloads should've tried, timed out and scheduled to be retried in INITIAL_DELAY_MS
        waitForOnPayloadIntakeAndDeliveryCompletion()
        assertEquals(2, executionService.sendAttempts())

        // All subsequent requests will be unable to reach the Embrace server
        blockConnection()

        // Moving the clock forward to when a retry should be invoked
        // first request will fail to connect and lock the delivery layer by INITIAL_DELAY_MS
        // second request not execute
        // both requests will be retried when the connection is unblocked
        schedulingExecutor.moveForwardAndRunBlocked(INITIAL_DELAY_MS)
        schedulingExecutor.awaitExecutionCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(3, executionService.sendAttempts())

        // Subsequent payloads will succeed
        allSendsSucceed()

        // Moving the time before to just before the connection blockage will not cause any retries
        schedulingExecutor.moveForwardAndRunBlocked(INITIAL_DELAY_MS - 1)
        schedulingExecutor.awaitExecutionCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(3, executionService.sendAttempts())

        // The connection changing will reset the connection blockage and make the previously blocked requests run and succeed
        networkConnectivityService.networkStatus = NetworkStatus.WIFI
        schedulingExecutor.runCurrentlyBlocked()
        schedulingExecutor.awaitExecutionCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        // Trigger delivery loop on scheduler thread
        schedulingExecutor.awaitExecutionCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(5, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test(expected = RejectedExecutionException::class)
    fun `test shutdown`() {
        logger.throwOnInternalError = false
        schedulingService.onPayloadIntake()
        schedulingService.shutdown()

        // Throws RejectedExecutionException. Note that this is a consequence of the
        // test setup & the real executor has its own rejection handler,
        // meaning the exception will never get thrown in prod.
        schedulingService.onPayloadIntake()
    }

    private fun waitForOnPayloadIntakeAndDeliveryCompletion() {
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.awaitExecutionCompletion()
    }

    private fun waitForOnPayloadIntakeTaskCompletion() {
        schedulingService.onPayloadIntake()
        schedulingExecutor.awaitExecutionCompletion()
    }

    private fun allSendsSucceed() {
        executionService.constantResponse = ExecutionResult.Success
    }

    private fun allSendsFail() {
        executionService.constantResponse = failure
    }

    private fun allSendsTimeout() {
        executionService.constantResponse = timeout
    }

    private fun disconnect() {
        executionService.constantResponse = failHostLookup
    }

    private fun blockConnection() {
        executionService.constantResponse = failHostLookup
    }

    private fun queuePayloadsWithoutExecution(setup: () -> Unit = {}) {
        schedulingExecutor.blockingMode = true
        deliveryExecutor.blockingMode = true
        setup()
    }

    private companion object {
        const val SHORT_BLOCKED_DURATION = 30_000L
        val failure = ExecutionResult.Failure(code = 500)

        val failHostLookup = ExecutionResult.Incomplete(UnknownHostException(), true)
        val cannotConnect = ExecutionResult.Incomplete(ConnectException(), true)

        val timeout = ExecutionResult.Incomplete(SocketTimeoutException(), true)
    }
}
