package io.embrace.android.embracesdk.internal.delivery.scheduling

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
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
import java.util.concurrent.RejectedExecutionException

internal class SchedulingServiceImplTest {

    private lateinit var storageService: FakePayloadStorageService
    private lateinit var executionService: FakeRequestExecutionService
    private lateinit var schedulingExecutor: BlockingScheduledExecutorService
    private lateinit var deliveryExecutor: BlockingScheduledExecutorService
    private lateinit var networkConnectivityService: FakeNetworkConnectivityService
    private lateinit var logger: FakeEmbLogger
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
        executionService = FakeRequestExecutionService(strictMode = false)
        logger = FakeEmbLogger()
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

    private companion object {
        const val SHORT_BLOCKED_DURATION = 30_000L
        val failure = ExecutionResult.Failure(code = 500)
    }
}
