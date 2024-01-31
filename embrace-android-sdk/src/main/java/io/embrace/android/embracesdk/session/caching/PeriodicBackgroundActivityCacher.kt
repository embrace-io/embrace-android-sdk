package io.embrace.android.embracesdk.session.caching

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.worker.ScheduledWorker
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.max

internal class PeriodicBackgroundActivityCacher(
    private val clock: Clock,
    private val scheduledWorker: ScheduledWorker,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) {

    companion object {

        /**
         * Minimum time between writes of the background activity to disk
         */
        private const val MIN_INTERVAL_BETWEEN_SAVES: Long = 5000
    }

    private var lastSaved: Long = 0
    private var scheduledFuture: ScheduledFuture<*>? = null

    /**
     * Save the background activity to disk
     */
    fun scheduleSave(provider: () -> SessionMessage?) {
        val delta = clock.now() - lastSaved
        val delay = max(0, MIN_INTERVAL_BETWEEN_SAVES - delta)
        val action: () -> Unit = {
            try {
                provider()
                lastSaved = clock.now()
            } catch (ex: Exception) {
                logger.logDebug("Error while caching active session", ex)
            }
        }
        scheduledFuture = scheduledWorker.schedule<Unit>(
            action,
            delay,
            TimeUnit.MILLISECONDS
        )
    }

    fun stop() {
        scheduledFuture?.cancel(false)
    }
}
