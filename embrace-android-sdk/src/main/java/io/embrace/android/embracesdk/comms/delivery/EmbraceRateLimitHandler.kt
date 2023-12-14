package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.EmbraceApiService.Companion.Endpoint
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.pow

/**
 * Handles rate limit responses from the server and schedules the retry of the api calls.
 * The retry is scheduled after the given retry after time or the exponential backoff delay
 * calculated from the number of retries.
 */
internal class EmbraceRateLimitHandler(
    private val scheduledExecutorService: ScheduledExecutorService,
) : RateLimitHandler {

    /**
     * A map containing the number of retries for each endpoint.
     */
    private val rateLimitMap = ConcurrentHashMap<Endpoint, Int>()

    /**
     * Sets the rate limit for the given endpoint and schedules a task to execute the api calls ofter
     * the given retry after time or the exponential backoff delay calculated from the number of retries.
     */
    override fun setRateLimitAndScheduleRetry(
        endpoint: Endpoint,
        retryAfter: Long?,
        retryMethod: () -> Unit,
    ) {
        synchronized(this) {
            rateLimitMap[endpoint] = rateLimitMap[endpoint]?.plus(1) ?: 1
            rateLimitMap[endpoint]?.let { retries ->
                scheduleRetryTask(retryAfter, retries, retryMethod)
            }
        }
    }

    /**
     * Returns true if the given endpoint is rate limited.
     */
    override fun isRateLimited(endpoint: Endpoint): Boolean {
        return rateLimitMap.containsKey(endpoint)
    }

    /**
     * Clears the rate limit for the given endpoint.
     */
    override fun clearRateLimit(endpoint: Endpoint) {
        synchronized(this) {
            if (rateLimitMap.containsKey(endpoint)) {
                rateLimitMap.remove(endpoint)
            }
        }
    }

    /**
     * Schedules a task to execute the api calls again after the given retry after time or
     * the exponential backoff delay calculated from the number of retries.
     */
    private fun scheduleRetryTask(
        retryAfter: Long?,
        retries: Int,
        retryMethod: () -> Unit,
    ) {
        try {
            val retryTask = Runnable {
                retryMethod()
            }
            val delay = calculateDelay(retryAfter, retries)
            scheduledExecutorService.schedule(retryTask, delay, TimeUnit.SECONDS)
        } catch (e: RejectedExecutionException) {
            InternalStaticEmbraceLogger.logger.logError(
                "Cannot schedule clear rate limit failed calls.",
                e
            )
        }
    }

    /**
     * Calculates the delay for the retry task.
     * If the retryAfter is not null, it will use that value.
     * Otherwise, it will calculate the delay using exponential backoff.
     */
    private fun calculateDelay(retryAfter: Long?, retries: Int): Long {
        return if (retryAfter != null) {
            retryAfter
        } else {
            val base = 3.0
            base.pow(retries).toLong()
        }
    }
}
