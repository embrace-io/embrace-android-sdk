package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.internal.worker.ScheduledWorker
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow

internal enum class Endpoint(val path: String, val version: String) {
    EVENTS("events", "v1"),
    LOGGING("logging", "v1"),
    LOGS("logs", "v2"),
    NETWORK("network", "v1"),
    SESSIONS("sessions", "v1"),
    SESSIONS_V2("spans", "v2"),
    UNKNOWN("unknown", "v1");

    private var rateLimitRetryCount = AtomicInteger(0)

    @Volatile
    var isRateLimited = false
        private set

    fun updateRateLimitStatus() {
        synchronized(this) {
            isRateLimited = true
            rateLimitRetryCount.incrementAndGet()
        }
    }

    fun clearRateLimit() {
        synchronized(this) {
            isRateLimited = false
            rateLimitRetryCount.set(0)
        }
    }

    /**
     * Schedules a task to execute the api calls ofter the given retry after time
     * or the exponential backoff delay calculated from the number of retries.
     */
    fun scheduleRetry(
        scheduledWorker: ScheduledWorker,
        retryAfter: Long?,
        retryMethod: () -> Unit
    ) {
        val retryTask = Runnable {
            retryMethod()
        }
        val delay = calculateDelay(retryAfter)
        scheduledWorker.schedule<Unit>(retryTask, delay, TimeUnit.SECONDS)
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
            base.pow(rateLimitRetryCount.get()).toLong()
        }
    }
}
