package io.embrace.android.embracesdk.internal.session.caching

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.ScheduledWorker
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
    fun start(provider: Provider<Envelope<SessionPayload>?>) {
        scheduledFuture = this.sessionPeriodicCacheScheduledWorker.scheduleWithFixedDelay(
            onPeriodicCache(provider),
            0,
            SESSION_CACHING_INTERVAL.toLong(),
            TimeUnit.SECONDS
        )
    }

    private fun onPeriodicCache(provider: Provider<Envelope<SessionPayload>?>) = Runnable {
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
