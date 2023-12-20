package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.pow

internal enum class Endpoint(val path: String) {
    EVENTS("events"),
    BLOBS("blobs"),
    LOGGING("logging"),
    NETWORK("network"),
    SESSIONS("sessions"),
    UNKNOWN("unknown");

    var isRateLimited = false
        private set

    var rateLimitRetryCount = 0
        private set

    fun setRateLimited() {
        isRateLimited = true
        rateLimitRetryCount++
    }

    fun clearRateLimit() {
        isRateLimited = false
        rateLimitRetryCount = 0
    }

    /**
     * Schedules a task to execute the api calls ofter the given retry after time
     * or the exponential backoff delay calculated from the number of retries.
     */
    fun scheduleRetry(
        scheduledExecutorService: ScheduledExecutorService,
        retryAfter: Long?,
        retryMethod: () -> Unit
    ) {
        synchronized(this) {
            try {
                val retryTask = Runnable {
                    retryMethod()
                }
                val delay = calculateDelay(retryAfter)
                scheduledExecutorService.schedule(retryTask, delay, TimeUnit.SECONDS)
            } catch (e: RejectedExecutionException) {
                InternalStaticEmbraceLogger.logger.logError(
                    "Cannot schedule clear rate limit failed calls.",
                    e
                )
            }
        }
    }

    /**
     * Calculates the delay for the retry task.
     * If the retryAfter is not null, it will use that value.
     * Otherwise, it will calculate the delay using exponential backoff.
     */
    private fun calculateDelay(retryAfter: Long?): Long {
        return if (retryAfter != null) {
            retryAfter
        } else {
            val base = 3.0
            base.pow(rateLimitRetryCount).toLong()
        }
    }
}
