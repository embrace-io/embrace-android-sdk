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
        waitForPayloadIntake()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(2, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
        assertTrue(executionService.attemptedHttpRequests.first().data is SessionPayload)
    }

    @Test
    fun `payloads being sent will not be resent when a new payload arrives`() {
        deliveryExecutor.blockingMode = true
        waitForPayloadIntake()
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)
        waitForPayloadIntake()
        deliveryExecutor.blockingMode = false
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(3, executionService.sendAttempts())
    }

    @Test
    fun `payloads that fail to send will not be ready to be resent immediately`() {
        allSendsFail()
        waitForPayloadIntakeAndDeliveryAttempt()
        assertEquals(2, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
        waitForPayloadIntakeAndDeliveryAttempt()
        assertEquals(2, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
    }

    @Test
    fun `payloads that fail to send will be retried with exponential back off`() {
        allSendsFail()
        schedulingExecutor.blockingMode = true
        waitForPayloadIntakeAndDeliveryAttempt()
        var delay = INITIAL_DELAY_MS
        repeat(10) { iteration ->
            tickAndWaitForDeliveryAttempt(delay + 1)
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
        waitForPayloadIntakeAndDeliveryAttempt()
        clock.tick(INITIAL_DELAY_MS + 1)
        waitForPayloadIntakeAndDeliveryAttempt()
        assertEquals(4, executionService.sendAttempts())
    }

    @Test
    fun `new payload arrival during delivery loop will be picked up and sent without delay`() {
        schedulingExecutor.blockingMode = true
        deliveryExecutor.blockingMode = true
        schedulingService.onPayloadIntake()
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)
        waitForPayloadIntake()
        deliveryExecutor.blockingMode = false
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(3, executionService.sendAttempts())
    }

    @Test
    fun `payloads to blocked endpoint will not be sent or retried until duration lapses`() {
        val longBlockedDuration = 90_000L
        resetToSingleSessionPayload()
        executionService.constantResponse = ExecutionResult.TooManyRequests(
            endpoint = Endpoint.SESSIONS,
            retryAfterMs = longBlockedDuration
        )
        waitForPayloadIntakeAndDeliveryAttempt()
        assertEquals(1, executionService.sendAttempts())
        allSendsSucceed()
        tickAndWaitForDeliveryAttempt(INITIAL_DELAY_MS + 1)
        assertEquals(1, executionService.sendAttempts())
        tickAndWaitForDeliveryAttempt(longBlockedDuration - INITIAL_DELAY_MS)
        assertEquals(2, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `payloads that fail to deliver because of a 429 will be retried before the default delay if endpoint is unblocked earlier`() {
        resetToSingleSessionPayload()
        executionService.constantResponse = ExecutionResult.TooManyRequests(
            endpoint = Endpoint.SESSIONS,
            retryAfterMs = SHORT_BLOCKED_DURATION
        )
        waitForPayloadIntakeAndDeliveryAttempt()
        allSendsSucceed()
        tickAndWaitForDeliveryAttempt(SHORT_BLOCKED_DURATION + 1)
        assertEquals(2, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `payloads to unblocked endpoint will not affect other endpoints`() {
        resetToSingleSessionPayload()
        executionService.constantResponse = ExecutionResult.TooManyRequests(
            endpoint = Endpoint.SESSIONS,
            retryAfterMs = SHORT_BLOCKED_DURATION
        )
        waitForPayloadIntakeAndDeliveryAttempt()
        assertEquals(1, executionService.sendAttempts())
        allSendsSucceed()
        storageService.addFakePayload(fakeLogStoredTelemetryMetadata)
        waitForPayloadIntakeAndDeliveryAttempt()
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
        waitForPayloadIntakeAndDeliveryAttempt()
        assertEquals(1, executionService.sendAttempts())
        tickAndWaitForDeliveryAttempt(SHORT_BLOCKED_DURATION + 1)
        assertEquals(2, executionService.sendAttempts())
    }

    @Test
    fun `payloads to already blocked endpoint will not be sent`() {
        resetToSingleSessionPayload()
        executionService.constantResponse = ExecutionResult.TooManyRequests(
            endpoint = Endpoint.SESSIONS,
            retryAfterMs = SHORT_BLOCKED_DURATION
        )
        waitForPayloadIntakeAndDeliveryAttempt()
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)
        waitForPayloadIntakeAndDeliveryAttempt()
        assertEquals(1, executionService.sendAttempts())
    }

    @Test
    fun `no sent attempt will be made if a payload cannot be found on disk`() {
        deliveryExecutor.blockingMode = true
        waitForPayloadIntake()
        storageService.delete(fakeLogStoredTelemetryMetadata)
        deliveryExecutor.blockingMode = false
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(1, executionService.sendAttempts())
    }

    @Test
    fun `ready payloads will not be sent if there's no network but will be sent when network comes back online`() {
        networkConnectivityService.networkStatus = NetworkStatus.NOT_REACHABLE
        waitForPayloadIntake()
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
        waitForPayloadIntake()
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
        tickAndWaitForDeliveryAttempt((INITIAL_DELAY_MS * 2) + 1)
        assertEquals(4, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `burst of already queued payloads when there is no network will not be sent`() {
        networkConnectivityService.networkStatus = NetworkStatus.NOT_REACHABLE
        queuePayloadsWithoutExecution()
        waitForPayloadIntakeAndDeliveryAttempt()
        assertEquals(0, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
    }

    @Test
    fun `unhandled exception during request sending will not be retried`() {
        logger.throwOnInternalError = false
        executionService.exceptionOnExecution = RuntimeException("die")
        schedulingExecutor.blockingMode = true
        waitForPayloadIntake()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(0, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
        assertEquals(2, logger.internalErrorMessages.size)
    }

    @Test
    fun `connection failure blocks further delivery attempts`() {
        blockConnection()
        waitForPayloadIntakeAndDeliveryAttempt()
        assertEquals(1, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)
        waitForPayloadIntakeAndDeliveryAttempt()
        assertEquals(1, executionService.sendAttempts())
        assertEquals(3, storageService.storedPayloadCount())
    }

    @Test
    fun `connection timeout doesn't further delivery attempts`() {
        allSendsTimeout()
        waitForPayloadIntakeAndDeliveryAttempt()
        assertEquals(2, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)
        waitForPayloadIntakeAndDeliveryAttempt()
        assertEquals(3, executionService.sendAttempts())
        assertEquals(3, storageService.storedPayloadCount())
    }

    @Test
    fun `burst of already queued payloads will result in only one request attempt if it results in connection blockage`() {
        // Blocking the delivery executor means all the cached payloads will be queued by the scheduling executor
        // without a chance for the first request to trigger a block. This simulates a burst of undelivered payloads
        // being queued up at SDK startup, which should result in just one failed request
        queuePayloadsWithoutExecution(::blockConnection)
        waitForPayloadIntakeAndDeliveryAttempt()
        assertEquals(1, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
    }

    @Test
    fun `connection attempt retried in an exponentially backed off manner`() {
        queuePayloadsWithoutExecution(::disconnect)
        waitForPayloadIntakeAndDeliveryAttempt()
        var delay = INITIAL_DELAY_MS
        repeat(10) { iteration ->
            tickAndWaitForDeliveryAttempt(delay)
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
        waitForPayloadIntakeAndDeliveryAttempt()
        allSendsSucceed()
        tickAndWaitForDeliveryAttempt(INITIAL_DELAY_MS - 1)
        assertEquals(1, executionService.sendAttempts())
        tickAndWaitForDeliveryAttempt(1)
        assertEquals(3, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `new payload will not result in connection attempt if paused period has not elapsed`() {
        queuePayloadsWithoutExecution(::blockConnection)
        waitForPayloadIntakeAndDeliveryAttempt()
        schedulingExecutor.moveForwardAndRunBlocked(INITIAL_DELAY_MS - 1)
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)
        waitForPayloadIntake()
        assertEquals(1, executionService.sendAttempts())
        assertEquals(3, storageService.storedPayloadCount())
    }

    @Test
    fun `network change after connection retry pause period elapse will trigger send`() {
        queuePayloadsWithoutExecution(::allSendsTimeout)

        // After this returns, both stored payloads should've tried, timed out and scheduled to be retried in INITIAL_DELAY_MS
        waitForPayloadIntakeAndDeliveryAttempt()
        assertEquals(2, executionService.sendAttempts())

        // All subsequent requests will be unable to reach the Embrace server
        blockConnection()

        // Moving the clock forward to when a retry should be invoked
        // first request will fail to connect and lock the delivery layer by INITIAL_DELAY_MS
        // second request not execute
        // both requests will be retried when the connection is unblocked
        tickAndWaitForDeliveryAttempt(INITIAL_DELAY_MS)
        assertEquals(3, executionService.sendAttempts())

        // Subsequent payloads will succeed
        allSendsSucceed()

        // Moving the time before to just before the connection blockage will not cause any retries
        tickAndWaitForDeliveryAttempt(INITIAL_DELAY_MS - 1)
        assertEquals(3, executionService.sendAttempts())

        // The connection changing will reset the connection blockage and make the previously blocked requests run and succeed
        triggerSwitchToWifi()
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

    /**
     * Wait for payloads in storage to be processed and attempted to be delivered
     */
    private fun waitForPayloadIntakeAndDeliveryAttempt() {
        waitForPayloadIntake()
        deliveryExecutor.awaitExecutionCompletion()
    }

    /**
     * Wait for payloads in storage to be processed and scheduled for delivery
     */
    private fun waitForPayloadIntake() {
        schedulingService.onPayloadIntake()
        schedulingExecutor.awaitExecutionCompletion()
    }

    /**
     * Advance the clock by [delayMs] and wait for all scheduling and delivery work to complete.
     */
    private fun tickAndWaitForDeliveryAttempt(delayMs: Long) {
        schedulingExecutor.moveForwardAndRunBlocked(delayMs)
        schedulingExecutor.awaitExecutionCompletion()
        deliveryExecutor.awaitExecutionCompletion()
    }

    /**
     * Simulate the network being changed to wifi and allow all callbacks to be invoked and processed
     */
    private fun triggerSwitchToWifi() {
        networkConnectivityService.networkStatus = NetworkStatus.WIFI
        schedulingExecutor.runCurrentlyBlocked()
        schedulingExecutor.awaitExecutionCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        schedulingExecutor.awaitExecutionCompletion()
        deliveryExecutor.awaitExecutionCompletion()
    }

    /**
     * Reset storage to a single session payload, discarding any payloads from the default setup.
     */
    private fun resetToSingleSessionPayload() {
        storageService.clearStorage()
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata)
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
