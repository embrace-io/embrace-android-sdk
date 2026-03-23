package io.embrace.android.embracesdk.internal.delivery.scheduling

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.FakeRequestExecutionService
import io.embrace.android.embracesdk.fixtures.fakeCrashStoredTelemetryMetadata
import io.embrace.android.embracesdk.fixtures.fakeLogStoredTelemetryMetadata
import io.embrace.android.embracesdk.fixtures.fakeSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.fixtures.fakeSessionStoredTelemetryMetadata2
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.comms.delivery.toConnectivityStatus
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.execution.ExecutionResult
import io.embrace.android.embracesdk.internal.delivery.execution.ExecutionResult.Failure
import io.embrace.android.embracesdk.internal.delivery.execution.ExecutionResult.Incomplete
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingServiceImpl.Companion.INITIAL_DELAY_MS
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
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
        schedulingExecutor = BlockingScheduledExecutorService(clock, blockingMode = true)
        deliveryExecutor = BlockingScheduledExecutorService(clock, blockingMode = true)
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
    fun `new payload will trigger new delivery attempt if the previous one is done`() {
        val countBefore = schedulingExecutor.submitCount
        schedulingService.onResurrectionComplete()
        assertEquals(countBefore + 1, schedulingExecutor.submitCount)
        schedulingExecutor.awaitExecutionCompletion()
        schedulingService.onPayloadIntake()
        assertTrue(schedulingExecutor.submitCount > countBefore + 1)
    }

    @Test
    fun `all payloads ready to be sent are sent in priority order`() {
        waitForResurrectionAndDeliveryAttempt(2)
        assertEquals(2, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
        assertTrue(executionService.attemptedHttpRequests.first().data is SessionPayload)
    }

    @Test
    fun `payloads already queued will not be re-queued when a new payload arrives`() {
        waitForResurrection()
        assertEquals(2, schedulingExecutor.submitCount)
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)
        waitForPayloadIntakeAndDeliveryAttempt(5)
        assertEquals(3, executionService.sendAttempts())
    }

    @Test
    fun `payloads that fail to send will not be ready to be resent immediately`() {
        allSendsFail()
        waitForResurrectionAndDeliveryAttempt(2)
        assertEquals(2, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
        waitForPayloadIntakeAndDeliveryAttempt()
        assertEquals(2, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
    }

    @Test
    fun `payloads that fail to send will be retried with exponential back off`() {
        allSendsFail()
        waitForResurrectionAndDeliveryAttempt(2)
        var delay = INITIAL_DELAY_MS
        repeat(10) { iteration ->
            tickAndWaitForDeliveryAttempts(delay + 1, 2)
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
        waitForResurrectionAndDeliveryAttempt(2)
        clock.tick(INITIAL_DELAY_MS + 1)
        waitForPayloadIntakeAndDeliveryAttempt(2)
        assertEquals(4, executionService.sendAttempts())
    }

    @Test
    fun `new payload arrival while delivery is happening will be picked up and sent without delay`() {
        // Don't unblock delivery worker and allow the resurrection to queue the first batch but not send
        waitForResurrection()
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)
        // Add another payload that will also be queued and not executed
        waitForPayloadIntake()
        assertEquals(0, executionService.sendAttempts())
        // All will be sent at once
        waitPayloadSendAttempt(3)
        assertEquals(3, executionService.sendAttempts())
    }

    @Test
    fun `payloads to blocked endpoint will not be sent or retried until duration lapses`() {
        val longBlockedDuration = 90_000L
        serverBusy(endpoint = Endpoint.SESSIONS, retryAfterMs = longBlockedDuration)
        resetToSingleSessionPayload()
        waitForResurrectionAndDeliveryAttempt()
        assertEquals(1, executionService.sendAttempts())
        allSendsSucceed()
        tickAndWaitForDeliveryAttempts(INITIAL_DELAY_MS + 1)
        assertEquals(1, executionService.sendAttempts())
        tickAndWaitForDeliveryAttempts(longBlockedDuration - INITIAL_DELAY_MS)
        assertEquals(2, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `payloads that fail to deliver because of a 429 will be retried before the default delay if endpoint is unblocked earlier`() {
        serverBusy(endpoint = Endpoint.SESSIONS, retryAfterMs = SHORT_BLOCKED_DURATION)
        resetToSingleSessionPayload()
        waitForResurrectionAndDeliveryAttempt()
        allSendsSucceed()
        tickAndWaitForDeliveryAttempts(SHORT_BLOCKED_DURATION + 1)
        assertEquals(2, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `payloads to unblocked endpoint will not affect other endpoints`() {
        serverBusy(endpoint = Endpoint.SESSIONS, retryAfterMs = SHORT_BLOCKED_DURATION)
        resetToSingleSessionPayload()
        waitForResurrectionAndDeliveryAttempt()
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
        allSendsTimeout()
        waitForResurrectionAndDeliveryAttempt(2)
        assertEquals(1, executionService.sendAttempts())
        tickAndWaitForDeliveryAttempts(SHORT_BLOCKED_DURATION + 1)
        assertEquals(2, executionService.sendAttempts())
    }

    @Test
    fun `payloads to already blocked endpoint will not be sent`() {
        allSendsTimeout()
        resetToSingleSessionPayload()
        waitForResurrectionAndDeliveryAttempt()
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)
        waitForPayloadIntakeAndDeliveryAttempt(2)
        assertEquals(1, executionService.sendAttempts())
    }

    @Test
    fun `no sent attempt will be made if a payload cannot be found on disk`() {
        waitForResurrection()
        storageService.delete(fakeLogStoredTelemetryMetadata)
        waitPayloadSendAttempt(2)
        assertEquals(1, executionService.sendAttempts())
    }

    @Test
    fun `ready payloads will not be sent if there's no network but will be sent when network comes back online`() {
        networkConnectivityService.connectivityStatus = NetworkStatus.NOT_REACHABLE.toConnectivityStatus()
        waitForResurrectionAndDeliveryAttempt(2)
        assertEquals(0, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
        networkConnectivityService.connectivityStatus = NetworkStatus.WIFI.toConnectivityStatus()
        waitForPayloadIntakeAndDeliveryAttempt(2)
        assertEquals(2, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `losing network will cause payload sends to stop and reconnection makes it start again`() {
        allSendsFail()
        networkConnectivityService.connectivityStatus = NetworkStatus.WAN.toConnectivityStatus()
        waitForResurrectionAndDeliveryAttempt(2)
        assertEquals(2, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
        networkConnectivityService.connectivityStatus = NetworkStatus.NOT_REACHABLE.toConnectivityStatus()
        tickAndWaitForDeliveryAttempts(INITIAL_DELAY_MS, 2)
        assertEquals(2, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
        allSendsSucceed()
        networkConnectivityService.connectivityStatus = NetworkStatus.UNKNOWN.toConnectivityStatus()
        tickAndWaitForDeliveryAttempts((INITIAL_DELAY_MS * 2) + 1, 2)
        assertEquals(4, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `burst of already queued payloads when there is no network will not be sent`() {
        networkConnectivityService.connectivityStatus = NetworkStatus.NOT_REACHABLE.toConnectivityStatus()
        waitForResurrectionAndDeliveryAttempt(2)
        assertEquals(0, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
    }

    @Test
    fun `unhandled exception during request sending will not be retried`() {
        logger.throwOnInternalError = false
        executionService.exceptionOnExecution = RuntimeException("die")
        waitForResurrectionAndDeliveryAttempt(2)
        assertEquals(0, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
        assertEquals(2, logger.internalErrorMessages.size)
    }

    @Test
    fun `connection failure blocks further delivery attempts`() {
        blockConnection()
        waitForResurrectionAndDeliveryAttempt(2)
        assertEquals(1, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)
        waitForPayloadIntakeAndDeliveryAttempt()
        assertEquals(1, executionService.sendAttempts())
        assertEquals(3, storageService.storedPayloadCount())
    }

    @Test
    fun `connection timeout schedules retry without blocking permanently`() {
        allSendsTimeout()
        waitForResurrectionAndDeliveryAttempt(2)
        assertEquals(2, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
        allSendsSucceed()
        tickAndWaitForDeliveryAttempts(INITIAL_DELAY_MS + 1, 2)
        assertEquals(4, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `burst of already queued payloads will result in only one request attempt if it results in connection blockage`() {
        // Blocking the delivery executor means all the cached payloads will be queued by the scheduling executor
        // without a chance for the first request to trigger a block. This simulates a burst of undelivered payloads
        // being queued up at SDK startup, which should result in just one failed request
        blockConnection()
        waitForResurrectionAndDeliveryAttempt(2)
        assertEquals(1, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
    }

    @Test
    fun `connection attempt retried in an exponentially backed off manner`() {
        disconnect()
        waitForResurrectionAndDeliveryAttempt(2)
        var delay = INITIAL_DELAY_MS
        repeat(10) { iteration ->
            tickAndWaitForDeliveryAttempts(delay)
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
        disconnect()
        waitForResurrectionAndDeliveryAttempt(2)
        allSendsSucceed()
        tickAndWaitForDeliveryAttempts(INITIAL_DELAY_MS - 1)
        assertEquals(1, executionService.sendAttempts())
        tickAndWaitForDeliveryAttempts(1, 2)
        assertEquals(3, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `new payload will not result in connection attempt if paused period has not elapsed`() {
        blockConnection()
        waitForResurrectionAndDeliveryAttempt(2)
        schedulingExecutor.moveForwardAndRunBlocked(INITIAL_DELAY_MS - 1)
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)
        waitForPayloadIntakeAndDeliveryAttempt(3)
        assertEquals(1, executionService.sendAttempts())
        assertEquals(3, storageService.storedPayloadCount())
    }

    @Test
    fun `network change after connection retry pause period elapse will trigger send`() {
        allSendsTimeout()

        // After this returns, both stored payloads should've tried, timed out and scheduled to be retried in INITIAL_DELAY_MS
        waitForResurrectionAndDeliveryAttempt(2)
        assertEquals(2, executionService.sendAttempts())

        // All subsequent requests will be unable to reach the Embrace server
        blockConnection()

        // Moving the clock forward to when a retry should be invoked
        // first request will fail to connect and lock the delivery layer by INITIAL_DELAY_MS
        // second request not execute
        // both requests will be retried when the connection is unblocked
        tickAndWaitForDeliveryAttempts(INITIAL_DELAY_MS, 2)
        assertEquals(3, executionService.sendAttempts())

        // Subsequent payloads will succeed
        allSendsSucceed()

        // Moving the time before to just before the connection blockage will not cause any retries
        tickAndWaitForDeliveryAttempts(INITIAL_DELAY_MS - 1, 2)
        assertEquals(3, executionService.sendAttempts())

        // The connection changing will reset the connection blockage and make the previously blocked requests run and succeed
        triggerSwitchToWifi()
        waitPayloadSendAttempt(2)
        assertEquals(5, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `non-crash payloads held until resurrection completes`() {
        waitForPayloadIntakeAndDeliveryAttempt(2)
        assertEquals(0, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
        waitForResurrectionAndDeliveryAttempt(2)
        assertEquals(2, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `crash payloads delivered before resurrection`() {
        waitForPayloadIntakeAndDeliveryAttempt(2)
        storageService.addFakePayload(fakeCrashStoredTelemetryMetadata)
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)
        assertEquals(4, storageService.storedPayloadCount())
        waitForPayloadIntakeAndDeliveryAttempt(4)
        assertEquals(1, executionService.sendAttempts())
        assertEquals(3, storageService.storedPayloadCount())
    }

    @Test
    fun `current session waits for resurrected sessions`() {
        // Store a session and attempt to deliver it before resurrection
        storageService.clearStorage()
        val newerPayload = Envelope(data = SessionPayload(spans = listOf(Span(name = "newer"))))
        storageService.addPayload(fakeSessionStoredTelemetryMetadata2, newerPayload)
        waitForPayloadIntakeAndDeliveryAttempt()
        assertEquals(0, executionService.sendAttempts())

        // Simulate resurrection completing and adding an older session
        val olderPayload = Envelope(data = SessionPayload(spans = listOf(Span(name = "older"))))
        storageService.addPayload(fakeSessionStoredTelemetryMetadata, olderPayload)
        waitForResurrectionAndDeliveryAttempt(2)

        // Both should now be delivered, with the older session first (priority ordering)
        val requests = executionService.getRequests<SessionPayload>()
        assertEquals(2, requests.size)
        assertEquals("older", requests[0].data.spans?.single()?.name)
        assertEquals("newer", requests[1].data.spans?.single()?.name)
    }

    @Test
    fun `onResurrectionComplete does nothing the next time`() {
        schedulingService.onResurrectionComplete()
        val countAfterFirst = schedulingExecutor.submitCount
        schedulingService.onResurrectionComplete()
        assertEquals(countAfterFirst, schedulingExecutor.submitCount)
    }

    @Test
    fun `session payloads are delivered in timestamp order`() {
        storageService.clearStorage()
        val s1 = Envelope(data = SessionPayload(spans = listOf(Span(name = "s1"))))
        val s2 = Envelope(data = SessionPayload(spans = listOf(Span(name = "s2"))))
        val s3 = Envelope(data = SessionPayload(spans = listOf(Span(name = "s3"))))
        // Add payloads to the queue out of order
        storageService.addPayload(fakeSessionStoredTelemetryMetadata2, s2)
        storageService.addPayload(fakeSessionStoredTelemetryMetadata, s1)
        val thirdSession = StoredTelemetryMetadata(
            timestamp = clock.now() + 20_000L,
            uuid = "c3c3c3c3-c3c3-c3c3-c3c3-c3c3c3c3c3c3",
            processIdentifier = "8115ec91-3e5e-4d8a-816d-cc40306f9822",
            envelopeType = SupportedEnvelopeType.SESSION,
            payloadType = PayloadType.SESSION,
        )
        storageService.addPayload(thirdSession, s3)
        waitForResurrectionAndDeliveryAttempt(3)
        val requests = executionService.getRequests<SessionPayload>()
        assertEquals(3, requests.size)
        assertEquals("s1", requests[0].data.spans?.single()?.name)
        assertEquals("s2", requests[1].data.spans?.single()?.name)
        assertEquals("s3", requests[2].data.spans?.single()?.name)
    }

    @Test
    fun `retryable failure keeps delivery blocked for payloads of that type with the first payload being retried`() {
        storageService.clearStorage()
        val s1 = Envelope(data = SessionPayload(spans = listOf(Span(name = "first"))))
        val s2 = Envelope(data = SessionPayload(spans = listOf(Span(name = "second"))))
        storageService.addPayload(fakeSessionStoredTelemetryMetadata, s1)
        storageService.addPayload(fakeSessionStoredTelemetryMetadata2, s2)
        allSendsFail()
        waitForResurrectionAndDeliveryAttempt(2)
        assertEquals(1, executionService.sendAttempts())
        assertEquals("first", executionService.getRequests<SessionPayload>().single().data.spans?.single()?.name)
        tickAndWaitForDeliveryAttempts(INITIAL_DELAY_MS)
        assertEquals(2, executionService.sendAttempts())
        tickAndWaitForDeliveryAttempts((INITIAL_DELAY_MS * 2) - 1)
        assertEquals(2, executionService.sendAttempts())
        tickAndWaitForDeliveryAttempts(1)
        assertEquals(3, executionService.sendAttempts())
        val currentPayloads = executionService.getRequests<SessionPayload>()
        assertEquals(currentPayloads[0], currentPayloads[1])
        assertEquals(currentPayloads[0], currentPayloads[2])
        allSendsSucceed()
        tickAndWaitForDeliveryAttempts(INITIAL_DELAY_MS * 4, 2)
        val requests = executionService.getRequests<SessionPayload>()
        assertEquals(5, requests.size)
        assertEquals("first", requests[3].data.spans?.single()?.name)
        assertEquals("second", requests[4].data.spans?.single()?.name)
    }

    @Test
    fun `request failures that will not retry will not block subsequent payloads`() {
        storageService.clearStorage()
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata)
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata2)

        // This makes the session not be retried
        allRequestsTooLarge()
        waitForResurrectionAndDeliveryAttempt(2)

        // Both sessions attempted to be sent but will be deleted instead
        assertEquals(2, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `different payload types will not block each other`() {
        storageService.clearStorage()
        storageService.addFakePayload(fakeCrashStoredTelemetryMetadata)
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata)
        allSendsFail()
        waitForResurrectionAndDeliveryAttempt(2)
        // Both payloads will be sent because they are of different types
        assertEquals(2, executionService.sendAttempts())
        allSendsSucceed()
        val fakeCrashPayload = Envelope(data = LogPayload(logs = listOf(Log(body = "crash"))))
        storageService.addPayload(fakeLogStoredTelemetryMetadata, fakeCrashPayload)
        waitForPayloadIntakeAndDeliveryAttempt(3)
        // While the to-be retried payloads are blocked, a new payload of a different type can be sent
        assertEquals(3, executionService.sendAttempts())
        assertEquals(fakeCrashPayload, executionService.getRequests<LogPayload>().last())
    }

    @Test
    fun `debounce coalesces multiple onPayloadIntake calls into one scheduling task`() {
        // onPayloadIntake should not queue work if resurrection has not completed yet
        repeat(10) { schedulingService.onPayloadIntake() }
        assertEquals(0, executionService.sendAttempts())

        // Once resurrection completes, delivery starts. Subsequent rapid intakes are coalesced.
        schedulingService.onResurrectionComplete()
        val countAfterResurrection = schedulingExecutor.submitCount
        repeat(100) { schedulingService.onPayloadIntake() }
        // At most 1 additional submit from the debounce (could be 0 if the flag is still set)
        assertTrue(schedulingExecutor.submitCount <= countAfterResurrection + 1)
    }

    @Test
    fun `network change to reachable triggers delivery via scheduling worker`() {
        networkConnectivityService.connectivityStatus = NetworkStatus.NOT_REACHABLE.toConnectivityStatus()
        waitForResurrectionAndDeliveryAttempt(2)
        assertEquals(0, executionService.sendAttempts())
        // Switch to wifi — should submit unblock + delivery to the scheduling worker
        networkConnectivityService.connectivityStatus = NetworkStatus.WIFI.toConnectivityStatus()
        waitForPayloadIntakeAndDeliveryAttempt(2)
        assertEquals(2, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `network change while connection blocked unblocks and delivers`() {
        blockConnection()
        waitForResurrectionAndDeliveryAttempt(2)
        assertEquals(1, executionService.sendAttempts())
        assertEquals(2, storageService.storedPayloadCount())
        // Connection is now blocked. A network status change (even reachable→reachable)
        // should unblock and allow delivery of payloads not in retry cooldown.
        allSendsSucceed()
        networkConnectivityService.connectivityStatus = NetworkStatus.WAN.toConnectivityStatus()
        // Process the unblock task and its chained queueNextPayload
        schedulingExecutor.runCurrentlyBlocked()
        deliveryExecutor.awaitExecutionCompletion()
        // The log was never attempted (blocked before it could be sent), so it delivers immediately.
        // The session is still in retry cooldown.
        waitPayloadSendAttempt(1)
        assertEquals(2, executionService.sendAttempts())
        assertEquals(1, storageService.storedPayloadCount())
        // After the retry delay, the session is also delivered
        tickAndWaitForDeliveryAttempts(INITIAL_DELAY_MS + 1)
        assertEquals(3, executionService.sendAttempts())
        assertEquals(0, storageService.storedPayloadCount())
    }

    @Test
    fun `processDeliveryResult chains inline on scheduling worker`() {
        // Verify that after a successful delivery, the next payload is found and
        // sent without requiring a separate intake signal
        storageService.clearStorage()
        val s1 = Envelope(data = SessionPayload(spans = listOf(Span(name = "first"))))
        val s2 = Envelope(data = SessionPayload(spans = listOf(Span(name = "second"))))
        val s3 = Envelope(data = SessionPayload(spans = listOf(Span(name = "third"))))
        storageService.addPayload(fakeSessionStoredTelemetryMetadata, s1)
        storageService.addPayload(fakeSessionStoredTelemetryMetadata2, s2)
        val thirdSession = StoredTelemetryMetadata(
            timestamp = clock.now() + 20_000L,
            uuid = "c3c3c3c3-c3c3-c3c3-c3c3-c3c3c3c3c3c3",
            processIdentifier = "8115ec91-3e5e-4d8a-816d-cc40306f9822",
            envelopeType = SupportedEnvelopeType.SESSION,
            payloadType = PayloadType.SESSION,
        )
        storageService.addPayload(thirdSession, s3)

        // Trigger ONE delivery. The chain should deliver all 3 sequentially
        // without any additional onPayloadIntake calls.
        waitForResurrectionAndDeliveryAttempt(3)

        val requests = executionService.getRequests<SessionPayload>()
        assertEquals(3, requests.size)
        assertEquals("first", requests[0].data.spans?.single()?.name)
        assertEquals("second", requests[1].data.spans?.single()?.name)
        assertEquals("third", requests[2].data.spans?.single()?.name)
    }

    @Test(expected = RejectedExecutionException::class)
    fun `test shutdown`() {
        logger.throwOnInternalError = false
        schedulingService.onResurrectionComplete()
        schedulingService.shutdown()

        // Throws RejectedExecutionException. Note that this is a consequence of the
        // test setup & the real executor has its own rejection handler,
        // meaning the exception will never get thrown in prod.
        schedulingService.onPayloadIntake()
    }

    /**
     * Wait for payloads in storage to be processed and attempted to be delivered
     */
    private fun waitForPayloadIntakeAndDeliveryAttempt(expectedPayloads: Int = 1) {
        waitForPayloadIntake()
        waitPayloadSendAttempt(expectedPayloads)
    }

    /**
     * Wait for resurrection to complete and all the payloads to be processed and delivery attempted
     */
    private fun waitForResurrectionAndDeliveryAttempt(expectedPayloads: Int = 1) {
        waitForResurrection()
        waitPayloadSendAttempt(expectedPayloads)
    }

    /**
     * Wait for payloads in storage to be processed and scheduled for delivery
     */
    private fun waitForPayloadIntake() {
        schedulingService.onPayloadIntake()
        schedulingExecutor.awaitExecutionCompletion()
    }

    /**
     * Wait for resurrection to complete and for the payloads to be scheduled
     */
    private fun waitForResurrection() {
        schedulingService.onResurrectionComplete()
        schedulingExecutor.awaitExecutionCompletion()
    }

    /**
     * Advance the clock by [delayMs] and wait for the expected number of payloads to be scheduled and delivered
     */
    private fun tickAndWaitForDeliveryAttempts(delayMs: Long, expectedPayloads: Int = 1) {
        schedulingExecutor.moveForwardAndRunBlocked(delayMs)
        deliveryExecutor.awaitExecutionCompletion()
        waitPayloadSendAttempt(expectedPayloads)
    }

    /**
     * Simulate the network being changed to wifi and allow all callbacks to be invoked and processed
     */
    private fun triggerSwitchToWifi() {
        networkConnectivityService.connectivityStatus = NetworkStatus.WIFI.toConnectivityStatus()
        schedulingExecutor.runCurrentlyBlocked()
        deliveryExecutor.awaitExecutionCompletion()
    }

    private fun waitPayloadSendAttempt(expectedPayloads: Int = 1) {
        repeat(expectedPayloads) {
            // Scheduling worker submitting job to delivery worker
            schedulingExecutor.awaitExecutionCompletion()
            // Delivery worker executing request and submitting results for scheduling worker to process
            deliveryExecutor.awaitExecutionCompletion()
            // Scheduling worker processing the execution result and scheduling the next delivery and retry
            schedulingExecutor.awaitExecutionCompletion()
        }
    }

    /**
     * Reset storage to a single session payload, discarding any payloads from the default setup.
     */
    private fun resetToSingleSessionPayload() {
        storageService.clearStorage()
        storageService.addFakePayload(fakeSessionStoredTelemetryMetadata)
    }

    private fun allSendsSucceed() = setExecutionResult(ExecutionResult.Success)

    private fun allSendsFail() = setExecutionResult(Failure(code = 500))

    private fun allSendsTimeout() = setExecutionResult(Incomplete(SocketTimeoutException(), true))

    private fun allRequestsTooLarge() = setExecutionResult(ExecutionResult.PayloadTooLarge)

    private fun serverBusy(endpoint: Endpoint, retryAfterMs: Long) =
        setExecutionResult(ExecutionResult.TooManyRequests(endpoint, retryAfterMs))

    private fun disconnect() = setExecutionResult(Incomplete(ConnectException(), true))

    private fun blockConnection() = setExecutionResult(Incomplete(UnknownHostException(), true))

    private fun setExecutionResult(result: ExecutionResult) {
        executionService.constantResponse = result
    }

    private companion object {
        const val SHORT_BLOCKED_DURATION = 30_000L
    }
}
