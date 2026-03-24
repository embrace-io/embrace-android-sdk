package io.embrace.android.embracesdk.internal.delivery.scheduling

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.execution.ExecutionResult
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.delivery.storedTelemetryComparator
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.io.InputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class SchedulingServiceImpl(
    private val storageService: PayloadStorageService,
    private val executionService: RequestExecutionService,
    private val schedulingWorker: BackgroundWorker,
    private val deliveryWorker: BackgroundWorker,
    private val clock: Clock,
    private val logger: InternalLogger,
    private val deliveryTracer: DeliveryTracer? = null,
) : SchedulingService {

    private val blockedEndpoints: MutableMap<Endpoint, Long> = ConcurrentHashMap()
    private val connectionStatus = ConnectionStatus(clock)
    private val activeSends: MutableSet<StoredTelemetryMetadata> = Collections.newSetFromMap(ConcurrentHashMap())
    private val deleteInProgress: MutableSet<StoredTelemetryMetadata> = Collections.newSetFromMap(ConcurrentHashMap())
    private val payloadsToRetry: MutableMap<StoredTelemetryMetadata, RetryInstance> = ConcurrentHashMap()
    private val resurrectionComplete = AtomicBoolean(false)
    private val payloadsInProgress = ConcurrentHashMap<SupportedEnvelopeType, StoredTelemetryMetadata>()
    private val intakeDeliveryPending = AtomicBoolean(false)
    private val scheduledDeliveryAttempt: ScheduledDeliveryAttempt = ScheduledDeliveryAttempt(
        clock = clock,
        scheduleAction = ::queueDeliveryAttempt,
        worker = schedulingWorker
    )

    override fun onPayloadIntake() {
        // We only need to schedule a delivery queuing if one wasn't already scheduled.
        // If one were scheduled, it will trigger a new delivery when it's complete.
        if (intakeDeliveryPending.compareAndSet(false, true)) {
            schedulingWorker.submit {
                intakeDeliveryPending.set(false)
                findAndDeliverNextPayload()
            }
        }
    }

    override fun onResurrectionComplete() {
        if (!resurrectionComplete.getAndSet(true)) {
            queueDeliveryAttempt()
        }
    }

    override fun shutdown() {
        // shutdown workers from further scheduling but don't wait for completion as
        // we can just retry in the next process
        schedulingWorker.shutdownAndWait(0)
        deliveryWorker.shutdownAndWait(0)
    }

    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
        val networkAcquired = connectionStatus.updateNetworkStatus(status.isReachable)
        // Schedule an unblock of the connection and schedule a delivery if we have recently connected
        // to a potentially connected network OR if the connection was previously blocked
        if (networkAcquired || connectionStatus.isBlocked()) {
            schedulingWorker.submit {
                connectionStatus.unblock()
                queueDeliveryAttempt()
            }
        }
    }

    /**
     * Schedule a delivery attempt at the given time. If the time is now or in the past, schedule the attempt to run now.
     */
    private fun scheduleFutureDeliveryAttempt(timestampMs: Long) {
        if (timestampMs <= clock.now()) {
            queueDeliveryAttempt()
        } else {
            scheduledDeliveryAttempt.update(timestampMs)
        }
    }

    /**
     * Schedule a delivery attempt for when the connection is expected to be unblocked. If an attempt has already been scheduled,
     * update the time to the unblocking time if it's earlier than the currently scheduled time.
     */
    private fun scheduleDeliveryAtUnblocking() = scheduleFutureDeliveryAttempt(connectionStatus.getUnblockTime())

    /**
     * Start delivery on the scheduler thread. Basically, this runs [findAndDeliverNextPayload] on the appropriate thread,
     * so it can be called anywhere, whereas [findAndDeliverNextPayload] has to be called on the scheduling thread.
     */
    private fun queueDeliveryAttempt() {
        deliveryTracer?.onQueueDeliveryAttempt()
        schedulingWorker.submit {
            findAndDeliverNextPayload()

            // Reschedule a unblocking attempt if the connection is still blocked
            if (connectionStatus.isBlocked()) {
                scheduleDeliveryAtUnblocking()
            }
        }
    }

    /**
     * Find the next eligible payload with the highest priority and attempt to deliver it if the connection is ready.
     */
    private fun findAndDeliverNextPayload() {
        try {
            connectionStatus.unblockIfWaitTimeExceeded(clock.now())
            if (connectionStatus.ready()) {
                findNextPayload()?.let { payload ->
                    payload.envelopeType.endpoint.updateBlockedEndpoint()
                    payloadsInProgress[payload.envelopeType] = payload
                    executeDelivery(payload)
                }
            }
        } catch (t: Throwable) {
            logger.trackInternalError(InternalErrorType.DELIVERY_SCHEDULING_FAIL, t)
        }
    }

    private fun findNextPayload(): StoredTelemetryMetadata? {
        val payloadsByPriority = storageService.getPayloadsByPriority()
        val payloadToSend = payloadsByPriority
            .filter { !connectionStatus.isPayloadBlocked(it) && it.eligibleForSending() }
            .sortedWith(storedTelemetryComparator)
            .firstOrNull()
        deliveryTracer?.onFindNextPayload(
            payloadsByPriority,
            payloadToSend
        )
        return payloadToSend
    }

    /**
     * Attempt payload delivery on worker thread. Handle connection blocking up execution attempt completing, but defer to the scheduling
     * worker to handle further scheduling.
     */
    private fun executeDelivery(payload: StoredTelemetryMetadata) {
        deliveryTracer?.onExecuteDelivery(payload)
        activeSends.add(payload)
        deliveryWorker.submit {
            // Do not execute if the network isn't ready
            val result: ExecutionResult = if (!connectionStatus.ready()) {
                ExecutionResult.NetworkNotReady
            } else {
                try {
                    payload.toStream()?.run {
                        executionService.attemptHttpRequest(
                            payloadStream = { this },
                            envelopeType = payload.envelopeType,
                            payloadType = payload.payloadTypesHeader
                        )
                    } ?: ExecutionResult.NotAttempted
                } catch (t: Throwable) {
                    logger.trackInternalError(InternalErrorType.DELIVERY_SCHEDULING_FAIL, t)
                    ExecutionResult.Incomplete(exception = t, retry = false)
                }
            }

            // Block the connection right away to prevent future delivery if the request just run dictates that
            if (result.failedToConnect()) {
                connectionStatus.block()
            }

            // Allow the scheduling thread to process the execution results
            schedulingWorker.submit {
                deliveryTracer?.onProcessingDeliveryResult(payload, result)
                result.processDeliveryResult(payload)
            }
        }
    }

    /**
     * Schedule future deliveries based on the latest delivery execution
     */
    private fun ExecutionResult.processDeliveryResult(
        payload: StoredTelemetryMetadata,
    ) {
        // If the request failed because the SDK cannot reach the Embrace server, schedule a delivery for when the connection unblocks.
        // At that time, the scheduler will determine what that payload is.
        if (failedToConnect()) {
            scheduleDeliveryAtUnblocking()
        } else if (connectedToServer()) {
            // If the request successfully connected to Embrace, regardless if it succeeded, validate the connection
            connectionStatus.connectionValidated()
        }

        // If a request either failed to connect to Embrace or execution was not attempted because the network was not ready,
        // notify the connection, which will either block it or extend its retry time due to consecutive failures.
        if (failedToConnect() || this is ExecutionResult.NetworkNotReady) {
            connectionStatus.payloadBlocked(payload)
        } else if (!shouldRetry) {
            // If the response is such that we should not ever retry the delivery of this payload,
            // delete it from both the in memory retry payloads map and on disk
            payloadsToRetry.remove(payload)
            deleteInProgress.add(payload)
            storageService.delete(payload) {
                // Remove delete flag on scheduling thread once the storage system finishes deletion
                // Doing it prematurely leads to a to-be-deleted payload seeming like it's eligible to be sent.
                // Trying to do so will lead to a failure when we attempt to do so, but the payload is not found
                schedulingWorker.submit {
                    deleteInProgress.remove(payload)
                }
            }
        } else {
            // If delivery of this payload should be retried, add or replace the entry in the retry map
            // with the new values for how many times it has failed, and when the next retry should happen
            val retryAttempts = payloadsToRetry[payload]?.failedAttempts ?: 0
            val nextRetryTimeMs = if (this is ExecutionResult.TooManyRequests && retryAfterMs != null) {
                val unblockedTimestampMs = clock.now() + retryAfterMs
                blockedEndpoints[endpoint] = unblockedTimestampMs
                unblockedTimestampMs + 1L
            } else {
                clock.calculateNextRetryTime(retryAttempts = retryAttempts)
            }

            payloadsToRetry[payload] = RetryInstance(
                failedAttempts = retryAttempts + 1,
                nextRetryTimeMs = nextRetryTimeMs
            )
        }

        // Any payload that will not be retried is either sent or deleted, so it should no longer considered to be in progress.
        // If it should be retried, leave the payload as being in progress so no other payloads of the same type will be scheduled.
        if (!shouldRetry) {
            payloadsInProgress.remove(payload.envelopeType)
        }

        /**
         * Always try to send the next payload and schedule the next retry or connection unblocking, whichever happens later.
         * This is fulfilling the promise made in [onPayloadIntake] so that any debounced intake will not result in a
         * new payload intake being ignored.
         */
        findAndDeliverNextPayload()
        payloadsToRetry.values.minOfOrNull { it.nextRetryTimeMs }?.let { retryTimeMs ->
            scheduleFutureDeliveryAttempt(maxOf(retryTimeMs, connectionStatus.getUnblockTime()))
        }
        activeSends.remove(payload)
    }

    /**
     * Whether the given payload is eligible to be delivered based on the state of the connection, delivery, and undelivered payloads.
     */
    private fun StoredTelemetryMetadata.eligibleForSending(): Boolean {
        // determine if the given payload is eligible to be sent
        // i.e. not already being sent, endpoint not blocked by 429, and isn't waiting to be retried
        if (activeSends.contains(this) || deleteInProgress.contains(this)) {
            return false
        }

        if (isEndpointBlocked()) {
            return false
        }

        if (envelopeType != SupportedEnvelopeType.CRASH && !resurrectionComplete.get()) {
            return false
        }

        val activePayload = payloadsInProgress[envelopeType]
        if (activePayload != null && activePayload != this) {
            return false
        }

        return payloadsToRetry[this]?.run {
            clock.now() >= nextRetryTimeMs
        } ?: true
    }

    private fun StoredTelemetryMetadata.toStream(): InputStream? = storageService.loadPayloadAsStream(this)

    private fun StoredTelemetryMetadata.isEndpointBlocked(): Boolean =
        blockedEndpoints[envelopeType.endpoint]?.let { timestampMs ->
            timestampMs > clock.now()
        } ?: false

    private fun Endpoint.updateBlockedEndpoint() {
        blockedEndpoints[this]?.let {
            if (it <= clock.now()) {
                blockedEndpoints.remove(this)
            }
        }
    }

    private fun ExecutionResult.failedToConnect(): Boolean =
        this is ExecutionResult.Incomplete && (connectionBlockingExceptions.contains(exception.javaClass))

    private fun ExecutionResult.connectedToServer(): Boolean =
        this !is ExecutionResult.NetworkNotReady && this !is ExecutionResult.NotAttempted

    private data class RetryInstance(
        val failedAttempts: Int,
        val nextRetryTimeMs: Long,
    )

    /**
     * Encapsulates the status of the network connection
     */
    private class ConnectionStatus(
        private val clock: Clock,
    ) {
        /**
         * A network connection is considered not blocked if this time is not 0.
         * The use of [isBlocked] internally as the solo method to check this should assure that.
         */
        private val connectionUnblockTime = AtomicLong(0L)
        private val hasNetworkConnection = AtomicBoolean(true)
        private val blockedPayloads: MutableSet<StoredTelemetryMetadata> = Collections.newSetFromMap(ConcurrentHashMap())
        private var consecutiveFailures = AtomicInteger(0)

        /**
         * Return true if we went from not having a network connection to having one
         *
         * Called on main thread
         */
        fun updateNetworkStatus(newStatus: Boolean): Boolean =
            if (!hasNetworkConnection.getAndSet(newStatus)) {
                newStatus
            } else {
                false
            }

        /**
         * Connection is ready to be used.
         *
         * Called on the scheduling thread for authoritative checks of network readiness, and on the delivery thread to only attempt
         * delivery if it could possibly succeed.
         */
        fun ready(): Boolean = hasNetworkConnection.get() && !isBlocked()

        /**
         * Block connection. If already blocked, the unblock time will be updated, which will be returned.
         *
         * Called on the delivery thread and must be synchronized with [unblock] which happens on the scheduling thread.
         */
        fun block(): Long {
            synchronized(connectionUnblockTime) {
                return clock.calculateNextRetryTime(consecutiveFailures.getAndIncrement()).apply {
                    connectionUnblockTime.set(this)
                }
            }
        }

        /**
         * Unblock connection if currently blocked.
         *
         * Only called on the scheduling thread. Must be synchronized with [block] which happens on the delivery thread
         */
        fun unblock() {
            synchronized(connectionUnblockTime) {
                if (isBlocked()) {
                    connectionUnblockTime.set(0L)
                    blockedPayloads.clear()
                }
            }
        }

        /**
         * Unblock the connection if the time period of blockage has elapsed.
         *
         * Only called on the scheduling thread.
         */
        fun unblockIfWaitTimeExceeded(currentTime: Long) {
            if (isBlocked() && currentTime >= connectionUnblockTime.get()) {
                unblock()
            }
        }

        /**
         * Returns the time at which the connection will be unblocked, or 0 if not currently blocked.
         *
         * Only called on the scheduling thread.
         */
        fun getUnblockTime(): Long = connectionUnblockTime.get()

        /**
         * Track a payload that was prevented from being sent because the connection is blocked.
         *
         * Only called on the scheduling thread.
         */
        fun payloadBlocked(payload: StoredTelemetryMetadata) {
            blockedPayloads.add(payload)
        }

        /**
         * Returns true if the payload was prevented from being sent because the connection is blocked.
         *
         * Only called on the scheduling thread.
         */
        fun isPayloadBlocked(
            payload: StoredTelemetryMetadata,
        ): Boolean = blockedPayloads.contains(payload) && isBlocked()

        /**
         * Validate that the Embrace server can be contacted, i.e. the next failure to connect is not the retrying of a blocked connection
         */
        fun connectionValidated() {
            if (consecutiveFailures.get() > 0) {
                consecutiveFailures.set(0)
            }
        }

        /**
         * Returns true if the connection is currently blocked.
         *
         * Called from both the scheduling and delivery threads, as well as the thread that handles network change listeners
         */
        fun isBlocked(): Boolean = connectionUnblockTime.get() > 0
    }

    /**
     * Manage the single scheduled future delivery attempt in the delivery layer
     */
    private class ScheduledDeliveryAttempt(
        private val clock: Clock,
        private val scheduleAction: () -> Unit,
        private val worker: BackgroundWorker,
    ) {
        private var scheduledTime: Long? = null
        private var scheduledTask: ScheduledFuture<*>? = null

        /**
         * Update the scheduled future delivery attempt based on the requested time.
         * If there is no scheduled attempt or the new requested time is earlier than the scheduled time,
         * cancel the existing task and schedule a new task for the earlier time.
         */
        fun update(requestedDeliveryAttemptTimeMs: Long) {
            val unblockAttemptRuntimeMs = scheduledTime
            if (unblockAttemptRuntimeMs == null || requestedDeliveryAttemptTimeMs < unblockAttemptRuntimeMs) {
                scheduledTask?.cancel(false)
                scheduledTask = worker.schedule<Unit>(
                    {
                        scheduledTask = null
                        scheduledTime = null
                        scheduleAction()
                    },
                    requestedDeliveryAttemptTimeMs - clock.now(),
                    TimeUnit.MILLISECONDS
                )
                scheduledTime = requestedDeliveryAttemptTimeMs
            }
        }
    }

    companion object {
        const val INITIAL_DELAY_MS = 15_000L

        /**
         * The set of exceptions thrown during network request execution which consider to be likely unrecoverable without a new
         * network connection.
         */
        internal val connectionBlockingExceptions: Set<Class<out Throwable>> =
            setOf(
                UnknownHostException::class.java,
                ConnectException::class.java,
                SSLHandshakeException::class.java,
                SSLPeerUnverifiedException::class.java,
                NoRouteToHostException::class.java
            )

        /**
         * Note: bit-shifting is used to raise 2 to the power of [retryAttempts]. This is the most efficient way of
         * doing this, and as much as it pains me to do this, it's isolated and tested, and the runtime penalty, however
         * tiny, is not worth incurring if we can instead do this.
         */
        internal fun Clock.calculateNextRetryTime(
            retryAttempts: Int,
        ): Long = now() + (INITIAL_DELAY_MS * (1 shl retryAttempts))
    }
}
