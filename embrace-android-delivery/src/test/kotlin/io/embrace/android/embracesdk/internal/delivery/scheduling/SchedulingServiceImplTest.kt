package io.embrace.android.embracesdk.internal.delivery.scheduling

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.FakeRequestExecutionService
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.fixtures.fakeLogStoredTelemetryMetadata
import io.embrace.android.embracesdk.fixtures.fakeSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.fixtures.fakeSessionStoredTelemetryMetadata2
import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingServiceImpl.Companion.INITIAL_DELAY_MS
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SchedulingServiceImplTest {

    private lateinit var storageService: FakePayloadStorageService
    private lateinit var executionService: FakeRequestExecutionService
    private lateinit var schedulingExecutor: BlockingScheduledExecutorService
    private lateinit var deliveryExecutor: BlockingScheduledExecutorService
    private lateinit var schedulingService: SchedulingServiceImpl

    @Volatile
    private lateinit var clock: FakeClock

    @Before
    fun setup() {
        val workerModule = FakeWorkerThreadModule(
            testWorkerName = Worker.Background.IoRegWorker,
            anotherTestWorkerName = Worker.Background.DeliveryWorker
        )
        schedulingExecutor = workerModule.executor.apply { blockingMode = false }
        deliveryExecutor = workerModule.anotherExecutor.apply { blockingMode = false }
        clock = workerModule.executorClock
        storageService = FakePayloadStorageService().apply {
            addFakePayload(fakeLogStoredTelemetryMetadata)
            addFakePayload(fakeSessionStoredTelemetryMetadata)
        }
        executionService = FakeRequestExecutionService()
        allSendsSucceed()
        schedulingService = SchedulingServiceImpl(
            storageService = storageService,
            executionService = executionService,
            schedulingWorker = workerModule.backgroundWorker(worker = Worker.Background.IoRegWorker),
            deliveryWorker = workerModule.backgroundWorker(worker = Worker.Background.DeliveryWorker),
            clock = clock,
        )
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
        assertEquals(1, schedulingExecutor.submitCount)
    }

    @Test
    fun `all payloads ready to be sent are sent in priority order`() {
        executionService.constantResponse = success
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
        executionService.constantResponse = ApiResponse.TooManyRequests(endpoint = Endpoint.SESSIONS_V2, longBlockedDuration)
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
        executionService.constantResponse = ApiResponse.TooManyRequests(endpoint = Endpoint.SESSIONS_V2, SHORT_BLOCKED_DURATION)
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
        executionService.constantResponse = ApiResponse.TooManyRequests(endpoint = Endpoint.SESSIONS_V2, SHORT_BLOCKED_DURATION)
        waitForOnPayloadIntakeTaskCompletion()
        deliveryExecutor.awaitExecutionCompletion()
        assertEquals(1, executionService.sendAttempts())
        executionService.constantResponse = success
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
        executionService.constantResponse = ApiResponse.TooManyRequests(endpoint = Endpoint.SESSIONS_V2, SHORT_BLOCKED_DURATION)
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
        executionService.constantResponse = ApiResponse.TooManyRequests(endpoint = Endpoint.SESSIONS_V2, SHORT_BLOCKED_DURATION)
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

    private fun waitForOnPayloadIntakeTaskCompletion() {
        schedulingService.onPayloadIntake()
        schedulingExecutor.awaitExecutionCompletion()
    }

    private fun allSendsSucceed() {
        executionService.constantResponse = success
    }

    private fun allSendsFail() {
        executionService.constantResponse = failure
    }

    private companion object {
        const val SHORT_BLOCKED_DURATION = 30_000L
        val success = ApiResponse.Success(body = "", headers = emptyMap())
        val failure = ApiResponse.Failure(code = 500, emptyMap())
    }
}
