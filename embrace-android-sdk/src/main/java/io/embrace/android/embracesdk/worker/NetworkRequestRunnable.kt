package io.embrace.android.embracesdk.worker

import io.embrace.android.embracesdk.comms.api.ApiRequest
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

private val counter = AtomicInteger(0)

/**
 * Marks a [Runnable] as a session request. This is used to prioritise session requests.
 */
internal class NetworkRequestRunnable(
    val request: ApiRequest,
    val base: Runnable,
) : Runnable by base {

    /**
     * The ordinal of this request. This is used to prioritise requests FIFO.
     */
    val ordinal = counter.incrementAndGet()
}

/**
 * Creates a [PriorityBlockingQueue] for the network request executor. The queue prioritises
 * sending sessions first rather than other network requests. This should improve
 * their deliverability when the queue is saturated.
 */
internal fun createNetworkRequestQueue(): PriorityBlockingQueue<NetworkRequestRunnable> {
    return PriorityBlockingQueue(
        100,
        compareBy<NetworkRequestRunnable> { runnable ->
            when {
                runnable.request.isSessionRequest() -> -1
                else -> 1
            }
        }.thenBy { runnable ->
            runnable.ordinal.toInt()
        }
    )
}
