package io.embrace.android.embracesdk.session.caching

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.worker.ScheduledWorker
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class PeriodicSessionCacher(
    private val clock: Clock,
    private val sessionPeriodicCacheScheduledWorker: ScheduledWorker,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
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
    fun start(provider: () -> SessionMessage?) {
        scheduledFuture = this.sessionPeriodicCacheScheduledWorker.scheduleWithFixedDelay(
            onPeriodicCache(provider),
            0,
            SESSION_CACHING_INTERVAL.toLong(),
            TimeUnit.SECONDS
        )
    }

    private fun onPeriodicCache(provider: () -> SessionMessage?) = Runnable {
        Systrace.trace("snapshot-session") {
            try {
                provider()
            } catch (ex: Exception) {
                logger.logDebug("Error while caching active session", ex)
            }
        }
    }

    fun stop() {
        scheduledFuture?.cancel(false)
    }
}
