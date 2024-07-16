package io.embrace.android.embracesdk.internal.session.caching

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.ScheduledWorker
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.max

internal class PeriodicBackgroundActivityCacher(
    private val clock: Clock,
    private val scheduledWorker: ScheduledWorker,
    private val logger: EmbLogger
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
    fun scheduleSave(provider: Provider<Envelope<SessionPayload>?>) {
        val delay = calculateDelay()
        val action: () -> Unit = {
            try {
                if (calculateDelay() <= 0) {
                    provider()
                    lastSaved = clock.now()
                }
            } catch (ex: Exception) {
                logger.logWarning("Error while caching active session", ex)
                logger.trackInternalError(InternalErrorType.BG_SESSION_CACHE_FAIL, ex)
            }
        }
        scheduledFuture = scheduledWorker.schedule<Unit>(
            action,
            delay,
            TimeUnit.MILLISECONDS
        )
    }

    private fun calculateDelay(): Long {
        val delta = clock.now() - lastSaved
        return max(0, MIN_INTERVAL_BETWEEN_SAVES - delta)
    }

    fun stop() {
        scheduledFuture?.cancel(false)
    }
}
