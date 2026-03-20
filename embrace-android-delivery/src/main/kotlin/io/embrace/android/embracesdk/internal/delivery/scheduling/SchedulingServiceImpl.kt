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
import java.net.UnknownHostException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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

    override fun onPayloadIntake() {
        startDeliveryLoop()
    }

    override fun onResurrectionComplete() {
        if (!resurrectionComplete.getAndSet(true)) {
            startDeliveryLoop()
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
        val wasBlocked = connectionStatus.isBlocked()
        // Trigger a new delivery loop we potentially went from offline to online, but do the unblocking on the delivery thread
        // so it runs after the currently queued up requests execute. This prevents a race between the network change
        // handling logic (which could be late) from unblocking during a delivery burst.
        // If it is late and processing the change that triggered the connection block, it will erroneously unblock and cause a failure,
        // If the unblocking is legit, the burst will just be skipped together and then retried by the unblock.
        if (wasBlocked || networkAcquired) {
            deliveryWorker.submit {
                connectionStatus.unblock()
                startDeliveryLoop()
            }
        }
    }

    /**
     * Schedule a delivery loop to start at the given time
     */
    private fun scheduleDeliveryLoopStart(timestampMs: Long) {
        if (timestampMs <= clock.now()) {
            startDeliveryLoop()
        } else if (timestampMs != Long.MAX_VALUE) {
            schedulingWorker.schedule<Unit>(
                ::startDeliveryLoop,
                calculateDelay(timestampMs),
                TimeUnit.MILLISECONDS
            )
        }
    }

    /**
     * Start the delivery loop on the scheduler thread
     */
    private fun startDeliveryLoop() {
        deliveryTracer?.onStartDeliveryLoop()
        schedulingWorker.submit {
            deliveryLoop()
        }
    }

    /**
     * Loop through the payloads ready to be sent by priority and queue for delivery
     */
    private fun deliveryLoop() {
        val failedPayloads = mutableSetOf<StoredTelemetryMetadata>()
        try {
            var payload: StoredTelemetryMetadata? = findNextPayload()
            while (payload != null && connectionStatus.ready()) {
                runCatching {
                    payload.run {
                        if (eligibleForSending() && connectionStatus.ready()) {
                            envelopeType.endpoint.updateBlockedEndpoint()
                            payloadsInProgress[envelopeType] = this
                            queueDelivery(this)
                        }
                    }
                }.exceptionOrNull()?.let { error ->
                    // This block catches unhandled errors resulting a single payload failing to be queued for delivery.
                    // Any payload failed to be queued will be bypassed in the current delivery loop cycle, as the
                    // SDK encountered an error as it tried to determine whether the payload should be delivered.
                    val fileName = payload.run {
                        // Keeping track of the payloads that failed to be queued prevents an infinite loop where a
                        // payload that refused to be queued successfully keeps being retried in the same loop.
                        // So even if it isn't possible now, this code prevents makes an attempt to prevent it from
                        // happening in the future, if, say, checking the state of the SDK can throw indefinitely.
                        failedPayloads.add(this)
                        filename
                    }
                    logger.trackInternalError(
                        type = InternalErrorType.DELIVERY_SCHEDULING_FAIL,
                        throwable = IllegalStateException("Failed to queue payload with file name $fileName", error)
                    )
                }
                payload = findNextPayload(failedPayloads)
            }
        } catch (t: Throwable) {
            // This block catches unhandled errors resulting from the recreation of a queue of payloads to be delivered
            // When this type of error encountered, we abort the delivery loop and wait for the next retry or intake
            // to retry any pending payloads.
            logger.trackInternalError(InternalErrorType.DELIVERY_SCHEDULING_FAIL, t)
        } finally {
            // Ensure the retry scheduling runs after all the payloads have been queued.
            // Will try delivery again at the earliest retry time or when the connection is expected to unblock, whichever is greater
            deliveryWorker.submit {
                payloadsToRetry.map { it.value.nextRetryTimeMs }.minOrNull()?.let { retryTimeMs ->
                    scheduleDeliveryLoopStart(maxOf(retryTimeMs, connectionStatus.getUnblockTime()))
                }
            }
        }
    }

    private fun findNextPayload(exclude: Set<StoredTelemetryMetadata> = emptySet()): StoredTelemetryMetadata? {
        // Eagerly evaluate whether it's time to unblock the connection
        connectionStatus.attemptUnblock(clock.now())
        val payloadsByPriority = storageService.getPayloadsByPriority()
        val payloadsToSend = payloadsByPriority
            .filter { it.eligibleForSending() && !exclude.contains(it) && !connectionStatus.isPayloadBlocked(it) }
            .sortedWith(storedTelemetryComparator)
        deliveryTracer?.onPayloadQueueCreated(
            payloadsByPriority,
            payloadsToSend,
        )
        return payloadsToSend.firstOrNull()
    }

    private fun queueDelivery(payload: StoredTelemetryMetadata): Future<ExecutionResult> {
        deliveryTracer?.onPayloadEnqueued(payload)
        activeSends.add(payload)
        return deliveryWorker.submit<ExecutionResult> {
            val result: ExecutionResult = if (connectionStatus.ready()) {
                try {
                    // If fail to convert metadata to stream, we can't expect it will succeed later, so we won't retry.
                    // The storage service will log an internal exception and we move on.
                    payload.toStream()?.run {
                        executionService.attemptHttpRequest(
                            payloadStream = { this },
                            envelopeType = payload.envelopeType,
                            payloadType = payload.payloadTypesHeader
                        )
                    } ?: ExecutionResult.NotAttempted
                } catch (t: Throwable) {
                    // An unknown error occurred, not the expected exceptions during request executions
                    // These types of errors happen before we execute the request, and results in us unable to
                    // turn the stored bytes in to a request that can be executed.
                    // For this, we log the error to ensure it's not a systemic problem and move on from the payload.
                    logger.trackInternalError(InternalErrorType.DELIVERY_SCHEDULING_FAIL, t)
                    ExecutionResult.Incomplete(exception = t, retry = false)
                }
            } else {
                ExecutionResult.NetworkNotReady
            }

            deliveryTracer?.onPayloadResult(payload, result)
            result.processDeliveryResult(payload)
            activeSends.remove(payload)
            result
        }
    }

    private fun ExecutionResult.processDeliveryResult(
        payload: StoredTelemetryMetadata,
    ) {
        // If the request failed because the SDK cannot reach the Embrace server,
        // update the connection status to prevent delivery attempt
        if (failedToConnect()) {
            val nextConnectionAttemptTime = connectionStatus.block()
            scheduleDeliveryLoopStart(nextConnectionAttemptTime)
        } else if (connectedToServer()) {
            connectionStatus.connectionValidated()
        }

        if (failedToConnect() || this is ExecutionResult.NetworkNotReady) {
            connectionStatus.payloadBlocked(payload)
        } else if (!shouldRetry) {
            // If the response is such that we should not ever retry the delivery of this payload,
            // delete it from both the in memory retry payloads map and on disk
            payloadsToRetry.remove(payload)
            deleteInProgress.add(payload)
            storageService.delete(payload) {
                deleteInProgress.remove(payload)
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

        if (!shouldRetry) {
            payloadsInProgress.remove(payload.envelopeType)
            startDeliveryLoop()
        }
    }

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

    private fun calculateDelay(nextRetryTimeMs: Long): Long = nextRetryTimeMs - clock.now()

    private fun ExecutionResult.failedToConnect(): Boolean =
        this is ExecutionResult.Incomplete && (exception is UnknownHostException || exception is ConnectException)

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
         * Connection is ready to be used
         *
         * Called on scheduler and delivery threads
         */
        fun ready(): Boolean = hasNetworkConnection.get() && !isBlocked()

        /**
         * Block connection. If already blocked, the unblock time will be updated, which will be returned.
         *
         * Only called on delivery thread
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
         * Called on delivery and scheduler threads
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
         * Unblock the connection if the time period of blockage has elapsed
         *
         * Only called in the scheduler thread. Will never be invoked concurrently with [isPayloadBlocked].
         */
        fun attemptUnblock(currentTime: Long) {
            if (shouldUnblockAtTime(currentTime)) {
                synchronized(connectionUnblockTime) {
                    if (shouldUnblockAtTime(currentTime)) {
                        unblock()
                    }
                }
            }
        }

        /**
         * Returns the time at which the connection will be unblocked, or 0 if not currently blocked.
         *
         * Called on the delivery thread
         */
        fun getUnblockTime(): Long = connectionUnblockTime.get()

        /**
         * Track a payload that was prevented from being sent because the connection is blocked
         *
         * Only called on delivery thread
         */
        fun payloadBlocked(payload: StoredTelemetryMetadata) {
            blockedPayloads.add(payload)
        }

        /**
         * Returns true if the payload was prevented from being sent because the connection is blocked
         *
         * Only called in the scheduler thread
         */
        fun isPayloadBlocked(
            payload: StoredTelemetryMetadata,
        ): Boolean = blockedPayloads.contains(payload) && isBlocked()

        fun connectionValidated() {
            if (consecutiveFailures.get() > 0) {
                consecutiveFailures.set(0)
            }
        }

        private fun shouldUnblockAtTime(time: Long): Boolean = isBlocked() && time >= connectionUnblockTime.get()

        fun isBlocked(): Boolean = connectionUnblockTime.get() > 0
    }

    companion object {
        const val INITIAL_DELAY_MS = 15_000L

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
