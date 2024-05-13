package io.embrace.android.embracesdk.session.caching

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.logging.InternalErrorType
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.worker.ScheduledWorker
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class PeriodicSessionCacher(
    private val sessionPeriodicCacheScheduledWorker: ScheduledWorker,
    private val logger: EmbLogger
) {

    companion object {

        /**
         * Session caching interval in seconds.
         */
        private const val SESSION_CACHING_INTERVAL = 2
    }

    private var scheduledFuture: ScheduledFuture<*>? = null

    /**
     * It starts a background job that will schedule a callback to do periodic caching.
     */
    fun start(provider: Provider<SessionMessage?>) {
        scheduledFuture = this.sessionPeriodicCacheScheduledWorker.scheduleWithFixedDelay(
            onPeriodicCache(provider),
            0,
            SESSION_CACHING_INTERVAL.toLong(),
            TimeUnit.SECONDS
        )
    }

    private fun onPeriodicCache(provider: Provider<SessionMessage?>) = Runnable {
        Systrace.traceSynchronous("snapshot-session") {
            try {
                provider()
            } catch (ex: Exception) {
                logger.logWarning("Error while caching active session", ex)
                logger.trackInternalError(InternalErrorType.FG_SESSION_CACHE_FAIL, ex)
            }
        }
    }

    fun stop() {
        scheduledFuture?.cancel(false)
    }
}
