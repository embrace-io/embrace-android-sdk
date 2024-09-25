package io.embrace.android.embracesdk.internal.delivery.scheduling

import io.embrace.android.embracesdk.assertions.assertCountedDown
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeRequestExecutionService
import io.embrace.android.embracesdk.fakes.FakeStorageService2
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.fixtures.fakeLogPayloadReference
import io.embrace.android.embracesdk.fixtures.fakeSessionPayloadReference
import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingServiceImpl.Companion.INITIAL_DELAY_MS
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class SchedulingServiceImplTest {

    private lateinit var storageService: FakeStorageService2
    private lateinit var executionService: FakeRequestExecutionService
    private lateinit var schedulingExecutor: BlockingScheduledExecutorService
    private lateinit var deliveryExecutor: BlockingScheduledExecutorService
    private lateinit var clock: FakeClock
    private lateinit var schedulingService: SchedulingServiceImpl

    @Before
    fun setup() {
        val workerModule = FakeWorkerThreadModule(
            testWorkerName = Worker.Background.IoRegWorker,
            anotherTestWorkerName = Worker.Background.DeliveryWorker
        )
        schedulingExecutor = workerModule.executor.apply { blockingMode = true }
        deliveryExecutor = workerModule.anotherExecutor.apply { blockingMode = true }
        clock = workerModule.executorClock
        storageService = FakeStorageService2(
            listOf(fakeSessionPayloadReference, fakeLogPayloadReference)
        )
        executionService = FakeRequestExecutionService()
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
        schedulingService.onPayloadIntake()
        assertEquals(1, schedulingExecutor.submitCount)
        val latch = schedulingExecutor.queueCompletionTask()
        schedulingExecutor.runCurrentlyBlocked()
        latch.assertCountedDown()
        schedulingService.onPayloadIntake()
        assertEquals(3, schedulingExecutor.submitCount)
    }

    @Test
    fun `new payload will not trigger new delivery loop job if one is running`() {
        schedulingService.onPayloadIntake()
        schedulingService.onPayloadIntake()
        assertEquals(1, schedulingExecutor.submitCount)
    }

    @Test
    fun `all payloads ready to be sent are queued up`() {
        schedulingService.onPayloadIntake()
        schedulingExecutor.runCurrentlyBlocked()
        val latch = deliveryExecutor.queueCompletionTask()
        deliveryExecutor.runCurrentlyBlocked()
        latch.assertCountedDown()
        assertEquals(3, deliveryExecutor.submitCount)
    }

    @Test
    fun `payloads remaining in storage will not be resent if retry period has not ended`() {
        executionService.constantResponse = ApiResponse.None
        schedulingExecutor.blockingMode = false
        deliveryExecutor.blockingMode = false
        schedulingService.onPayloadIntake()
        deliveryExecutor.queueCompletionTask().assertCountedDown()
        val submittedDeliveries = deliveryExecutor.submitCount
        schedulingService.onPayloadIntake()
        deliveryExecutor.queueCompletionTask().assertCountedDown()
        assertEquals(submittedDeliveries + 1, deliveryExecutor.submitCount)
    }

    @Test
    fun `payloads remaining in storage will resent if retry period has ended`() {
        executionService.constantResponse = ApiResponse.None
        schedulingExecutor.blockingMode = false
        deliveryExecutor.blockingMode = false
        schedulingService.onPayloadIntake()
        deliveryExecutor.queueCompletionTask().assertCountedDown()
        val submittedDeliveries = deliveryExecutor.submitCount
        clock.tick(INITIAL_DELAY_MS + 1)
        schedulingService.onPayloadIntake()
        deliveryExecutor.queueCompletionTask().assertCountedDown()
        assertEquals(submittedDeliveries + 3, deliveryExecutor.submitCount)
    }

    @Test
    fun `new payload arrival will trigger it to be sent and not resend in progress payloads`() {
        schedulingService.onPayloadIntake()
        schedulingExecutor.runCurrentlyBlocked()
        storageService.cachedPayloads.add(fakeSessionPayloadReference.copy(fileName = "session2.json"))
        schedulingService.onPayloadIntake()
        val scheduleLatch = schedulingExecutor.queueCompletionTask()
        schedulingExecutor.runCurrentlyBlocked()
        scheduleLatch.assertCountedDown()
        val deliveryLatch = deliveryExecutor.queueCompletionTask()
        deliveryExecutor.runCurrentlyBlocked()
        deliveryLatch.assertCountedDown()
        assertEquals(4, deliveryExecutor.submitCount)
    }
}
